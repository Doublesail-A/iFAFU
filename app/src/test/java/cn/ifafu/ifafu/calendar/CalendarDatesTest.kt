package cn.ifafu.ifafu.calendar

import cn.ifafu.ifafu.util.DateUtils
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.Executors

class CalendarDatesTest {
    @Test fun firstWeekBoundariesAndDifferentSemestersAreStableAcrossThreads() {
        val zone = ZoneId.systemDefault()
        fun midnight(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
        val opening = midnight("2026-08-30") + 14 * 3600000
        assertEquals(1, DateUtils.getCurrentWeek(opening, midnight("2026-08-30")))
        assertEquals(-1, DateUtils.getCurrentWeek(opening, midnight("2026-08-29")))
        assertEquals(6, DateUtils.getCurrentWeek(opening, midnight("2026-10-05")))
        val executor = Executors.newFixedThreadPool(6)
        try {
            val work = (1..100).map { n ->
                executor.submit<Boolean> {
                    val start = if (n % 2 == 0) "2026-08-30" else "2027-02-21"
                    val end = LocalDate.parse(start).plusWeeks(5).toString()
                    DateUtils.getCurrentWeek(midnight(start), midnight(end)) == 6
                }
            }
            assertTrue(work.all { it.get() })
        } finally { executor.shutdownNow() }
    }
}
