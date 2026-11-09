package dev.akiskontrol.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Inclusive start of the active quota period. Short months renew on their last day. */
fun periodStart(date: LocalDate, period: QuotaPeriod, renewalDay: Int = 1): LocalDate {
    require(renewalDay in 1..31) { "Yenilenme günü 1–31 arasında olmalı" }
    return when (period) {
        QuotaPeriod.DAILY -> date
        QuotaPeriod.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        QuotaPeriod.MONTHLY -> {
            val boundary = date.withDayOfMonth(minOf(renewalDay, date.lengthOfMonth()))
            if (!date.isBefore(boundary)) boundary
            else date.minusMonths(1).let { it.withDayOfMonth(minOf(renewalDay, it.lengthOfMonth())) }
        }
    }
}
