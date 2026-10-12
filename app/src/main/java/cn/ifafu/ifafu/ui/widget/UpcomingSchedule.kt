package cn.ifafu.ifafu.ui.widget

import cn.ifafu.ifafu.schedule.ScheduleEvent
import java.util.Calendar

object UpcomingSchedule {
    const val RECENT_COURSE_MS = 10 * 60000L
    private val order = compareBy<ScheduleEvent> { it.start }
        .thenBy { if (it.kind == "exam") 0 else 1 }.thenBy { it.uid }
    fun next(events: List<ScheduleEvent>, now: Long): ScheduleEvent? = events
        .filter { it.start > now && it.kind in setOf("course", "exam") }.minWithOrNull(order)
    private fun dayStart(now: Long) = Calendar.getInstance().apply {
        timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private fun midnight(now: Long) = Calendar.getInstance().apply {
        timeInMillis = dayStart(now); add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
    fun recentCourse(event: ScheduleEvent, now: Long) = event.kind == "course" &&
        now >= event.start && now < minOf(event.end, event.start + RECENT_COURSE_MS)
    fun today(events: List<ScheduleEvent>, now: Long): List<ScheduleEvent> {
        val start = dayStart(now); val end = midnight(now)
        return events.filter { it.start >= start && it.start < end &&
            it.kind in setOf("course", "exam") && (it.start > now || recentCourse(it, now)) }
            .distinctBy { it.uid }.sortedWith(order).take(2)
    }
    fun minutesUntil(event: ScheduleEvent, now: Long): Long = maxOf(0, (event.start - now + 59999) / 60000)
    fun urgent(event: ScheduleEvent, now: Long) = event.start > now &&
        event.start - now <= (if (event.kind == "exam") 30 else 15) * 60000L
    fun countdownText(minutes: Long, kind: String): String {
        val action = if (kind == "exam") "考试" else "上课"
        if (minutes <= 0) return "即将" + action
        if (minutes < 60) return minutes.toString() + "分钟后" + action
        return buildString {
            append((minutes / 60).toString() + "小时")
            if (minutes % 60 != 0L) append((minutes % 60).toString() + "分")
            append("后")
        }
    }
    fun label(event: ScheduleEvent, now: Long): String = if (recentCourse(event, now))
        "已上课" + ((now - event.start) / 60000) + "分钟"
        else countdownText(minutesUntil(event, now), event.kind)
    /** Labels change on their actual minute boundary, including the ten-minute handoff. */
    fun nextRefresh(events: List<ScheduleEvent>, now: Long): Long =
        today(events, now).map {
            if (recentCourse(it, now)) minOf(it.start + ((now - it.start) / 60000 + 1) * 60000,
                it.end, it.start + RECENT_COURSE_MS)
            else now + (it.start - now - 1) % 60000 + 1
        }.plus(midnight(now) + 1000).minOrNull()!!
    /** Sleeping devices retain one non-wakeup structural deadline, never minute polls. */
    fun nextTransition(events: List<ScheduleEvent>, now: Long): Long {
        val midnight = midnight(now)
        return events.filter { it.kind in setOf("course", "exam") }
            .flatMap { listOf(it.start - (if (it.kind == "exam") 30 else 15) * 60000L,
                it.start, if (it.kind == "course") minOf(it.end, it.start + RECENT_COURSE_MS) else it.start) }
            .filter { it > now && it < midnight }.plus(midnight + 1000).minOrNull()!!
    }
}
