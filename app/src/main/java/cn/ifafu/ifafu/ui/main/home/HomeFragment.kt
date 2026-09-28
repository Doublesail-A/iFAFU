package cn.ifafu.ifafu.ui.main.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.databinding.HomeFragmentBinding
import cn.ifafu.ifafu.ui.examlist.ExamListActivity
import cn.ifafu.ifafu.ui.main.MainActivity
import cn.ifafu.ifafu.ui.timetable.TimetableActivity
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.material.color.MaterialColors

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.home_fragment) {

    private val viewModel: HomeViewModel by viewModels()
    private var binding: HomeFragmentBinding? = null
    private val minuteRefresh = object : Runnable {
        override fun run() {
            viewModel.refresh()
            binding?.root?.postDelayed(this, 60_000L)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentBinding = HomeFragmentBinding.bind(view)
        binding = currentBinding

        currentBinding.cardTodayCourses.setOnClickListener {
            startActivity(Intent(requireContext(), TimetableActivity::class.java))
        }
        currentBinding.cardExam.setOnClickListener {
            startActivity(Intent(requireContext(), ExamListActivity::class.java))
        }
        viewModel.state.observe(viewLifecycleOwner) { render(it) }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
        binding?.root?.removeCallbacks(minuteRefresh)
        binding?.root?.postDelayed(minuteRefresh, 60_000L)
    }

    override fun onStop() {
        binding?.root?.removeCallbacks(minuteRefresh)
        super.onStop()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun render(state: HomeUiState) {
        val currentBinding = binding ?: return
        currentBinding.progressHome.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        currentBinding.tvHomeDate.text = state.date
        currentBinding.tvHomeGreeting.text = state.greeting
        state.themeSeed?.let { (activity as? MainActivity)?.updateCourseThemeSeed(it) }

        currentBinding.tvCourseSummary.text = when (state.courses.size) {
            0 -> "查看完整课程表"
            1 -> "接下来 1 门"
            else -> "接下来 2 门"
        }
        bindCourse(
            state.courses.getOrNull(0),
            currentBinding.rowCourse1,
            currentBinding.tvCourseName1,
            currentBinding.tvCourseCountdown1,
            currentBinding.tvCourseMeta1,
            currentBinding.tvCoursePlace1,
        )
        bindCourse(
            state.courses.getOrNull(1),
            currentBinding.rowCourse2,
            currentBinding.tvCourseName2,
            currentBinding.tvCourseCountdown2,
            currentBinding.tvCourseMeta2,
            currentBinding.tvCoursePlace2,
        )
        currentBinding.tvCoursesEmpty.visibility =
            if (state.courses.isEmpty() && !state.isLoading) View.VISIBLE else View.GONE

        val exam = state.exam
        val hasExam = exam != null
        currentBinding.tvExamEmpty.visibility = if (hasExam) View.GONE else View.VISIBLE
        currentBinding.tvExamCountdown.visibility = if (hasExam) View.VISIBLE else View.GONE
        currentBinding.tvExamName.visibility = if (hasExam) View.VISIBLE else View.GONE
        currentBinding.tvExamTime.visibility = if (hasExam) View.VISIBLE else View.GONE
        currentBinding.tvExamPlace.visibility = if (hasExam) View.VISIBLE else View.GONE
        if (exam != null) {
            currentBinding.tvExamCountdown.text = exam.countdown
            currentBinding.tvExamName.text = exam.name
            currentBinding.tvExamTime.text = exam.time
            currentBinding.tvExamPlace.text = exam.place
        }
    }

    private fun bindCourse(
        course: HomeCourseUi?,
        row: View,
        name: android.widget.TextView,
        countdown: android.widget.TextView,
        meta: android.widget.TextView,
        place: android.widget.TextView,
    ) {
        row.visibility = if (course == null) View.GONE else View.VISIBLE
        if (course != null) {
            name.text = course.name
            countdown.text = course.countdown
            countdown.setBackgroundResource(
                if (course.urgent) R.drawable.shape_home_countdown_urgent
                else R.drawable.shape_home_countdown,
            )
            countdown.setTextColor(
                MaterialColors.getColor(
                    countdown,
                    if (course.urgent) com.google.android.material.R.attr.colorOnErrorContainer
                    else com.google.android.material.R.attr.colorOnSecondaryContainer,
                ),
            )
            meta.text = course.time
            place.text = course.place
        }
    }
}
