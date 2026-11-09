package mobile

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"net/netip"
	"strconv"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/xjasonlyu/tun2socks/v2/core"
	"github.com/xjasonlyu/tun2socks/v2/core/adapter"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
)

const (
	maxFlows       = 1024
	tcpIdleTimeout = 5 * time.Minute
	udpIdleTimeout = time.Minute
	writeTimeout   = 30 * time.Second
)

var (
	errDenied           = errors.New("traffic denied by policy")
	errSocketProtection = errors.New("protect/bind outbound socket failed")
)

type engine struct {
	bridge     Bridge
	link       stack.LinkEndpoint
	stack      *stack.Stack
	ctx        context.Context
	cancel     context.CancelFunc
	dialer     net.Dialer
	mu         sync.Mutex
	closed     bool
	flows      map[*flow]struct{}
	wg         sync.WaitGroup
	once       sync.Once
	eventMu    sync.Mutex
	lastEvents map[string]time.Time
}

type flow struct {
	engine  *engine
	local   net.Conn
	remote  net.Conn
	ctx     context.Context
	cancel  context.CancelFunc
	mu      sync.Mutex
	closed  bool
	uid     int
	active  atomic.Int64
	pending atomic.Int32
}

func newEngine(link stack.LinkEndpoint, bridge Bridge) (*engine, error) {
	ctx, cancel := context.WithCancel(context.Background())
	e := &engine{bridge: bridge, link: link, ctx: ctx, cancel: cancel, flows: make(map[*flow]struct{}), lastEvents: make(map[string]time.Time)}
	e.dialer = net.Dialer{Timeout: 15 * time.Second, KeepAlive: 30 * time.Second,
		Control: func(_, _ string, raw syscall.RawConn) error {
			var protected bool
			if err := raw.Control(func(fd uintptr) { protected = bridge.Protect(int(fd)) }); err != nil {
				return err
			}
			if !protected {
				return errSocketProtection
			}
			return nil
		}}
	s, err := core.CreateStack(&core.Config{LinkEndpoint: link, TransportHandler: e})
	if err != nil {
		cancel()
		return nil, fmt.Errorf("create IP stack: %w", err)
	}
	e.stack = s
	return e, nil
}

func (e *engine) stop() {
	e.once.Do(func() {
		e.mu.Lock()
		e.closed = true
		e.cancel()
		flows := make([]*flow, 0, len(e.flows))
		for f := range e.flows {
			flows = append(flows, f)
		}
		e.mu.Unlock()
		// Detaching the TUN closes its duplicate descriptor and joins both
		// packet workers before any descriptor can be reused.
		e.link.Attach(nil)
		for _, f := range flows {
			f.close()
		}
		e.stack.Close()
		e.wg.Wait()
		e.stack.Wait()
		e.link.Close()
	})
}

