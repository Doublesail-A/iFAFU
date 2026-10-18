package cn.ifafu.ifafu.schedule

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.db.JiaowuDatabase
import cn.ifafu.ifafu.entity.AgendaItem
import cn.ifafu.ifafu.entity.NewCourse
import cn.ifafu.ifafu.entity.User
import cn.ifafu.ifafu.constant.Constants
import cn.ifafu.ifafu.ui.timetable_item.TimetableItemActivity
import cn.ifafu.ifafu.ui.view.timetable.TimetableItem
import cn.ifafu.ifafu.ui.view.timetable.TimetableView
import com.blankj.utilcode.util.SPUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AgendaIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun migrationPreservesCoursesAndAgendaIsAccountScoped() = runBlocking {
        val name = "agenda-migration-test.db"
        context.deleteDatabase(name)
        val isolated = object : android.content.ContextWrapper(context) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getDatabasePath(ignored: String) = context.getDatabasePath(name)
            override fun openOrCreateDatabase(ignored: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?) =
                context.openOrCreateDatabase(name, mode, factory)
            override fun openOrCreateDatabase(ignored: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?, handler: android.database.DatabaseErrorHandler?) =
                context.openOrCreateDatabase(name, mode, factory, handler)
        }
        var db = JiaowuDatabase.getInstance(isolated)
        db.newCourseDao.saveCourses(NewCourse(id = 991001, account = "fixture", name = "保留课程", weeks = sortedSetOf(1, 2)))
        db.close()
        // Version 18 has the same other tables; remove only the newly introduced table.
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
            it.execSQL("DROP TABLE agenda_item"); it.version = 18
        }
        db = JiaowuDatabase.getInstance(isolated)
        try {
            assertEquals("保留课程", db.newCourseDao.getCourseById(991001)!!.name)
            val item = AgendaItem(account = "fixture", title = "假期自己的日程", date = "2026-10-03", startMinute = 480, endMinute = 540)
            db.agendaDao.save(item)
            assertEquals(item, db.agendaDao.find(item.id, "fixture"))
            assertNull(db.agendaDao.find(item.id, "another"))
            db.agendaDao.delete(item.id, "another")
            assertEquals(1, db.agendaDao.all("fixture").size)
            db.agendaDao.delete(item.id, "fixture")
            assertTrue(db.agendaDao.all("fixture").isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun nativeTimetableKeepsOverlappingAgendaBesideTheCourse() {
        instrumentation.runOnMainSync {
            val view = TimetableView(ContextThemeWrapper(context, R.style.AppTheme))
            view.setTimeText(arrayOf("8:00","8:50","9:55","10:45","11:35","14:00","14:50","15:50","16:40","18:25","19:15","20:05"))
            val course = TimetableItem().apply { name = "课程"; dayOfWeek = 2; startNode = 1; nodeCount = 2; startMinute = 480; endMinute = 575 }
            val agenda = TimetableItem().apply { name = "读书会"; dayOfWeek = 2; startNode = 1; nodeCount = 1; startMinute = 500; endMinute = 550; this.agenda = true; customColor = 0xffedabc2.toInt() }
            view.setItems(listOf(course, agenda))
            view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 1080, 1800)
            val children = (0 until view.childCount).map { view.getChildAt(it) }.filter { it.tag is TimetableItem }
            val courseView = children.single { it.tag === course }; val agendaView = children.single { it.tag === agenda }
            assertTrue(courseView.right <= agendaView.left)
            assertTrue(courseView.width > agendaView.width)
            assertTrue(agendaView.height > 0)
            assertTrue(agendaView.top >= courseView.top)
            val bitmap = android.graphics.Bitmap.createBitmap(1080,1800,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(context.getExternalFilesDir(null), "agenda-overlap.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
    }

    @Test fun editorDefaultsToAgendaPreservesCourseFormAndSavesActualFields() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("isolatedAgendaTest") == "true")
        // L8 shrinks the separate test APK's runtime independently. Keep the date
        // APIs used by the target editor so its j$.time classes cannot be shadowed
        // by a partial test copy, even though this test calls the editor indirectly.
        val date = java.time.LocalDate.now()
        val time = java.time.LocalTime.now()
        val zone = java.time.ZoneId.systemDefault()
        val instant = date.atStartOfDay().plusMinutes((time.hour * 60 + time.minute).toLong()).atZone(zone).toInstant()
        assertTrue(instant.toEpochMilli() > 0)
        assertEquals(date, java.time.Instant.ofEpochMilli(instant.toEpochMilli()).atZone(zone).toLocalDate())
        assertTrue(date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日 · EEEE", java.util.Locale.CHINA)).isNotBlank())
        assertTrue(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() > 0)
        val prefs = SPUtils.getInstance(Constants.SP_USER_INFO)
        val previous = prefs.getString("account", "")
        val account = "agenda-ui-fixture"
        val db = JiaowuDatabase.getInstance(context)
        db.userDao.saveUsing(User(account = account))
        context.getSharedPreferences("startup_permissions_v1", 0).edit().putBoolean("notifications",true).putBoolean("exact",true).commit()
        try {
            val intent = TimetableItemActivity.intentForAdd(context, "2026-2027", "1")
                .putExtra("date", "2026-10-03").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<TimetableItemActivity>(intent).use { scenario ->
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.agenda_editor).visibility)
                    assertTrue(activity.findViewById<TextView>(R.id.agenda_date).text.contains("星期六"))
                    activity.findViewById<View>(R.id.type_course).performClick()
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.layout_course_edit).visibility)
                    activity.findViewById<View>(R.id.type_agenda).performClick()
                    activity.findViewById<TextView>(R.id.agenda_title).text = "读书会测试"
                    activity.findViewById<TextView>(R.id.agenda_location).text = "图书馆"
                    activity.findViewById<TextView>(R.id.agenda_notes).text = "日程备注"
                }
                instrumentation.waitForIdleSync()
                Thread.sleep(250) // Let the native floating hint animation finish before capturing.
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    val bitmap = android.graphics.Bitmap.createBitmap(root.width,root.height,android.graphics.Bitmap.Config.ARGB_8888)
                    root.draw(android.graphics.Canvas(bitmap))
                    File(context.getExternalFilesDir(null), "agenda-editor.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                    activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.tool_bar).menu.performIdentifierAction(R.id.timetable_menu_save,0)
                }
                val until = android.os.SystemClock.elapsedRealtime() + 5000
                while (db.agendaDao.all(account).isEmpty() && android.os.SystemClock.elapsedRealtime() < until) Thread.sleep(100)
                val saved = db.agendaDao.all(account).single()
                assertEquals("2026-10-03",saved.date); assertEquals("图书馆",saved.location)
                assertEquals("日程备注",saved.notes); assertEquals("读书会测试",saved.title)
            }
        } finally { db.userDao.deleteAllData(account); prefs.put("account", previous,true); db.close() }
    }
}
