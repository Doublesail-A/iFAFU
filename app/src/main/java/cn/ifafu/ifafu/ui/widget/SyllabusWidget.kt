package cn.ifafu.ifafu.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** Cold receivers only render persisted data, without database or network work. */
open class ScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ScheduleWidget.updateAll(context, force = true)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = ScheduleWidget.updateAll(context, force = true)
    override fun onDeleted(context: Context, ids: IntArray) = ScheduleWidget.updateAll(context)
    override fun onDisabled(context: Context) = ScheduleWidget.updateAll(context)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in setOf(ScheduleWidget.ACTION_REFRESH, Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"))
            ScheduleWidget.updateAll(context, force = intent.action != ScheduleWidget.ACTION_REFRESH)
    }
}

// Preserve the original component so existing desktop widgets upgrade in place.
class SyllabusWidget : ScheduleWidgetProvider()
class CompactScheduleWidget : ScheduleWidgetProvider()
class LargeScheduleWidget : ScheduleWidgetProvider()
