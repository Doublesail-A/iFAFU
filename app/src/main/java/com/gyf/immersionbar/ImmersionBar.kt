package com.gyf.immersionbar

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.fragment.app.Fragment

/**
 * Compatibility shim for the small ImmersionBar API surface used by iFAFU.
 * The platform edge-to-edge APIs are sufficient for these screens and keep
 * the app independent from the abandoned third-party status-bar library.
 */
class ImmersionBar private constructor(private val activity: Activity) {
    private var darkIcons: Boolean? = null
    private var barColor: Int? = null

    fun titleBar(view: View): ImmersionBar = apply { }
    fun titleBarMarginTop(view: View): ImmersionBar = apply { }
    fun titleBarMarginTop(@androidx.annotation.IdRes viewId: Int): ImmersionBar = apply { }

    fun statusBarColor(color: String): ImmersionBar = apply {
        barColor = runCatching { Color.parseColor(color) }.getOrNull()
    }

    fun statusBarDarkFont(enabled: Boolean): ImmersionBar = apply {
        darkIcons = enabled
    }

    fun init() {
        barColor?.let { activity.window.statusBarColor = it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var flags = activity.window.decorView.systemUiVisibility
            flags = if (darkIcons == true) {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else if (darkIcons == false) {
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                flags
            }
            activity.window.decorView.systemUiVisibility = flags
        }
    }

    companion object {
        @JvmStatic
        fun with(activity: Activity): ImmersionBar = ImmersionBar(activity)

        @JvmStatic
        fun with(fragment: Fragment): ImmersionBar = ImmersionBar(fragment.requireActivity())
    }
}
