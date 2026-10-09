package cn.ifafu.ifafu.ui.widget

import cn.ifafu.ifafu.schedule.ScheduleEvent
import java.util.Calendar

/** Select actual dated events from the shared semester schedule, not a weekday grid. */
object UpcomingSchedule {
    fun next(events: List<ScheduleEvent>, now: Long): ScheduleEvent? = events
        .filter { it.start > now && it.kind in setOf("course", "exam") }
        .minWithOrNull(compareBy<ScheduleEvent> { it.start }.thenBy { if (it.kind == "exam") 0 else 1 }
            .thenBy { it.uid })

    fun nextRefresh(events: List<ScheduleEvent>, now: Long): Long {
        val midnight = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 1); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return minOf(midnight, next(events, now)?.start?.plus(1000L) ?: midnight)
    }
}
