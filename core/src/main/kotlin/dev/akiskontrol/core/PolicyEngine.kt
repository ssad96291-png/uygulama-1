package dev.akiskontrol.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

/**
 * Thread-safe admission control shared by every flow, keyed by Android UID and physical network.
 * Bytes are reserved before forwarding, then [complete] records the actual forwarded payload.
 * Reservations consume quota immediately, so parallel connections cannot overspend it.
 * The caller must complete every accepted token, including cancellations with zero actual bytes.
 */
class PolicyEngine(
    private val clock: () -> Long = System::currentTimeMillis,
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private data class Key(val uid: Int, val network: NetworkKind, val day: LocalDate)
    private data class GlobalKey(val network: NetworkKind, val day: LocalDate)
    private data class RateKey(val uid: Int, val network: NetworkKind, val upload: Boolean)
    private data class Counter(var down: Long = 0, var up: Long = 0) {
        fun total(): Long = add(down, up)
    }
    private data class Reservation(
        val key: Key,
        val upload: Boolean,
        val amount: Int,
        val readyNanos: Long,
        val rateBps: Long,
        val pacedNanos: Long,
    )

    private var rules = emptyMap<Int, AppRule>()
    private var globalQuota = GlobalQuota()
    private var settings = AppSettings()
    private var temporary = emptyMap<Int, Long>()
    private val usage = mutableMapOf<Key, Counter>()
    private val globalUsage = mutableMapOf<GlobalKey, Long>()
    private val pending = mutableMapOf<Key, Long>()
    private val globalPending = mutableMapOf<GlobalKey, Long>()
    private val tokens = mutableMapOf<Long, Reservation>()
    private val nextFinish = mutableMapOf<RateKey, Long>()
    private var nextToken = 1L
    private var lastPruneDay: LocalDate? = null

    @Synchronized
    fun configure(
        rules: Map<Int, AppRule>,
        globalQuota: GlobalQuota,
        settings: AppSettings,
        temporaryAllowUntil: Map<Int, Long>,
    ) {
        require(rules.all { (uid, rule) -> uid >= 0 && rule.uid == uid }) { "Geçersiz uygulama kimliği" }
        rules.values.forEach { validate(it.wifi); validate(it.mobile) }
        require(globalQuota.wifiBytes >= 0 && globalQuota.mobileBytes >= 0) { "Kota negatif olamaz" }
        require(temporaryAllowUntil.keys.all { it >= 0 }) { "Geçersiz geçici izin" }
        this.rules = rules.toMap()
        this.globalQuota = globalQuota
        this.settings = settings
        temporary = temporaryAllowUntil.toMap()
        // Retain pacing already reserved by in-flight transfers. Resetting it on each UI update
        // would allow repeated settings changes to create an unbounded burst.
    }

    /** Load persisted successful byte counts; replacing live counters would lose reservations. */
    @Synchronized
    fun restoreUsage(entries: List<UsageEntry>) {
        check(tokens.isEmpty()) { "Aktif aktarımlar varken kullanım değiştirilemez" }
        val replacement = mutableMapOf<Key, Counter>()
        entries.forEach { entry ->
            require(entry.uid >= 0 && entry.network in SUPPORTED_NETWORKS) { "Geçersiz kullanım kaydı" }
            require(entry.downloadBytes >= 0 && entry.uploadBytes >= 0) { "Kullanım negatif olamaz" }
            val key = Key(entry.uid, entry.network, LocalDate.parse(entry.day))
            require(!replacement.containsKey(key)) { "Yinelenen kullanım kaydı" }
            replacement[key] = Counter(entry.downloadBytes, entry.uploadBytes)
        }
        usage.clear()
        usage.putAll(replacement)
        globalUsage.clear()
        usage.forEach { (key, counter) ->
            val g = GlobalKey(key.network, key.day)
            globalUsage[g] = add(globalUsage[g] ?: 0, counter.total())
        }
        pending.clear()
        globalPending.clear()
        nextFinish.clear()
        lastPruneDay = null
    }

    @Synchronized
    fun snapshotUsage(): List<UsageEntry> = usage.entries
        .sortedWith(compareByDescending<Map.Entry<Key, Counter>> { it.key.day }.thenBy { it.key.uid }.thenBy { it.key.network.ordinal })
        .map { (key, value) -> UsageEntry(key.uid, key.network, key.day.toString(), value.down, value.up) }

    /** Retire an Android UID without assigning its historical traffic to its next owner. */
    @Synchronized
    fun archiveUid(uid: Int, archiveUid: Int) {
        require(uid >= 0 && archiveUid >= 0 && uid != archiveUid)
        require(usage.keys.none { it.uid == archiveUid } && pending.keys.none { it.uid == archiveUid } && archiveUid !in rules) {
            "Arşiv uygulama kimliği kullanımda"
        }
        usage.keys.filter { it.uid == uid }.forEach { key -> usage[key.copy(uid = archiveUid)] = usage.remove(key)!! }
        pending.keys.filter { it.uid == uid }.forEach { key -> pending[key.copy(uid = archiveUid)] = pending.remove(key)!! }
        tokens.entries.forEach { entry ->
            if (entry.value.key.uid == uid) entry.setValue(entry.value.copy(key = entry.value.key.copy(uid = archiveUid)))
        }
        nextFinish.keys.filter { it.uid == uid }.forEach { key -> nextFinish[key.copy(uid = archiveUid)] = nextFinish.remove(key)!! }
        // Global counters are independent of UID and remain unchanged, including reservations.
    }

    /** Positive opaque token on success, zero if the rule/quota does not allow this transfer. */
    @Synchronized
    fun reserve(uid: Int, network: NetworkKind, upload: Boolean, requestedBytes: Int): Long =
        reserveInternal(uid, network, upload, requestedBytes, wholeDatagram = false)

    /** UDP cannot be split: admit and pace the entire datagram, or consume no budget. */
    @Synchronized
    fun reserveDatagram(uid: Int, network: NetworkKind, upload: Boolean, requestedBytes: Int): Long =
        reserveInternal(uid, network, upload, requestedBytes, wholeDatagram = true)

    private fun reserveInternal(
        uid: Int,
        network: NetworkKind,
        upload: Boolean,
        requestedBytes: Int,
        wholeDatagram: Boolean,
    ): Long {
        if (uid < 0 || network !in SUPPORTED_NETWORKS || requestedBytes <= 0) return 0
        if (wholeDatagram && requestedBytes > MAX_CHUNK_BYTES) return 0
        val nowMillis = clock()
        val day = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        prune(day)
        val rule = rule(uid, network)
        val temporaryAllowed = (temporary[uid] ?: Long.MIN_VALUE) > nowMillis
        if (!temporaryAllowed && !rule.allowed) return 0

        var granted = min(requestedBytes, MAX_CHUNK_BYTES).toLong()
        val globalLimit = if (network == NetworkKind.WIFI) globalQuota.wifiBytes else globalQuota.mobileBytes
        if (globalLimit > 0) {
            val consumed = total(null, network, globalQuota.period, day, globalQuota.renewalDay)
            granted = min(granted, remaining(globalLimit, consumed))
        }
        var rate = if (temporaryAllowed) 0L else if (upload) rule.uploadBps else rule.downloadBps
        if (!temporaryAllowed && rule.quotaBytes > 0) {
            val consumed = total(uid, network, rule.period, day, rule.renewalDay)
            val remaining = remaining(rule.quotaBytes, consumed)
            if (rule.quotaAction == QuotaAction.BLOCK || (remaining > 0 && (!wholeDatagram || granted <= remaining))) {
                granted = min(granted, remaining)
            } else {
                // An indivisible UDP datagram can cross a SLOW quota boundary. Pace the
                // whole datagram at the slower rate so a small leftover budget cannot
                // permanently block it. The global hard quota still bounds granted.
                rate = if (rate == 0L) rule.afterQuotaBps else min(rate, rule.afterQuotaBps)
                // A malformed zero slowdown rate must never accidentally become unlimited.
                if (rate <= 0) return 0
            }
        }
        if (granted <= 0) return 0
        if (wholeDatagram && granted < requestedBytes) return 0

        val monotonic = monotonicNanos()
        val rateKey = RateKey(uid, network, upload)
        var ready = monotonic
        var duration = 0L
        if (rate > 0) {
            // TCP can use short chunks, but UDP must retain its complete payload at low rates.
            if (!wholeDatagram) granted = min(granted, maxOf(1L, rate / 4L))
            duration = ceilDivide(granted * NANOS_PER_SECOND, rate)
            val previous = nextFinish[rateKey]
            val outstanding = if (previous == null) 0L else (previous - monotonic).coerceAtLeast(0L)
            // Bound memory/queued work when an application opens thousands of parallel streams.
            // One indivisible datagram may need longer at a very low rate; it occupies an empty
            // queue alone instead of being denied forever merely because it cannot be split.
            if (outstanding > 0L && outstanding > MAX_QUEUED_NANOS - duration) return 0
            ready = monotonic + outstanding + duration // nanoTime differences support natural wrap.
            nextFinish[rateKey] = ready
        }

        val key = Key(uid, network, day)
        val amount = granted.toInt()
        val token = allocateToken()
        tokens[token] = Reservation(key, upload, amount, ready, rate, duration)
        pending[key] = add(pending[key] ?: 0L, granted)
        val globalKey = GlobalKey(network, day)
        globalPending[globalKey] = add(globalPending[globalKey] ?: 0L, granted)
        return token
    }

    @Synchronized
    fun reservedBytes(token: Long): Int = tokens[token]?.amount ?: 0

    /** Remaining delay, not original delay, so callback/processing time does not add extra pacing. */
    @Synchronized
    fun delay(token: Long): Long {
        val reservation = tokens[token] ?: return 0L
        val remaining = (reservation.readyNanos - monotonicNanos()).coerceAtLeast(0L)
        return ceilDivide(remaining, NANOS_PER_MILLI)
    }

    /** Idempotent for an already settled token; invalid actual byte counts are rejected. */
    @Synchronized
    fun complete(token: Long, actualBytes: Int) {
        require(actualBytes >= 0) { "Aktarılan veri negatif olamaz" }
        val reservation = tokens[token] ?: return
        require(actualBytes <= reservation.amount) { "Aktarım ayrılan miktarı aşamaz" }
        tokens.remove(token)
        subtract(pending, reservation.key, reservation.amount.toLong())
        val globalKey = GlobalKey(reservation.key.network, reservation.key.day)
        subtract(globalPending, globalKey, reservation.amount.toLong())
        refundTailPacing(reservation, actualBytes)
        if (actualBytes == 0) return
        val counter = usage.getOrPut(reservation.key) { Counter() }
        if (reservation.upload) counter.up = add(counter.up, actualBytes.toLong())
        else counter.down = add(counter.down, actualBytes.toLong())
        globalUsage[globalKey] = add(globalUsage[globalKey] ?: 0L, actualBytes.toLong())
    }

    private fun refundTailPacing(reservation: Reservation, actualBytes: Int) {
        if (reservation.rateBps == 0L || actualBytes == reservation.amount) return
        val key = RateKey(reservation.key.uid, reservation.key.network, reservation.upload)
        // Never move a later accepted transfer's scheduled slot: callers may already be waiting
        // on that deadline. Reclaiming the tail is safe and covers rejected/canceled UDP sends.
        if (nextFinish[key] != reservation.readyNanos) return
        val usedDuration = ceilDivide(actualBytes.toLong() * NANOS_PER_SECOND, reservation.rateBps)
        val revisedFinish = reservation.readyNanos - (reservation.pacedNanos - usedDuration)
        if (revisedFinish - monotonicNanos() > 0L) nextFinish[key] = revisedFinish
        else nextFinish.remove(key)
    }

    @Synchronized
    fun pendingReservationCount(): Int = tokens.size

    @Synchronized
    fun reasonFor(uid: Int, network: NetworkKind): String {
        if (uid < 0) return "Uygulama kimliği belirlenemedi"
        if (network !in SUPPORTED_NETWORKS) return "Desteklenen ağ bağlantısı yok"
        val now = clock()
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val globalLimit = if (network == NetworkKind.WIFI) globalQuota.wifiBytes else globalQuota.mobileBytes
        if (globalLimit > 0 && total(null, network, globalQuota.period, day, globalQuota.renewalDay) >= globalLimit) return "Genel kota doldu"
        if ((temporary[uid] ?: Long.MIN_VALUE) > now) return "Geçici izin etkin"
        val rule = rule(uid, network)
        if (!rule.allowed) return if (network == NetworkKind.WIFI) "Wi-Fi erişimi kapalı" else "Mobil veri erişimi kapalı"
        if (rule.quotaBytes > 0 && total(uid, network, rule.period, day, rule.renewalDay) >= rule.quotaBytes) {
            return if (rule.quotaAction == QuotaAction.BLOCK) "Uygulama kotası doldu" else "Kota doldu; düşük hız etkin"
        }
        return "Erişim açık"
    }

    private fun rule(uid: Int, network: NetworkKind): NetworkRule {
        val app = rules[uid]
        return if (network == NetworkKind.WIFI) app?.wifi ?: NetworkRule(allowed = settings.defaultWifiAllowed)
        else app?.mobile ?: NetworkRule(allowed = settings.defaultMobileAllowed)
    }

    /** At most 31 daily lookups per monthly quota, independent of installed application count. */
    private fun total(uid: Int?, network: NetworkKind, period: QuotaPeriod, today: LocalDate, renewalDay: Int): Long {
        var day = periodStart(today, period, renewalDay)
        var result = 0L
        while (!day.isAfter(today)) {
            val completed: Long
            val reserved: Long
            if (uid == null) {
                val key = GlobalKey(network, day)
                completed = globalUsage[key] ?: 0L
                reserved = globalPending[key] ?: 0L
            } else {
                val key = Key(uid, network, day)
                completed = usage[key]?.total() ?: 0L
                reserved = pending[key] ?: 0L
            }
            result = add(result, add(completed, reserved))
            day = day.plusDays(1)
        }
        return result
    }

    private fun prune(today: LocalDate) {
        if (lastPruneDay == today) return
        lastPruneDay = today
        val oldest = today.minusDays(RETAIN_DAYS)
        usage.keys.removeAll { it.day.isBefore(oldest) && !pending.containsKey(it) }
        globalUsage.keys.removeAll { it.day.isBefore(oldest) && !globalPending.containsKey(it) }
        val nowNanos = monotonicNanos()
        nextFinish.entries.removeAll { it.value - nowNanos <= 0 }
    }

    private fun allocateToken(): Long {
        while (tokens.containsKey(nextToken)) nextToken = if (nextToken == Long.MAX_VALUE) 1L else nextToken + 1L
        val value = nextToken
        nextToken = if (nextToken == Long.MAX_VALUE) 1L else nextToken + 1L
        return value
    }

    companion object {
        private val SUPPORTED_NETWORKS = setOf(NetworkKind.WIFI, NetworkKind.MOBILE)
        private const val MAX_CHUNK_BYTES = 64 * 1024
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val MAX_QUEUED_NANOS = 60L * NANOS_PER_SECOND
        private const val RETAIN_DAYS = 400L

        private fun add(left: Long, right: Long): Long = if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
        private fun remaining(limit: Long, consumed: Long): Long = if (consumed >= limit) 0L else limit - consumed
        private fun ceilDivide(value: Long, divisor: Long): Long = value / divisor + if (value % divisor == 0L) 0L else 1L
        private fun <K> subtract(map: MutableMap<K, Long>, key: K, amount: Long) {
            val left = ((map[key] ?: 0L) - amount).coerceAtLeast(0L)
            if (left == 0L) map.remove(key) else map[key] = left
        }
        private fun validate(rule: NetworkRule) {
            require(rule.downloadBps >= 0 && rule.uploadBps >= 0 && rule.quotaBytes >= 0) { "Sınırlar negatif olamaz" }
            require(rule.afterQuotaBps > 0) { "Kota sonrası hız pozitif olmalı" }
        }
    }
}
