package mobile

import (
	"bytes"
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"errors"
	"io"
	"math/big"
	"net"
	"net/netip"
	"strings"
	"sync"
	"testing"
	"time"

	"golang.org/x/sys/unix"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/adapters/gonet"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/link/channel"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv4"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv6"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/tcp"
	"gvisor.dev/gvisor/pkg/tcpip/transport/udp"
)

// These tests send IP packets between two independent gVisor stacks. Only the
// final protected outbound socket uses the host network, against local servers.
// Loopback capabilities let those test links carry 127.0.0.1/::1 destinations.
// Receive checksum offload matches gVisor's real loopback endpoint: loopback
// routes omit transport checksums, and the receiving NIC must accept them;
// production TUN endpoints have their normal non-loopback capabilities.
type integrationHarness struct {
	engine *engine
	client *stack.Stack
}

func newIntegrationHarness(t *testing.T, bridge Bridge) *integrationHarness {
	t.Helper()
	engineLink := channel.New(256, 1500, "")
	engineLink.LinkEPCapabilities = stack.CapabilityLoopback | stack.CapabilityRXChecksumOffload
	e, err := newEngine(engineLink, bridge)
	if err != nil {
		t.Fatal(err)
	}
	clientLink := channel.New(256, 1500, "")
	clientLink.LinkEPCapabilities = stack.CapabilityLoopback | stack.CapabilityRXChecksumOffload
	client := stack.New(stack.Options{
		NetworkProtocols:   []stack.NetworkProtocolFactory{ipv4.NewProtocol, ipv6.NewProtocol},
		TransportProtocols: []stack.TransportProtocolFactory{tcp.NewProtocol, udp.NewProtocol},
	})
	if err := client.CreateNIC(1, clientLink); err != nil {
		t.Fatal(err)
	}
	for _, address := range []struct {
		ip       string
		protocol tcpip.NetworkProtocolNumber
		prefix   int
	}{{"10.78.0.2", ipv4.ProtocolNumber, 24}, {"fd78::2", ipv6.ProtocolNumber, 64}} {
		if err := client.AddProtocolAddress(1, tcpip.ProtocolAddress{
			Protocol:          address.protocol,
			AddressWithPrefix: tcpip.AddressWithPrefix{Address: testIPAddress(address.ip), PrefixLen: address.prefix},
		}, stack.AddressProperties{}); err != nil {
			t.Fatal(err)
		}
	}
	client.SetRouteTable([]tcpip.Route{{Destination: header.IPv4EmptySubnet, NIC: 1}, {Destination: header.IPv6EmptySubnet, NIC: 1}})
	ctx, cancel := context.WithCancel(context.Background())
	var pumps sync.WaitGroup
	pump := func(from, to *channel.Endpoint) {
		defer pumps.Done()
		for {
			packet := from.ReadContext(ctx)
			if packet == nil {
				return
			}
			protocol := packet.NetworkProtocolNumber
			incoming := stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: packet.ToBuffer()})
			packet.DecRef()
			to.InjectInbound(protocol, incoming)
			incoming.DecRef()
		}
	}
	pumps.Add(2)
	go pump(engineLink, clientLink)
	go pump(clientLink, engineLink)
	t.Cleanup(func() {
		e.stop()
		cancel()
		client.Close()
		clientLink.Close()
		pumps.Wait()
		client.Wait()
	})
	return &integrationHarness{engine: e, client: client}
}

func testIPAddress(s string) tcpip.Address {
	ip := netip.MustParseAddr(s)
	if ip.Is4() {
		return tcpip.AddrFrom4(ip.As4())
	}
	return tcpip.AddrFrom16(ip.As16())
}

