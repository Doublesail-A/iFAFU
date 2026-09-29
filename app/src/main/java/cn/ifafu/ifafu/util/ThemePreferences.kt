package cn.ifafu.ifafu.util

import android.content.Context
import cn.ifafu.ifafu.entity.GlobalSetting

/** Small synchronous mirror of the account setting used before an Activity inflates its UI. */
object ThemePreferences {

    private const val PREFS = "material_you_theme"
    private const val KEY_MODE = "mode"
    private const val KEY_COURSE_SEED = "course_seed"
    private const val KEY_WALLPAPER_SEED = "wallpaper_seed"
    private const val KEY_COURSE_DEFAULT_MIGRATED = "course_default_migrated_v2"
    private const val DEFAULT_COURSE_SEED = 0xFF6750A4.toInt()

    val modes = intArrayOf(
        GlobalSetting.THEME_COURSE,
        GlobalSetting.THEME_WALLPAPER,
        GlobalSetting.THEME_SYSTEM
    )

    val labels = arrayOf("下一节课 · Material 动态配色", "所选背景 · Material 动态配色", "系统壁纸 · Android 原生配色")

    fun getTheme(context: Context): Int {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_COURSE_DEFAULT_MIGRATED, false)) {
            preferences.edit()
                .putInt(KEY_MODE, GlobalSetting.THEME_COURSE)
                .putBoolean(KEY_COURSE_DEFAULT_MIGRATED, true)
                .apply()
            return GlobalSetting.THEME_COURSE
        }
        val value = preferences
            .getInt(KEY_MODE, GlobalSetting.THEME_COURSE)
        return if (value in modes) value else GlobalSetting.THEME_SYSTEM
    }

    fun setTheme(context: Context, theme: Int) {
        val value = if (theme in modes) theme else GlobalSetting.THEME_COURSE
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MODE, value)
            .apply()
    }

    fun label(theme: Int): String {
        val index = modes.indexOf(theme)
        return labels.getOrElse(index) { labels.first() }
    }

    fun getCourseSeed(context: Context): Int = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(KEY_COURSE_SEED, DEFAULT_COURSE_SEED)

    fun getWallpaperSeed(context: Context): Int = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(KEY_WALLPAPER_SEED, DEFAULT_COURSE_SEED)

    fun setWallpaperSeed(context: Context, seed: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_WALLPAPER_SEED, seed).apply()
    }

    /** Returns true only when a new seed was persisted. */
    fun setCourseSeed(context: Context, seed: Int): Boolean {
        if (getCourseSeed(context) == seed) return false
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_COURSE_SEED, seed)
            .apply()
        return true
    }
}
