package cn.ifafu.ifafu.ui.main.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.ifafu.ifafu.annotation.GetCourseStrategy
import cn.ifafu.ifafu.bean.vo.Resource
import cn.ifafu.ifafu.entity.Exam
import cn.ifafu.ifafu.entity.NewCourse
import cn.ifafu.ifafu.entity.SyllabusSetting
import cn.ifafu.ifafu.repository.ExamRepository
import cn.ifafu.ifafu.repository.TimetableRepository
import cn.ifafu.ifafu.ui.timetable.CourseColorPalette
import cn.ifafu.ifafu.util.DateUtils
import cn.ifafu.ifafu.util.CourseSchedule
import com.blankj.utilcode.util.Utils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HomeCourseUi(
    val name: String,
    val time: String,
    val place: String,
    val countdown: String,
    val urgent: Boolean,
)

data class HomeExamUi(
    val name: String,
    val countdown: String,
    val time: String,
    val place: String,
    val urgent: Boolean = false,
)

data class HomeUiState(
    val date: String = "",
    val greeting: String = "",
    val courses: List<HomeCourseUi> = emptyList(),
    val exam: HomeExamUi? = null,
    val themeSeed: Int? = null,
    val isLoading: Boolean = true,
)

private data class CourseSnapshot(
    val today: List<HomeCourseUi>,
    val nextCourseSeed: Int?,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val timetableRepository: TimetableRepository,
    private val examRepository: ExamRepository,
    private val agendaRepository: cn.ifafu.ifafu.schedule.AgendaRepository,
) : ViewModel() {

    private val _state = MediatorLiveData<HomeUiState>().apply {
        value = createBaseState()
    }
    val state: LiveData<HomeUiState> = _state
    private var refreshJob: Job? = null

    init {
        refresh()
        runCatching { examRepository.getNowExamsLiveDataFromNet() }
            .getOrNull()
            ?.let { source ->
                _state.addSource(source) { resource ->
                    if (resource is Resource.Success) {
                        updateExam(resource.data)
                    }
                }
            }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val base = createBaseState()
            _state.value = (_state.value ?: base).copy(
                date = base.date,
                greeting = base.greeting,
                isLoading = true,
            )
            val localExam = runCatching { examRepository.getNowExamsFromLocal() }
                .getOrDefault(emptyList())
            val courseSnapshot = try { loadCourses() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { CourseSnapshot(emptyList(), null) }
            _state.value = base.copy(
                courses = courseSnapshot.today,
                exam = selectNextExam(localExam)?.toUi(),
                themeSeed = courseSnapshot.nextCourseSeed,
                isLoading = false,
            )
        }
    }

    private suspend fun loadCourses(): CourseSnapshot {
        val agenda = agendaRepository.all().map { cn.ifafu.ifafu.schedule.AgendaEvents.event(it) }
        var seed: Int? = null
        val lessons = try {
            val options = timetableRepository.getTermOptions()
            if (options == null) emptyList() else {
                val opening = timetableRepository.getOpeningDay(options.selected.year, options.selected.term)
                val setting = timetableRepository.getTimetableSetting()
                val courses = timetableRepository.getCourses(options.selected.year, options.selected.term, GetCourseStrategy.NETWORK_IF_LOCAL_EMPTY)
                val colors = CourseColorPalette.forCourses(Utils.getApp(), courses.map { it.name })
                seed = CourseSchedule.nextCourse(courses, opening.getOpeningDay(), setting)?.let { colors.seedFor(it.name) }
                cn.ifafu.ifafu.schedule.ScheduleEvents.courses(courses, opening.getOpeningDay(), setting) { colors.seedFor(it) }
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        val now = System.currentTimeMillis()
        val today = cn.ifafu.ifafu.schedule.TodaySchedule.today(lessons + agenda, now).map { event ->
            val format = SimpleDateFormat("HH:mm", Locale.CHINA)
            HomeCourseUi(event.title, format.format(Date(event.start)) + "–" + format.format(Date(event.end)),
                listOf(event.location.ifBlank { "地点待定" }, event.teacher).filter { it.isNotBlank() }.joinToString(" · "),
                cn.ifafu.ifafu.schedule.TodaySchedule.label(event, now), cn.ifafu.ifafu.schedule.TodaySchedule.urgent(event, now))
        }
        return CourseSnapshot(today, seed)
    }

    private fun updateExam(exams: List<Exam>) {
        val current = _state.value ?: createBaseState()
        _state.value = current.copy(exam = selectNextExam(exams)?.toUi())
    }

    private fun selectNextExam(exams: List<Exam>): Exam? {
        val now = System.currentTimeMillis()
        return exams
            .filter { it.endTime <= 0L || it.endTime >= now }
            .minByOrNull { if (it.startTime <= 0L) Long.MAX_VALUE else it.startTime }
    }

    private fun Exam.toUi(): HomeExamUi {
        val now = System.currentTimeMillis()
        val timeText = if (startTime <= 0L) {
            "考试时间待定"
        } else {
            val day = SimpleDateFormat("M月d日 E", Locale.CHINA).format(Date(startTime))
            val start = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(startTime))
            val end = if (endTime > startTime) {
                SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(endTime))
            } else {
                "待定"
            }
            "$day · $start–$end"
        }
        val countdownText = when {
            startTime <= 0L -> "时间待定"
            startTime <= now && endTime > now -> cn.ifafu.ifafu.schedule.TodaySchedule.label(cn.ifafu.ifafu.schedule.ScheduleEvents.exams(listOf(this)).first(), now)
            startTime <= now && endTime <= 0L -> "正在进行"
            startTime > now -> cn.ifafu.ifafu.schedule.TodaySchedule.label(cn.ifafu.ifafu.schedule.ScheduleEvents.exams(listOf(this)).first(), now)
            else -> "即将更新"
        }
        val location = address.ifBlank { "考场待定" }
        val placeText = if (seatNumber.isBlank()) location else "$location · 座位 $seatNumber"
        return HomeExamUi(name, countdownText, timeText, placeText, startTime > now && startTime - now <= 30 * 60000L)
    }

    private fun createBaseState(): HomeUiState {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..10 -> "早上好，今天安排如下"
            in 11..13 -> "中午好，今天安排如下"
            in 14..17 -> "下午好，今天安排如下"
            else -> "晚上好，今天安排如下"
        }
        val date = SimpleDateFormat("M月d日 · EEEE", Locale.CHINA).format(now.time)
        return HomeUiState(date = date, greeting = greeting)
    }
}