func (h *integrationHarness) dial(t *testing.T, network, address string) net.Conn {
	t.Helper()
	ap := netip.MustParseAddrPort(address)
	protocol := ipv4.ProtocolNumber
	if ap.Addr().Is6() {
		protocol = ipv6.ProtocolNumber
	}
	remote := tcpip.FullAddress{NIC: 1, Addr: testIPAddress(ap.Addr().String()), Port: ap.Port()}
	var conn net.Conn
	var err error
	if network == "tcp" {
		ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
		defer cancel()
		conn, err = gonet.DialContextTCP(ctx, h.client, remote, protocol)
	} else {
		conn, err = gonet.DialUDP(h.client, nil, &remote, protocol)
	}
	if err != nil {
		t.Fatalf("dial %s %s: %v", network, address, err)
	}
	t.Cleanup(func() { conn.Close() })
	if err := conn.SetDeadline(time.Now().Add(3 * time.Second)); err != nil {
		t.Fatal(err)
	}
	return conn
}

type bridgeReservation struct {
	bytes  int
	upload bool
}

type integrationOwner struct {
	protocol        int
	source          string
	sourcePort      int
	destination     string
	destinationPort int
}

type integrationBridge struct {
	mu                     sync.Mutex
	uid                    int
	protect                bool
	protectCalls           int
	connectedWhenProtected bool
	dnsServer              string
	owners                 []integrationOwner
	next                   int64
	active                 map[int64]bridgeReservation
	uploadBudget           int // -1 means unlimited; refunds return unused reserved bytes.
	maxChunk               int
	delayMS                int64
	uploaded               int
	downloaded             int
	refunded               int
	events                 []string
}

func newIntegrationBridge() *integrationBridge {
	return &integrationBridge{uid: 10042, protect: true, dnsServer: "127.0.0.1", active: make(map[int64]bridgeReservation), uploadBudget: -1}
}

func (b *integrationBridge) Protect(fd int) bool {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.protectCalls++
	if _, err := unix.Getpeername(fd); !errors.Is(err, unix.ENOTCONN) {
		b.connectedWhenProtected = true
	}
	return b.protect
}
func (b *integrationBridge) Owner(protocol int, source string, sourcePort int, destination string, destinationPort int) int {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.owners = append(b.owners, integrationOwner{protocol, source, sourcePort, destination, destinationPort})
	return b.uid
}
func (b *integrationBridge) Reserve(uid int, upload bool, requested int) int64 {
	return b.reserve(uid, upload, requested, true)
}
func (b *integrationBridge) ReserveDatagram(uid int, upload bool, requested int) int64 {
	return b.reserve(uid, upload, requested, false)
}
func (b *integrationBridge) reserve(uid int, upload bool, requested int, stream bool) int64 {
	b.mu.Lock()
	defer b.mu.Unlock()
	if uid < 0 || requested <= 0 {
		return 0
	}
	granted := requested
	if stream && b.maxChunk > 0 && granted > b.maxChunk {
		granted = b.maxChunk
	}
	if upload && b.uploadBudget >= 0 {
		if granted > b.uploadBudget {
			granted = b.uploadBudget
		}
		b.uploadBudget -= granted
	}
	if granted == 0 {
		return 0
	}
	b.next++
	b.active[b.next] = bridgeReservation{bytes: granted, upload: upload}
	return b.next
}
func (b *integrationBridge) ReservedBytes(token int64) int {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.active[token].bytes
}
func (b *integrationBridge) Delay(token int64) int64 {
	b.mu.Lock()
	defer b.mu.Unlock()
	// A one-byte admission reservation must not delay the handshake.
	if b.active[token].bytes > 1 {
		return b.delayMS
	}
	return 0
}
func (b *integrationBridge) Complete(token int64, actual int) {
	b.mu.Lock()
	defer b.mu.Unlock()
	r, ok := b.active[token]
	if !ok {
		return
	}
	delete(b.active, token)
	b.refunded += r.bytes - actual
	if r.upload {
		b.uploaded += actual
		if b.uploadBudget >= 0 {
			b.uploadBudget += r.bytes - actual
		}
	} else {
		b.downloaded += actual
	}
}
func (b *integrationBridge) Event(message string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.events = append(b.events, message)
}
func (b *integrationBridge) DnsServer() string {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.dnsServer
}

