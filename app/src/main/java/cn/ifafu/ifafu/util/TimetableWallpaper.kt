package cn.ifafu.ifafu.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import cn.ifafu.ifafu.entity.GlobalSetting
import com.google.android.material.color.DynamicColorsOptions
import java.io.File

/** Store the image independently of the picker grant; extract with Google's Celebi + Score. */
object TimetableWallpaper {
    fun file(context: Context) = File(context.getExternalFilesDir("background"), "syllabus.jpg")

    fun import(context: Context, uri: Uri) {
        val destination = file(context)
        destination.parentFile?.mkdirs()
        val candidate = File(destination.parentFile, "syllabus.pending")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取图片" }
                candidate.outputStream().use { input.copyTo(it) }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(candidate.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片格式不受支持" }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
            val bitmap = requireNotNull(BitmapFactory.decodeFile(candidate.path,
                BitmapFactory.Options().apply { inSampleSize = sample }))
            val seed = try {
                requireNotNull(DynamicColorsOptions.Builder()
                    .setContentBasedSource(bitmap).build().contentBasedSeedColor)
            } finally {
                bitmap.recycle()
            }
            require(candidate.renameTo(destination)) { "无法保存背景" }
            ThemePreferences.setWallpaperSeed(context, seed)
            ThemePreferences.setTheme(context, GlobalSetting.THEME_WALLPAPER)
        } finally {
            candidate.delete()
        }
    }

    fun clear(context: Context) {
        file(context).delete()
        if (ThemePreferences.getTheme(context) == GlobalSetting.THEME_WALLPAPER) {
            ThemePreferences.setTheme(context, GlobalSetting.THEME_COURSE)
        }
    }
}
