package cn.ifafu.ifafu.ui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/** Process-local system clock observer; no service, wake lock or repository initialization. */
object WidgetClock {
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_TIME_TICK &&
                (!ScheduleWidget.interactive(context) || !ScheduleWidget.clockTickDue(System.currentTimeMillis()))) return
            ScheduleWidget.updateAll(context, force = intent.action != Intent.ACTION_TIME_TICK)
        }
    }
    @Synchronized fun ensure(context: Context, needed: Boolean) {
        val app = context.applicationContext
        if (needed && !registered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_LOCALE_CHANGED); addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            }
            ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        } else if (!needed && registered) {
            app.unregisterReceiver(receiver)
            registered = false
        }
    }
}