func waitForIntegration(t *testing.T, why string, condition func() bool) {
	t.Helper()
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		if condition() {
			return
		}
		time.Sleep(5 * time.Millisecond)
	}
	t.Fatalf("timed out waiting for %s", why)
}

func TestEngineTCPAndUDPIPv4IPv6(t *testing.T) {
	for _, network := range []string{"tcp", "udp"} {
		for _, ip := range []string{"127.0.0.1", "::1"} {
			t.Run(network+"/"+ip, func(t *testing.T) {
				bridge := newIntegrationBridge()
				bridge.maxChunk = 317 // Ensure a TCP buffer can require several reservations.
				h := newIntegrationHarness(t, bridge)
				address := startIntegrationEcho(t, network, ip)
				conn := h.dial(t, network, address)
				payload := bytes.Repeat([]byte("akis-ip-forwarding!"), 200)
				if _, err := conn.Write(payload); err != nil {
					t.Fatal(err)
				}
				response := make([]byte, len(payload)+1)
				var n int
				var err error
				if network == "tcp" {
					n, err = io.ReadFull(conn, response[:len(payload)])
				} else {
					n, err = conn.Read(response)
				}
				if err != nil || !bytes.Equal(response[:n], payload) {
					t.Fatalf("echo = %d bytes, error %v; want %d exact bytes", n, err, len(payload))
				}
				waitForIntegration(t, "settled payload accounting", func() bool {
					bridge.mu.Lock()
					defer bridge.mu.Unlock()
					return bridge.uploaded == len(payload) && bridge.downloaded == len(payload) && len(bridge.active) == 0
				})
				bridge.mu.Lock()
				defer bridge.mu.Unlock()
				if bridge.protectCalls == 0 || len(bridge.owners) == 0 {
					t.Fatal("outbound traffic bypassed socket protection or owner lookup")
				}
				if bridge.connectedWhenProtected {
					t.Fatal("outbound socket was already connected when protection was requested")
				}
				wantProtocol := 6
				if network == "udp" {
					wantProtocol = 17
				}
				owner := bridge.owners[0]
				if owner.protocol != wantProtocol {
					t.Fatalf("owner protocol = %d, want %d", owner.protocol, wantProtocol)
				}
				wantSource := "10.78.0.2"
				if ip == "::1" {
					wantSource = "fd78::2"
				}
				remote := netip.MustParseAddrPort(address)
				if owner.source != wantSource || owner.sourcePort <= 0 || owner.destination != ip || owner.destinationPort != int(remote.Port()) {
					t.Fatalf("owner lookup received incorrect application tuple: %+v", owner)
				}
			})
		}
	}
}

