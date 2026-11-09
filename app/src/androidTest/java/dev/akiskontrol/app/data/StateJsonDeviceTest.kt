package dev.akiskontrol.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.akiskontrol.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual Android JSON implementation used for local state and portable backups. */
@RunWith(AndroidJUnit4::class)
class StateJsonDeviceTest {
    private val sample = StoredState(
        rules = mapOf(10001 to AppRule(10001,
            wifi = NetworkRule(quotaBytes = 1000, period = QuotaPeriod.MONTHLY, renewalDay = 31),
            mobile = NetworkRule(downloadBps = 8192, quotaAction = QuotaAction.SLOW, renewalDay = 17))),
        usage = listOf(UsageEntry(10001, NetworkKind.WIFI, "2026-11-01", 120, 30)),
        globalQuota = GlobalQuota(wifiBytes = 9000, period = QuotaPeriod.MONTHLY, renewalDay = 28),
        identities = mapOf(10001 to listOf("dev.example.app")),
        temporary = mapOf(10001 to 123456789L),
    )

    @Test fun localStateAndPortableBackupRoundTrip() {
        assertEquals(sample, StateJson.decode(StateJson.encode(sample, false), false))
        val backup = StateJson.encode(sample, true)
        assertEquals(sample.copy(temporary = emptyMap()), StateJson.decode(backup, true))
        assertFalse(JSONObject(backup).has("temporary"))
    }

    @Test fun earlierVersionOneFilesDefaultRenewalToFirstDay() {
        val json = JSONObject(StateJson.encode(sample, false))
        json.getJSONObject("globalQuota").remove("renewalDay")
        val rule = json.getJSONArray("rules").getJSONObject(0)
        rule.getJSONObject("wifi").remove("renewalDay")
        rule.getJSONObject("mobile").remove("renewalDay")
        val restored = StateJson.decode(json.toString(), false)
        assertEquals(1, restored.globalQuota.renewalDay)
        assertEquals(1, restored.rules.getValue(10001).wifi.renewalDay)
        assertEquals(1, restored.rules.getValue(10001).mobile.renewalDay)
    }

    @Test fun malformedLimitsAndDuplicatePackageIdentitiesAreRejected() {
        val malformed = JSONObject(StateJson.encode(sample, true))
        malformed.getJSONObject("globalQuota").put("renewalDay", 32)
        assertThrows(IllegalArgumentException::class.java) { StateJson.decode(malformed.toString(), true) }
        malformed.getJSONObject("globalQuota").put("renewalDay", "28")
        assertThrows(IllegalArgumentException::class.java) { StateJson.decode(malformed.toString(), true) }
        malformed.getJSONObject("globalQuota").put("renewalDay", 28)
        malformed.getJSONArray("identities").put(JSONObject().put("uid", 10002)
            .put("packages", org.json.JSONArray(listOf("dev.example.app"))))
        assertThrows(IllegalArgumentException::class.java) { StateJson.decode(malformed.toString(), true) }
    }
}
