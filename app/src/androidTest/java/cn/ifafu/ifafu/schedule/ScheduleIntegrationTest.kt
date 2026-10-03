package cn.ifafu.ifafu.schedule

import android.app.NotificationManager
import android.content.ContentUris
import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.ifafu.ifafu.constant.Constants
import com.blankj.utilcode.util.SPUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun coloredCalendarImportIsIdempotentAndDoesNotTouchOtherEvents() {
        val scope = "instrumentation-test"
        val time = System.currentTimeMillis() + 86_400_000
        val events = listOf(
            ScheduleEvent("test-a", "math-test", "日历验证 · 数学", "教室104", "验证数据", time, time + 2700000, 0xff6750a4.toInt(), scope = scope),
            ScheduleEvent("test-b", "lab-test", "日历验证 · 实验", "实验室204", "验证数据", time + 3600000, time + 6300000, 0xff006a6a.toInt(), scope = scope))
        val uri = CalendarContract.Events.CONTENT_URI
        val selection = CalendarContract.Events.CUSTOM_APP_URI + "=?"
        val args = arrayOf("ifafu://term/" + scope)
        fun count() = context.contentResolver.query(uri, arrayOf("_id"), selection, args, null)!!.use { it.count }
        try {
            assertEquals(2, CalendarImporter.import(context, events, "integration-test"))
            assertEquals(2, count())
            assertEquals(2, CalendarImporter.import(context, events.map { it.copy(location = "更新后的教室") }, "integration-test"))
            assertEquals(2, count())
            context.contentResolver.query(uri, arrayOf(CalendarContract.Events.EVENT_COLOR, CalendarContract.Events.EVENT_LOCATION), selection, args, null)!!.use {
                val colors = mutableSetOf<Int>()
                while (it.moveToNext()) { colors.add(it.getInt(0)); assertEquals("更新后的教室", it.getString(1)) }
                assertEquals(2, colors.size)
            }
        } finally {
            context.contentResolver.delete(uri, selection, args)
            val calendars = CalendarContract.Calendars.CONTENT_URI
            val account = "iFAFU-" + ScheduleEvents.key("integration-test").take(12)
            val sync = calendars.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter("account_name", account).appendQueryParameter("account_type", CalendarContract.ACCOUNT_TYPE_LOCAL).build()
            context.contentResolver.delete(sync, "account_name=?", arrayOf(account))
        }
    }

    /** External ADB verification kills this process after instrumentation finishes.
     * Two separate deadlines detect cold-start refresh erasing the remaining queue.
     * Skipped in the ordinary test run; use -e coldStartDelayMs 30000 on a test device.
     */
    @Test fun prepareProcessDeathReminders() {
        val delay = InstrumentationRegistry.getArguments().getString("coldStartDelayMs")?.toLong()
        org.junit.Assume.assumeNotNull(delay)
        Thread.sleep(1500)
        val accountStore = SPUtils.getInstance(Constants.SP_USER_INFO)
        check(accountStore.getString("account", "") in listOf("", "integration-cold-start")) {
            "Cold-start fixtures require an empty isolated test device"
        }
        context.getSharedPreferences("schedule_reminders", android.content.Context.MODE_PRIVATE)
            .edit().remove("delivered").commit()
        accountStore.put("account", "integration-cold-start", true)
        assertTrue(ReminderScheduler.allowed(context))
        assertTrue(ReminderScheduler.exact(context))
        val now = System.currentTimeMillis()
        val course = ScheduleEvent("cold-start-course", "math-test", "冷启动验证 · 数学", "创104", "",
            now + 15 * 60000 + delay!!, now + 16 * 60000 + delay, 0xff2196f3.toInt())
        val gap = if (InstrumentationRegistry.getArguments().getString("coldStartTogether") == "true") 0L else 35000L
        val exam = course.copy(uid = "cold-start-exam", title = "冷启动验证 · 考试", kind = "exam",
            start = now + 30 * 60000 + delay + gap, end = now + 31 * 60000 + delay,
            location = "考场201", description = "座位：23")
        context.getSystemService(NotificationManager::class.java).apply {
            cancel("cold-start-course".hashCode()); cancel("cold-start-exam".hashCode())
        }
        ReminderScheduler.update(context, listOf(course, exam), "integration-cold-start")
        ReminderScheduler.setEnabled(context, "course", true)
        ReminderScheduler.setEnabled(context, "exam", true)
    }

    @Test fun clearProcessDeathReminders() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("coldStartCleanup") == "true")
        val accountStore = SPUtils.getInstance(Constants.SP_USER_INFO)
        check(accountStore.getString("account", "") == "integration-cold-start")
        context.getSystemService(NotificationManager::class.java).apply {
            cancel("cold-start-course".hashCode()); cancel("cold-start-exam".hashCode())
        }
        accountStore.remove("account")
        ReminderScheduler.update(context, emptyList(), "")
        ReminderScheduler.setEnabled(context, "course", false)
        ReminderScheduler.setEnabled(context, "exam", false)
    }

    @Test fun systemAlarmDeliversCourseAndExamNotificationsWithDetails() {
        val accountStore = SPUtils.getInstance(Constants.SP_USER_INFO)
        val previous = accountStore.getString("account", "")
        val courseEnabled = ReminderScheduler.enabled(context, "course")
        val examEnabled = ReminderScheduler.enabled(context, "exam")
        Thread.sleep(1500) // Initial application's database refresh has finished.
        val now = System.currentTimeMillis()
        val course = ScheduleEvent("integration-course", "test", "通知验证 · 数学", "创104", "", now + 15 * 60000 + 3500, now + 16 * 60000, 1)
        val exam = course.copy(uid = "integration-exam", title = "通知验证 · 考试", kind = "exam",
            start = now + 30 * 60000 + 3500, end = now + 31 * 60000, location = "考场201", description = "座位：23")
        try {
            accountStore.put("account", "integration-test")
            assertTrue(ReminderScheduler.allowed(context))
            assertTrue(ReminderScheduler.exact(context))
            ReminderScheduler.update(context, listOf(course, exam), "integration-test")
            ReminderScheduler.setEnabled(context, "course", true)
            ReminderScheduler.setEnabled(context, "exam", true)
            Thread.sleep(6500)
            val notifications = context.getSystemService(NotificationManager::class.java).activeNotifications
            val details = notifications.map { it.notification.extras.getCharSequence("android.bigText").toString() }
            assertTrue(details.any { it.contains("数学") && it.contains("创104") })
            assertTrue(details.any { it.contains("考试") && it.contains("考场201") && it.contains("23") })
        } finally {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.cancel("integration-course".hashCode()); manager.cancel("integration-exam".hashCode())
            accountStore.put("account", previous)
            ReminderScheduler.update(context, emptyList(), previous)
            ReminderScheduler.setEnabled(context, "course", courseEnabled)
            ReminderScheduler.setEnabled(context, "exam", examEnabled)
        }
    }
}
