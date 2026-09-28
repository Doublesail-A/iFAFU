package cn.ifafu.ifafu.util

import android.app.Activity
import android.os.Build
import android.content.res.Configuration
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.entity.GlobalSetting
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

/** Applies the selected palette before any view is inflated. */
object ThemeManager {

    fun apply(activity: Activity) {
        when (ThemePreferences.getTheme(activity)) {
            GlobalSetting.THEME_COURSE -> applySeedTheme(activity, ThemePreferences.getCourseSeed(activity))
            GlobalSetting.THEME_WALLPAPER -> applySeedTheme(activity, ThemePreferences.getWallpaperSeed(activity))
            GlobalSetting.THEME_GREEN -> activity.setTheme(R.style.AppTheme_Green)
            GlobalSetting.THEME_BLUE -> activity.setTheme(R.style.AppTheme_Blue)
            GlobalSetting.THEME_ROSE -> activity.setTheme(R.style.AppTheme_Rose)
            else -> Unit // AppTheme + DynamicColors handles the system option.
        }
    }

    private fun applySeedTheme(activity: Activity, seed: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            activity.setTheme(R.style.AppTheme_Green)
            return
        }
        DynamicColors.applyToActivityIfAvailable(
            activity,
            DynamicColorsOptions.Builder()
                .setContentBasedSource(seed)
                .build(),
        )
    }

    fun paletteKey(activity: Activity): String {
        val mode = ThemePreferences.getTheme(activity)
        val seed = when (mode) {
            GlobalSetting.THEME_COURSE -> ThemePreferences.getCourseSeed(activity)
            GlobalSetting.THEME_WALLPAPER -> ThemePreferences.getWallpaperSeed(activity)
            else -> 0
        }
        return "$mode:$seed"
    }

    fun isNight(activity: Activity): Boolean {
        return activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }
}
