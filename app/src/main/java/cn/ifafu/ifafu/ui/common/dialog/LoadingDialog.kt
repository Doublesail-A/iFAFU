package cn.ifafu.ifafu.ui.common.dialog

import android.content.Context
import android.widget.TextView
import cn.ifafu.ifafu.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LoadingDialog(private val context: Context, private var text: String = "加载中") {
    private val content by lazy { android.view.LayoutInflater.from(context).inflate(R.layout.dialog_base_progress, null) }
    private val dialog by lazy { MaterialAlertDialogBuilder(context).setView(content).create().apply { setCanceledOnTouchOutside(false) } }
    fun show(text: String? = this.text) {
        this.text = text?.takeIf { it.isNotBlank() } ?: "加载中"
        content.findViewById<TextView>(R.id.base_tv_loading).text = this.text
        dialog.show()
    }
    fun cancel() { if (dialog.isShowing) dialog.dismiss() }
    fun isShowing() = dialog.isShowing
}
