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
        val options = timetableRepository.getTermOptions()
            ?: return CourseSnapshot(emptyList(), null)
        val openingDay = timetableRepository.getOpeningDay(
            options.selected.year,
            options.selected.term,
        )
        val week = openingDay.getCurrentWeek()
        val setting = timetableRepository.getTimetableSetting()
        val courses = timetableRepository.getCourses(
            options.selected.year,
            options.selected.term,
            GetCourseStrategy.NETWORK_IF_LOCAL_EMPTY,
        )
        val calendar = Calendar.getInstance()
        val weekday = calendar.get(Calendar.DAY_OF_WEEK)
        val nowInMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val today = if (week < 1) emptyList() else courses
            .asSequence()
            .filter { it.weekday == weekday && it.weeks.contains(week) }
            .filter { courseEndInMinutes(it, setting) >= nowInMinutes }
            .sortedBy { it.beginNode }
            .take(2)
            .map { it.toUi(setting, nowInMinutes) }
            .toList()
        val next = CourseSchedule.nextCourse(courses, openingDay.getOpeningDay(), setting)
        return CourseSnapshot(
            today = today,
            nextCourseSeed = next?.let {
                CourseColorPalette.forCourses(Utils.getApp(), courses.map { course -> course.name })
                    .seedFor(it.name)
            },
        )
    }

    private fun NewCourse.toUi(setting: SyllabusSetting, nowInMinutes: Int): HomeCourseUi {
        val start = nodeStartInMinutes(beginNode, setting)
        val end = courseEndInMinutes(this, setting)
        val location = classroom.ifBlank { "地点待定" }
        val detail = if (teacher.isBlank()) location else "$location · $teacher"
        val countdown = if (nowInMinutes >= start) {
            remainingText(end - nowInMinutes, "下课")
        } else {
            remainingText(start - nowInMinutes, "上课")
        }
        return HomeCourseUi(
            name = name,
            time = "${formatMinutes(start)}–${formatMinutes(end)} · 第${beginNode}–${endNode}节",
            place = detail,
            countdown = countdown,
            urgent = start - nowInMinutes in 0 until 15,
        )
    }

    private fun remainingText(minutes: Int, action: String): String {
        if (minutes <= 0) return "即将$action"
        val value = buildString {
            if (minutes >= 60) append("${minutes / 60}小时")
            if (minutes % 60 != 0) append("${minutes % 60}分钟")
        }
        return "${value}后$action"
    }

    private fun nodeStartInMinutes(node: Int, setting: SyllabusSetting): Int {
        val value = setting.beginTime.getOrNull(node) ?: return 0
        return value / 100 * 60 + value % 100
    }

    private fun courseEndInMinutes(course: NewCourse, setting: SyllabusSetting): Int {
        return nodeStartInMinutes(course.endNode, setting) + setting.nodeLength
    }

    private fun formatMinutes(value: Int): String {
        if (value <= 0) return "待定"
        return "%02d:%02d".format(value / 60, value % 60)
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
            startTime <= now && (endTime <= 0L || endTime >= now) -> "正在进行"
            startTime > now -> "还有 ${DateUtils.calcIntervalTime(now, startTime)}"
            else -> "即将更新"
        }
        val location = address.ifBlank { "考场待定" }
        val placeText = if (seatNumber.isBlank()) location else "$location · 座位 $seatNumber"
        return HomeExamUi(name, countdownText, timeText, placeText)
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
