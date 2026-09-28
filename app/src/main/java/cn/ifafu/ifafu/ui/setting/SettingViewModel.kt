package cn.ifafu.ifafu.ui.setting

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cn.ifafu.ifafu.ui.common.BaseViewModel
import cn.ifafu.ifafu.entity.GlobalSetting
import cn.ifafu.ifafu.repository.GlobalSettingRepository
import cn.ifafu.ifafu.ui.view.adapter.syllabus_setting.SettingItem
import cn.ifafu.ifafu.ui.view.adapter.syllabus_setting.TextViewItem
import cn.ifafu.ifafu.util.ThemePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val globalSettingRepository: GlobalSettingRepository,
    @ApplicationContext private val appContext: Context
) : BaseViewModel() {

    val settings by lazy { MutableLiveData<List<SettingItem>>() }
    val needCheckTheme by lazy { MutableLiveData<Boolean>() }
    val showThemePicker by lazy { MutableLiveData<Unit>() }

    private lateinit var setting: GlobalSetting
    private var selectedTheme = GlobalSetting.THEME_SYSTEM

    fun initSetting() {
        viewModelScope.launch {
            try {
                setting = globalSettingRepository.get()
                val savedTheme = ThemePreferences.getTheme(appContext)
                selectedTheme = savedTheme
                setting.theme = selectedTheme
                ThemePreferences.setTheme(appContext, selectedTheme)
                settings.postValue(themeItems())
                needCheckTheme.postValue(false)
            } catch (e: Exception) {
                // TODO
                e.printStackTrace()
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            if (::setting.isInitialized) {
                setting.theme = selectedTheme
                globalSettingRepository.save(setting)
            }
        }
    }

    fun selectTheme(theme: Int) {
        selectedTheme = if (theme in ThemePreferences.modes) {
            theme
        } else {
            GlobalSetting.THEME_COURSE
        }
        if (::setting.isInitialized) {
            setting.theme = selectedTheme
        }
        ThemePreferences.setTheme(appContext, selectedTheme)
        settings.postValue(themeItems())
        needCheckTheme.postValue(true)
    }

    fun selectedThemeIndex(): Int = ThemePreferences.modes.indexOf(selectedTheme).coerceAtLeast(0)

    fun requestThemePicker() {
        showThemePicker.value = Unit
    }

    private fun themeItems(): List<SettingItem> = listOf(
        TextViewItem(
            "主题配色",
            "${ThemePreferences.label(selectedTheme)} · Material 3 色阶",
            click = { showThemePicker.postValue(Unit) },
            longClick = {}
        )
    )
}