// Plaintext echo tests do not exercise TLS record boundaries, certificate
// validation, handshake round trips, or encrypted records larger than the TUN
// MTU. Use the same client/server verification a browser connection requires.
func TestEngineTLSIPv4IPv6LargeTransfer(t *testing.T) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	now := time.Now()
	template := &x509.Certificate{
		SerialNumber: big.NewInt(1), Subject: pkix.Name{CommonName: "akis.test.invalid"},
		NotBefore: now.Add(-time.Hour), NotAfter: now.Add(time.Hour),
		KeyUsage: x509.KeyUsageDigitalSignature, ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		DNSNames: []string{"akis.test.invalid"},
	}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if err != nil {
		t.Fatal(err)
	}
	certificate, err := x509.ParseCertificate(der)
	if err != nil {
		t.Fatal(err)
	}
	roots := x509.NewCertPool()
	roots.AddCert(certificate)
	serverConfig := &tls.Config{MinVersion: tls.VersionTLS12, Certificates: []tls.Certificate{{Certificate: [][]byte{der}, PrivateKey: key}}}
	for _, ip := range []string{"127.0.0.1", "::1"} {
		for _, version := range []uint16{tls.VersionTLS12, tls.VersionTLS13} {
			t.Run(ip+"/"+tls.VersionName(version), func(t *testing.T) {
				listener, err := tls.Listen("tcp", net.JoinHostPort(ip, "0"), serverConfig)
				if err != nil {
					t.Fatal(err)
				}
				t.Cleanup(func() { listener.Close() })
				serverDone := make(chan error, 1)
				go func() {
					conn, err := listener.Accept()
					if err != nil {
						serverDone <- err
						return
					}
					defer conn.Close()
					conn.SetDeadline(time.Now().Add(5 * time.Second))
					_, err = io.Copy(conn, conn)
					serverDone <- err
				}()
				bridge := newIntegrationBridge()
				bridge.maxChunk = 317
				h := newIntegrationHarness(t, bridge)
				raw := h.dial(t, "tcp", listener.Addr().String())
				secure := tls.Client(raw, &tls.Config{RootCAs: roots, ServerName: "akis.test.invalid", MinVersion: version, MaxVersion: version})
				if err := secure.Handshake(); err != nil {
					t.Fatalf("validated TLS handshake failed: %v", err)
				}
				payload := bytes.Repeat([]byte("encrypted-browser-transfer"), 8192)
				writeDone := make(chan error, 1)
				go func() { _, err := secure.Write(payload); writeDone <- err }()
				response := make([]byte, len(payload))
				if _, err := io.ReadFull(secure, response); err != nil || !bytes.Equal(response, payload) {
					t.Fatalf("TLS payload mismatch/error: %v", err)
				}
				if err := <-writeDone; err != nil {
					t.Fatal(err)
				}
				secure.Close()
				if err := <-serverDone; err != nil {
					t.Fatalf("TLS shutdown failed: %v", err)
				}
				waitForIntegration(t, "TLS bytes charged and settled", func() bool {
					bridge.mu.Lock()
					defer bridge.mu.Unlock()
					return bridge.uploaded > len(payload) && bridge.downloaded > len(payload) && len(bridge.active) == 0
				})
			})
		}
	}
}

func startIntegrationEcho(t *testing.T, network, ip string) string {
	return startIntegrationEchoAt(t, network, net.JoinHostPort(ip, "0"))
}

func startIntegrationEchoAt(t *testing.T, network, address string) string {
	t.Helper()
	if network == "tcp" {
		listener, err := net.Listen("tcp", address)
		if err != nil {
			t.Fatal(err)
		}
		t.Cleanup(func() { listener.Close() })
		go func() {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			defer conn.Close()
			conn.SetDeadline(time.Now().Add(5 * time.Second))
			io.Copy(conn, conn)
		}()
		return listener.Addr().String()
	}
	conn, err := net.ListenPacket("udp", address)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { conn.Close() })
	go func() {
		buffer := make([]byte, 65535)
		for {
			n, peer, err := conn.ReadFrom(buffer)
			if err != nil {
				return
			}
			if _, err := conn.WriteTo(buffer[:n], peer); err != nil {
				return
			}
		}
	}()
	return conn.LocalAddr().String()
}

func TestEngineUnknownOwnerFailsClosed(t *testing.T) {
	bridge := newIntegrationBridge()
	bridge.uid = -1
	h := newIntegrationHarness(t, bridge)
	conn := h.dial(t, "udp", startIntegrationEcho(t, "udp", "127.0.0.1"))
	if _, err := conn.Write([]byte("must not leave the VPN")); err != nil {
		t.Fatal(err)
	}
	waitForIntegration(t, "owner lookup", func() bool {
		bridge.mu.Lock()
		defer bridge.mu.Unlock()
		return len(bridge.owners) > 0
	})
	h.engine.stop()
	bridge.mu.Lock()
	defer bridge.mu.Unlock()
	if bridge.protectCalls != 0 || bridge.uploaded != 0 || bridge.downloaded != 0 || len(bridge.active) != 0 {
		t.Fatalf("unknown owner reached outbound socket or quota accounting: protected=%d upload=%d download=%d active=%d", bridge.protectCalls, bridge.uploaded, bridge.downloaded, len(bridge.active))
	}
	if len(bridge.events) != 1 || bridge.events[0] != "Uygulama sahibi bulunamadı · UDP/IPv4" {
		t.Fatalf("unknown owner was not diagnosed without endpoint data: %q", bridge.events)
	}
}

