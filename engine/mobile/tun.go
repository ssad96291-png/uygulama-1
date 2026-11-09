package mobile

import (
	"context"
	"fmt"
	"os"
	"sync"
	"time"

	"golang.org/x/sys/unix"
	"gvisor.dev/gvisor/pkg/buffer"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/link/channel"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
)

// tunEndpoint owns one duplicate of the Android TUN descriptor. os.File uses
// Go's poller to interrupt both reads and writes when Close closes that duplicate;
// no per-engine eventfd or blocking syscall survives shutdown.
type tunEndpoint struct {
	*channel.Endpoint
	file    *os.File
	mtu     uint32
	ctx     context.Context
	cancel  context.CancelFunc
	mu      sync.Mutex
	closed  bool
	started bool
	wg      sync.WaitGroup
}

var _ stack.LinkEndpoint = (*tunEndpoint)(nil)

func newTUNEndpoint(fd int, mtu uint32) (*tunEndpoint, error) {
	dup, err := unix.FcntlInt(uintptr(fd), unix.F_DUPFD_CLOEXEC, 0)
	if err != nil {
		return nil, fmt.Errorf("duplicate descriptor: %w", err)
	}
	// O_NONBLOCK belongs to the shared open-file description, so it also affects
	// Java's original descriptor. VpnService.Builder.setBlocking(false) already
	// supplies this mode; the Java owner must not switch it back while running.
	if err := unix.SetNonblock(dup, true); err != nil {
		_ = unix.Close(dup)
		return nil, fmt.Errorf("set nonblocking TUN: %w", err)
	}
	file := os.NewFile(uintptr(dup), "akis-tun")
	// Reject descriptors unsupported by Go's poller, rather than starting a read
	// that Close could not interrupt. A zero deadline leaves normal I/O unbounded.
	if err := file.SetDeadline(time.Time{}); err != nil {
		_ = file.Close()
		return nil, fmt.Errorf("register TUN with poller: %w", err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	return &tunEndpoint{
		Endpoint: channel.New(256, mtu, ""), file: file, mtu: mtu,
		ctx: ctx, cancel: cancel,
	}, nil
}

func (e *tunEndpoint) Attach(dispatcher stack.NetworkDispatcher) {
	if dispatcher == nil {
		e.Close()
		return
	}
	e.mu.Lock()
	defer e.mu.Unlock()
	if e.closed {
		return
	}
	e.Endpoint.Attach(dispatcher)
	if !e.started {
		e.started = true
		e.wg.Add(2)
		go func() { defer e.wg.Done(); defer e.shutdown(); e.readPackets() }()
		go func() { defer e.wg.Done(); defer e.shutdown(); e.writePackets() }()
	}
}

// shutdown is also called by workers on I/O failure and must never join itself.
func (e *tunEndpoint) shutdown() {
	e.mu.Lock()
	defer e.mu.Unlock()
	if e.closed {
		return
	}
	e.closed = true
	e.cancel()
	e.Endpoint.Attach(nil)
	_ = e.file.Close()
	e.Endpoint.Close()
}

func (e *tunEndpoint) Close() {
	e.shutdown()
	e.wg.Wait()
}

func (e *tunEndpoint) Wait() { e.wg.Wait() }

func (e *tunEndpoint) readPackets() {
	// The extra byte detects oversized packets instead of injecting truncation.
	data := make([]byte, int(e.mtu)+1)
	for {
		n, err := e.file.Read(data)
		if err != nil {
			return
		}
		if n == 0 || n > int(e.mtu) {
			continue
		}
		var protocol tcpip.NetworkProtocolNumber
		switch header.IPVersion(data[:n]) {
		case header.IPv4Version:
			if n < header.IPv4MinimumSize {
				continue
			}
			protocol = header.IPv4ProtocolNumber
		case header.IPv6Version:
			if n < header.IPv6MinimumSize {
				continue
			}
			protocol = header.IPv6ProtocolNumber
		default:
			continue
		}
		// MakeWithData copies the read buffer, which can then be reused safely.
		packet := stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: buffer.MakeWithData(data[:n])})
		e.InjectInbound(protocol, packet)
		packet.DecRef()
	}
}

func (e *tunEndpoint) writePackets() {
	for {
		packet := e.ReadContext(e.ctx)
		if packet == nil {
			return
		}
		data := packet.ToBuffer()
		packet.DecRef()
		bytes := data.Flatten()
		n, err := e.file.Write(bytes)
		short := n != len(bytes)
		data.Release()
		if err != nil || short {
			return
		}
	}
}
