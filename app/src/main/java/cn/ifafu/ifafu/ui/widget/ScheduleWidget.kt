package cn.ifafu.ifafu.ui.widget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.SizeF
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.constant.Constants
import cn.ifafu.ifafu.entity.GlobalSetting
import cn.ifafu.ifafu.schedule.ScheduleEvent
import cn.ifafu.ifafu.ui.activity.SplashActivity
import cn.ifafu.ifafu.util.ThemePreferences
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ScheduleWidget {
    const val ACTION_REFRESH = "cn.ifafu.ifafu.widget.REFRESH"
    private val providers = listOf(SyllabusWidget::class.java, CompactScheduleWidget::class.java,
        LargeScheduleWidget::class.java)

    fun requestUpdate(context: Context) {
        context.sendBroadcast(Intent(context, SyllabusWidget::class.java).setAction(ACTION_REFRESH))
    }

    @Synchronized
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = providers.flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(context, 4301,
            Intent(context, SyllabusWidget::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.cancel(pending)
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        val events = ScheduleWidgetStore.load(context)
        val event = UpcomingSchedule.next(events, now)
        val colors = palette(context, events, now)
        fun configured(night: Int): Context = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        })
        val light = if (Build.VERSION.SDK_INT >= 31) palette(configured(Configuration.UI_MODE_NIGHT_NO), events, now) else colors
        val dark = if (Build.VERSION.SDK_INT >= 31) palette(configured(Configuration.UI_MODE_NIGHT_YES), events, now) else null
        ids.forEach { id ->
            val views = if (Build.VERSION.SDK_INT >= 31) RemoteViews(mapOf(
                SizeF(110f, 150f) to render(context, R.layout.widget_schedule_compact, event, light, now, dark),
                SizeF(250f, 150f) to render(context, R.layout.timetable_widget, event, light, now, dark),
                SizeF(250f, 240f) to render(context, R.layout.widget_schedule_large, event, light, now, dark)
            )) else {
                val options = manager.getAppWidgetOptions(id)
                render(context, layoutFor(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250),
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)), event, colors, now)
            }
            manager.updateAppWidget(id, views)
        }
        // Separate from notifications: no notification or exact-alarm permission is needed.
        val time = UpcomingSchedule.nextRefresh(events, now)
        try {
            if (Build.VERSION.SDK_INT >= 23 && (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()))
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
            else if (Build.VERSION.SDK_INT >= 23) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
            else alarm.setExact(AlarmManager.RTC_WAKEUP, time, pending)
        } catch (_: SecurityException) {
            // The permission can be revoked between checking and registering.
            if (Build.VERSION.SDK_INT >= 23) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
            else alarm.set(AlarmManager.RTC_WAKEUP, time, pending)
        }
    }

    internal fun layoutFor(width: Int, height: Int) = when {
        width < 250 -> R.layout.widget_schedule_compact
        height >= 240 -> R.layout.widget_schedule_large
        else -> R.layout.timetable_widget
    }

    data class Colors(val surface: Int, val ink: Int, val secondary: Int, val accent: Int,
        val container: Int, val onContainer: Int)

    @SuppressLint("RestrictedApi")
    internal fun palette(context: Context, events: List<ScheduleEvent>, now: Long): Colors {
        val mode = ThemePreferences.getTheme(context)
        if (mode == GlobalSetting.THEME_SYSTEM || Build.VERSION.SDK_INT < 31) {
            val themed = DynamicColors.wrapContextIfAvailable(ContextThemeWrapper(context, R.style.AppTheme))
            fun color(attr: Int) = MaterialColors.getColor(themed, attr, "desktop widget")
            return Colors(color(com.google.android.material.R.attr.colorSurface),
                color(com.google.android.material.R.attr.colorOnSurface),
                color(com.google.android.material.R.attr.colorOnSurfaceVariant),
                color(com.google.android.material.R.attr.colorPrimary),
                color(com.google.android.material.R.attr.colorPrimaryContainer),
                color(com.google.android.material.R.attr.colorOnPrimaryContainer))
        }
        val seed = if (mode == GlobalSetting.THEME_WALLPAPER) ThemePreferences.getWallpaperSeed(context)
            else UpcomingSchedule.next(events.filter { it.kind == "course" }, now)?.color
                ?: ThemePreferences.getCourseSeed(context)
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val scheme = SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0)
        val roles = MaterialDynamicColors()
        return Colors(roles.surface().getArgb(scheme), roles.onSurface().getArgb(scheme),
            roles.onSurfaceVariant().getArgb(scheme), roles.primary().getArgb(scheme),
            roles.primaryContainer().getArgb(scheme), roles.onPrimaryContainer().getArgb(scheme))
    }

    internal fun render(context: Context, layout: Int, event: ScheduleEvent?, colors: Colors,
        now: Long, nightColors: Colors? = null): RemoteViews = RemoteViews(context.packageName, layout).apply {
        fun tint(id: Int, method: String, light: Int, night: Int?) {
            if (Build.VERSION.SDK_INT >= 31 && night != null) setColorInt(id, method, light, night)
            else setInt(id, method, light)
        }
        tint(R.id.widget_surface, "setColorFilter", colors.surface, nightColors?.surface)
        tint(R.id.widget_badge_background, "setColorFilter", colors.container, nightColors?.container)
        tint(R.id.widget_kind, "setTextColor", colors.onContainer, nightColors?.onContainer)
        tint(R.id.widget_title, "setTextColor", colors.ink, nightColors?.ink)
        tint(R.id.widget_time, "setTextColor", colors.secondary, nightColors?.secondary)
        tint(R.id.widget_location, "setTextColor", colors.secondary, nightColors?.secondary)
        tint(R.id.widget_icon, "setColorFilter", colors.accent, nightColors?.accent)
        setImageViewResource(R.id.widget_icon, if (event?.kind == "exam") R.drawable.ic_m3_event_note else R.drawable.ic_m3_calendar_month)
        setTextViewText(R.id.widget_kind, if (event?.kind == "exam") "下一场考试" else "下一门课程")
        setTextViewText(R.id.widget_title, event?.title ?: "暂无后续安排")
        setTextViewText(R.id.widget_time, event?.let { timeText(it, now, layout == R.layout.widget_schedule_compact) } ?: "打开 iFAFU 同步课程与考试")
        setTextViewText(R.id.widget_location, event?.location?.ifBlank { "地点待定" } ?: "")
        val destination = when {
            event == null -> -1
            event.kind == "exam" -> Constants.ACTIVITY_EXAM
            else -> Constants.SYLLABUS_WIDGET
        }
        val intent = Intent(context, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("from", destination)
        setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context,
            if (event?.kind == "exam") 4303 else 4302, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    internal fun timeText(event: ScheduleEvent, now: Long, compact: Boolean = false): String {
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val date = Calendar.getInstance().apply { timeInMillis = event.start }
        val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        val label = when {
            same(today, date) -> "今天"
            same(tomorrow, date) -> "明天"
            else -> SimpleDateFormat(if (compact) "M/d E" else if (today.get(Calendar.YEAR) == date.get(Calendar.YEAR)) "M月d日 E" else "yyyy年M月d日 E", Locale.CHINA).format(Date(event.start))
        }
        val format = SimpleDateFormat("HH:mm", Locale.CHINA)
        val end = if (event.end > event.start) "–" + format.format(Date(event.end)) else ""
        return label + (if (compact) "\n" else " · ") + format.format(Date(event.start)) + end
    }
}
