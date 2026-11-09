package mobile

import (
	"bytes"
	"errors"
	"os"
	"testing"
	"time"

	"golang.org/x/sys/unix"
	"gvisor.dev/gvisor/pkg/buffer"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
)

// A datagram socket pair preserves the packet boundaries of an Android TUN
// while exercising the real duplicated descriptor and Go poller on the host.
func tunSocketPair(t *testing.T) [2]int {
	t.Helper()
	fds, err := unix.Socketpair(unix.AF_UNIX, unix.SOCK_DGRAM|unix.SOCK_CLOEXEC|unix.SOCK_NONBLOCK, 0)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		_ = unix.Close(fds[0])
		_ = unix.Close(fds[1])
	})
	return fds
}

type tunReceivedPacket struct {
	protocol tcpip.NetworkProtocolNumber
	packet   *stack.PacketBuffer
}

type tunTestDispatcher struct{ packets chan tunReceivedPacket }

func (d *tunTestDispatcher) DeliverNetworkPacket(protocol tcpip.NetworkProtocolNumber, packet *stack.PacketBuffer) {
	packet.IncRef()
	d.packets <- tunReceivedPacket{protocol, packet}
}

func (*tunTestDispatcher) DeliverLinkPacket(tcpip.NetworkProtocolNumber, *stack.PacketBuffer) {}

func closeTUNWithinDeadline(t *testing.T, endpoint *tunEndpoint) {
	t.Helper()
	done := make(chan struct{})
	go func() {
		endpoint.Close()
		close(done)
	}()
	select {
	case <-done:
	case <-time.After(2 * time.Second):
		t.Fatal("TUN Close did not interrupt I/O and join workers")
	}
}

func TestTUNCloseInterruptsReadAndPreservesOriginalDescriptor(t *testing.T) {
	fds := tunSocketPair(t)
	endpoint, err := newTUNEndpoint(fds[0], 1500)
	if err != nil {
		t.Fatal(err)
	}
	endpoint.Attach(&tunTestDispatcher{packets: make(chan tunReceivedPacket, 1)})
	closeTUNWithinDeadline(t, endpoint)
	closeTUNWithinDeadline(t, endpoint) // Closing twice must also return promptly.
	if _, err := endpoint.file.Read(make([]byte, 1)); !errors.Is(err, os.ErrClosed) {
		t.Fatalf("duplicate descriptor read error = %v, want closed", err)
	}
	payload := []byte("original descriptor remains owned by caller")
	if _, err := unix.Write(fds[1], payload); err != nil {
		t.Fatal(err)
	}
	response := make([]byte, 128)
	n, err := unix.Read(fds[0], response)
	if err != nil || !bytes.Equal(response[:n], payload) {
		t.Fatalf("caller descriptor after Close = %q, %v", response[:max(n, 0)], err)
	}
}

func TestTUNCloseInterruptsSaturatedWrite(t *testing.T) {
	fds := tunSocketPair(t)
	if err := unix.SetsockoptInt(fds[0], unix.SOL_SOCKET, unix.SO_SNDBUF, 4096); err != nil {
		t.Fatal(err)
	}
	payload := bytes.Repeat([]byte{0x45}, 1400)
	for {
		_, err := unix.Write(fds[0], payload)
		if errors.Is(err, unix.EAGAIN) {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
	}
	endpoint, err := newTUNEndpoint(fds[0], 1500)
	if err != nil {
		t.Fatal(err)
	}
	var packets stack.PacketBufferList
	packets.PushBack(stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: buffer.MakeWithData(payload)}))
	n, writeErr := endpoint.WritePackets(packets)
	packets.DecRef()
	if writeErr != nil || n != 1 {
		endpoint.Close()
		t.Fatalf("queue outgoing packet = %d, %v", n, writeErr)
	}
	endpoint.Attach(&tunTestDispatcher{packets: make(chan tunReceivedPacket, 1)})
	waitForIntegration(t, "TUN writer to consume queued packet", func() bool { return endpoint.NumQueued() == 0 })
	closeTUNWithinDeadline(t, endpoint)
}

func TestTUNPacketsRetainBytesAfterReadBufferReuse(t *testing.T) {
	fds := tunSocketPair(t)
	endpoint, err := newTUNEndpoint(fds[0], 1500)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(endpoint.Close)
	dispatcher := &tunTestDispatcher{packets: make(chan tunReceivedPacket, 2)}
	endpoint.Attach(dispatcher)
	want := [][]byte{bytes.Repeat([]byte{0x45}, 64), bytes.Repeat([]byte{0x60}, 128)}
	var received []tunReceivedPacket
	t.Cleanup(func() {
		for _, packet := range received {
			packet.packet.DecRef()
		}
	})
	for _, payload := range want {
		if _, err := unix.Write(fds[1], payload); err != nil {
			t.Fatal(err)
		}
		select {
		case packet := <-dispatcher.packets:
			received = append(received, packet)
		case <-time.After(2 * time.Second):
			t.Fatal("TUN reader did not deliver packet")
		}
	}
	// Both reads have completed before inspecting either retained packet.
	for i, packet := range received {
		data := packet.packet.ToBuffer()
		got := data.Flatten()
		equal := bytes.Equal(got, want[i])
		data.Release()
		if !equal {
			t.Fatalf("packet %d changed after TUN reused its read buffer", i)
		}
	}
	if received[0].protocol != header.IPv4ProtocolNumber || received[1].protocol != header.IPv6ProtocolNumber {
		t.Fatal("TUN did not preserve IPv4/IPv6 protocol selection")
	}
}

func TestTUNRejectsInvalidAndUnpollableDescriptors(t *testing.T) {
	if endpoint, err := newTUNEndpoint(-1, 1500); err == nil {
		endpoint.Close()
		t.Fatal("invalid descriptor accepted")
	}
	file, err := os.CreateTemp(t.TempDir(), "unpollable")
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	if endpoint, err := newTUNEndpoint(int(file.Fd()), 1500); err == nil {
		endpoint.Close()
		t.Fatal("regular file accepted despite non-interruptible poller I/O")
	}
	if _, err := file.WriteString("still owned by caller"); err != nil {
		t.Fatalf("failed startup closed original descriptor: %v", err)
	}
}
