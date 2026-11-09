package dev.akiskontrol.app.vpn

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpSupportTest {
    private fun addresses(vararg literals: String) = literals.map(InetAddress::getByName)

    @Test fun ipv4UplinkDoesNotAdvertiseIpv6ToBrowsers() {
        assertFalse(supportsIpv6(addresses("192.0.2.10"), false))
        assertFalse(supportsIpv6(addresses("192.0.2.10"), true))
    }

    @Test fun linkLocalOnlyIpv6CannotCarryInternetConnections() {
        assertFalse(supportsIpv6(addresses("fe80::1", "::1", "::", "ff02::1"), true))
    }

    @Test fun addressWithoutDefaultRouteDoesNotAdvertiseInternetIpv6() {
        assertFalse(supportsIpv6(addresses("2001:db8::1"), false))
    }

    @Test fun deprecatedSiteLocalAddressIsNotAnInternetSourceEvenWithDefaultRoute() {
        assertFalse(supportsIpv6(addresses("fec0::15"), true))
        assertFalse(supportsIpv6(addresses("feff::1", "192.0.2.10"), true))
        // A second, globally usable address still supports IPv6 on the uplink.
        assertTrue(supportsIpv6(addresses("fec0::15", "2001:db8::1"), true))
    }

    @Test fun routedIpv6IsRetainedForDualStackAndIpv6OnlyUplinks() {
        assertTrue(supportsIpv6(addresses("192.0.2.10", "2001:db8::1"), true))
        assertTrue(supportsIpv6(addresses("2001:db8::1"), true))
        assertTrue(supportsIpv6(addresses("fd00::1"), true))
    }

    @Test fun missingNetworkInformationDoesNotAdvertiseIpv6() {
        assertFalse(supportsIpv6(emptyList(), false))
        assertFalse(supportsIpv6(emptyList(), true))
    }
}