func TestEngineTCPPartialQuotaForwardsOnlyAuthorizedBytes(t *testing.T) {
	bridge := newIntegrationBridge()
	bridge.uploadBudget = 5
	bridge.maxChunk = 3
	h := newIntegrationHarness(t, bridge)
	listener, err := net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { listener.Close() })
	received := make(chan []byte, 1)
	go func() {
		conn, err := listener.Accept()
		if err != nil {
			return
		}
		defer conn.Close()
		conn.SetDeadline(time.Now().Add(3 * time.Second))
		data, _ := io.ReadAll(conn)
		received <- data
	}()
	conn := h.dial(t, "tcp", listener.Addr().String())
	if _, err := conn.Write([]byte("0123456789")); err != nil {
		t.Fatal(err)
	}
	select {
	case data := <-received:
		if string(data) != "01234" {
			t.Fatalf("upstream received %q, want exactly authorized prefix 01234", data)
		}
	case <-time.After(4 * time.Second):
		t.Fatal("quota exhaustion did not close TCP flow")
	}
	waitForIntegration(t, "partial TCP quota settlement", func() bool {
		bridge.mu.Lock()
		defer bridge.mu.Unlock()
		return bridge.uploaded == 5 && bridge.uploadBudget == 0 && len(bridge.active) == 0
	})
}

func TestEnginePartialUDPQuotaDropsWholeDatagram(t *testing.T) {
	bridge := newIntegrationBridge()
	bridge.uploadBudget = 5
	h := newIntegrationHarness(t, bridge)
	conn := h.dial(t, "udp", startIntegrationEcho(t, "udp", "127.0.0.1"))
	if _, err := conn.Write([]byte("0123456789")); err != nil {
		t.Fatal(err)
	}
	waitForIntegration(t, "partial datagram refund", func() bool {
		bridge.mu.Lock()
		defer bridge.mu.Unlock()
		return bridge.refunded >= 5 && len(bridge.active) == 0
	})
	if err := conn.SetReadDeadline(time.Now().Add(80 * time.Millisecond)); err != nil {
		t.Fatal(err)
	}
	if n, err := conn.Read(make([]byte, 64)); n != 0 || err == nil {
		t.Fatalf("partially authorized UDP datagram escaped: bytes=%d error=%v", n, err)
	}
	h.engine.stop()
	bridge.mu.Lock()
	defer bridge.mu.Unlock()
	if bridge.uploaded != 0 || bridge.downloaded != 0 || bridge.uploadBudget != 5 {
		t.Fatalf("UDP quota not refunded: upload=%d download=%d budget=%d", bridge.uploaded, bridge.downloaded, bridge.uploadBudget)
	}
}

func TestEngineStopInterruptsPacingAndRefunds(t *testing.T) {
	bridge := newIntegrationBridge()
	bridge.delayMS = 60_000
	h := newIntegrationHarness(t, bridge)
	conn := h.dial(t, "udp", startIntegrationEcho(t, "udp", "127.0.0.1"))
	if _, err := conn.Write([]byte("interrupt this delayed datagram")); err != nil {
		t.Fatal(err)
	}
	waitForIntegration(t, "delayed quota reservation", func() bool {
		bridge.mu.Lock()
		defer bridge.mu.Unlock()
		for _, reservation := range bridge.active {
			if reservation.bytes > 1 {
				return true
			}
		}
		return false
	})
	stopped := make(chan struct{})
	go func() {
		h.engine.stop()
		close(stopped)
	}()
	select {
	case <-stopped:
	case <-time.After(2 * time.Second):
		t.Fatal("stop did not interrupt pacing wait")
	}
	bridge.mu.Lock()
	defer bridge.mu.Unlock()
	if bridge.uploaded != 0 || bridge.downloaded != 0 || len(bridge.active) != 0 {
		t.Fatalf("stop left an unsettled reservation: upload=%d download=%d active=%d", bridge.uploaded, bridge.downloaded, len(bridge.active))
	}
}

