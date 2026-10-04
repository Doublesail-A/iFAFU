package cn.ifafu.ifafu.ui.elective

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.databinding.ElectiveFragmentBinding
import cn.ifafu.ifafu.entity.Score
import cn.ifafu.ifafu.ui.view.LoadingDialog
import cn.ifafu.ifafu.util.SystemToast
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ElectiveFragment : Fragment(R.layout.elective_fragment) {

    private val loadingDialog by lazy { LoadingDialog(requireContext()) }

    private val viewModel by viewModels<ElectiveViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val listener: (View, Score) -> Unit = { _, score ->
            val action = ElectiveFragmentDirections
                .actionFragmentElectiveToFragmentScoreDetail(score)
            findNavController().navigate(action)
        }
        val binding = ElectiveFragmentBinding.bind(view).apply {
            lifecycleOwner = viewLifecycleOwner
            vm = viewModel
            eTotal.setOnScoreClickListener(listener)
            eCxcy.setOnScoreClickListener(listener)
            eRwsk.setOnScoreClickListener(listener)
            eWxsy.setOnScoreClickListener(listener)
            eYsty.setOnScoreClickListener(listener)
            eZrkx.setOnScoreClickListener(listener)
        }

        viewModel.toast.observe(viewLifecycleOwner, {
            SystemToast.show(it)
        })

        loadingDialog.observe(viewLifecycleOwner, viewModel.loading)

    }

}
