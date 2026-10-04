package cn.ifafu.ifafu.ui.common

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.LiveData
import cn.ifafu.ifafu.bean.vo.Resource
import cn.ifafu.ifafu.ui.common.dialog.LoadingDialog
import cn.ifafu.ifafu.util.ThemeManager
import com.google.android.material.color.MaterialColors
import com.gyf.immersionbar.ImmersionBar

abstract class BaseActivity : AppCompatActivity {

    private val mLoadingDialog: LoadingDialog by lazy { LoadingDialog(this) }

    private var toast: Toast? = null
    private var appliedPaletteKey = ""

    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        appliedPaletteKey = ThemeManager.paletteKey(this)
        super.onCreate(savedInstanceState)
        (application as cn.ifafu.ifafu.IFAFU).scheduleRepository.get().start()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface,
                android.graphics.Color.WHITE) or (0xff shl 24)))
    }

    override fun onResume() {
        super.onResume()
        if (appliedPaletteKey != ThemeManager.paletteKey(this)) recreate()
    }

    protected fun setTransparentStatusBar() {
        ImmersionBar.with(this).init()
    }

    /**
     * 设置亮色状态栏（黑色图标）
     */
    fun setLightUiBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val light = !ThemeManager.isNight(this)
            var flags = window.decorView.systemUiVisibility
            flags = if (light) {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = if (light) {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                }
            }
            window.decorView.systemUiVisibility = flags
            window.statusBarColor = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorSurface,
                window.statusBarColor
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.navigationBarColor = MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurface,
                    window.navigationBarColor
                )
            }
        }
    }

    protected fun <VDB : ViewDataBinding> bind(@LayoutRes layoutRes: Int): VDB {
        return DataBindingUtil.setContentView<VDB>(this, layoutRes).apply {
            lifecycleOwner = this@BaseActivity
        }
    }

    /**
     * when you override [showLoading], remember to override [hideLoading]
     */
    protected open fun showLoading(message: String) {
        mLoadingDialog.show(message)
    }

    protected open fun hideLoading() {
        if (mLoadingDialog.isShowing()) {
            mLoadingDialog.cancel()
        }
    }

    override fun onStop() {
        hideLoading()
        super.onStop()
    }

    protected open fun snackbar(message: String) = showToast(message)

    protected open fun showToast(message: String) {
        toast?.cancel()
        toast = Toast.makeText(this, message, Toast.LENGTH_SHORT).also { it.show() }
    }

    fun <T> LiveData<Resource<T>>.observeResource(
        loadingMessage: String,
        onSuccess: (T) -> Unit
    ) {
        this.observe(this@BaseActivity, { res ->
            when (res) {
                is Resource.Success -> {
                    onSuccess(res.data)
                    hideLoading()
                }
                is Resource.Failure -> {
                    res.handleMessage { msg ->
                        snackbar(msg)
                    }
                    hideLoading()
                }
                is Resource.Loading -> {
                    showLoading(loadingMessage)
                }
            }
        })
    }

}