func TestEngineSocketProtectionFailureFailsClosed(t *testing.T) {
	bridge := newIntegrationBridge()
	bridge.protect = false
	h := newIntegrationHarness(t, bridge)
	conn := h.dial(t, "udp", startIntegrationEcho(t, "udp", "127.0.0.1"))
	if _, err := conn.Write([]byte("never send an unprotected socket")); err != nil {
		t.Fatal(err)
	}
	waitForIntegration(t, "socket protection failure diagnostic", func() bool {
		bridge.mu.Lock()
		defer bridge.mu.Unlock()
		return bridge.protectCalls > 0 && len(bridge.events) > 0
	})
	h.engine.stop()
	bridge.mu.Lock()
	defer bridge.mu.Unlock()
	if bridge.uploaded != 0 || bridge.downloaded != 0 || len(bridge.active) != 0 {
		t.Fatalf("protection failure forwarded or leaked quota: upload=%d download=%d active=%d", bridge.uploaded, bridge.downloaded, len(bridge.active))
	}
	if bridge.events[0] != "Dış bağlantı kurulamadı · UDP/IPv4 · soket koruma/ağa bağlama" {
		t.Fatalf("protection error was not categorized: %q", bridge.events)
	}
}

func TestEngineUnknownDNSOwnerIsDiagnosedAndNeverBypassesRules(t *testing.T) {
	bridge := newIntegrationBridge()
	bridge.uid = -1
	h := newIntegrationHarness(t, bridge)
	for i := 0; i < 4; i++ {
		conn := h.dial(t, "udp", "10.111.0.1:53")
		if _, err := conn.Write([]byte("private query contents")); err != nil {
			t.Fatal(err)
		}
	}
	waitForIntegration(t, "all DNS ownership failures", func() bool {
		bridge.mu.Lock()
		defer bridge.mu.Unlock()
		return len(bridge.owners) == 4
	})
	h.engine.stop()
	bridge.mu.Lock()
	defer bridge.mu.Unlock()
	if bridge.protectCalls != 0 || bridge.uploaded != 0 || bridge.downloaded != 0 {
		t.Fatal("unknown DNS owner was allowed around application rules")
	}
	if len(bridge.events) != 1 || bridge.events[0] != "Uygulama sahibi bulunamadı · UDP/IPv4/DNS" {
		t.Fatalf("DNS failure storm was not bounded and categorized: %q", bridge.events)
	}
}

func TestEngineFailedDNSUpstreamDiagnosedWithoutEndpoint(t *testing.T) {
	bridge := newIntegrationBridge()
	bridge.dnsServer = "127.0.0.54"
	h := newIntegrationHarness(t, bridge)
	// Reserve the port without listening, so TCP refusal is deterministic and
	// independent of whether the host has a system DNS server on port 53.
	fd, err := unix.Socket(unix.AF_INET, unix.SOCK_STREAM|unix.SOCK_CLOEXEC, 0)
	if err != nil {
		t.Fatal(err)
	}
	defer unix.Close(fd)
	if err := unix.Bind(fd, &unix.SockaddrInet4{Port: 53, Addr: [4]byte{127, 0, 0, 54}}); err != nil {
		t.Fatal(err)
	}
	conn := h.dial(t, "tcp", "10.111.0.1:53")
	_, _ = conn.Write([]byte("private query contents"))
	waitForIntegration(t, "DNS upstream connection diagnostic", func() bool {
		bridge.mu.Lock()
		defer bridge.mu.Unlock()
		return len(bridge.events) > 0
	})
	h.engine.stop()
	bridge.mu.Lock()
	defer bridge.mu.Unlock()
	if bridge.uploaded != 0 || bridge.downloaded != 0 || len(bridge.active) != 0 {
		t.Fatal("failed DNS upstream forwarded or leaked reservation")
	}
	if bridge.events[0] != "Dış bağlantı kurulamadı · TCP/IPv4/DNS · bağlantı reddedildi" {
		t.Fatalf("missing categorized DNS failure: %q", bridge.events)
	}
	for _, message := range bridge.events {
		if strings.Contains(message, "127.0.") || strings.Contains(message, "10.111.") || strings.Contains(message, "private") {
			t.Fatalf("diagnostic leaked endpoint or query: %q", message)
		}
	}
}

