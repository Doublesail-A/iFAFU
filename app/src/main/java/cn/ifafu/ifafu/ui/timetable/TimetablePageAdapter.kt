package cn.ifafu.ifafu.ui.timetable

import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import androidx.recyclerview.widget.RecyclerView
import cn.ifafu.ifafu.bean.vo.OpeningDayVO
import cn.ifafu.ifafu.bean.vo.TimetableVO
import cn.ifafu.ifafu.entity.SyllabusSetting
import cn.ifafu.ifafu.entity.AgendaItem
import cn.ifafu.ifafu.ui.view.timetable.TimetableItem
import cn.ifafu.ifafu.ui.view.timetable.TimetableView
import com.google.android.material.color.MaterialColors
import com.google.android.material.color.utilities.TonalPalette
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar

class TimetablePageAdapter(
    private val onItemClickListener: (View, TimetableItem) -> Unit,
    private val onItemLongClickListener: (View, TimetableItem) -> Unit
) : RecyclerView.Adapter<TimetablePageAdapter.SyllabusViewHolder>() {
    private var data: TimetableVO? = null
    private var setting: SyllabusSetting? = null
    private var openingDay: OpeningDayVO? = null
    private var agenda: List<AgendaItem> = emptyList()
    private var hasWallpaper = false
    private var firstWeek = 1
    private var lastWeek = 20
    private var includedDate: LocalDate? = null
    private fun firstSunday(): LocalDate {
        val first = runCatching { LocalDate.parse(openingDay?.openingDay) }.getOrNull() ?: LocalDate.now()
        return first.minusDays((first.dayOfWeek.value % 7).toLong())
    }
    private fun weekOf(date: LocalDate) = Math.floorDiv(ChronoUnit.DAYS.between(firstSunday(), date), 7L).toInt() + 1
    private fun range() {
        val weeks = agenda.map { weekOf(LocalDate.parse(it.date)) } + listOfNotNull(includedDate?.let(::weekOf))
        firstWeek = minOf(1, weeks.minOrNull() ?: 1)
        lastWeek = maxOf(setting?.weekCnt ?: 20, weeks.maxOrNull() ?: 1)
    }
    fun weekAt(position: Int) = firstWeek + position
    fun positionForWeek(week: Int) = (week - firstWeek).coerceIn(0, itemCount - 1)
    fun dateForWeek(week: Int) = firstSunday().plusWeeks((week - 1).toLong())
    fun positionForDate(date: LocalDate) = positionForWeek(weekOf(date))
    fun includeDate(date: LocalDate) { includedDate = date; range(); notifyDataSetChanged() }
    fun updateAgenda(items: List<AgendaItem>) { agenda = items; range(); notifyDataSetChanged() }
    fun updateWallpaper(present: Boolean) { hasWallpaper = present; notifyDataSetChanged() }
    fun updateTimetable(timetable: TimetableVO) { data = timetable; notifyDataSetChanged() }
    fun updateSetting(value: SyllabusSetting) { setting = value; range(); notifyDataSetChanged() }
    fun updateOpeningDay(value: OpeningDayVO) { openingDay = value; range(); notifyDataSetChanged() }
    fun refreshUrgency() { notifyItemRangeChanged(0, itemCount) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SyllabusViewHolder {
        val view = TimetableView(parent.context)
        view.layoutParams = ViewGroup.LayoutParams(-1, -1)
        view.setItemClickListener { v, item -> onItemClickListener(v, item) }
        view.setItemLongClickListener { v, item -> onItemLongClickListener(v, item) }
        return SyllabusViewHolder(view)
    }
    override fun onBindViewHolder(holder: SyllabusViewHolder, position: Int) {
        val view = holder.timetable
        val settings = setting ?: SyllabusSetting()
        val week = weekAt(position)
        val date = dateForWeek(week)
        view.setBackgroundColor(if (hasWallpaper) Color.TRANSPARENT else MaterialColors.getColor(view,
            com.google.android.material.R.attr.colorSurface, Color.WHITE))
        val colors = CourseColorPalette.forCourses(view.context, data?.data.orEmpty().flatten().map { it.name })
        view.config = view.config.apply {
            totalNodeCount = settings.totalNode
            showTime = settings.showBeginTimeText
            showHorizontalLine = settings.showHorizontalLine
            showVerticalLine = settings.showVerticalLine
            itemTextSize = settings.textSize.toFloat()
            otherTextColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY)
            colorPool = MaterialCourseColorPool(view, colors)
        }
        view.setTimeText(settings.getBeginTimeText().drop(1).toTypedArray())
        val items = data?.getWeek(week).orEmpty().map { source ->
            val first = settings.beginTime.getOrNull(source.startNode) ?: 0
            val last = settings.beginTime.getOrNull(source.startNode + source.nodeCount - 1) ?: first
            TimetableItem().apply {
                name = source.name; address = source.address; tag = source.tag
                dayOfWeek = source.dayOfWeek; startNode = source.startNode; nodeCount = source.nodeCount
                startMinute = first / 100 * 60 + first % 100
                endMinute = last / 100 * 60 + last % 100 + settings.nodeLength
            }
        }.toMutableList()
        val dark = view.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        agenda.filter { LocalDate.parse(it.date) in date..date.plusDays(6) }.forEach { event ->
            items += TimetableItem().apply {
                name = event.title; address = event.location; tag = event; agenda = true
                dayOfWeek = LocalDate.parse(event.date).dayOfWeek.value % 7 + 1
                startNode = 1; nodeCount = 1; startMinute = event.startMinute; endMinute = event.endMinute
                customColor = if (dark) TonalPalette.fromInt(event.color).tone(40) else event.color
            }
        }
        view.setItems(items)
        val now = Calendar.getInstance()
        val today = LocalDate.now()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        view.setUrgentItemKeys(items.filter {
            date.plusDays((it.dayOfWeek - 1).toLong()) == today && it.startMinute - nowMinutes in 1..15
        }.map(TimetableView::itemKey).toSet())
        view.setMonth(date.monthValue)
        view.setDateTexts((0L..6L).map { date.plusDays(it).dayOfMonth.toString() }.toTypedArray())
        view.setTodayColumn(ChronoUnit.DAYS.between(date, today).toInt().takeIf { it in 0..6 } ?: -1)
    }
    override fun getItemCount() = (lastWeek - firstWeek + 1).coerceAtLeast(1)
    class SyllabusViewHolder(val timetable: TimetableView) : RecyclerView.ViewHolder(timetable)
}
