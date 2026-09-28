package cn.ifafu.ifafu.ui.score

import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.core.widget.ImageViewCompat
import androidx.navigation.findNavController
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.entity.Score
import cn.ifafu.ifafu.util.GlobalLib
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.google.android.material.color.MaterialColors

class ScoreListAdapter : BaseQuickAdapter<Score, BaseViewHolder>(R.layout.score_list_item) {

    override fun convert(holder: BaseViewHolder, item: Score) {
        holder.setText(R.id.tv_score_name, item.name)
        val score = item.realScore
        holder.setText(
            R.id.tv_score,
            if (score == Score.FREE_COURSE) "免修" else GlobalLib.formatFloat(score, 2),
        )

        val isFailure = score < 60 && score != Score.FREE_COURSE
        val scoreColorAttr = if (isFailure) {
            com.google.android.material.R.attr.colorError
        } else {
            com.google.android.material.R.attr.colorPrimary
        }
        val scoreColor = MaterialColors.getColor(holder.itemView, scoreColorAttr)
        holder.setTextColor(R.id.tv_score, scoreColor)

        val icon = when {
            isFailure -> R.drawable.ic_m3_warning
            item.nature.contains("选修") -> R.drawable.ic_m3_menu_book
            score == Score.FREE_COURSE -> R.drawable.ic_m3_event_note
            else -> R.drawable.ic_m3_grade
        }
        holder.setImageResource(R.id.iv_tip, icon)
        ImageViewCompat.setImageTintList(
            holder.getView<ImageView>(R.id.iv_tip),
            ColorStateList.valueOf(scoreColor),
        )
        holder.itemView.setOnClickListener { view ->
            val action = ScoreListFragmentDirections
                .actionFragmentScoreListToFragmentScoreDetail(item)
            view.findNavController().navigate(action)
        }
    }
}
