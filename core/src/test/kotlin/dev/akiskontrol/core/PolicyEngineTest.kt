package dev.akiskontrol.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PolicyEngineTest {
    private class Fixture {
        var now = Instant.parse("2026-11-06T10:00:00Z").toEpochMilli()
        var nano = 1_000_000_000L
        val engine = PolicyEngine({ now }, { nano }, ZoneId.of("UTC"))
        fun config(
            wifi: NetworkRule = NetworkRule(),
            mobile: NetworkRule = NetworkRule(),
            global: GlobalQuota = GlobalQuota(),
            temporary: Map<Int, Long> = emptyMap(),
            settings: AppSettings = AppSettings(),
        ) = engine.configure(mapOf(10001 to AppRule(10001, wifi, mobile)), global, settings, temporary)
    }

    @Test fun unsupportedNetworkAndUnknownOwnerFailClosed() {
        val e = Fixture().engine
        assertEquals(0L, e.reserve(-1, NetworkKind.WIFI, false, 100))
        assertEquals(0L, e.reserve(10001, NetworkKind.NONE, false, 100))
        assertEquals(0L, e.reserve(10001, NetworkKind.OTHER, false, 100))
        assertEquals(0L, e.reserve(10001, NetworkKind.WIFI, false, -1))
        assertEquals(0L, e.reserve(10001, NetworkKind.WIFI, false, 0))
    }

    @Test fun wifiAndMobileRulesAreIndependent() {
        val f = Fixture()
        f.config(wifi = NetworkRule(allowed = false), mobile = NetworkRule())
        assertEquals(0L, f.engine.reserve(10001, NetworkKind.WIFI, false, 100))
        assertTrue(f.engine.reserve(10001, NetworkKind.MOBILE, false, 100) > 0L)
    }

    @Test fun defaultsApplyToNewApplications() {
        val f = Fixture()
        f.config(settings = AppSettings(defaultMobileAllowed = false))
        assertEquals(0L, f.engine.reserve(20002, NetworkKind.MOBILE, false, 100))
        assertTrue(f.engine.reserve(20002, NetworkKind.WIFI, false, 100) > 0)
    }

    @Test fun quotaReturnsFinalPartialChunkAndRefundsFailures() {
        val f = Fixture()
        f.config(wifi = NetworkRule(quotaBytes = 100))
        val first = f.engine.reserve(10001, NetworkKind.WIFI, false, 80)
        val second = f.engine.reserve(10001, NetworkKind.WIFI, true, 80)
        assertEquals(80, f.engine.reservedBytes(first))
        assertEquals(20, f.engine.reservedBytes(second))
        assertEquals(0L, f.engine.reserve(10001, NetworkKind.WIFI, false, 1))
        f.engine.complete(first, 60)
        f.engine.complete(second, 0)
        val third = f.engine.reserve(10001, NetworkKind.WIFI, false, 100)
        assertEquals(40, f.engine.reservedBytes(third))
        f.engine.complete(third, 40)
        assertEquals(100L, f.engine.snapshotUsage().single().downloadBytes)
        assertEquals(0L, f.engine.snapshotUsage().single().uploadBytes)
    }

    @Test fun simultaneousFlowsCannotOverspendGlobalQuota() {
        val f = Fixture()
        f.config(global = GlobalQuota(wifiBytes = 999))
        val workers = Executors.newFixedThreadPool(12)
        val begin = CountDownLatch(1)
        val jobs = (1..100).map { id -> workers.submit<Int> {
            begin.await()
            val token = f.engine.reserve(10000 + id, NetworkKind.WIFI, false, 100)
            val count = f.engine.reservedBytes(token)
            f.engine.complete(token, count)
            count
        } }
        begin.countDown()
        assertEquals(999, jobs.sumOf { it.get(10, TimeUnit.SECONDS) })
        workers.shutdownNow()
        assertEquals(999L, f.engine.snapshotUsage().sumOf { it.downloadBytes })
        assertEquals(0, f.engine.pendingReservationCount())
    }

    @Test fun globalAndApplicationQuotasBothApply() {
        val f = Fixture()
        f.config(wifi = NetworkRule(quotaBytes = 70), global = GlobalQuota(wifiBytes = 90))
        val a = f.engine.reserve(10001, NetworkKind.WIFI, false, 100)
        assertEquals(70, f.engine.reservedBytes(a))
        val b = f.engine.reserve(10002, NetworkKind.WIFI, true, 100)
        assertEquals(20, f.engine.reservedBytes(b))
        assertEquals(0L, f.engine.reserve(10003, NetworkKind.WIFI, false, 1))
    }

    @Test fun rateBudgetIsSharedAcrossFlowsButNotDirectionsOrNetworks() {
        val f = Fixture()
        f.config(wifi = NetworkRule(downloadBps = 1000, uploadBps = 1000), mobile = NetworkRule(downloadBps = 1000))
        val one = f.engine.reserve(10001, NetworkKind.WIFI, false, 250)
        val two = f.engine.reserve(10001, NetworkKind.WIFI, false, 250)
        val upload = f.engine.reserve(10001, NetworkKind.WIFI, true, 250)
        val mobile = f.engine.reserve(10001, NetworkKind.MOBILE, false, 250)
        assertEquals(250L, f.engine.delay(one))
        assertEquals(500L, f.engine.delay(two))
        assertEquals(250L, f.engine.delay(upload))
        assertEquals(250L, f.engine.delay(mobile))
        f.nano += 400_000_000L
        assertEquals(0L, f.engine.delay(one))
        assertEquals(100L, f.engine.delay(two))
    }

    @Test fun rateHasNoInitialUnpacedChunkAndSmallRatesStayFinite() {
        val f = Fixture()
        f.config(wifi = NetworkRule(downloadBps = 1))
        val token = f.engine.reserve(10001, NetworkKind.WIFI, false, Int.MAX_VALUE)
        assertEquals(1, f.engine.reservedBytes(token))
        assertEquals(1000L, f.engine.delay(token))
    }

    @Test fun udpDatagramsArePacedWholeWhileTcpRetainsSmallChunks() {
        val f = Fixture()
        f.config(wifi = NetworkRule(downloadBps = 1000))
        val udp = f.engine.reserveDatagram(10001, NetworkKind.WIFI, false, 1500)
        assertTrue(udp > 0L)
        assertEquals(1500, f.engine.reservedBytes(udp))
        assertEquals(1500L, f.engine.delay(udp))
        val tcp = f.engine.reserve(10001, NetworkKind.WIFI, false, 1500)
        assertEquals(250, f.engine.reservedBytes(tcp))
        assertEquals(1750L, f.engine.delay(tcp))
    }

    @Test fun udpInsufficientQuotaConsumesNeitherQuotaNorPacing() {
        val f = Fixture()
        f.config(wifi = NetworkRule(downloadBps = 1000, quotaBytes = 1200), global = GlobalQuota(wifiBytes = 1100))
        assertEquals(0L, f.engine.reserveDatagram(10001, NetworkKind.WIFI, false, 1500))
        assertEquals(0, f.engine.pendingReservationCount())
        val accepted = f.engine.reserveDatagram(10001, NetworkKind.WIFI, false, 1000)
        assertEquals(1000, f.engine.reservedBytes(accepted))
        assertEquals(1000L, f.engine.delay(accepted))
        assertEquals(0L, f.engine.reserveDatagram(10002, NetworkKind.WIFI, false, 101))
        val remainder = f.engine.reserve(10002, NetworkKind.WIFI, false, 1000)
        assertEquals(100, f.engine.reservedBytes(remainder))
    }

    @Test fun verySlowUdpMayOccupyEmptyQueueAndCancellationReleasesItsDelay() {
        val f = Fixture()
        f.config(wifi = NetworkRule(downloadBps = 1))
        val datagram = f.engine.reserveDatagram(10001, NetworkKind.WIFI, false, 1500)
        assertEquals(1500, f.engine.reservedBytes(datagram))
        assertEquals(1_500_000L, f.engine.delay(datagram))
        assertEquals(0L, f.engine.reserveDatagram(10001, NetworkKind.WIFI, false, 1500))
        assertEquals(1, f.engine.pendingReservationCount())
        f.engine.complete(datagram, 0)
        val retry = f.engine.reserveDatagram(10001, NetworkKind.WIFI, false, 1500)
        assertEquals(1_500_000L, f.engine.delay(retry))
        f.engine.complete(retry, 0)
        val tcp = f.engine.reserve(10001, NetworkKind.WIFI, false, 1)
        assertEquals(1000L, f.engine.delay(tcp))
        assertTrue(f.engine.snapshotUsage().isEmpty())
    }

    @Test fun udpInvalidSizeAndUnknownOwnersAreRejectedWithoutReservations() {
        val f = Fixture()
        assertEquals(0L, f.engine.reserveDatagram(-1, NetworkKind.WIFI, false, 100))
        assertEquals(0L, f.engine.reserveDatagram(10001, NetworkKind.NONE, false, 100))
        assertEquals(0L, f.engine.reserveDatagram(10001, NetworkKind.WIFI, false, 0))
        assertEquals(0L, f.engine.reserveDatagram(10001, NetworkKind.WIFI, false, 65537))
        assertEquals(0, f.engine.pendingReservationCount())
    }

    @Test fun partialCompletionReturnsUnusedTailPacingWithoutMovingLaterSlots() {
        val f = Fixture()
        f.config(wifi = NetworkRule(downloadBps = 1000))
        val first = f.engine.reserve(10001, NetworkKind.WIFI, false, 250)
        val second = f.engine.reserve(10001, NetworkKind.WIFI, false, 250)
        f.engine.complete(first, 0)
        assertEquals(500L, f.engine.delay(second))
        f.engine.complete(second, 100)
        val third = f.engine.reserve(10001, NetworkKind.WIFI, false, 250)
        assertEquals(600L, f.engine.delay(third))
        assertEquals(100L, f.engine.snapshotUsage().single().downloadBytes)
    }

    @Test fun updatingSettingsCannotResetAlreadyReservedPacing() {
        val f = Fixture()
        f.config(wifi = NetworkRule(downloadBps = 1000))
        f.engine.reserve(10001, NetworkKind.WIFI, false, 250)
        f.config(wifi = NetworkRule(downloadBps = 1000), settings = AppSettings(darkTheme = false))
        val next = f.engine.reserve(10001, NetworkKind.WIFI, false, 250)
        assertEquals(500L, f.engine.delay(next))
    }

    @Test fun quotaSlowdownStartsAfterNormalBudget() {
        val f = Fixture()
        f.config(wifi = NetworkRule(quotaBytes = 100, quotaAction = QuotaAction.SLOW, afterQuotaBps = 100))
        val first = f.engine.reserve(10001, NetworkKind.WIFI, false, 150)
        assertEquals(100, f.engine.reservedBytes(first))
        assertEquals(0L, f.engine.delay(first))
        f.engine.complete(first, 100)
        val slow = f.engine.reserve(10001, NetworkKind.WIFI, false, 100)
        assertEquals(25, f.engine.reservedBytes(slow))
        assertEquals(250L, f.engine.delay(slow))
        assertTrue(f.engine.reasonFor(10001, NetworkKind.WIFI).contains("düşük hız"))
    }

    @Test fun udpCrossesSlowQuotaBoundaryWithoutLosingTheWholeDatagram() {
        val f = Fixture()
        f.config(wifi = NetworkRule(quotaBytes = 100, quotaAction = QuotaAction.SLOW, afterQuotaBps = 1000))
        val crossing = f.engine.reserveDatagram(10001, NetworkKind.WIFI, false, 1200)
        assertEquals(1200, f.engine.reservedBytes(crossing))
        assertEquals(1200L, f.engine.delay(crossing))
        f.engine.complete(crossing, 1200)
        assertEquals(1200L, f.engine.snapshotUsage().single().downloadBytes)
        assertTrue(f.engine.reasonFor(10001, NetworkKind.WIFI).contains("düşük hız"))
    }

    @Test fun udpSlowQuotaCrossingStillRespectsGlobalHardQuota() {
        val f = Fixture()
        f.config(wifi = NetworkRule(quotaBytes = 100, quotaAction = QuotaAction.SLOW, afterQuotaBps = 1000),
            global = GlobalQuota(wifiBytes = 1100))
        assertEquals(0L, f.engine.reserveDatagram(10001, NetworkKind.WIFI, false, 1200))
        assertEquals(0, f.engine.pendingReservationCount())
        assertTrue(f.engine.snapshotUsage().isEmpty())
        val accepted = f.engine.reserveDatagram(10001, NetworkKind.WIFI, false, 1000)
        assertEquals(1000, f.engine.reservedBytes(accepted))
        assertEquals(1000L, f.engine.delay(accepted))
    }

    @Test fun recycledUidArchivesCompletedAndPendingUsageWithoutResettingGlobalQuota() {
        val f = Fixture()
        f.config(wifi = NetworkRule(quotaBytes = 100), global = GlobalQuota(wifiBytes = 200))
        val completed = f.engine.reserve(10001, NetworkKind.WIFI, false, 60)
        f.engine.complete(completed, 60)
        val inFlight = f.engine.reserve(10001, NetworkKind.WIFI, true, 40)
        f.engine.archiveUid(10001, Int.MAX_VALUE)
        f.engine.complete(inFlight, 40)
        val newOwner = f.engine.reserve(10001, NetworkKind.WIFI, false, 100)
        assertEquals(100, f.engine.reservedBytes(newOwner))
        f.engine.complete(newOwner, 100)
        assertEquals(0L, f.engine.reserve(10002, NetworkKind.WIFI, false, 1))
        val history = f.engine.snapshotUsage().associateBy { it.uid }
        assertEquals(60L, history.getValue(Int.MAX_VALUE).downloadBytes)
        assertEquals(40L, history.getValue(Int.MAX_VALUE).uploadBytes)
        assertEquals(100L, history.getValue(10001).downloadBytes)
        assertEquals(0, f.engine.pendingReservationCount())
    }

    @Test fun temporaryPermissionExpiresAndDoesNotBypassGlobalQuota() {
        val f = Fixture()
        f.config(wifi = NetworkRule(allowed = false), global = GlobalQuota(wifiBytes = 10), temporary = mapOf(10001 to f.now + 1000))
        val token = f.engine.reserve(10001, NetworkKind.WIFI, false, 100)
        assertEquals(10, f.engine.reservedBytes(token))
        assertEquals(0L, f.engine.reserve(10001, NetworkKind.WIFI, false, 1))
        f.engine.complete(token, 0)
        f.now += 1000
        assertEquals(0L, f.engine.reserve(10001, NetworkKind.WIFI, false, 1))
    }

    @Test fun midnightSettlesOldReservationToOldDayAndResetsDailyBudget() {
        val f = Fixture()
        f.now = Instant.parse("2026-11-06T23:59:59Z").toEpochMilli()
        f.config(wifi = NetworkRule(quotaBytes = 100))
        val previous = f.engine.reserve(10001, NetworkKind.WIFI, false, 100)
        f.now += 2000
        val current = f.engine.reserve(10001, NetworkKind.WIFI, false, 100)
        assertEquals(100, f.engine.reservedBytes(current))
        f.engine.complete(previous, 100)
        f.engine.complete(current, 100)
        assertEquals(setOf("2026-11-06", "2026-11-07"), f.engine.snapshotUsage().map { it.day }.toSet())
        assertEquals(0L, f.engine.reserve(10001, NetworkKind.WIFI, false, 1))
    }

    @Test fun monthRolloverResetsMonthlyBudgetButNotWithinMonth() {
        val f = Fixture()
        f.config(wifi = NetworkRule(quotaBytes = 100, period = QuotaPeriod.MONTHLY))
        f.engine.restoreUsage(listOf(UsageEntry(10001, NetworkKind.WIFI, "2026-11-01", 100, 0)))
        assertEquals(0L, f.engine.reserve(10001, NetworkKind.WIFI, false, 100))
        f.now = Instant.parse("2026-12-01T00:00:00Z").toEpochMilli()
        assertEquals(100, f.engine.reservedBytes(f.engine.reserve(10001, NetworkKind.WIFI, false, 100)))
    }

    @Test fun customRenewalCountsPreviousMonthUntilBoundaryAndSettlesOldReservations() {
        val f = Fixture()
        f.now = Instant.parse("2026-11-14T23:59:59Z").toEpochMilli()
        f.config(wifi = NetworkRule(quotaBytes = 100, period = QuotaPeriod.MONTHLY, renewalDay = 15))
        f.engine.restoreUsage(listOf(
            UsageEntry(10001, NetworkKind.WIFI, "2026-10-14", 100, 0),
            UsageEntry(10001, NetworkKind.WIFI, "2026-10-15", 60, 0),
        ))
        val previous = f.engine.reserve(10001, NetworkKind.WIFI, false, 100)
        assertEquals(40, f.engine.reservedBytes(previous))
        assertEquals("Uygulama kotası doldu", f.engine.reasonFor(10001, NetworkKind.WIFI))
        f.now += 1000
        f.engine.complete(previous, 40)
        assertEquals("Erişim açık", f.engine.reasonFor(10001, NetworkKind.WIFI))
        assertEquals(100, f.engine.reservedBytes(f.engine.reserve(10001, NetworkKind.WIFI, false, 100)))
    }

    @Test fun applicationAndGlobalQuotasUseIndependentRenewalDates() {
        val f = Fixture()
        f.config(
            wifi = NetworkRule(quotaBytes = 100, period = QuotaPeriod.MONTHLY, renewalDay = 5),
            global = GlobalQuota(wifiBytes = 100, period = QuotaPeriod.MONTHLY, renewalDay = 15),
        )
        f.engine.restoreUsage(listOf(
            UsageEntry(10001, NetworkKind.WIFI, "2026-10-20", 60, 0),
            UsageEntry(10001, NetworkKind.WIFI, "2026-11-05", 20, 0),
        ))
        val beforeGlobalRenewal = f.engine.reserve(10001, NetworkKind.WIFI, false, 100)
        assertEquals(20, f.engine.reservedBytes(beforeGlobalRenewal))
        f.engine.complete(beforeGlobalRenewal, 20)
        assertEquals("Genel kota doldu", f.engine.reasonFor(10001, NetworkKind.WIFI))
        f.now = Instant.parse("2026-11-15T00:00:00Z").toEpochMilli()
        val afterGlobalRenewal = f.engine.reserve(10001, NetworkKind.WIFI, false, 100)
        assertEquals(60, f.engine.reservedBytes(afterGlobalRenewal))
        assertEquals("Uygulama kotası doldu", f.engine.reasonFor(10001, NetworkKind.WIFI))
    }

    @Test fun monthlyDay31QuotaRenewsOnLeapDayAndRetainsUsageThroughoutMarch() {
        val f = Fixture()
        f.now = Instant.parse("2028-02-28T23:59:59Z").toEpochMilli()
        f.config(wifi = NetworkRule(quotaBytes = 100, period = QuotaPeriod.MONTHLY, renewalDay = 31))
        f.engine.restoreUsage(listOf(UsageEntry(10001, NetworkKind.WIFI, "2028-01-31", 100, 0)))
        assertEquals(0L, f.engine.reserve(10001, NetworkKind.WIFI, false, 1))
        f.now += 1000
        val leapDay = f.engine.reserve(10001, NetworkKind.WIFI, false, 100)
        assertEquals(100, f.engine.reservedBytes(leapDay))
        f.engine.complete(leapDay, 100)
        f.now = Instant.parse("2028-03-30T23:59:59Z").toEpochMilli()
        assertEquals(0L, f.engine.reserve(10001, NetworkKind.WIFI, false, 1))
        f.now += 1000
        assertEquals(100, f.engine.reservedBytes(f.engine.reserve(10001, NetworkKind.WIFI, false, 100)))
    }

    @Test fun udpSlowdownUsesCustomPeriodAndReturnsToNormalAtRenewal() {
        val f = Fixture()
        f.now = Instant.parse("2026-11-14T23:59:59Z").toEpochMilli()
        f.config(wifi = NetworkRule(
            quotaBytes = 100, period = QuotaPeriod.MONTHLY, quotaAction = QuotaAction.SLOW,
            afterQuotaBps = 1000, renewalDay = 15,
        ))
        f.engine.restoreUsage(listOf(UsageEntry(10001, NetworkKind.WIFI, "2026-10-15", 80, 0)))
        val crossing = f.engine.reserveDatagram(10001, NetworkKind.WIFI, false, 30)
        assertEquals(30, f.engine.reservedBytes(crossing))
        assertEquals(30L, f.engine.delay(crossing))
        f.engine.complete(crossing, 30)
        assertEquals("Kota doldu; düşük hız etkin", f.engine.reasonFor(10001, NetworkKind.WIFI))
        f.now += 1000
        f.nano += 1_000_000_000
        val renewed = f.engine.reserveDatagram(10001, NetworkKind.WIFI, false, 30)
        assertEquals(30, f.engine.reservedBytes(renewed))
        assertEquals(0L, f.engine.delay(renewed))
        assertEquals("Erişim açık", f.engine.reasonFor(10001, NetworkKind.WIFI))
    }

    @Test fun weekStartsOnMonday() {
        val f = Fixture()
        f.config(wifi = NetworkRule(quotaBytes = 100, period = QuotaPeriod.WEEKLY))
        f.engine.restoreUsage(listOf(UsageEntry(10001, NetworkKind.WIFI, "2026-11-02", 100, 0)))
        assertEquals(0L, f.engine.reserve(10001, NetworkKind.WIFI, false, 100))
        f.now = Instant.parse("2026-11-09T00:00:00Z").toEpochMilli()
        assertTrue(f.engine.reserve(10001, NetworkKind.WIFI, false, 100) > 0)
    }

    @Test fun networkChangeDoesNotMisattributeAlreadyReservedBytes() {
        val f = Fixture()
        val wifi = f.engine.reserve(10001, NetworkKind.WIFI, false, 123)
        val mobile = f.engine.reserve(10001, NetworkKind.MOBILE, true, 456)
        f.engine.complete(mobile, 456)
        f.engine.complete(wifi, 123)
        val byNetwork = f.engine.snapshotUsage().associateBy { it.network }
        assertEquals(123L, byNetwork.getValue(NetworkKind.WIFI).downloadBytes)
        assertEquals(456L, byNetwork.getValue(NetworkKind.MOBILE).uploadBytes)
    }

    @Test fun invalidCompletionDoesNotDestroyReservationAndDoubleCompletionIsHarmless() {
        val f = Fixture()
        val token = f.engine.reserve(10001, NetworkKind.WIFI, false, 100)
        assertThrows(IllegalArgumentException::class.java) { f.engine.complete(token, 101) }
        assertEquals(100, f.engine.reservedBytes(token))
        f.engine.complete(token, 50)
        f.engine.complete(token, 50)
        assertEquals(50L, f.engine.snapshotUsage().single().downloadBytes)
        assertEquals(0, f.engine.pendingReservationCount())
    }

    @Test fun hugePersistedCountsSaturateRatherThanWrapQuota() {
        val f = Fixture()
        f.config(wifi = NetworkRule(quotaBytes = MAX_QUOTA_BYTES))
        f.engine.restoreUsage(listOf(UsageEntry(10001, NetworkKind.WIFI, "2026-11-06", Long.MAX_VALUE, Long.MAX_VALUE)))
        assertEquals(0L, f.engine.reserve(10001, NetworkKind.WIFI, false, 100))
    }

    @Test fun globalHistorySaturatesAcrossApplicationsAndDirections() {
        val f = Fixture()
        f.config(global = GlobalQuota(wifiBytes = MAX_QUOTA_BYTES))
        f.engine.restoreUsage(listOf(
            UsageEntry(10001, NetworkKind.WIFI, "2026-11-06", Long.MAX_VALUE, 0),
            UsageEntry(10002, NetworkKind.WIFI, "2026-11-06", 0, Long.MAX_VALUE),
        ))
        assertEquals(0L, f.engine.reserve(10003, NetworkKind.WIFI, false, 100))
        assertTrue(f.engine.reserve(10003, NetworkKind.MOBILE, false, 100) > 0L)
    }

    @Test fun malformedModelLimitsAreRejectedBeforeConfiguration() {
        assertThrows(IllegalArgumentException::class.java) { NetworkRule(downloadBps = -1) }
        assertThrows(IllegalArgumentException::class.java) { NetworkRule(uploadBps = MAX_RATE_BPS + 1) }
        assertThrows(IllegalArgumentException::class.java) { NetworkRule(quotaBytes = MAX_QUOTA_BYTES + 1) }
        assertThrows(IllegalArgumentException::class.java) { NetworkRule(afterQuotaBps = 0) }
        assertThrows(IllegalArgumentException::class.java) { GlobalQuota(wifiBytes = -1) }
        assertThrows(IllegalArgumentException::class.java) { AppRule(-1) }
        assertThrows(IllegalArgumentException::class.java) { UsageEntry(10001, NetworkKind.WIFI, "2026-11-06", -1, 0) }
        assertThrows(IllegalArgumentException::class.java) { UsageEntry(10001, NetworkKind.OTHER, "2026-11-06", 0, 0) }
    }

    @Test fun restoringInvalidHistoryIsAtomic() {
        val f = Fixture()
        val original = listOf(UsageEntry(10001, NetworkKind.WIFI, "2026-11-06", 10, 0))
        f.engine.restoreUsage(original)
        assertThrows(IllegalArgumentException::class.java) {
            f.engine.restoreUsage(listOf(original.single(), original.single()))
        }
        assertEquals(original, f.engine.snapshotUsage())
    }

    @Test fun restoreDuringTransferIsRejected() {
        val f = Fixture()
        f.engine.reserve(10001, NetworkKind.WIFI, false, 1)
        assertThrows(IllegalStateException::class.java) { f.engine.restoreUsage(emptyList()) }
    }

    @Test fun wallClockMovementDoesNotChangeRateDelay() {
        val f = Fixture()
        f.config(wifi = NetworkRule(downloadBps = 1000))
        val token = f.engine.reserve(10001, NetworkKind.WIFI, false, 250)
        f.now += 60 * 60 * 1000
        assertEquals(250L, f.engine.delay(token))
        f.nano += 250_000_000
        assertEquals(0L, f.engine.delay(token))
    }

    @Test fun unlimitedSpeedStillCapsChunkAndHasNoDelay() {
        val f = Fixture()
        val token = f.engine.reserve(10001, NetworkKind.WIFI, false, Int.MAX_VALUE)
        assertEquals(65536, f.engine.reservedBytes(token))
        assertEquals(0L, f.engine.delay(token))
    }
}
