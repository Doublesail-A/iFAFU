package cn.ifafu.ifafu.ui.timetable_item

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.constant.WHAT_WRONG_WITH_IFAFU
import cn.ifafu.ifafu.databinding.TimetableActivityDetailBinding
import cn.ifafu.ifafu.entity.NewCourse
import cn.ifafu.ifafu.repository.TimetableRepository
import cn.ifafu.ifafu.ui.common.BaseActivity
import cn.ifafu.ifafu.ui.view.adapter.WeekItemAdapter
import cn.ifafu.ifafu.util.DataUtils
import com.bigkoo.pickerview.builder.OptionsPickerBuilder
import com.bigkoo.pickerview.view.OptionsPickerView
import com.blankj.utilcode.util.KeyboardUtils
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TimetableItemActivity : BaseActivity(), View.OnClickListener {

    private lateinit var binding: TimetableActivityDetailBinding

    private val timeOPV: OptionsPickerView<String> by lazy {
        OptionsPickerBuilder(this) { options1, options2, options3, _ ->
            course.weekday = options1 + 1
            course.beginNode = options2 + 1
            course.nodeLength = options3 + 1
            val timeText = "${weeks[options1]} · 第${options2 + 1}–${options2 + options3 + 1}节"
            binding.etCourseTime.text = timeText
        }
            .setOutSideCancelable(false)
            .setCancelText("取消")
            .setSubmitText("确定")
            .setTitleText("请选择时间")
            .setTitleColor(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorPrimary,
                    0xFF6750A4.toInt(),
                ),
            )
            .setTitleSize(13)
            .build()
    }

    private val weeks = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

    private val mWeekAdapter: WeekItemAdapter = WeekItemAdapter(this)

    @Inject lateinit var agendas: cn.ifafu.ifafu.schedule.AgendaRepository
    private var agendaMode = true
    private var agendaId: String? = null
    private var agendaDate = java.time.LocalDate.now()
    private var agendaStart = (java.time.LocalTime.now().hour * 60 + 30).coerceAtMost(1380)
    private var agendaEnd = (agendaStart + 60).coerceAtMost(1439)
    private var agendaColor = cn.ifafu.ifafu.schedule.AgendaEvents.swatches[0]
    private var enterType = ADD

    @Inject
    lateinit var repository: TimetableRepository

    private var course: NewCourse = NewCourse()

    companion object {

        private const val CHECK = 1
        private const val ADD = 2

        fun intentForCheck(context: Context, course: NewCourse): Intent {
            return Intent(context, TimetableItemActivity::class.java).apply {
                putExtra("course", course)
                putExtra("type", CHECK)
            }
        }

        fun intentForAgenda(context: Context, id: String) = Intent(context, TimetableItemActivity::class.java).putExtra("agenda_id", id).putExtra("type", CHECK)

        fun intentForAdd(context: Context, year: String, term: String): Intent {
            return Intent(context, TimetableItemActivity::class.java).apply {
                putExtra("year", year)
                putExtra("term", term)
                putExtra("type", ADD)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setLightUiBar()
        binding = TimetableActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enterType = intent.getIntExtra("type", -1)
        agendaId = intent.getStringExtra("agenda_id")
        agendaMode = savedInstanceState?.getBoolean("agenda_mode") ?: (enterType == ADD || agendaId != null)
        agendaDate = java.time.LocalDate.parse(savedInstanceState?.getString("agenda_date") ?: intent.getStringExtra("date") ?: agendaDate.toString())
        agendaStart = savedInstanceState?.getInt("agenda_start") ?: agendaStart
        agendaEnd = savedInstanceState?.getInt("agenda_end") ?: agendaEnd
        agendaColor = savedInstanceState?.getInt("agenda_color") ?: agendaColor
        binding.typeAgenda.setOnClickListener { agendaMode = true; editMode(true) }
        binding.typeCourse.setOnClickListener { agendaMode = false; editMode(true) }
        initAgendaEditor()

        lifecycleScope.launchWhenCreated {
            if (agendaId != null) {
                val item = agendas.find(agendaId!!) ?: run { showToast("日程不存在或已删除"); finish(); return@launchWhenCreated }
                if (savedInstanceState == null) {
                    agendaDate = java.time.LocalDate.parse(item.date); agendaStart = item.startMinute
                    agendaEnd = item.endMinute; agendaColor = item.color
                }
                binding.agendaEditor.agendaTitle.setText(item.title)
                binding.agendaEditor.agendaLocation.setText(item.location)
                binding.agendaEditor.agendaNotes.setText(item.notes)
                updateAgendaButtons(); editMode(true)
                return@launchWhenCreated
            }
            when (enterType) {
                ADD -> {
                    val year = intent.getStringExtra("year")
                    val term = intent.getStringExtra("term")
                    if (year == null || term == null) {
                        snackbar(WHAT_WRONG_WITH_IFAFU)
                        return@launchWhenCreated
                    }
                    course = NewCourse(
                        weekday = 1,
                        beginNode = 1,
                        nodeLength = 1,
                        year = year,
                        term = term,
                        local = true
                    )
                    editMode(true)
                    initCourseView(course)
                }
                CHECK -> {
                    val courseFromIntent = intent.getParcelableExtra<NewCourse>("course")
                        ?: return@launchWhenCreated
                    val id = courseFromIntent.id
                    if (id == -1) {
                        snackbar(WHAT_WRONG_WITH_IFAFU)
                        return@launchWhenCreated
                    }
                    val byId = repository.getCourseById(id)
                    if (byId == null) {
                        snackbar(WHAT_WRONG_WITH_IFAFU)
                        return@launchWhenCreated
                    }
                    course = byId
                    editMode(false)
                    initCourseView(course)
                }
                else -> {
                    snackbar(WHAT_WRONG_WITH_IFAFU)
                    return@launchWhenCreated
                }
            }
        }


        binding.weeksRv.adapter = mWeekAdapter
        binding.etCourseTime.setOnClickListener(this)

        initTimePicker()
        initToolbar()
    }

    private fun initToolbar() {
        binding.toolBar.setNavigationOnClickListener {
            finish()
        }

        binding.toolBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.timetable_menu_edit -> editMode(true)
                R.id.timetable_menu_delete -> confirmDelete()
                R.id.timetable_menu_save -> save()
            }
            true
        }
    }

    private fun initCourseView(it: NewCourse) {
        val weekday = it.weekday.coerceIn(1, 7)
        val timeText = "${weeks[weekday - 1]} · 第${it.beginNode}–${it.endNode}节"
        binding.tvCourseTime.text = timeText
        binding.tvCourseName.text = it.name
        binding.tvCourseTeacher.text = it.teacher
        binding.tvCourseAddress.text = it.classroom

        binding.etCourseTime.text = timeText
        binding.etCourseName.setText(it.name)
        binding.etCourseTeacher.setText(it.teacher)
        binding.etCourseAddress.setText(it.classroom)

        mWeekAdapter.weekList = it.weeks
        mWeekAdapter.notifyDataSetChanged()

        timeOPV.setSelectOptions(it.weekday - 1, it.beginNode - 1, it.nodeLength - 1)
    }

    private fun initTimePicker() {
        val node1t: MutableList<String> = ArrayList()
        val node2t: MutableList<MutableList<String>> = ArrayList()
        for (i in 1..12) {
            node1t.add("第" + i + "节")
            val node2tt = ArrayList<String>()
            for (j in i..12) {
                node2tt.add("第" + j + "节")
            }
            node2t.add(node2tt)
        }
        val node1: MutableList<MutableList<String>> = ArrayList()
        val node2: MutableList<MutableList<MutableList<String>>> = ArrayList()
        for (i in 1..7) {
            node1.add(node1t)
        }
        for (i in 1..node1.size) {
            node2.add(node2t)
        }
        timeOPV.setPicker(weeks, node1, node2)
    }

    private fun editMode(edit: Boolean) {
        mWeekAdapter.editMode = edit
        binding.layoutCourseRead.isVisible = !edit
        binding.layoutCourseEdit.isVisible = edit && !agendaMode
        binding.layoutCourseRead.isVisible = !edit && !agendaMode
        binding.projectTypes.isVisible = enterType == ADD
        binding.agendaEditor.root.isVisible = agendaMode
        binding.tvWeeksHeading.isVisible = !agendaMode
        binding.tvWeeksSupporting.isVisible = !agendaMode
        binding.weeksRv.isVisible = !agendaMode
        listOf(binding.typeAgenda to agendaMode, binding.typeCourse to !agendaMode).forEach { (button, selected) ->
            button.isSelected = selected
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(MaterialColors.getColor(button,
                if (selected) com.google.android.material.R.attr.colorPrimaryContainer else com.google.android.material.R.attr.colorSurfaceVariant))
            button.setTextColor(MaterialColors.getColor(button, if (selected) com.google.android.material.R.attr.colorOnPrimaryContainer else com.google.android.material.R.attr.colorOnSurfaceVariant))
            button.icon = if (selected) androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_m3_check) else null
        }
        binding.tvWeeksHeading.text = if (edit) "选择上课周次" else "上课周次"
        binding.tvWeeksSupporting.text = if (edit) "点按周次可添加或移除" else "课程出现的教学周"
        binding.toolBar.title = when {
            enterType == ADD -> "添加项目"
            agendaMode -> "编辑日程"
            edit -> "编辑课程"
            else -> "课程详情"
        }

        binding.toolBar.menu.findItem(R.id.timetable_menu_save)?.isVisible = edit
        binding.toolBar.menu.findItem(R.id.timetable_menu_delete)?.isVisible = !edit || agendaId != null
        binding.toolBar.menu.findItem(R.id.timetable_menu_edit)?.isVisible = !edit && !agendaMode
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.et_course_time -> timeOPV.show()
        }
    }

    private fun save() {
        if (agendaMode) { saveAgenda(); return }
        if (binding.etCourseName.text.isNullOrBlank()) {
            snackbar("请填写课程名称")
            return
        }
        if (mWeekAdapter.weekList.isEmpty()) {
            snackbar("请至少选择一个上课周次")
            return
        }
        lifecycleScope.launch {
            course.weeks = mWeekAdapter.weekList
            course.name = binding.etCourseName.text.toString()
            course.classroom = binding.etCourseAddress.text.toString()
            course.teacher = binding.etCourseTeacher.text.toString()
            if (enterType == ADD) {
                course.id = course.hashCode()
                course.account = DataUtils.getSno()
            }
            repository.saveCourse(course)
            snackbar("保存成功")
            setResult(Activity.RESULT_OK)
            editMode(false)
            KeyboardUtils.hideSoftInput(this@TimetableItemActivity)
            initCourseView(course)
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("agenda_mode", agendaMode)
        outState.putString("agenda_date", agendaDate.toString())
        outState.putInt("agenda_start", agendaStart); outState.putInt("agenda_end", agendaEnd)
        outState.putInt("agenda_color", agendaColor)
        super.onSaveInstanceState(outState)
    }

    private fun initAgendaEditor() {
        val editor = binding.agendaEditor
        editor.agendaDate.setOnClickListener {
            val picker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                .setTitleText("选择日程日期")
                .setSelection(agendaDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()).build()
            picker.addOnPositiveButtonClickListener { utc ->
                agendaDate = java.time.Instant.ofEpochMilli(utc).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                updateAgendaButtons()
            }
            picker.show(supportFragmentManager, "agenda_date")
        }
        editor.agendaStart.setOnClickListener { chooseAgendaTime(true) }
        editor.agendaEnd.setOnClickListener { chooseAgendaTime(false) }
        editor.agendaCustomColor.setOnClickListener {
            val field = com.google.android.material.textfield.TextInputLayout(this)
            val input = com.google.android.material.textfield.TextInputEditText(field.context).apply {
                setSingleLine(); setText("#%06X".format(agendaColor and 0xffffff))
            }
            field.hint = "颜色（#RRGGBB）"; field.addView(input)
            val inset = (24 * resources.displayMetrics.density).toInt()
            val container = android.widget.FrameLayout(this).apply { setPadding(inset, 0, inset, 0); addView(field) }
            val dialog = MaterialAlertDialogBuilder(this).setTitle("自定义日程颜色").setView(container)
                .setNegativeButton("取消", null).setPositiveButton("确定", null).create()
            dialog.setOnShowListener {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val value = input.text.toString().trim()
                    if (!value.matches(Regex("#[0-9a-fA-F]{6}"))) field.error = "请输入六位十六进制颜色"
                    else { agendaColor = android.graphics.Color.parseColor(value); updateAgendaButtons(); dialog.dismiss() }
                }
            }
            dialog.show()
        }
        updateAgendaButtons()
    }

    private fun chooseAgendaTime(start: Boolean) {
        val minutes = if (start) agendaStart else agendaEnd.coerceAtMost(1439)
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
            .setHour(minutes / 60).setMinute(minutes % 60).setTitleText(if (start) "开始时间" else "结束时间").build()
        picker.addOnPositiveButtonClickListener {
            if (start) agendaStart = picker.hour * 60 + picker.minute else agendaEnd = picker.hour * 60 + picker.minute
            updateAgendaButtons()
        }
        picker.show(supportFragmentManager, if (start) "agenda_start" else "agenda_end")
    }

    private fun updateAgendaButtons() {
        val editor = binding.agendaEditor
        editor.agendaDate.text = agendaDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日 · EEEE", java.util.Locale.CHINA))
        fun time(value: Int) = "%02d:%02d".format(value / 60, value % 60)
        editor.agendaStart.text = "开始 " + time(agendaStart)
        editor.agendaEnd.text = "结束 " + time(agendaEnd)
        editor.agendaColors.removeAllViews()
        val choices = (cn.ifafu.ifafu.schedule.AgendaEvents.swatches.toList() + agendaColor).distinct()
        choices.forEachIndexed { index, color ->
            val selected = color == agendaColor
            val button = com.google.android.material.button.MaterialButton(this).apply {
                val dp = resources.displayMetrics.density
                layoutParams = android.widget.LinearLayout.LayoutParams((48 * dp).toInt(), (52 * dp).toInt()).apply { marginEnd = (6 * dp).toInt() }
                minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0); cornerRadius = (24 * dp).toInt()
                backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                setTextColor(cn.ifafu.ifafu.ui.timetable.CourseColorPalette.foreground(color))
                text = if (selected) "✓" else ""
                contentDescription = "颜色 ${index + 1}" + if (selected) "，已选中" else ""
                setOnClickListener { agendaColor = color; updateAgendaButtons() }
            }
            editor.agendaColors.addView(button)
        }
    }

    private fun saveAgenda() {
        val editor = binding.agendaEditor
        val item = cn.ifafu.ifafu.entity.AgendaItem(id = agendaId ?: java.util.UUID.randomUUID().toString(),
            account = agendas.account(), title = editor.agendaTitle.text.toString().trim(), date = agendaDate.toString(),
            startMinute = agendaStart, endMinute = agendaEnd, location = editor.agendaLocation.text.toString().trim(),
            notes = editor.agendaNotes.text.toString().trim(), color = agendaColor)
        try { cn.ifafu.ifafu.schedule.AgendaEvents.validate(item) }
        catch (e: IllegalArgumentException) { showToast(e.message ?: "请检查日程信息"); return }
        lifecycleScope.launch {
            runCatching { agendas.save(item) }.onSuccess {
                showToast("日程已保存"); setResult(Activity.RESULT_OK, Intent().putExtra("date", item.date)); finish()
            }.onFailure { showToast(it.message ?: "保存失败，请重试") }
        }
    }

    private fun confirmDelete() {
        if (agendaMode) {
            MaterialAlertDialogBuilder(this).setTitle("删除这项日程？").setNegativeButton("取消", null)
                .setPositiveButton("删除") { _, _ -> lifecycleScope.launch {
                    agendaId?.let { agendas.delete(it) }; setResult(Activity.RESULT_OK); finish()
                } }.show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("删除这门课程？")
            .setMessage("删除后将从当前课表中移除。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ -> deleteCourse() }
            .show()
    }

    private fun deleteCourse() {
        lifecycleScope.launch {
            repository.deleteCourse(course)
            showToast("删除成功")
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

}
