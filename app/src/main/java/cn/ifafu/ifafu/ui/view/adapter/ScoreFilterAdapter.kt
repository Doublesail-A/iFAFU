package cn.ifafu.ifafu.ui.view.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.entity.Score
import cn.ifafu.ifafu.util.GlobalLib
import com.google.android.material.checkbox.MaterialCheckBox

class ScoreFilterAdapter(
    context: Context,
    private val onCheckedChangeListener: (score: Score, isChecked: Boolean) -> Unit,
) : RecyclerView.Adapter<ScoreFilterAdapter.ViewHolder>() {

    var data: List<Score> = emptyList()
    private val layoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(layoutInflater.inflate(R.layout.score_filter_item, parent, false))
    }

    override fun getItemCount(): Int = data.size

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val score = data[position]
        holder.title.text = score.name
        holder.score.text = if (score.realScore == Score.FREE_COURSE) {
            "免修"
        } else {
            GlobalLib.formatFloat(score.realScore, 2) + "分"
        }
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = score.isIESItem
        holder.checkBox.setOnCheckedChangeListener { _, checked ->
            onCheckedChangeListener(score, checked)
        }
        holder.itemView.setOnClickListener {
            holder.checkBox.isChecked = !holder.checkBox.isChecked
        }
    }

    fun setAllChecked() {
        data.forEach { it.isIESItem = true }
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.tv_score_name)
        val score: TextView = itemView.findViewById(R.id.tv_score)
        val checkBox: MaterialCheckBox = itemView.findViewById(R.id.checkbox)
    }
}
