package cn.ifafu.ifafu.schedule

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.ui.common.BaseActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ScheduleToolsActivity : BaseActivity() {
    @Inject lateinit var repository: ScheduleRepository
    private var snapshot: ScheduleSnapshot? = null
    private var busy = false
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refreshStatus()
        ReminderScheduler.scheduleNext(this)
        if (granted) explainExactPermission()
    }
    private val calendar = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) importCalendar()
        else snackbar("导入需要日历权限；也可以使用文件导出")
    }
    private val saveIcs = registerForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri != null) export(uri, false)
    }
    private val saveZip = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) export(uri, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.schedule_tools_activity)
        setLightUiBar()
        findViewById<MaterialToolbar>(R.id.schedule_toolbar).setNavigationOnClickListener { finish() }
        findViewById<MaterialSwitch>(R.id.course_reminder).apply {
            isChecked = ReminderScheduler.enabled(this@ScheduleToolsActivity, "course")
            setOnCheckedChangeListener { _, checked -> enable("course", checked) }
        }
        findViewById<MaterialSwitch>(R.id.exam_reminder).apply {
            isChecked = ReminderScheduler.enabled(this@ScheduleToolsActivity, "exam")
            setOnCheckedChangeListener { _, checked -> enable("exam", checked) }
        }
        button(R.id.reminder_status).setOnClickListener { requestDeliveryPermissions() }
        button(R.id.reminder_background).setOnClickListener { explainBackgroundSettings() }
        button(R.id.reminder_test).setOnClickListener {
            if (!ReminderScheduler.allowed(this) || !ReminderScheduler.exact(this)) {
                requestDeliveryPermissions(); return@setOnClickListener
            }
            ReminderScheduler.test(this)
            snackbar("30 秒后发送测试通知，可以返回桌面验证")
        }
        button(R.id.calendar_import).setOnClickListener {
            if (listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
                    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) importCalendar()
            else calendar.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
        button(R.id.calendar_ics).setOnClickListener { saveIcs.launch("iFAFU课表.ics") }
        button(R.id.calendar_zip).setOnClickListener { saveZip.launch("iFAFU分课程日历.zip") }
        lifecycleScope.launch {
            runCatching { repository.snapshot(intent.getStringExtra("year"), intent.getStringExtra("term")) }
                .onSuccess {
                    snapshot = it
                    findViewById<TextView>(R.id.calendar_summary).text =
                        it.label + "\n" + it.courses.map { lesson -> lesson.subject }.distinct().size +
                            " 门课程 · " + it.courses.size + " 次上课"
                    updateButtons()
                }.onFailure { Timber.e(it); snackbar("课表读取失败，请先在课程表中刷新数据") }
        }
        updateButtons()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            runCatching { repository.refreshReminders() }.onFailure { Timber.e(it) }
            refreshStatus()
            ReminderScheduler.scheduleNext(this@ScheduleToolsActivity)
        }
    }

    private fun button(id: Int) = findViewById<MaterialButton>(id)
    private fun refreshStatus() {
        findViewById<TextView>(R.id.reminder_status_text).text = ReminderScheduler.status(this)
        val optimized = Build.VERSION.SDK_INT >= 23 &&
            !(getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        button(R.id.reminder_background).text = if (optimized) "后台与电池设置" else "后台设置 · 已无电池限制"
        button(R.id.reminder_status).text = when {
            !ReminderScheduler.allowed(this) -> "允许通知"
            !ReminderScheduler.exact(this) -> "允许准时提醒"
            else -> "系统通知设置"
        }
    }
    private fun enable(kind: String, checked: Boolean) {
        ReminderScheduler.setEnabled(this, kind, checked)
        if (checked && (!ReminderScheduler.allowed(this) || !ReminderScheduler.exact(this))) requestDeliveryPermissions()
        refreshStatus()
    }
    private fun requestDeliveryPermissions() {
        when {
            Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            !ReminderScheduler.allowed(this) ->
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            Build.VERSION.SDK_INT >= 31 && !ReminderScheduler.exact(this) ->
                explainExactPermission()
            else -> startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }
    private fun explainExactPermission() {
        if (Build.VERSION.SDK_INT < 31 || ReminderScheduler.exact(this)) return
        MaterialAlertDialogBuilder(this)
            .setTitle("让提醒按时送达")
            .setMessage("请允许“闹钟和提醒”。上课前 15 分钟、考试前 30 分钟的提醒由 Android 系统触发，退出应用也能收到；未允许时系统可能延迟提醒。")
            .setPositiveButton("去允许") { _, _ ->
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + packageName)))
            }
            .setNegativeButton("稍后", null).show()
    }

    private fun explainBackgroundSettings() {
        MaterialAlertDialogBuilder(this)
            .setTitle("后台提醒设置")
            .setMessage("如果允许准时提醒后，清理后台仍收不到通知，请在应用信息的电池设置中选择“不受限制”，并开启手机系统提供的自启动权限。\n\n无需让 iFAFU 一直开着。系统设置中的“强行停止”会取消闹钟，之后需重新打开应用。")
            .setPositiveButton("打开应用信息") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName)))
            }
            .setNegativeButton("关闭", null).show()
    }

    private fun updateButtons() {
        val ready = !busy && snapshot?.courses?.isNotEmpty() == true
        listOf(R.id.calendar_import, R.id.calendar_ics, R.id.calendar_zip).forEach { button(it).isEnabled = ready }
    }
    private fun work(message: String, action: suspend () -> String) {
        if (busy) return
        busy = true; updateButtons(); showLoading(message)
        lifecycleScope.launch {
            try { snackbar(withContext(Dispatchers.IO) { action() }) }
            catch (e: Exception) { Timber.e(e); snackbar(e.message ?: "操作失败，请重试") }
            finally { busy = false; hideLoading(); updateButtons() }
        }
    }
    private fun importCalendar() {
        val data = snapshot ?: return
        work("正在创建彩色日历") {
            val count = CalendarImporter.import(this, data.courses, data.account)
            "已导入 " + count + " 次课程；在日历应用中勾选 iFAFU 日历即可显示"
        }
    }
    private fun export(uri: Uri, zip: Boolean) {
        val events = snapshot?.courses ?: return
        work("正在导出课表") {
            requireNotNull(contentResolver.openOutputStream(uri, "wt")).use { output ->
                if (zip) CalendarExport.zip(events, output)
                else output.write(CalendarExport.ics(events).toByteArray(Charsets.UTF_8))
            }
            "课表已保存"
        }
    }
}
