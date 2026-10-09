package cn.ifafu.ifafu.ui.score

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.bean.vo.Resource
import cn.ifafu.ifafu.databinding.ScoreFragmentListBinding
import cn.ifafu.ifafu.ui.common.BaseFragment
import cn.ifafu.ifafu.ui.view.LoadingDialog
import cn.ifafu.ifafu.ui.view.SemesterOptionPicker
import cn.ifafu.ifafu.util.trimEnd
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ScoreListFragment : BaseFragment(), View.OnClickListener {

    private val adapter = ScoreListAdapter()
    private lateinit var binding: ScoreFragmentListBinding
    private val viewModel: ScoreViewModel by activityViewModels()
    private val loadingDialog by lazy { LoadingDialog(requireContext(), "获取中") }
    private val semesterPicker by lazy {
        SemesterOptionPicker(requireActivity()) { year, term ->
            viewModel.switchYearAndTerm(year, term)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = ScoreFragmentListBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            vm = viewModel
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvScoreTitle.setOnClickListener(this)
        binding.layoutIes.setOnClickListener(this)
        binding.layoutCnt.setOnClickListener(this)
        binding.rvScore.adapter = adapter

        viewModel.iesDetail.observe(viewLifecycleOwner) { event ->
            event.runContentIfNotHandled { detail ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("智育分计算详情").setMessage(detail)
                    .setNeutralButton("计算规则") { _, _ ->
                        MaterialAlertDialogBuilder(requireContext()).setTitle("智育分计算规则")
                            .setMessage(R.string.score_ies_rule).setPositiveButton("知道了", null).show()
                    }.setPositiveButton("知道了", null).show()
            }
        }
        viewModel.scoresResource.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Success -> {
                    val empty = resource.data.isEmpty()
                    binding.rvScore.visibility = if (empty) View.GONE else View.VISIBLE
                    binding.viewExamEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                    binding.tvCntBig.text = resource.data.size.toString()
                    adapter.setList(resource.data)
                    resource.handleMessage(::snackbar)
                    loadingDialog.cancel()
                }
                is Resource.Failure -> {
                    snackbar(resource.message)
                    loadingDialog.cancel()
                }
                is Resource.Loading -> loadingDialog.show()
            }
        }
        viewModel.ies.observe(viewLifecycleOwner, ::showIes)
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.tv_score_title -> viewModel.semester.value?.let {
                semesterPicker.setSemester(it)
                semesterPicker.show()
            }
            R.id.layout_ies -> viewModel.iesCalculationDetail()
            R.id.layout_cnt -> openFilter()
        }
    }

    private fun openFilter() {
        val semester = viewModel.semester.value
        if (semester == null) {
            snackbar("未找到学期信息")
            return
        }
        val action = ScoreListFragmentDirections.actionFragmentScoreListToFragmentScoreFilter(
            semester.yearStr,
            semester.termStr,
        )
        findNavController().navigate(action)
    }

    private fun showIes(ies: Float) {
        val result = if (ies.isNaN() || ies <= 0F) "0" else ies.trimEnd(2)
        val index = result.indexOf('.')
        if (index == -1) {
            binding.tvIes1.text = result
            binding.tvIes2.text = "分"
        } else {
            binding.tvIes1.text = result.substring(0, index)
            binding.tvIes2.text = result.substring(index) + "分"
        }
    }
}
