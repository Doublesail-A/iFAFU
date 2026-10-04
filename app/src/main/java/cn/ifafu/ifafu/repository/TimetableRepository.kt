package cn.ifafu.ifafu.repository

import cn.ifafu.ifafu.annotation.GetCourseStrategy
import cn.ifafu.ifafu.bean.dto.*
import cn.ifafu.ifafu.bean.vo.OpeningDayVO
import cn.ifafu.ifafu.db.JiaowuDatabase
import cn.ifafu.ifafu.db.dao.CourseDao
import cn.ifafu.ifafu.db.dao.OpeningDayDao
import cn.ifafu.ifafu.di.Ifafu
import cn.ifafu.ifafu.entity.Holiday
import cn.ifafu.ifafu.entity.NewCourse
import cn.ifafu.ifafu.entity.SyllabusSetting
import cn.ifafu.ifafu.exception.Failure
import cn.ifafu.ifafu.service.IFAFUService
import cn.ifafu.ifafu.service.TimetableService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import androidx.room.withTransaction
import cn.ifafu.ifafu.calendar.*
import cn.ifafu.ifafu.entity.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimetableRepository @Inject constructor(
    private val database: JiaowuDatabase,
    private val service: TimetableService,
    @Ifafu private val retrofit: Retrofit,
    private val calendars: SchoolCalendarStore,
) : AbstractJwRepository(database.userDao) {

    private val ifafuService: IFAFUService = retrofit.create(IFAFUService::class.java)

    private val openingDayDao: OpeningDayDao = database.openingDayDao
    private val courseDao: CourseDao = database.newCourseDao
    private val syllabusSettingDao = database.syllabusSettingDao
    private val holidayDao = database.holidayDao

    val calendarChanges get() = calendars.changes

    private val metadataMutex = Mutex()
    private var metadataAttempt = 0L

    private suspend fun originalMetadata(force: Boolean = false) = metadataMutex.withLock {
        val now = System.currentTimeMillis()
        if (!force && now - metadataAttempt in 0 until 86_400_000L) return@withLock
        metadataAttempt = now
        try {
            openingDayDao.save(ifafuService.firstWeeks())
            val holidays = ifafuService.holiday()
            if (holidays.isNotEmpty()) holidayDao.save(holidays)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Timber.w(e, "课表日期数据暂不可用，继续使用本地数据") }
    }

    fun getTermOptionsResource(): Flow<TermOptions> = flow {
        val local = courseDao.getOptions()
        if (local != null) {
            emit(local)
        }
        try {
            val termOptions = getTermOptionsFromNetworkInner()
            courseDao.saveDefaultOptions(termOptions.selected)
            emit(termOptions)
        } catch (e: Exception) {
            Timber.e("获取网络课表出错, err: ${e.message}")
        }
    }

    suspend fun getTermOptions(): TermOptions? {
        return withContext(Dispatchers.IO) {
            val options = courseDao.getOptions()
            if (options != null) {
                return@withContext options
            }

            val user = getUsingUser()
                ?: return@withContext null
            val resp = service.getTermOptions(user)
            val data = resp.data
            if (resp.code == IFResponse.SUCCESS && data != null) {
                courseDao.saveOptions(data.options)
            }
            return@withContext data
        }
    }

    private suspend fun getCoursesNetwork(user: User, year: String, term: String): List<NewCourse> {
        val resp = service.getTimetable(user, year, term)
        if (resp.code != IFResponse.SUCCESS) throw Failure(resp.message)
        val remote = resp.data ?: throw Failure("教务系统没有返回有效课表")
        if (getUsingUser()?.account != user.account) throw CancellationException("Account changed")
        val manual = courseDao.getAllCourses(user.account, year, term).filter { it.local }
        database.withTransaction {
            courseDao.delete(user.account, year, term)
            courseDao.saveCourses(*(remote + manual).toTypedArray())
        }
        return remote + manual
    }

    fun getCoursesFlow(
        year: String,
        term: String,
        @GetCourseStrategy strategy: Int = GetCourseStrategy.NETWORK_IF_LOCAL_EMPTY,
    ): Flow<List<NewCourse>> = flow {
        if (year.isBlank() || term.isBlank()) { emit(emptyList()); return@flow }
        val user = getUsingUser() ?: run { emit(emptyList()); return@flow }
        val force = strategy == GetCourseStrategy.NETWORK
        var calendar = calendars.resolve(user.school, year, term, force)
        if (calendar == null) {
            originalMetadata(force)
            calendar = AcademicCalendar(user.school, year, term, openingDayDao.find(year, term), holidayDao.findAll())
        }
        fun adjusted(courses: List<NewCourse>) = calendar?.let { CalendarCourses.apply(courses, it) }
            ?: courses.map { it.copy(weeks = TreeSet(it.weeks)) }
        val local = courseDao.getAllCourses(user.account, year, term)
        if (strategy != GetCourseStrategy.NETWORK) {
            if (getUsingUser()?.account != user.account) { emit(emptyList()); return@flow }
            emit(adjusted(local))
        }
        if (strategy == GetCourseStrategy.NETWORK || strategy == GetCourseStrategy.LOCAL_AND_NETWORK ||
            (strategy == GetCourseStrategy.NETWORK_IF_LOCAL_EMPTY && local.isEmpty())) {
            val remote = getCoursesNetwork(user, year, term)
            if (getUsingUser()?.account == user.account) emit(adjusted(remote))
            else emit(emptyList())
        }
    }

    suspend fun getCourses(
        year: String,
        term: String,
        @GetCourseStrategy strategy: Int = GetCourseStrategy.NETWORK_IF_LOCAL_EMPTY,
    ): List<NewCourse> = withContext(Dispatchers.IO) {
        getCoursesFlow(year, term, strategy).last()
    }

    private suspend fun getTermOptionsFromNetworkInner(): TermOptions {
        val user = getUsingUser() ?: throw IllegalArgumentException("获取学期信息失败")
        val resp = service.getTermOptions(user)
        if (resp.code == IFResponse.SUCCESS && resp.data != null) {
            courseDao.saveOptions(resp.data.options)
            courseDao.saveDefaultOptions(resp.data.selected)
        }
        return resp.data ?: throw IllegalArgumentException("获取学期信息失败")
    }

    suspend fun getTimetableSetting(): SyllabusSetting {
        return withContext(Dispatchers.IO) {
            val account = getUsingAccount() ?: return@withContext SyllabusSetting()
            syllabusSettingDao.getTimetableSetting(account)
        }
    }

    suspend fun getCourseById(id: Int): NewCourse? {
        return withContext(Dispatchers.IO) {
            courseDao.getCourseById(id)
        }
    }

    suspend fun deleteCourse(course: NewCourse) {
        withContext(Dispatchers.IO) {
            courseDao.deleteCourseById(course.id)
        }
    }

    suspend fun saveCourse(course: NewCourse) {
        withContext(Dispatchers.IO) {
            courseDao.saveCourse(course)
        }
    }

    suspend fun getOpeningDay(year: String, term: String): OpeningDayVO =
        withContext(Dispatchers.IO) {
            val user = getUsingUser()
            val official = if (user != null && year.isNotBlank() && term.isNotBlank())
                calendars.resolve(user.school, year, term) else null
            if (official == null) originalMetadata()
            val openingDay = official?.firstWeek ?: openingDayDao.find(year, term)
            val currentTerm = courseDao.getOptions()
            val isC = currentTerm == null ||
                    currentTerm.selected.year == year &&
                    currentTerm.selected.term == term
            OpeningDayVO(
                year = year, term = term,
                openingDay = openingDay,
                isCurrentTerm = isC
            )
        }

    suspend fun getHolidays(): List<Holiday> {
        return withContext(Dispatchers.IO) {
            holidayDao.findAll()
        }
    }

    suspend fun saveTimetableSetting(setting: SyllabusSetting) {
        withContext(Dispatchers.IO) {
            syllabusSettingDao.saveSetting(setting)
        }
    }


}
