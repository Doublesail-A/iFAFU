package cn.ifafu.ifafu

import android.app.Application
import cn.ifafu.ifafu.entity.GlobalSetting
import cn.ifafu.ifafu.util.ThemePreferences
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class IFAFU : Application() {
    @javax.inject.Inject lateinit var scheduleRepository: dagger.Lazy<cn.ifafu.ifafu.schedule.ScheduleRepository>

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this) { _, _ ->
            ThemePreferences.getTheme(this) == GlobalSetting.THEME_SYSTEM
        }
        Timber.plant(Timber.DebugTree())
        // Receiver-only launches use the persisted reminder queue without database refresh.
    }
}
