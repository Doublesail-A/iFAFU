package cn.ifafu.ifafu.ui.score

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.transition.TransitionInflater
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.bean.vo.Resource
import cn.ifafu.ifafu.databinding.ScoreFragmentFilterBinding
import cn.ifafu.ifafu.entity.Score
import cn.ifafu.ifafu.ui.common.BaseFragment
import cn.ifafu.ifafu.ui.view.adapter.ScoreFilterAdapter
import cn.ifafu.ifafu.util.trimEnd
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ScoreFilterFragment : BaseFragment() {

    private val viewModel: ScoreViewModel by activityViewModels()
    private val adapter by lazy {
        ScoreFilterAdapter(requireContext()) { score: Score, checked: Boolean ->
            viewModel.itemChecked(score, checked)
        }
    }
    private lateinit var binding: ScoreFragmentFilterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition =
            TransitionInflater.from(requireContext()).inflateTransition(android.R.transition.move)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = ScoreFragmentFilterBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            vm = viewModel
        }
        return binding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvScoreFilter.adapter = adapter
        binding.btnFilterAll.setOnClickListener {
            adapter.setAllChecked()
            viewModel.allChecked()
        }
        viewModel.scoresResource.observe(viewLifecycleOwner) { resource ->
            if (resource is Resource.Success) {
                adapter.data = resource.data
                adapter.notifyDataSetChanged()
            }
        }
        viewModel.ies.observe(viewLifecycleOwner) { ies ->
            binding.tvNowIes.text = getString(R.string.score_filter_now_ies, ies.trimEnd(2))
        }
    }
}
