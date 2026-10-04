package cn.ifafu.ifafu.util

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.Utils

/** Native text toasts, including messages emitted by background ViewModels. */
object SystemToast {
    private val main = Handler(Looper.getMainLooper())
    fun show(message: String) {
        val display = Runnable {
            Toast.makeText(ActivityUtils.getTopActivity() ?: Utils.getApp(),
                message, Toast.LENGTH_SHORT).show()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) display.run() else main.post(display)
    }
}
