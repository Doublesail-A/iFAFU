package cn.ifafu.ifafu.schedule

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.constant.Constants
import cn.ifafu.ifafu.ui.timetable.TimetableActivity
import cn.ifafu.ifafu.ui.examlist.ExamListActivity
import com.blankj.utilcode.util.SPUtils
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One persisted alarm queue, with exact idle alarms when the user allows them. */
object ReminderScheduler {
    private fun prefs(context: Context) = context.getSharedPreferences("schedule_reminders", Context.MODE_PRIVATE)
    fun enabled(context: Context, kind: String) = prefs(context).getBoolean(kind, false)
    fun setEnabled(context: Context, kind: String, value: Boolean) {
        prefs(context).edit().putBoolean(kind, value).commit()
        scheduleNext(context)
    }
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun exact(context: Context) = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("course_reminders", "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply { description = "上课前 15 分钟，显示课程和教室" })
            manager.createNotificationChannel(NotificationChannel("exam_reminders", "考试提醒", NotificationManager.IMPORTANCE_HIGH).apply { description = "考试前 30 分钟，显示科目、考场和座位" })
        }
    }

    @Synchronized
    fun update(context: Context, events: List<ScheduleEvent>, account: String) {
        val now = System.currentTimeMillis()
        val tests = load(context).filter { it.kind == "test" && it.start > now }
        val array = JSONArray()
        (events.filter { it.start > now } + tests).distinctBy { it.uid }.sortedBy { it.reminderAt }.forEach {
            array.put(JSONObject().put("uid", it.uid).put("title", it.title).put("location", it.location)
                .put("description", it.description).put("start", it.start).put("end", it.end)
                .put("color", it.color).put("kind", it.kind).put("scope", it.scope))
        }
        prefs(context).edit().putString("events", array.toString())
            .putString("account", ScheduleEvents.key(account)).commit()
        scheduleNext(context)
    }

    private fun load(context: Context): List<ScheduleEvent> = runCatching {
        val array = JSONArray(prefs(context).getString("events", "[]"))
        (0 until array.length()).map {
            val obj = array.getJSONObject(it)
            ScheduleEvent(obj.getString("uid"), "", obj.getString("title"), obj.getString("location"),
                obj.getString("description"), obj.getLong("start"), obj.getLong("end"),
                obj.getInt("color"), obj.getString("kind"), obj.optString("scope"))
        }
    }.getOrDefault(emptyList())

    private fun pending(context: Context) = PendingIntent.getBroadcast(context, 4201,
        Intent(context, ReminderReceiver::class.java).setAction("cn.ifafu.REMINDER"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun eligible(context: Context, event: ScheduleEvent, now: Long): Boolean {
        if (event.start <= now) return false
        if (event.uid in prefs(context).getStringSet("delivered", emptySet()).orEmpty()) return false
        if (event.kind == "test") return true
        val current = SPUtils.getInstance(Constants.SP_USER_INFO).getString("account", "")
        return ScheduleEvents.key(current) == prefs(context).getString("account", "") && enabled(context, event.kind)
    }

    @Synchronized
    fun scheduleNext(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = pending(context)
        alarm.cancel(intent)
        // Permission denial must not consume occurrences or cause one-second retries.
        if (!allowed(context)) return
        val now = System.currentTimeMillis()
        val next = load(context).filter { eligible(context, it, now) }.minOfOrNull { it.reminderAt } ?: return
        val trigger = maxOf(now + 1_000, next)
        try {
            if (Build.VERSION.SDK_INT < 23) alarm.setExact(AlarmManager.RTC_WAKEUP, trigger, intent)
            else if (exact(context)) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= 23) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
            else alarm.set(AlarmManager.RTC_WAKEUP, trigger, intent)
        }
    }

    @Synchronized
    fun deliverDue(context: Context) {
        val now = System.currentTimeMillis()
        val events = load(context)
        val due = events.filter { eligible(context, it, now) && it.reminderAt <= now + 1_000 }
        val delivered = prefs(context).getStringSet("delivered", emptySet()).orEmpty().toMutableSet()
        due.forEach { event ->
            if (allowed(context) && notify(context, event)) delivered.add(event.uid)
        }
        // Keep only current occurrences; prevents unbounded growth and duplicate alerts.
        prefs(context).edit().putStringSet("delivered", delivered.intersect(events.map { it.uid }.toSet())).commit()
        scheduleNext(context)
    }

    private fun notify(context: Context, event: ScheduleEvent): Boolean {
        createChannels(context)
        val exam = event.kind == "exam"
        val destination = Intent(context, if (exam) ExamListActivity::class.java else TimetableActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(context, event.uid.hashCode(), destination,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val time = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(event.start))
        val heading = if (event.kind == "test") "提醒测试" else if (exam) "30 分钟后考试" else "15 分钟后上课"
        val body = time + " · " + event.title + "\n" + event.location +
            if (exam) "\n" + event.description else ""
        val notification = NotificationCompat.Builder(context, if (exam) "exam_reminders" else "course_reminders")
            .setSmallIcon(R.drawable.ic_m3_calendar_month).setContentTitle(heading + " · " + event.title)
            .setContentText(time + " · " + event.location).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setColor(event.color).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(event.uid.hashCode(), notification)
            true
        } catch (_: SecurityException) { false }
    }

    fun test(context: Context) {
        val now = System.currentTimeMillis()
        // The normal queue exercises background delivery, not a foreground-only toast.
        val test = ScheduleEvent("test-" + now, "test", "iFAFU 通知已就绪", "课程名、教室、时间将在这里显示",
            "", now + 15 * 60_000 + 30_000, now + 16 * 60_000, 0xff6750a4.toInt(), "test")
        val current = SPUtils.getInstance(Constants.SP_USER_INFO).getString("account", "")
        update(context, load(context) + test, current)
    }

    fun status(context: Context): String {
        val now = System.currentTimeMillis()
        val count = load(context).count { eligible(context, it, now) && it.kind != "test" }
        return when {
            !allowed(context) -> "尚未允许通知，点击开启系统通知权限"
            !exact(context) -> "已准备 " + count + " 条提醒 · 未允许准时提醒，系统可能延迟"
            else -> "已准备 " + count + " 条提醒 · 已登记系统闹钟，无需保持应用运行"
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Keep delivery synchronous: AlarmManager holds the wake lock for onReceive.
        // Posting a notification and registering the next alarm only need the small
        // disk-backed queue; no network, database refresh or long-lived service.
        if (intent.action == "cn.ifafu.REMINDER") ReminderScheduler.deliverDue(context)
        else ReminderScheduler.scheduleNext(context)
    }
}
