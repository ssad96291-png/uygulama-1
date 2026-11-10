package dev.akiskontrol.core

import java.time.LocalDate

const val MAX_RATE_BPS: Long = 1_000_000_000L
const val MAX_QUOTA_BYTES: Long = 9_000_000_000_000_000L

enum class NetworkKind { WIFI, MOBILE, OTHER, NONE }
enum class QuotaPeriod { DAILY, WEEKLY, MONTHLY }
enum class QuotaAction { BLOCK, SLOW }

data class NetworkRule(
    val allowed: Boolean = true,
    val downloadBps: Long = 0,
    val uploadBps: Long = 0,
    val quotaBytes: Long = 0,
    val period: QuotaPeriod = QuotaPeriod.DAILY,
    val quotaAction: QuotaAction = QuotaAction.BLOCK,
    val afterQuotaBps: Long = 16_384,
    val renewalDay: Int = 1,
) {
    init {
        require(downloadBps in 0..MAX_RATE_BPS && uploadBps in 0..MAX_RATE_BPS) { "Hız değeri geçersiz" }
        require(quotaBytes in 0..MAX_QUOTA_BYTES) { "Kota değeri geçersiz" }
        require(afterQuotaBps in 1..MAX_RATE_BPS) { "Kota sonrası hız pozitif olmalı" }
        require(renewalDay in 1..31) { "Yenilenme günü 1–31 arasında olmalı" }
    }
}

data class AppRule(val uid: Int, val wifi: NetworkRule = NetworkRule(), val mobile: NetworkRule = NetworkRule()) {
    init { require(uid >= 0) { "Uygulama kimliği geçersiz" } }
}
data class GlobalQuota(
    val wifiBytes: Long = 0,
    val mobileBytes: Long = 0,
    val period: QuotaPeriod = QuotaPeriod.DAILY,
    val renewalDay: Int = 1,
) {
    init {
        require(wifiBytes in 0..MAX_QUOTA_BYTES && mobileBytes in 0..MAX_QUOTA_BYTES) { "Toplam kota geçersiz" }
        require(renewalDay in 1..31) { "Yenilenme günü 1–31 arasında olmalı" }
    }
}
data class AppEntry(val uid: Int, val packageName: String, val label: String, val isSystem: Boolean)
data class UsageEntry(val uid: Int, val network: NetworkKind, val day: String, val downloadBytes: Long, val uploadBytes: Long) {
    init {
        require(uid >= 0 && network in listOf(NetworkKind.WIFI, NetworkKind.MOBILE)) { "Kullanım kaydı geçersiz" }
        require(downloadBytes >= 0 && uploadBytes >= 0) { "Negatif kullanım kaydı" }
        LocalDate.parse(day)
    }
}
data class LogEntry(val time: Long, val message: String)
enum class ProtectionStatus { STOPPED, STARTING, RUNNING, ERROR }
data class AppSettings(
    val advanced: Boolean = false,
    val darkTheme: Boolean = false,
    val defaultWifiAllowed: Boolean = true,
    val defaultMobileAllowed: Boolean = true,
    val showSystemApps: Boolean = false,
)
data class AppState(
    val apps: List<AppEntry> = emptyList(),
    val rules: Map<Int, AppRule> = emptyMap(),
    val usage: List<UsageEntry> = emptyList(),
    val globalQuota: GlobalQuota = GlobalQuota(),
    val settings: AppSettings = AppSettings(),
    val status: ProtectionStatus = ProtectionStatus.STOPPED,
    val network: NetworkKind = NetworkKind.NONE,
    val statusMessage: String = "Koruma kapalı",
    val logs: List<LogEntry> = emptyList(),
    val temporaryAllowUntil: Map<Int, Long> = emptyMap(),
)