func (e *engine) begin(local net.Conn, id stack.TransportEndpointID, udp bool) {
	e.mu.Lock()
	if e.closed || len(e.flows) >= maxFlows {
		e.mu.Unlock()
		_ = local.Close()
		return
	}
	ctx, cancel := context.WithCancel(e.ctx)
	f := &flow{engine: e, local: local, ctx: ctx, cancel: cancel}
	f.touch()
	e.flows[f] = struct{}{}
	e.wg.Add(1)
	e.mu.Unlock()
	go func() {
		defer e.wg.Done()
		defer func() {
			f.close()
			e.mu.Lock()
			delete(e.flows, f)
			e.mu.Unlock()
		}()
		protocol, network, idle := 6, "tcp", tcpIdleTimeout
		if udp {
			protocol, network, idle = 17, "udp", udpIdleTimeout
		}
		// Include only fixed categories in maintenance diagnostics. Never retain
		// addresses, DNS questions, port numbers, or payload contents.
		kind := flowKind(id, udp)
		f.uid = e.bridge.Owner(protocol, id.RemoteAddress.String(), int(id.RemotePort), id.LocalAddress.String(), int(id.LocalPort))
		if f.uid < 0 {
			e.event("Uygulama sahibi bulunamadı · " + kind)
			return
		}
		if !f.allowed() {
			return
		}
		host := id.LocalAddress.String()
		if host == "10.111.0.1" && id.LocalPort == 53 {
			dns, err := netip.ParseAddr(e.bridge.DnsServer())
			if err != nil || dns.IsUnspecified() || dns.IsMulticast() || dns.String() == host {
				e.event("Geçerli ağ DNS sunucusu bulunamadı; DNS isteği engellendi")
				return
			}
			host = dns.String()
		}
		remote, err := e.dialer.DialContext(f.ctx, network, net.JoinHostPort(host, strconv.Itoa(int(id.LocalPort))))
		if err != nil {
			if f.ctx.Err() == nil {
				e.event("Dış bağlantı kurulamadı · " + kind + " · " + connectionFailure(err))
			}
			return
		}
		f.mu.Lock()
		if f.closed {
			f.mu.Unlock()
			_ = remote.Close()
			return
		}
		f.remote = remote
		f.mu.Unlock()
		f.touch()
		monitorDone := make(chan struct{})
		go func() { defer close(monitorDone); f.expireIdle(idle) }()
		if err := f.relay(udp); err != nil {
			e.event("Aktarım kesildi · " + kind + " · " + connectionFailure(err))
		}
		f.close()
		<-monitorDone
	}()
}

func flowKind(id stack.TransportEndpointID, udp bool) string {
	protocol, family := "TCP", "IPv4"
	if udp {
		protocol = "UDP"
	}
	if id.LocalAddress.Len() == 16 {
		family = "IPv6"
	}
	kind := protocol + "/" + family
	if id.LocalAddress.String() == "10.111.0.1" && id.LocalPort == 53 {
		kind += "/DNS"
	}
	return kind
}

func connectionFailure(err error) string {
	switch {
	case errors.Is(err, errSocketProtection):
		return "soket koruma/ağa bağlama"
	case errors.Is(err, syscall.ENETUNREACH), errors.Is(err, syscall.EHOSTUNREACH):
		return "ağa erişilemiyor"
	case errors.Is(err, syscall.ECONNREFUSED):
		return "bağlantı reddedildi"
	case errors.Is(err, syscall.EACCES), errors.Is(err, syscall.EPERM):
		return "soket izni reddedildi"
	case errors.Is(err, context.DeadlineExceeded):
		return "zaman aşımı"
	default:
		var netErr net.Error
		if errors.As(err, &netErr) && netErr.Timeout() {
			return "zaman aşımı"
		}
		return "ağ/soket hatası"
	}
}

// The keys are drawn exclusively from the fixed diagnostic categories above.
// Throttle each category separately so an owner-lookup storm cannot hide DNS
// or socket-binding failures, or fill the local maintenance log.
func (e *engine) event(message string) {
	e.eventMu.Lock()
	now := time.Now()
	if now.Sub(e.lastEvents[message]) < 2*time.Second {
		e.eventMu.Unlock()
		return
	}
	e.lastEvents[message] = now
	e.eventMu.Unlock()
	e.bridge.Event(message)
}

func (e *engine) HandleTCP(c adapter.TCPConn) { e.begin(c, *c.ID(), false) }
func (e *engine) HandleUDP(c adapter.UDPConn) { e.begin(c, *c.ID(), true) }

func (f *flow) close() {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.closed {
		return
	}
	f.closed = true
	f.cancel()
	_ = f.local.Close()
	if f.remote != nil {
		_ = f.remote.Close()
	}
}

func (f *flow) touch() { f.active.Store(time.Now().UnixNano()) }

func (f *flow) expireIdle(idle time.Duration) {
	ticker := time.NewTicker(idle / 2)
	defer ticker.Stop()
	for {
		select {
		case <-f.ctx.Done():
			return
		case now := <-ticker.C:
			if f.pending.Load() == 0 && now.Sub(time.Unix(0, f.active.Load())) >= idle {
				f.close()
				return
			}
		}
	}
}

