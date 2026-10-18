package cn.ifafu.ifafu.schedule

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cn.ifafu.ifafu.ui.widget.ScheduleWidget

/** Request delivery permissions at the first real screen, never behind the routing splash. */
class StartupPermissions(private val activity: AppCompatActivity) {
    private val prefs = activity.getSharedPreferences("startup_permissions_v1", 0)
    private val exact = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        ScheduleWidget.updateAll(activity, force = true)
        ReminderScheduler.scheduleNext(activity)
    }
    private val notifications = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestExact()
    }
    private var checking = false
    fun check() {
        if (checking) return
        checking = true
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("notifications", false)) {
            prefs.edit().putBoolean("notifications", true).apply()
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else requestExact()
    }
    private fun requestExact() {
        if (Build.VERSION.SDK_INT < 31 || ReminderScheduler.exact(activity) || prefs.getBoolean("exact", false)) return
        prefs.edit().putBoolean("exact", true).apply()
        MaterialAlertDialogBuilder(activity).setTitle("允许准时更新与提醒")
            .setMessage("允许“闹钟和提醒”，让桌面倒计时和课程、日程提醒在应用退出后也能准时更新。")
            .setNegativeButton("稍后", null)
            .setPositiveButton("前往设置") { _, _ ->
                runCatching { exact.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + activity.packageName))) }
            }.show()
    }
}
