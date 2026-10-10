package cn.ifafu.ifafu.ui.widget

import cn.ifafu.ifafu.schedule.ScheduleEvent
import java.util.Calendar

object UpcomingSchedule {
    private val order = compareBy<ScheduleEvent> { it.start }
        .thenBy { if (it.kind == "exam") 0 else 1 }.thenBy { it.uid }
    fun next(events: List<ScheduleEvent>, now: Long): ScheduleEvent? = events
        .filter { it.start > now && it.kind in setOf("course", "exam") }.minWithOrNull(order)

    private fun midnight(now: Long) = Calendar.getInstance().apply {
        timeInMillis = now; add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun today(events: List<ScheduleEvent>, now: Long): List<ScheduleEvent> {
        val end = midnight(now)
        return events.filter { it.start > now && it.start < end && it.kind in setOf("course", "exam") }
            .distinctBy { it.uid }.sortedWith(order).take(2)
    }
    fun minutesUntil(event: ScheduleEvent, now: Long): Long = maxOf(0, (event.start - now + 59999) / 60000)

    fun countdownText(minutes: Long, kind: String, multiline: Boolean = false): String {
        val action = if (kind == "exam") "考试" else "上课"
        if (minutes <= 0) return "即将$action"
        val duration = if (minutes < 60) "${minutes}分钟" else buildString {
            append("${minutes / 60}小时")
            if (minutes % 60 != 0L) append("${minutes % 60}分")
        }
        return duration + (if (multiline) "\n" else "") + "后" + action
    }

    /** Update the minute labels while there is a today's event; otherwise wait for the date change. */
    fun nextRefresh(events: List<ScheduleEvent>, now: Long): Long =
        today(events, now).map { now + (it.start - now - 1) % 60000 + 1 }
            .plus(midnight(now) + 1000).minOrNull()!!
}