// Check permission before dialing: forbidden applications must not produce
// outbound TCP handshakes. Refunding the probe leaves usage unchanged.
func (f *flow) allowed() bool {
	if f.ctx.Err() != nil {
		return false
	}
	token := f.engine.bridge.Reserve(f.uid, true, 1)
	if token <= 0 {
		return false
	}
	granted := f.engine.bridge.ReservedBytes(token) > 0
	f.engine.bridge.Complete(token, 0)
	return granted
}

func (f *flow) relay(udp bool) error {
	results := make(chan error, 2)
	copyDirection := func(dst, src net.Conn, upload bool) {
		err := f.copy(dst, src, upload, udp)
		if !udp && errors.Is(err, io.EOF) {
			if half, ok := dst.(interface{ CloseWrite() error }); ok {
				_ = half.CloseWrite()
			}
		}
		results <- err
	}
	go copyDirection(f.remote, f.local, true)
	go copyDirection(f.local, f.remote, false)
	first := <-results
	// Capture the failure before close cancels the flow. The caller logs only
	// operational errors; normal EOF, policy denials and shutdown stay quiet.
	report := first
	if f.ctx.Err() != nil || errors.Is(report, io.EOF) || errors.Is(report, errDenied) || errors.Is(report, context.Canceled) || errors.Is(report, net.ErrClosed) {
		report = nil
	}
	if udp || !errors.Is(first, io.EOF) {
		f.close()
	}
	<-results
	return report
}

func (f *flow) copy(dst, src net.Conn, upload, udp bool) error {
	size := 32 * 1024
	if udp {
		size = 65535
	}
	buf := make([]byte, size)
	for {
		n, readErr := src.Read(buf)
		if n > 0 || (udp && readErr == nil) {
			f.touch()
			f.pending.Add(1)
			err := f.forward(dst, buf[:n], upload, udp)
			f.pending.Add(-1)
			f.touch()
			if err != nil {
				return err
			}
		}
		if readErr != nil {
			return readErr
		}
	}
}

func (f *flow) forward(dst net.Conn, data []byte, upload, datagram bool) error {
	bridge := f.engine.bridge
	for {
		if err := f.ctx.Err(); err != nil {
			return err
		}
		requested := len(data)
		if requested == 0 {
			requested = 1 // Empty datagrams still require permission.
		}
		var token int64
		if datagram {
			token = bridge.ReserveDatagram(f.uid, upload, requested)
		} else {
			token = bridge.Reserve(f.uid, upload, requested)
		}
		if token <= 0 {
			return errDenied
		}
		granted := bridge.ReservedBytes(token)
		if granted <= 0 || granted > requested || (datagram && granted < requested) {
			bridge.Complete(token, 0)
			return errDenied
		}
		if err := f.wait(bridge.Delay(token)); err != nil {
			bridge.Complete(token, 0)
			return err
		}
		count := min(granted, len(data))
		_ = dst.SetWriteDeadline(time.Now().Add(writeTimeout))
		n, err := dst.Write(data[:count])
		bridge.Complete(token, n)
		if err != nil {
			return err
		}
		if n != count {
			return io.ErrShortWrite
		}
		data = data[n:]
		if datagram || len(data) == 0 {
			return nil
		}
	}
}

func (f *flow) wait(milliseconds int64) error {
	if milliseconds <= 0 {
		return f.ctx.Err()
	}
	// Bound the conversion to avoid overflowing time.Duration for very low
	// configured rates; policy waits beyond this are still interruptible.
	const maxMilliseconds = int64(^uint64(0)>>1) / int64(time.Millisecond)
	if milliseconds > maxMilliseconds {
		milliseconds = maxMilliseconds
	}
	timer := time.NewTimer(time.Duration(milliseconds) * time.Millisecond)
	defer timer.Stop()
	select {
	case <-f.ctx.Done():
		return f.ctx.Err()
	case <-timer.C:
		return f.ctx.Err()
	}
}
