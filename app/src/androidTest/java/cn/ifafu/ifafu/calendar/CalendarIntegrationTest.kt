package cn.ifafu.ifafu.calendar

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.ifafu.ifafu.entity.NewCourse
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CalendarIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    @Test fun bundledAndDiskDateDataWorkWithoutDocumentReaders() = runBlocking {
        // Keep the shared desugared TimeZone bridge in the separate test APK.
        assertEquals("UTC", java.util.TimeZone.getTimeZone("UTC").id)
        val synchronized = java.util.Collections.synchronizedMap(mutableMapOf<String, Int>())
        synchronized["bridge"] = 1
        assertEquals(1, synchronized["bridge"])
        val date = LocalDate.parse("2026-08-30")
        assertEquals("2026-08-29", date.minusDays(1).toString())
        assertEquals("2026-09-06", date.plusWeeks(1).toString())
        assertEquals("2027-08-30", date.plusYears(1).toString())
        assertEquals(8, date.monthValue)
        assertEquals(7, date.dayOfWeek.value)
        assertTrue(date.isBefore(date.plusDays(1)))
        assertTrue(date.isAfter(date.minusDays(1)))
        assertEquals(1L, java.time.temporal.ChronoUnit.DAYS.between(date, date.plusDays(1)))
        assertEquals(date, LocalDate.of(2026, 8, 30))

        val dir = File(instrumentation.targetContext.cacheDir, "calendar-data-integration").apply { mkdirs() }
        val context = object : ContextWrapper(instrumentation.targetContext) {
            override fun getFilesDir(): File = dir
        }
        val file = File(dir, "school-calendar-data.json")
        val bundled = context.assets.open("school-calendars.json").bufferedReader().use { it.readText() }
        file.writeText(bundled)
        val calendar = SchoolCalendarStore(context).resolve("FAFU", "2026-2027", "1")!!
        assertEquals("2026-08-30", calendar.firstWeek)
        assertTrue(calendar.source.startsWith("https://jwc.fafu.edu.cn/"))
        assertEquals(calendar, SchoolCalendarStore(context).resolve("FAFU", "2026-2027", "1"))
        val courses = (2..6).map { day -> NewCourse(name = "验证课程", weeks = (1..20).toSortedSet(), weekday = day) }
        val dates = CalendarCourses.apply(courses, calendar).flatMap { course ->
            course.weeks.map { LocalDate.parse(calendar.firstWeek).plusDays((it - 1) * 7L + course.weekday - 1).toString() }
        }
        for (day in 1..7) assertFalse(dates.contains("2026-10-0" + day))
        assertTrue(dates.contains("2026-10-10"))
        assertEquals("2027-02-21", SchoolCalendarStore(context).resolve("FAFU", "2026-2027", "2")!!.firstWeek)
    }

    @Test fun repositoryAndCalendarExportUseIdenticalAdjustedOccurrences() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val context = instrumentation.targetContext
        val preferences = com.blankj.utilcode.util.SPUtils.getInstance(cn.ifafu.ifafu.constant.Constants.SP_USER_INFO)
        org.junit.Assume.assumeTrue("Requires an explicitly isolated test device", InstrumentationRegistry.getArguments().getString("isolatedCalendarTest") == "true")
        val previousAccount = preferences.getString("account", "")
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, cn.ifafu.ifafu.db.JiaowuDatabase::class.java).build()
        val network = cn.ifafu.ifafu.di.NetworkModule
        val client = network.provideJwHttpClient(network.provideJwOkHttpClient(context), db, context)
        val retrofit = network.provideIFAFUApiRetrofit(network.provideIFAFUOkHttpClient())
        val repository = cn.ifafu.ifafu.repository.TimetableRepository(db,
            cn.ifafu.ifafu.service.TimetableService(client, retrofit), retrofit, SchoolCalendarStore(context))
        val schedule = cn.ifafu.ifafu.schedule.ScheduleRepository(context, db, repository,
            cn.ifafu.ifafu.repository.ExamRepository(db, cn.ifafu.ifafu.service.ExamService(client)))
        val account = "2699999999"
        try {
            db.userDao.saveUsing(cn.ifafu.ifafu.entity.User(account = account))
            val raw = (1..7).map { day -> NewCourse(id = 990000 + day, name = "管线验证$day",
                classroom = "验证教室", teacher = "验证教师", weeks = (1..20).toSortedSet(),
                weekday = day, beginNode = 1, nodeLength = 2, year = "2026-2027", term = "1", account = account) }
            db.newCourseDao.saveCourses(*raw.toTypedArray())
            val adjusted = repository.getCourses("2026-2027", "1", cn.ifafu.ifafu.annotation.GetCourseStrategy.LOCAL)
            assertEquals("2026-08-30", repository.getOpeningDay("2026-2027", "1").openingDay)
            assertEquals(2, adjusted.count { it.name.startsWith("[调课]") })
            adjusted.forEach { assertEquals(account, it.account); assertEquals("验证教室", it.classroom) }
            assertEquals(adjusted, repository.getCourses("2026-2027", "1", cn.ifafu.ifafu.annotation.GetCourseStrategy.LOCAL))
            val snapshot = schedule.snapshot("2026-2027", "1")
            val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
            val dates = snapshot.courses.map { format.format(java.util.Date(it.start)) }
            for (day in 1..7) assertFalse(dates.contains("2026-10-0$day"))
            assertTrue(dates.contains("2026-10-10"))
            assertEquals(7, db.newCourseDao.getAllCourses(account, "2026-2027", "1").size)
            db.newCourseDao.getAllCourses(account, "2026-2027", "1").forEach { assertEquals(20, it.weeks.size) }
        } finally { db.userDao.deleteAllData(account); db.close(); preferences.put("account", previousAccount) }
    }
}
