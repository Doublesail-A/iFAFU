package cn.ifafu.ifafu.ui.widget

import cn.ifafu.ifafu.schedule.ScheduleEvent
import cn.ifafu.ifafu.schedule.TodaySchedule

/** Compatibility facade; all home/widget decisions live in TodaySchedule. */
object UpcomingSchedule {
    fun next(events: List<ScheduleEvent>, now: Long) = TodaySchedule.next(events, now)
    fun today(events: List<ScheduleEvent>, now: Long) = TodaySchedule.today(events, now)
    fun minutesUntil(event: ScheduleEvent, now: Long) = TodaySchedule.minutesUntil(event, now)
    fun urgent(event: ScheduleEvent, now: Long) = TodaySchedule.urgent(event, now)
    fun countdownText(minutes: Long, kind: String) = TodaySchedule.countdownText(minutes, kind)
    fun label(event: ScheduleEvent, now: Long) = TodaySchedule.label(event, now)
    fun nextRefresh(events: List<ScheduleEvent>, now: Long) = TodaySchedule.nextRefresh(events, now)
    fun nextTransition(events: List<ScheduleEvent>, now: Long) = TodaySchedule.nextTransition(events, now)
}
