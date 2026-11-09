package dev.akiskontrol.app.vpn

import java.net.Inet6Address
import java.net.InetAddress

/** Only advertise Internet IPv6 if the selected uplink can route it. */
internal fun supportsIpv6(addresses: List<InetAddress>, hasIpv6DefaultRoute: Boolean): Boolean =
    hasIpv6DefaultRoute && addresses.any {
        // Deprecated fec0::/10 is site-scoped (RT_SCOPE_SITE in Android), so
        // a default route alone does not make it an Internet-capable source.
        // Unique-local fc00::/7 is different and can be used on a routed/NAT66
        // uplink; Inet6Address.isSiteLocalAddress does not classify it as fec0.
        it is Inet6Address && !it.isAnyLocalAddress && !it.isLoopbackAddress &&
            !it.isLinkLocalAddress && !it.isSiteLocalAddress && !it.isMulticastAddress
    }
