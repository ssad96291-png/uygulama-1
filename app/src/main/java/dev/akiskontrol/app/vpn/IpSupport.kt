package dev.akiskontrol.app.vpn

import java.net.Inet6Address
import java.net.InetAddress

/** Only advertise Internet IPv6 if the selected uplink can route it. */
internal fun supportsIpv6(addresses: List<InetAddress>, hasIpv6DefaultRoute: Boolean): Boolean =
    hasIpv6DefaultRoute && addresses.any {
        it is Inet6Address && !it.isAnyLocalAddress && !it.isLoopbackAddress &&
            !it.isLinkLocalAddress && !it.isMulticastAddress
    }
