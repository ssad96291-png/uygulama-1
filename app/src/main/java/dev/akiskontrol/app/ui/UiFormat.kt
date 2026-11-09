package dev.akiskontrol.app.ui

import dev.akiskontrol.core.NetworkKind
import dev.akiskontrol.core.NetworkRule
import dev.akiskontrol.core.QuotaPeriod
import dev.akiskontrol.core.periodStart
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.Locale

internal val Turkish = Locale.forLanguageTag("tr-TR")
internal fun addBytes(left: Long, right: Long): Long = if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
internal inline fun <T> Iterable<T>.sumBytes(value: (T) -> Long): Long = fold(0L) { total, item -> addBytes(total, value(item)) }
internal fun bytesLabel(bytes: Long): String {
    val n = bytes.coerceAtLeast(0)
    return when {
        n >= 1_000_000_000L -> String.format(Turkish, "%.2f GB", n / 1_000_000_000.0)
        n >= 1_000_000L -> String.format(Turkish, "%.1f MB", n / 1_000_000.0)
        n >= 1_000L -> String.format(Turkish, "%.1f KB", n / 1_000.0)
        else -> "$n B"
    }
}
internal fun speedLabel(bps: Long): String = if (bps == 0L) "Sınırsız" else "${bytesLabel(bps)}/s"
internal fun NetworkKind.label(): String = when (this) {
    NetworkKind.WIFI -> "Wi-Fi"
    NetworkKind.MOBILE -> "Mobil veri"
    NetworkKind.OTHER -> "Desteklenmeyen ağ"
    NetworkKind.NONE -> "Bağlantı yok"
}
internal fun QuotaPeriod.label(): String = when (this) {
    QuotaPeriod.DAILY -> "Günlük"
    QuotaPeriod.WEEKLY -> "Haftalık"
    QuotaPeriod.MONTHLY -> "Aylık"
}
internal fun QuotaPeriod.renewalLabel(renewalDay: Int = 1): String =
    if (this == QuotaPeriod.MONTHLY) "Aylık · ayın $renewalDay. günü" else label()
internal fun NetworkRule.shortLabel(): String = when {
    !allowed -> "Engelli"
    downloadBps > 0L || uploadBps > 0L -> "↓ ${speedLabel(downloadBps)} · ↑ ${speedLabel(uploadBps)}"
    quotaBytes > 0L -> "${bytesLabel(quotaBytes)} kota · ${period.renewalLabel(renewalDay)}"
    else -> "İzinli · Sınırsız"
}
internal data class DateRange(val start: LocalDate, val end: LocalDate) {
    fun contains(day: String): Boolean = runCatching { LocalDate.parse(day) }.getOrNull()?.let { it >= start && it <= end } ?: false
    fun title(): String {
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Turkish)
        return if (start == end) start.format(fmt) else "${start.format(fmt)} – ${end.format(fmt)}"
    }
}
internal fun dateRange(period: QuotaPeriod, offset: Int = 0, today: LocalDate = LocalDate.now()): DateRange = when (period) {
    QuotaPeriod.DAILY -> today.plusDays(offset.toLong()).let { DateRange(it, it) }
    QuotaPeriod.WEEKLY -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong()).let { DateRange(it, it.plusDays(6)) }
    QuotaPeriod.MONTHLY -> today.withDayOfMonth(1).plusMonths(offset.toLong()).let { DateRange(it, it.with(TemporalAdjusters.lastDayOfMonth())) }
}
internal fun quotaDateRange(period: QuotaPeriod, renewalDay: Int = 1, today: LocalDate = LocalDate.now()): DateRange {
    val start = periodStart(today, period, renewalDay)
    val end = when (period) {
        QuotaPeriod.DAILY -> start
        QuotaPeriod.WEEKLY -> start.plusDays(6)
        QuotaPeriod.MONTHLY -> start.withDayOfMonth(1).plusMonths(1).let {
            it.withDayOfMonth(renewalDay.coerceAtMost(it.lengthOfMonth())).minusDays(1)
        }
    }
    return DateRange(start, end)
}
internal fun numberFor(bytes: Long, multiplier: Long): String = BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(multiplier), 6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
internal fun parseAmount(text: String, multiplier: Long, title: String): Long {
    require(text.length <= 24) { "$title değeri çok uzun." }
    val value = text.trim().replace(',', '.').toBigDecimalOrNull()
        ?: throw IllegalArgumentException("$title için geçerli bir sayı yaz.")
    require(value > BigDecimal.ZERO) { "$title sıfırdan büyük olmalı. Sınır istemiyorsan Sınırsız seçeneğini kullan." }
    return try {
        value.multiply(BigDecimal.valueOf(multiplier)).setScale(0, RoundingMode.UNNECESSARY).longValueExact().also {
            require(it > 0) { "$title en az 1 bayt olmalı." }
        }
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("$title değeri çok büyük veya desteklenmeyen hassasiyette.")
    }
}
