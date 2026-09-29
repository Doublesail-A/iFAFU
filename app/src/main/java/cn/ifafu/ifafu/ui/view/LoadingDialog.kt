package cn.ifafu.ifafu.ui.view

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData

class LoadingDialog(private val context: Context, text: String? = null) {
    private val dialog by lazy { cn.ifafu.ifafu.ui.common.dialog.LoadingDialog(context, text ?: "加载中") }
    val isShowing: Boolean get() = dialog.isShowing()
    fun show() = dialog.show()
    fun show(@StringRes resId: Int) = dialog.show(context.getString(resId))
    fun show(text: String) = dialog.show(text)
    fun cancel() = dialog.cancel()
    fun observe(owner: LifecycleOwner, liveData: LiveData<String?>) {
        liveData.observe(owner) { if (it.isNullOrBlank()) cancel() else show(it) }
    }
}
