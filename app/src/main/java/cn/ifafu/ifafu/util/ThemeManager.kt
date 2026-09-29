package cn.ifafu.ifafu.util

import android.app.Activity
import android.annotation.SuppressLint
import android.os.Build
import android.content.res.Configuration
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.entity.GlobalSetting
import com.google.android.material.color.ColorResourcesOverride
import com.google.android.material.color.MaterialColorUtilitiesHelper
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot

/** Applies the selected palette before any view is inflated. */
object ThemeManager {

    fun apply(activity: Activity) {
        when (ThemePreferences.getTheme(activity)) {
            GlobalSetting.THEME_COURSE -> applySeedTheme(activity, ThemePreferences.getCourseSeed(activity))
            GlobalSetting.THEME_WALLPAPER -> applySeedTheme(activity, ThemePreferences.getWallpaperSeed(activity))
            else -> Unit // AppTheme + DynamicColors handles the system option.
        }
    }

    @SuppressLint("RestrictedApi") // Pinned Material 1.11 official palette and resource pipeline.
    private fun applySeedTheme(activity: Activity, seed: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            activity.setTheme(R.style.AppTheme)
            return
        }
        // Android's tonal scheme keeps containers at light/dark role tones rather
        // than copying the seed's mid-tone into every card (SchemeContent).
        val scheme = SchemeTonalSpot(Hct.fromInt(seed), isNight(activity), 0.0)
        val applied = ColorResourcesOverride.getInstance()?.applyIfPossible(activity,
            MaterialColorUtilitiesHelper.createColorResourcesIdsToColorValues(scheme)) == true
        if (!applied) activity.setTheme(R.style.AppTheme)
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
