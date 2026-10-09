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
import android.view.View
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
        val today = UpcomingSchedule.today(events, now)
        val colors = palette(context, events, now)
        fun configured(night: Int): Context = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        })
        val light = if (Build.VERSION.SDK_INT >= 31) palette(configured(Configuration.UI_MODE_NIGHT_NO), events, now) else colors
        val dark = if (Build.VERSION.SDK_INT >= 31) palette(configured(Configuration.UI_MODE_NIGHT_YES), events, now) else null
        ids.forEach { id ->
            val views = if (Build.VERSION.SDK_INT >= 31) RemoteViews(mapOf(
                SizeF(250f, 60f) to render(context, R.layout.timetable_widget, today, light, now, dark),
                SizeF(110f, 140f) to render(context, R.layout.widget_schedule_compact, today, light, now, dark),
                SizeF(110f, 180f) to render(context, R.layout.widget_schedule_compact, today, light, now, dark, roomy = true),
                SizeF(250f, 120f) to render(context, R.layout.widget_schedule_large, today, light, now, dark),
                SizeF(250f, 150f) to render(context, R.layout.widget_schedule_large, today, light, now, dark, roomy = true)
            )) else {
                val options = manager.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 60)
                render(context, layoutFor(width, height), today, colors, now, roomy = height >= if (width >= 250) 150 else 180)
            }
            manager.updateAppWidget(id, views)
        }
        // Non-wakeup refresh: minute labels update while the device is awake; sleeping phones are not woken.
        val time = UpcomingSchedule.nextRefresh(events, now)
        try {
            if (Build.VERSION.SDK_INT >= 23 && (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()))
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC, time, pending)
            else if (Build.VERSION.SDK_INT >= 23) alarm.setAndAllowWhileIdle(AlarmManager.RTC, time, pending)
            else alarm.setExact(AlarmManager.RTC, time, pending)
        } catch (_: SecurityException) {
            // The permission can be revoked between checking and registering.
            if (Build.VERSION.SDK_INT >= 23) alarm.setAndAllowWhileIdle(AlarmManager.RTC, time, pending)
            else alarm.set(AlarmManager.RTC, time, pending)
        }
    }

    internal fun layoutFor(width: Int, height: Int) = when {
        width < 250 -> R.layout.widget_schedule_compact
        height >= 120 -> R.layout.widget_schedule_large
        else -> R.layout.timetable_widget
    }

    data class Colors(val surface: Int, val ink: Int, val secondary: Int, val accent: Int,
        val container: Int, val onContainer: Int, val error: Int)

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
                color(com.google.android.material.R.attr.colorOnPrimaryContainer),
                color(com.google.android.material.R.attr.colorError))
        }
        val seed = if (mode == GlobalSetting.THEME_WALLPAPER) ThemePreferences.getWallpaperSeed(context)
            else UpcomingSchedule.next(events.filter { it.kind == "course" }, now)?.color
                ?: ThemePreferences.getCourseSeed(context)
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val scheme = SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0)
        val roles = MaterialDynamicColors()
        return Colors(roles.surface().getArgb(scheme), roles.onSurface().getArgb(scheme),
            roles.onSurfaceVariant().getArgb(scheme), roles.primary().getArgb(scheme),
            roles.primaryContainer().getArgb(scheme), roles.onPrimaryContainer().getArgb(scheme),
            roles.error().getArgb(scheme))
    }

    internal fun render(context: Context, layout: Int, events: List<ScheduleEvent>, colors: Colors,
        now: Long, nightColors: Colors? = null, roomy: Boolean = false): RemoteViews =
        RemoteViews(context.packageName, layout).apply {
        fun tint(id: Int, method: String, light: Int, night: Int?) {
            if (Build.VERSION.SDK_INT >= 31 && night != null) setColorInt(id, method, light, night)
            else setInt(id, method, light)
        }
        tint(R.id.widget_surface, "setColorFilter", colors.surface, nightColors?.surface)
        tint(R.id.widget_empty, "setTextColor", colors.secondary, nightColors?.secondary)
        val titles = intArrayOf(R.id.widget_title, R.id.widget_title_2)
        val places = intArrayOf(R.id.widget_location, R.id.widget_location_2)
        val countdowns = intArrayOf(R.id.widget_countdown, R.id.widget_countdown_2)
        val times = intArrayOf(R.id.widget_time, R.id.widget_time_2)
        val rows = intArrayOf(R.id.widget_row_1, R.id.widget_row_2)
        val compact = layout == R.layout.widget_schedule_compact
        val selected = UpcomingSchedule.today(events, now).take(if (layout == R.layout.timetable_widget) 1 else 2)
        setViewVisibility(R.id.widget_empty, if (selected.isEmpty()) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_content, if (selected.isEmpty()) View.GONE else View.VISIBLE)
        setTextViewText(R.id.widget_empty, "今天接下来的时间留给自己")
        selected.forEachIndexed { index, event ->
            val minutes = UpcomingSchedule.minutesUntil(event, now)
            val urgent = minutes <= if (event.kind == "exam") 30 else 15
            setViewVisibility(rows[index], View.VISIBLE)
            setTextViewText(titles[index], (if (event.kind == "exam") "考试 · " else "") + event.title)
            setTextViewText(places[index], event.location.ifBlank { "地点待定" })
            setTextViewText(countdowns[index], minutes.toString() + "分钟后" + if (event.kind == "exam") "考试" else "上课")
            setTextViewText(times[index], listOf(event.teacher, timeText(event)).filter { it.isNotBlank() }.joinToString(" · "))
            setViewVisibility(times[index], if (roomy && !compact && layout != R.layout.timetable_widget) View.VISIBLE else View.GONE)
            if (compact) setInt(titles[index], "setMaxLines", if (roomy) 2 else 1)
            tint(titles[index], "setTextColor", colors.ink, nightColors?.ink)
            tint(places[index], "setTextColor", colors.ink, nightColors?.ink)
            tint(countdowns[index], "setTextColor", if (urgent) colors.error else colors.accent,
                nightColors?.let { if (urgent) it.error else it.accent })
            tint(times[index], "setTextColor", colors.secondary, nightColors?.secondary)
            setOnClickPendingIntent(rows[index], open(context, event))
        }
        if (selected.size < 2) setViewVisibility(R.id.widget_row_2, View.GONE)
        setOnClickPendingIntent(R.id.widget_root, open(context, selected.firstOrNull()))
    }

    private fun open(context: Context, event: ScheduleEvent?): PendingIntent {
        val destination = when {
            event == null -> -1
            event.kind == "exam" -> Constants.ACTIVITY_EXAM
            else -> Constants.SYLLABUS_WIDGET
        }
        val intent = Intent(context, SplashActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("from", destination)
        return PendingIntent.getActivity(context, 4400 + destination, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun timeText(event: ScheduleEvent): String {
        val format = SimpleDateFormat("HH:mm", Locale.CHINA)
        return format.format(Date(event.start)) + if (event.end > event.start) "–" + format.format(Date(event.end)) else ""
    }
}
