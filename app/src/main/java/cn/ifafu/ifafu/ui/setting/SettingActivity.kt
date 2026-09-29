package cn.ifafu.ifafu.ui.setting

import android.app.Activity
import android.os.Bundle
import androidx.activity.viewModels
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.entity.GlobalSetting
import cn.ifafu.ifafu.util.TimetableWallpaper
import cn.ifafu.ifafu.databinding.SettingActivityBinding
import cn.ifafu.ifafu.ui.common.BaseActivity
import cn.ifafu.ifafu.ui.view.adapter.syllabus_setting.TextViewItem
import cn.ifafu.ifafu.util.ThemePreferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingActivity : BaseActivity() {
    private val mViewModel: SettingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setLightUiBar()
        val binding = bind<SettingActivityBinding>(R.layout.setting_activity)

        binding.tbSetting.setNavigationOnClickListener { finish() }
        binding.cardTheme.setOnClickListener { mViewModel.requestThemePicker() }
        binding.scheduleTools.setOnClickListener {
            startActivity(android.content.Intent(this, cn.ifafu.ifafu.schedule.ScheduleToolsActivity::class.java))
        }

        mViewModel.settings.observe(this, {
            binding.tvThemeValue.text = it.filterIsInstance<TextViewItem>()
                .firstOrNull()?.subtitle.orEmpty()
        })
        mViewModel.needCheckTheme.observe(this, {
            if (it) {
                setResult(Activity.RESULT_OK)
            } else {
                setResult(Activity.RESULT_CANCELED)
            }
        })
        mViewModel.showThemePicker.observe(this) { request ->
            if (request == null) return@observe
            // Consume the one-shot click so a configuration change does not reopen the dialog.
            mViewModel.showThemePicker.value = null
            MaterialAlertDialogBuilder(this)
                .setTitle("主题配色")
                .setSingleChoiceItems(
                    ThemePreferences.labels,
                    mViewModel.selectedThemeIndex()
                ) { dialog, which ->
                    if (ThemePreferences.modes[which] == GlobalSetting.THEME_WALLPAPER &&
                        !TimetableWallpaper.file(this).exists()) {
                        showToast("请先在课程表中选择背景图片")
                        return@setSingleChoiceItems
                    }
                    mViewModel.selectTheme(ThemePreferences.modes[which])
                    // Keep the result on the activity that owns the setting flow.
                    // The host applies the palette when this screen is closed.
                    setResult(Activity.RESULT_OK)
                    dialog.dismiss()
                    recreate()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        mViewModel.initSetting()
    }

    override fun onPause() {
        mViewModel.save()
        super.onPause()
    }
}
