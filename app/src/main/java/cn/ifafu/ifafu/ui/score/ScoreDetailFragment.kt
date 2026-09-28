package cn.ifafu.ifafu.ui.score

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.navArgs
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.databinding.ScoreFragmentDetailBinding
import cn.ifafu.ifafu.ui.common.BaseFragment
import cn.ifafu.ifafu.ui.view.adapter.ScoreItemAdapter
import cn.ifafu.ifafu.util.GlobalLib
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ScoreDetailFragment : BaseFragment(R.layout.score_fragment_detail) {

    private val args: ScoreDetailFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = ScoreFragmentDetailBinding.bind(view)
        val score = args.score
        binding.tvDetailName.text = score.name
        binding.tvDetailScore.text = if (score.score != -1F) {
            GlobalLib.formatFloat(score.score, 2) + " 分"
        } else {
            "暂无成绩"
        }

        val details = linkedMapOf(
            "学分" to if (score.credit != -1F) GlobalLib.formatFloat(score.credit, 2) else "无信息",
            "绩点" to if (score.gpa != -1F) GlobalLib.formatFloat(score.gpa, 2) else "无信息",
            "补考成绩" to if (score.makeupScore != -1F) GlobalLib.formatFloat(score.makeupScore, 2) else "无信息",
            "课程性质" to score.nature.ifEmpty { "无信息" },
            "课程属性" to score.attr.ifEmpty { "无信息" },
            "开课学院" to score.institute.ifEmpty { "无信息" },
            "学年" to score.year,
            "学期" to score.term,
            "备注" to score.remarks.ifEmpty { "无" },
        )
        binding.adapter = ScoreItemAdapter(details.toList())
    }
}
