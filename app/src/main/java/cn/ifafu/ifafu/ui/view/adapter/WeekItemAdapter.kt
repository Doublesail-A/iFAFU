package cn.ifafu.ifafu.ui.view.adapter

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cn.ifafu.ifafu.util.DensityUtils
import com.google.android.material.color.MaterialColors
import com.google.android.material.textview.MaterialTextView
import java.util.SortedSet
import java.util.TreeSet

class WeekItemAdapter(private val context: Context) : RecyclerView.Adapter<WeekItemAdapter.VH>() {

    var weekList: SortedSet<Int> = TreeSet()
    private var listener: OnItemClickListener? = null
    var editMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val textView = MaterialTextView(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                DensityUtils.dp2px(context, 48f),
            ).also {
                val margin = DensityUtils.dp2px(context, 4f)
                it.setMargins(margin, margin, margin, margin)
            }
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            isFocusable = true
        }
        return VH(textView)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val week = position + 1
        val selected = weekList.contains(week)
        holder.textView.text = "第${week}周"
        holder.textView.background = pillBackground(selected)
        holder.textView.alpha = if (selected) 1f else 0.58f
        holder.textView.setTextColor(
            MaterialColors.getColor(
                holder.textView,
                if (selected && editMode) com.google.android.material.R.attr.colorOnPrimary
                else if (selected) com.google.android.material.R.attr.colorOnPrimaryContainer
                else com.google.android.material.R.attr.colorOnSurfaceVariant,
                0xFF1D1B20.toInt(),
            ),
        )
        holder.textView.isClickable = editMode
        holder.textView.contentDescription = "第${week}周${if (selected) "，已选择" else "，未选择"}"
        holder.textView.setOnClickListener {
            listener?.onItemClick(week - 1)
            if (!editMode) return@setOnClickListener
            if (selected) weekList.remove(week) else weekList.add(week)
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = 20

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.listener = listener
    }

    private fun pillBackground(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = DensityUtils.dp2px(context, 16f).toFloat()
            setColor(
                MaterialColors.getColor(
                    context,
                    if (selected && editMode) com.google.android.material.R.attr.colorPrimary
                    else if (selected) com.google.android.material.R.attr.colorPrimaryContainer
                    else com.google.android.material.R.attr.colorSurfaceVariant,
                    0xFFE7E0EC.toInt(),
                ),
            )
        }
    }

    fun interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView = itemView as MaterialTextView
    }
}
