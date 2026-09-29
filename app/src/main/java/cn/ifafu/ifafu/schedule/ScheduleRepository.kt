package cn.ifafu.ifafu.schedule

import android.content.Context
import androidx.room.InvalidationTracker
import cn.ifafu.ifafu.annotation.GetCourseStrategy
import cn.ifafu.ifafu.db.JiaowuDatabase
import cn.ifafu.ifafu.repository.ExamRepository
import cn.ifafu.ifafu.repository.TimetableRepository
import cn.ifafu.ifafu.ui.timetable.CourseColorPalette
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

data class ScheduleSnapshot(val account: String, val label: String, val courses: List<ScheduleEvent>)

@Singleton
class ScheduleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: JiaowuDatabase,
    private val timetable: TimetableRepository,
    private val exams: ExamRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null
    private val mutex = Mutex()
    private var observing = false

    fun start() {
        if (observing) return
        observing = true
        db.invalidationTracker.addObserver(object : InvalidationTracker.Observer("new_course", "Exam", "User", "SyllabusSetting", "to_week", "holiday") {
            override fun onInvalidated(tables: Set<String>) = refresh()
        })
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(300)
            try { refreshReminders() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Timber.e(e, "更新本地提醒失败") }
        }
    }

    private suspend fun termEvents(year: String, term: String): List<ScheduleEvent> {
        val courses = timetable.getCourses(year, term, GetCourseStrategy.LOCAL)
        val setting = timetable.getTimetableSetting()
        val opening = timetable.getOpeningDay(year, term).getOpeningDay()
        val colors = CourseColorPalette.forCourses(context, db.newCourseDao.getAllCourses(setting.account).map { it.name })
        return ScheduleEvents.courses(courses, opening, setting) { colors.seedFor(it) }
    }

    suspend fun snapshot(year: String? = null, term: String? = null): ScheduleSnapshot = withContext(Dispatchers.IO) {
        val account = db.userDao.getUsingAccount().orEmpty()
        val selected = db.newCourseDao.getOptions()?.selected
        val y = year?.takeIf { it.isNotBlank() } ?: selected?.year.orEmpty()
        val t = term?.takeIf { it.isNotBlank() } ?: selected?.term.orEmpty()
        ScheduleSnapshot(account, y + " 学年 · 第 " + t + " 学期", termEvents(y, t))
    }

    suspend fun refreshReminders() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val account = db.userDao.getUsingAccount().orEmpty()
            val terms = db.newCourseDao.getAllCourses(account).map { it.year to it.term }.distinct()
            val events = terms.flatMap { termEvents(it.first, it.second) } +
                ScheduleEvents.exams(exams.getAllExamsFromLocal("全部", "全部"))
            ReminderScheduler.update(context, events, account)
        }
    }
}
