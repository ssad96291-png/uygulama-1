package dev.akiskontrol.core

import org.junit.Assert.*
import org.junit.Test

class TrafficOwnerTest {
    @Test fun mapsOnlySdkSandboxAppIdBoundaries() {
        assertEquals(19_999, appUidForSdkSandbox(19_999))
        assertEquals(10_000, appUidForSdkSandbox(20_000))
        assertEquals(19_999, appUidForSdkSandbox(29_999))
        assertEquals(30_000, appUidForSdkSandbox(30_000))
    }

    @Test fun preservesAndroidUserPrefix() {
        assertEquals(112_345, appUidForSdkSandbox(122_345))
        assertEquals(1_010_000, appUidForSdkSandbox(1_020_000))
        assertEquals(1_019_999, appUidForSdkSandbox(1_029_999))
    }

    @Test fun preservesUnknownSystemOrdinaryAndIsolatedOwners() {
        listOf(Int.MIN_VALUE, -1, 0, 1_000, 10_000, 19_999, 30_000, 90_000,
            99_000, 100_000, 110_000, 130_000, Int.MAX_VALUE).forEach { uid ->
            assertEquals("Unexpected owner conversion for $uid", uid, appUidForSdkSandbox(uid))
        }
    }

    @Test fun sandboxTrafficSharesItsParentsRulesQuotaAndUsage() {
        val parent = 10_345
        val sandboxOwner = appUidForSdkSandbox(20_345)
        val policy = PolicyEngine()
        policy.configure(mapOf(parent to AppRule(parent,
            wifi = NetworkRule(allowed = false), mobile = NetworkRule(quotaBytes = 100))),
            GlobalQuota(), AppSettings(), emptyMap())

        assertEquals(0L, policy.reserve(sandboxOwner, NetworkKind.WIFI, false, 1))
        val sdkTransfer = policy.reserve(sandboxOwner, NetworkKind.MOBILE, false, 80)
        assertTrue(sdkTransfer > 0)
        policy.complete(sdkTransfer, 80)
        val parentTransfer = policy.reserve(parent, NetworkKind.MOBILE, true, 80)
        assertEquals(20, policy.reservedBytes(parentTransfer))
        policy.complete(parentTransfer, 20)
        assertEquals(0L, policy.reserve(sandboxOwner, NetworkKind.MOBILE, false, 1))
        val usage = policy.snapshotUsage().single()
        assertEquals(parent, usage.uid)
        assertEquals(80L, usage.downloadBytes)
        assertEquals(20L, usage.uploadBytes)
    }
}
