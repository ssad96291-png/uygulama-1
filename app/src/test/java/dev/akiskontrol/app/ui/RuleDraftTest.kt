package dev.akiskontrol.app.ui

import dev.akiskontrol.core.MAX_RATE_BPS
import dev.akiskontrol.core.NetworkRule
import dev.akiskontrol.core.QuotaAction
import dev.akiskontrol.core.QuotaPeriod
import org.junit.Assert.*
import org.junit.Test

class RuleDraftTest {
    private val units = listOf(InputUnit("KB/s", 1_000), InputUnit("MB/s", 1_000_000), InputUnit("Mbps", 125_000))

    @Test fun changingRateUnitPreservesTheExactNumberOfBytesPerSecond() {
        val draft = AmountDraft(1_000_001L, units, MAX_RATE_BPS)
        assertEquals("1.000001", draft.text)
        draft.changeUnit(units[2])
        assertEquals("8.000008", draft.text)
        assertEquals(1_000_001L, draft.value("Hız"))
        draft.changeUnit(units[0])
        assertEquals("1000.001", draft.text)
        assertEquals(1_000_001L, draft.value("Hız"))
    }

    @Test fun commaDecimalsAndZeroUnlimitedAreAcceptedButSlowdownMustBePositive() {
        val draft = AmountDraft(1_000L, units, MAX_RATE_BPS)
        draft.text = "1,5"
        assertEquals(1_500L, draft.value("Hız"))
        draft.text = "0"
        assertEquals(0L, draft.value("Hız"))
        val slowdown = AmountDraft(1_000L, units, MAX_RATE_BPS, canBeUnlimited = false)
        slowdown.text = "0"
        assertNull(slowdown.value("Kota sonrası hız"))
        assertNotNull(slowdown.error)
    }

    @Test fun invalidRatesAreRejectedWithoutChangingTheTypedInput() {
        val draft = AmountDraft(1_000L, units, MAX_RATE_BPS)
        for (text in listOf("-1", "1e3", "0.0001", "1000001", "x", "9".repeat(33))) {
            draft.text = text
            assertNull("Expected rejection for $text", draft.value("Hız"))
            assertNotNull(draft.error)
            assertEquals(text, draft.text)
        }
    }

    @Test fun restoringDraftKeepsUnsavedInvalidInputsUnitsAndErrors() {
        val original = NetworkDraft(NetworkRule(downloadBps = 125_000L, quotaBytes = 2_000_000_000L))
        original.allowed = false
        original.download.changeUnit(original.download.units.first())
        original.download.text = "1,"
        original.upload.unlimited = false
        original.upload.text = "not-a-number"
        original.upload.error = "Geçerli bir sayı yaz."
        original.period = QuotaPeriod.MONTHLY
        original.renewalDay.text = ""
        original.renewalDay.error = "1 ile 31 arasında bir gün yaz."
        original.action = QuotaAction.SLOW
        original.afterQuota.text = "12,5"
        val saved = original.saveState()

        val restored = NetworkDraft.restoreState(saved)

        assertEquals(saved, restored.saveState())
        assertEquals("not-a-number", restored.upload.text)
        assertNull(restored.rule())
        assertNotNull(restored.upload.error)
        assertNotNull(restored.renewalDay.error)
    }

    @Test fun restoringTwoNetworkDraftsKeepsTheirIndependentRules() {
        val wifi = NetworkDraft(NetworkRule(downloadBps = 1_000_000L)).apply {
            upload.unlimited = false
            upload.text = "2"
            upload.unit = upload.units.first { it.label == "MB/s" }
        }
        val mobile = NetworkDraft(NetworkRule(allowed = false, quotaBytes = 500_000_000L, period = QuotaPeriod.WEEKLY))
        val restoredWifi = NetworkDraft.restoreState(wifi.saveState()).rule()!!
        val restoredMobile = NetworkDraft.restoreState(mobile.saveState()).rule()!!
        assertEquals(1_000_000L, restoredWifi.downloadBps)
        assertEquals(2_000_000L, restoredWifi.uploadBps)
        assertTrue(restoredWifi.allowed)
        assertFalse(restoredMobile.allowed)
        assertEquals(500_000_000L, restoredMobile.quotaBytes)
        assertEquals(QuotaPeriod.WEEKLY, restoredMobile.period)
    }

    @Test fun disabledQuotaRetainsStoredSlowdownAndRenewalEvenAfterRestoration() {
        val initial = NetworkRule(
            downloadBps = 2_000L,
            uploadBps = 3_000L,
            quotaBytes = 10_000_000L,
            period = QuotaPeriod.MONTHLY,
            quotaAction = QuotaAction.SLOW,
            afterQuotaBps = 42_000L,
            renewalDay = 31,
        )
        val draft = NetworkDraft(initial).apply {
            afterQuota.text = "invalid unfinished change"
            renewalDay.text = ""
            quota.unlimited = true
        }

        val restored = NetworkDraft.restoreState(draft.saveState())

        assertEquals(initial.copy(quotaBytes = 0L), restored.rule())
    }
}
