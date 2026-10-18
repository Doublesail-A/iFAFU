package cn.ifafu.ifafu.schedule

import java.util.Calendar

/** Shared by the home screen and widgets; there is no separate widget grace period. */
object TodaySchedule {
    val kinds = setOf("course", "exam", "agenda")
    private val order = compareBy<ScheduleEvent> { it.start }
        .thenBy { if (it.kind == "exam") 0 else 1 }.thenBy { it.uid }

    fun dayStart(now: Long) = Calendar.getInstance().apply {
        timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    fun midnight(now: Long) = Calendar.getInstance().apply {
        timeInMillis = dayStart(now); add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
    fun active(event: ScheduleEvent, now: Long) = event.start <= now && event.end > now
    fun today(events: List<ScheduleEvent>, now: Long, limit: Int = 2): List<ScheduleEvent> {
        val start = dayStart(now); val end = midnight(now)
        return events.filter { it.kind in kinds && it.start >= start && it.start < end &&
            (it.start > now || active(it, now)) }.distinctBy { it.uid }.sortedWith(order).take(limit)
    }
    fun next(events: List<ScheduleEvent>, now: Long) = events.filter { it.kind in kinds && it.start > now }.minWithOrNull(order)
    fun minutesUntil(event: ScheduleEvent, now: Long) = maxOf(0L, (event.start - now + 59999) / 60000)
    fun urgent(event: ScheduleEvent, now: Long) = event.start > now &&
        event.start - now <= (if (event.kind == "exam") 30 else 15) * 60000L
    fun duration(minutes: Long): String = buildString {
        if (minutes >= 60) append("${minutes / 60}小时")
        if (minutes % 60 != 0L || minutes == 0L) append("${minutes % 60}" + if (minutes >= 60) "分" else "分钟")
    }
    fun countdownText(minutes: Long, kind: String, ongoing: Boolean = false): String {
        val action = when (kind) {
            "course" -> if (ongoing) "下课" else "上课"
            "exam" -> if (ongoing) "考试结束" else "考试"
            else -> if (ongoing) "结束" else "开始"
        }
        return if (minutes <= 0) "即将$action" else duration(minutes) + "后" + action
    }
    fun label(event: ScheduleEvent, now: Long): String {
        val ongoing = active(event, now)
        val target = if (ongoing) event.end else event.start
        return countdownText(maxOf(0, (target - now + 59999) / 60000), event.kind, ongoing)
    }
    fun nextTransition(events: List<ScheduleEvent>, now: Long): Long = events.filter { it.kind in kinds }
        .flatMap { listOf(it.start - (if (it.kind == "exam") 30 else 15) * 60000L, it.start, it.end) }
        .filter { it > now && it < midnight(now) }.plus(midnight(now) + 1000).minOrNull()!!
    fun nextRefresh(events: List<ScheduleEvent>, now: Long): Long {
        val labels = today(events, now).map {
            val target = if (active(it, now)) it.end else it.start
            now + (target - now - 1) % 60000 + 1
        }
        // Include start/end boundaries even when hidden behind the first two items.
        return (labels + nextTransition(events, now)).minOrNull()!!
    }
}
