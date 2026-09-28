package cn.ifafu.ifafu.ui.timetable

import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import androidx.recyclerview.widget.RecyclerView
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.bean.vo.OpeningDayVO
import cn.ifafu.ifafu.bean.vo.TimetableVO
import cn.ifafu.ifafu.entity.SyllabusSetting
import cn.ifafu.ifafu.ui.view.timetable.TimetableItem
import cn.ifafu.ifafu.ui.view.timetable.TimetableView
import cn.ifafu.ifafu.util.DateUtils
import java.util.*
import com.google.android.material.color.MaterialColors

class TimetablePageAdapter(
    private val onItemClickListener: (View, TimetableItem) -> Unit,
    private val onItemLongClickListener: (View, TimetableItem) -> Unit
) : RecyclerView.Adapter<TimetablePageAdapter.SyllabusViewHolder>() {

    /**
     * 按周分类
     */
    private var data: TimetableVO? = null
    private var dataVersion = 0

    private var setting: SyllabusSetting? = null
    private var settingVersion = 0 //用于记录setting的版本

    private var openingDay: OpeningDayVO? = null
    private var openingDayVersion = 0

    private var hasWallpaper = false

    fun updateWallpaper(present: Boolean) {
        if (hasWallpaper == present) return
        hasWallpaper = present
        notifyDataSetChanged()
    }

    fun updateTimetable(timetable: TimetableVO) {
        this.data = timetable
        dataVersion++
        notifyDataSetChanged()
    }

    fun updateSetting(setting: SyllabusSetting) {
        this.setting = setting
        settingVersion++
        notifyDataSetChanged()
    }

    fun updateOpeningDay(openingDay: OpeningDayVO) {
        this.openingDay = openingDay
        openingDayVersion++
        notifyDataSetChanged()
    }

    fun refreshUrgency() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    //将View和ViewHolder绑定在一起
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SyllabusViewHolder {
        val timetable = TimetableView(parent.context)
        timetable.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        timetable.config = timetable.config.apply {
            otherTextColor = MaterialColors.getColor(
                timetable,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                Color.DKGRAY,
            )
        }
        timetable.setItemClickListener { v, item ->
            onItemClickListener.invoke(v, item)
        }
        timetable.setItemLongClickListener { v, item ->
            onItemLongClickListener.invoke(v, item)
        }
        return SyllabusViewHolder(timetable)
    }

    //将数据显示在View上
    override fun onBindViewHolder(holder: SyllabusViewHolder, position: Int) {
        holder.timetable.setBackgroundColor(if (hasWallpaper) Color.TRANSPARENT else
            MaterialColors.getColor(holder.timetable, com.google.android.material.R.attr.colorSurface, Color.WHITE))
        val setting = setting
        if (setting != null) {
            if (holder.settingVersion < settingVersion) {
                holder.timetable.config = holder.timetable.config.apply {
                    totalNodeCount = setting.totalNode
                    showTime = setting.showBeginTimeText
                    showHorizontalLine = setting.showHorizontalLine
                    showVerticalLine = setting.showVerticalLine
                    itemTextSize = setting.textSize.toFloat()
                    otherTextColor = MaterialColors.getColor(
                        holder.timetable,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        setting.themeColor.takeIf { it != Color.BLACK } ?: Color.DKGRAY
                    )
                }
                holder.timetable.setTimeText(setting.getBeginTimeText().drop(1).toTypedArray())
                holder.settingVersion = settingVersion
            }
        }

        if (holder.dataVersion != dataVersion || holder.showPosition != position) {
            holder.dataVersion = dataVersion
            val colors = CourseColorPalette.forCourses(holder.itemView.context,
                data?.data.orEmpty().flatten().map { it.name })
            holder.timetable.config = holder.timetable.config.apply {
                colorPool = MaterialCourseColorPool(holder.timetable, colors)
            }
            val courseList = data?.getWeek(position + 1)
            holder.timetable.setItems(courseList)
        }

        val now = Calendar.getInstance()
        val currentWeek = openingDay?.getCurrentWeek()
        val courseList = data?.getWeek(position + 1).orEmpty()
        val urgentKeys = if (currentWeek == position + 1) {
            val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            courseList.asSequence()
                .filter { it.dayOfWeek == now.get(Calendar.DAY_OF_WEEK) }
                .filter { it.startNode in 1 until (setting?.beginTime?.size ?: 0) }
                .filter {
                    val encoded = setting!!.beginTime[it.startNode]
                    val startMinutes = encoded / 100 * 60 + encoded % 100
                    startMinutes - nowMinutes in 0 until 15
                }
                .map(TimetableView::itemKey)
                .toSet()
        } else {
            emptySet()
        }
        holder.timetable.setUrgentItemKeys(urgentKeys)

        if (holder.openingDayVersion != openingDayVersion || holder.showPosition != position) {
            holder.openingDayVersion = openingDayVersion
            val d = openingDay?.getOpeningDay()
            if (d == null) {
                holder.timetable.setMonth(-1)
                holder.timetable.setDateTexts(null)
                holder.timetable.setTodayColumn(-1)
            } else {
                val weekStart = Calendar.getInstance().apply {
                    time = d
                    add(Calendar.DAY_OF_YEAR, -(get(Calendar.DAY_OF_WEEK) - 1))
                    add(Calendar.DAY_OF_YEAR, position * 7)
                }
                val dates = DateUtils.getWeekOffsetDates(Date(weekStart.timeInMillis), 0, "d")
                val month = weekStart.get(Calendar.MONTH) + 1
                holder.timetable.setMonth(month)
                holder.timetable.setDateTexts(dates.toTypedArray())
                val todayOffset = DateUtils.calcLastDays(weekStart.time, Date())
                holder.timetable.setTodayColumn(todayOffset.takeIf { it in 0..6 } ?: -1)
            }
        }
        holder.showPosition = position
    }

    override fun getItemCount(): Int {
        return setting?.weekCnt ?: 0
    }

    class SyllabusViewHolder(itemView: TimetableView) : RecyclerView.ViewHolder(itemView) {
        val timetable: TimetableView = itemView
        var showPosition = -1

        var openingDayVersion = -1
        var dataVersion = -1
        var settingVersion = -1
    }

}
