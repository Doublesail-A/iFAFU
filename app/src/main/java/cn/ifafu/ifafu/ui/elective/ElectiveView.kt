package cn.ifafu.ifafu.ui.elective

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.bean.bo.Elective
import cn.ifafu.ifafu.entity.Score
import com.google.android.material.color.MaterialColors
import java.util.LinkedHashMap

class ElectiveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var clickListener: ((View, Score) -> Unit)? = null
    private val rootLayout: LinearLayout
    private val expandButton: ImageButton
    private val categoryText: TextView
    private val statisticsText: TextView
    private val emptyText: TextView
    private var elective: Elective? = null
    private var isCollapsed = true
    private val itemViews = LinkedHashMap<Score, View>()

    private val expandAnimation by lazy { rotation(0F, -180F) }
    private val collapseAnimation by lazy { rotation(-180F, 0F) }

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.elective_list_item, this)
        rootLayout = view.findViewById(R.id.layout_root)
        expandButton = view.findViewById(R.id.btn_sign)
        categoryText = view.findViewById(R.id.category)
        statisticsText = view.findViewById(R.id.statistics)
        emptyText = view.findViewById(R.id.tv_empty)
        rootLayout.setOnClickListener { expandOrCollapse() }
        expandButton.setOnClickListener { expandOrCollapse() }
    }

    fun setElective(value: Elective?) {
        if (value == null) return
        itemViews.values.forEach(rootLayout::removeView)
        itemViews.clear()
        elective = value
        isCollapsed = true
        expandButton.clearAnimation()
        categoryText.text = value.category
        statisticsText.text = value.statistics
        statisticsText.setTextColor(
            MaterialColors.getColor(
                this,
                if (value.done) {
                    com.google.android.material.R.attr.colorPrimary
                } else {
                    com.google.android.material.R.attr.colorError
                },
            ),
        )

        value.scores.forEach { score ->
            val item = LayoutInflater.from(context)
                .inflate(R.layout.elective_list_item_item, rootLayout, false)
            item.findViewById<TextView>(R.id.tv_name).text = score.name
            val credit = item.findViewById<TextView>(R.id.tv_credit)
            val isFailure = score.realScore < 60 && score.credit != 0F
            credit.text = when {
                score.credit == 0F -> "重复"
                isFailure -> "未通过"
                else -> "${score.credit} 学分"
            }
            credit.setTextColor(
                MaterialColors.getColor(
                    this,
                    if (isFailure) {
                        com.google.android.material.R.attr.colorError
                    } else {
                        com.google.android.material.R.attr.colorPrimary
                    },
                ),
            )
            item.visibility = View.GONE
            itemViews[score] = item
            clickListener?.let { listener ->
                item.setOnClickListener { listener(it, score) }
            }
            rootLayout.addView(item)
        }
        emptyText.visibility = View.GONE
    }

    fun getElective(): Elective? = elective

    fun setOnScoreClickListener(listener: (View, Score) -> Unit) {
        clickListener = listener
        itemViews.forEach { (score, view) ->
            view.setOnClickListener { listener(it, score) }
        }
    }

    private fun expandOrCollapse() {
        isCollapsed = if (isCollapsed) {
            expandButton.startAnimation(expandAnimation)
            if (itemViews.isEmpty()) show(emptyText) else itemViews.values.forEach(::show)
            false
        } else {
            expandButton.startAnimation(collapseAnimation)
            if (itemViews.isEmpty()) hide(emptyText) else itemViews.values.forEach(::hide)
            true
        }
    }

    private fun show(view: View) {
        view.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(view, View.ALPHA, 0F, 1F).apply {
            duration = 180L
            interpolator = FastOutSlowInInterpolator()
        }.start()
    }

    private fun hide(view: View) {
        ObjectAnimator.ofFloat(view, View.ALPHA, 1F, 0F).apply {
            duration = 160L
            interpolator = FastOutSlowInInterpolator()
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) = Unit
                override fun onAnimationRepeat(animation: Animator) = Unit
                override fun onAnimationCancel(animation: Animator) = Unit
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                }
            })
        }.start()
    }

    private fun rotation(from: Float, to: Float): RotateAnimation {
        return RotateAnimation(
            from,
            to,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f,
        ).apply {
            fillAfter = true
            duration = 220L
            interpolator = FastOutSlowInInterpolator()
        }
    }
}