func TestEngineDNSAliasUsesUnderlyingServerAndOriginalOwnerTuple(t *testing.T) {
	for _, network := range []string{"tcp", "udp"} {
		for _, dnsIP := range []string{"127.0.0.53", "::1"} {
			t.Run(network+"/"+dnsIP, func(t *testing.T) {
				bridge := newIntegrationBridge()
				bridge.dnsServer = dnsIP
				startIntegrationEchoAt(t, network, net.JoinHostPort(dnsIP, "53"))
				h := newIntegrationHarness(t, bridge)
				conn := h.dial(t, network, "10.111.0.1:53")
				payload := []byte("opaque DNS payload")
				if _, err := conn.Write(payload); err != nil {
					t.Fatal(err)
				}
				response := make([]byte, len(payload))
				if _, err := io.ReadFull(conn, response); err != nil || !bytes.Equal(response, payload) {
					t.Fatalf("DNS alias response = %q, error=%v", response, err)
				}
				bridge.mu.Lock()
				defer bridge.mu.Unlock()
				if len(bridge.owners) != 1 || bridge.owners[0].destination != "10.111.0.1" || bridge.owners[0].destinationPort != 53 {
					t.Fatalf("DNS lookup did not retain application destination: %+v", bridge.owners)
				}
			})
		}
	}
}

func TestEngineInvalidUnderlyingDNSFailsClosed(t *testing.T) {
	for _, dnsIP := range []string{"", "host.invalid", "0.0.0.0", "::", "224.0.0.1", "ff02::1", "10.111.0.1"} {
		t.Run(dnsIP, func(t *testing.T) {
			bridge := newIntegrationBridge()
			bridge.dnsServer = dnsIP
			h := newIntegrationHarness(t, bridge)
			conn := h.dial(t, "udp", "10.111.0.1:53")
			if _, err := conn.Write([]byte("DNS query")); err != nil {
				t.Fatal(err)
			}
			waitForIntegration(t, "DNS owner lookup", func() bool {
				bridge.mu.Lock()
				defer bridge.mu.Unlock()
				return len(bridge.owners) != 0
			})
			h.engine.stop()
			bridge.mu.Lock()
			defer bridge.mu.Unlock()
			if bridge.protectCalls != 0 || bridge.uploaded != 0 || len(bridge.active) != 0 {
				t.Fatalf("invalid DNS opened socket or leaked quota: protected=%d uploaded=%d active=%d", bridge.protectCalls, bridge.uploaded, len(bridge.active))
			}
		})
	}
}

func TestEnginePacingDelaysForwarding(t *testing.T) {
	bridge := newIntegrationBridge()
	bridge.delayMS = 80
	h := newIntegrationHarness(t, bridge)
	conn := h.dial(t, "udp", startIntegrationEcho(t, "udp", "127.0.0.1"))
	start := time.Now()
	payload := []byte("paced datagram")
	if _, err := conn.Write(payload); err != nil {
		t.Fatal(err)
	}
	response := make([]byte, len(payload))
	if _, err := io.ReadFull(conn, response); err != nil || !bytes.Equal(response, payload) {
		t.Fatalf("paced response = %q, error=%v", response, err)
	}
	if elapsed := time.Since(start); elapsed < 2*80*time.Millisecond {
		t.Fatalf("both forwarding directions must observe policy delay; elapsed=%s", elapsed)
	}
}
