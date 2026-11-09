package dev.akiskontrol.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class QuotaPeriodsTest {
    private fun start(date: String, day: Int) = periodStart(LocalDate.parse(date), QuotaPeriod.MONTHLY, day)

    @Test fun monthlyPeriodStartsOnConfiguredDayIncludingPreviousYear() {
        assertEquals(LocalDate.parse("2026-10-15"), start("2026-11-14", 15))
        assertEquals(LocalDate.parse("2026-11-15"), start("2026-11-15", 15))
        assertEquals(LocalDate.parse("2026-11-15"), start("2026-12-14", 15))
        assertEquals(LocalDate.parse("2025-12-15"), start("2026-01-01", 15))
    }

    @Test fun day31ClampsToFebruaryEndAndReturnsToMarch31() {
        assertEquals(LocalDate.parse("2026-01-31"), start("2026-02-27", 31))
        assertEquals(LocalDate.parse("2026-02-28"), start("2026-02-28", 31))
        assertEquals(LocalDate.parse("2026-02-28"), start("2026-03-30", 31))
        assertEquals(LocalDate.parse("2026-03-31"), start("2026-03-31", 31))
    }

    @Test fun leapFebruaryRenewsOn29AndDay30DoesNotDrift() {
        assertEquals(LocalDate.parse("2028-01-31"), start("2028-02-28", 31))
        assertEquals(LocalDate.parse("2028-02-29"), start("2028-02-29", 31))
        assertEquals(LocalDate.parse("2028-02-29"), start("2028-03-30", 31))
        assertEquals(LocalDate.parse("2028-02-29"), start("2028-03-29", 30))
        assertEquals(LocalDate.parse("2028-03-30"), start("2028-03-30", 30))
    }

    @Test fun defaultMonthlyDailyAndWeeklyRemainCompatible() {
        val date = LocalDate.parse("2026-11-06")
        assertEquals(LocalDate.parse("2026-11-01"), periodStart(date, QuotaPeriod.MONTHLY))
        assertEquals(date, periodStart(date, QuotaPeriod.DAILY, 31))
        assertEquals(LocalDate.parse("2026-11-02"), periodStart(date, QuotaPeriod.WEEKLY, 31))
    }

    @Test fun invalidRenewalDaysAreRejectedBeforeTheyCanWrap() {
        listOf(Int.MIN_VALUE, 0, 32, Int.MAX_VALUE).forEach { day ->
            assertThrows(IllegalArgumentException::class.java) { NetworkRule(renewalDay = day) }
            assertThrows(IllegalArgumentException::class.java) { GlobalQuota(renewalDay = day) }
            assertThrows(IllegalArgumentException::class.java) { start("2026-11-06", day) }
        }
    }
}
