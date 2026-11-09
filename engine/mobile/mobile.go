// Package mobile provides Akış's Android local VPN transport engine.
// TCP and UDP are terminated by gVisor and relayed through protected native
// sockets. Application payloads, including TLS, are never interpreted.
package mobile

import (
	"errors"
	"fmt"
	"sync"
)

// Bridge is implemented by the Android VpnService. Calls may arrive
// concurrently. All Go int parameters/results bind to Java long.
type Bridge interface {
	// Protect excludes and binds the socket to the current underlying network
	// before connect. Returning false rejects the connection.
	Protect(fd int) bool
	// Owner resolves the original application-side tuple. Unknown owners must
	// return -1; no unknown UID traffic is forwarded.
	Owner(protocol int, sourceIP string, sourcePort int, destinationIP string, destinationPort int) int
	// Reserve atomically grants a TCP payload chunk; ReserveDatagram grants a
	// complete UDP datagram without applying the TCP pacing chunk size limit.
	Reserve(uid int, upload bool, requestedBytes int) int64
	ReserveDatagram(uid int, upload bool, requestedBytes int) int64
	ReservedBytes(token int64) int
	Delay(token int64) int64
	// Complete settles a token exactly once, refunding unused bytes.
	Complete(token int64, actualBytes int)
	// DnsServer returns the current network's numeric DNS address. The stable
	// VPN DNS endpoint 10.111.0.1:53 is mapped to this address on the wire.
	DnsServer() string
	Event(message string)
}

var lifecycle struct {
	sync.Mutex
	current *engine
}

// Start duplicates fd; ownership of the supplied descriptor stays with Java.
// A running engine must be stopped before another one can be started.
func Start(fd int, mtu int, bridge Bridge) error {
	if fd < 0 || mtu < 1280 || mtu > 65535 || bridge == nil {
		return errors.New("invalid TUN descriptor, MTU, or bridge")
	}
	lifecycle.Lock()
	defer lifecycle.Unlock()
	if lifecycle.current != nil {
		return errors.New("VPN engine is already running")
	}
	link, err := newTUNEndpoint(fd, uint32(mtu))
	if err != nil {
		return fmt.Errorf("attach TUN: %w", err)
	}
	e, err := newEngine(link, bridge)
	if err != nil {
		link.Close()
		return err
	}
	lifecycle.current = e
	return nil
}

// Stop cancels pacing, closes flows and the duplicate TUN descriptor, and waits
// for all engine workers. It is safe to call repeatedly.
func Stop() {
	lifecycle.Lock()
	defer lifecycle.Unlock()
	if lifecycle.current == nil {
		return
	}
	lifecycle.current.stop()
	lifecycle.current = nil
}

// Running reports whether Start has succeeded without a subsequent Stop.
func Running() bool {
	lifecycle.Lock()
	defer lifecycle.Unlock()
	return lifecycle.current != nil
}
