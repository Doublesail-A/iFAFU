package cn.ifafu.ifafu.ui.widget

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.pow
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.ifafu.ifafu.R
import cn.ifafu.ifafu.constant.Constants
import cn.ifafu.ifafu.entity.GlobalSetting
import cn.ifafu.ifafu.schedule.ScheduleEvent
import cn.ifafu.ifafu.util.ThemePreferences
import com.blankj.utilcode.util.SPUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    // Independent WCAG measurement; do not add production keep rules for a test-only API.
    private fun contrast(first: Int, second: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val value = ((color ushr shift) and 255) / 255.0
                return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val a = luminance(first); val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
    private fun fixture(kind: String = "course", start: Long = System.currentTimeMillis() + 3600000) =
        ScheduleEvent("widget-test-" + kind, "", "高等数学D（课程信息验证）", "创104", "", start, start + 5700000,
            0xff92d8ce.toInt(), kind)

    @Test fun threeSizesApplyWithReadableLightAndDarkColorsAndNoClippedLocation() {
        val original = ThemePreferences.getTheme(context)
        try {
            ThemePreferences.setTheme(context, GlobalSetting.THEME_COURSE)
            for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val configuration = Configuration(context.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night; fontScale = 1f
                }
                val themed = context.createConfigurationContext(configuration)
                val now = java.util.Calendar.getInstance().apply { clear(); set(2026, 9, 9, 8, 0) }.timeInMillis
                val course = fixture(start = now + 12 * 60000).copy(teacher = "林老师", week = 6)
                val exam = fixture("exam", now + 141 * 60000).copy(title = "普通化学", location = "考场201")
                val events = listOf(course, exam)
                val colors = ScheduleWidget.palette(themed, events, now)
                fun paletteFor(mode: Int) = ScheduleWidget.palette(context.createConfigurationContext(
                    Configuration(configuration).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode }
                ), events, now)
                val day = paletteFor(Configuration.UI_MODE_NIGHT_NO)
                val dark = paletteFor(Configuration.UI_MODE_NIGHT_YES)
                assertTrue(contrast(colors.ink, colors.surface) >= 4.5)
                assertTrue(contrast(colors.secondary, colors.surface) >= 4.5)
                for ((layout, size) in listOf(R.layout.timetable_widget to (280 to 60),
                    R.layout.widget_schedule_compact to (110 to 140), R.layout.widget_schedule_compact to (150 to 190),
                    R.layout.widget_schedule_large to (280 to 120), R.layout.widget_schedule_large to (280 to 150), R.layout.widget_schedule_large to (280 to 190))) {
                    instrumentation.runOnMainSync {
                        val view = ScheduleWidget.render(themed, layout, events, day, now, dark, roomy = size.second >= if (layout == R.layout.widget_schedule_large) 150 else 180)
                            .apply(themed, FrameLayout(themed))
                        val density = themed.resources.displayMetrics.density
                        val width = (size.first * density).toInt(); val height = (size.second * density).toInt()
                        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                        view.layout(0, 0, width, height)
                        // Complete TextView pre-draw scrolling, as a launcher does before presenting its frame.
                        view.viewTreeObserver.dispatchOnPreDraw()
                        assertEquals(course.title, view.findViewById<TextView>(R.id.widget_title).text.toString())
                        val ids = (if (layout == R.layout.timetable_widget) listOf(R.id.widget_location, R.id.widget_countdown)
                            else listOf(R.id.widget_location, R.id.widget_countdown, R.id.widget_location_2, R.id.widget_countdown_2)) +
                            if (layout == R.layout.timetable_widget) listOf(R.id.widget_time) else listOf(R.id.widget_time, R.id.widget_time_2)
                        for (id in ids) {
                            val text = view.findViewById<TextView>(id)
                            assertTrue("Required text must have a complete visible line: " + id + " layout=" + layout + " size=" + size + " actual=" + text.height + " metrics=" + text.paint.fontMetrics + " siblingHeights=" + listOf(R.id.widget_title, R.id.widget_location, R.id.widget_time).map { id -> view.findViewById<TextView>(id).let { it.height.toString() + "/" + it.lineHeight + "/" + it.lineSpacingExtra + "/" + it.textSize } },
                                text.height >= (text.paint.fontMetrics.descent - text.paint.fontMetrics.ascent).toInt())
                            val rect = android.graphics.Rect(); text.getDrawingRect(rect)
                            (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(text, rect)
                            assertTrue("Text exceeds widget bounds: " + rect + " height=" + height, rect.bottom <= height)
                            assertEquals("Required text cannot be ellipsized: " + text.text, 0, text.layout.getEllipsisCount(0))
                            for (line in 0 until text.layout.lineCount) assertTrue("Text line must fit: " + text.text,
                                text.layout.getLineWidth(line) <= text.width - text.compoundPaddingLeft - text.compoundPaddingRight + 1)
                        }
                        assertEquals(UpcomingSchedule.countdownText(12, "course", layout != R.layout.widget_schedule_compact), view.findViewById<TextView>(R.id.widget_countdown).text.toString())
                        assertEquals(colors.ink, view.findViewById<TextView>(R.id.widget_title).currentTextColor)
                        if (layout != R.layout.timetable_widget) {
                            assertEquals("考试 · 普通化学", view.findViewById<TextView>(R.id.widget_title_2).text.toString())
                            assertEquals(UpcomingSchedule.countdownText(141, "exam", layout != R.layout.widget_schedule_compact), view.findViewById<TextView>(R.id.widget_countdown_2).text.toString())
                        } else assertEquals(View.GONE, view.findViewById<View>(R.id.widget_row_2).visibility)
                        assertTrue(view.findViewById<TextView>(R.id.widget_location).text.toString().contains("林老师"))
                        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_time).visibility)
                        val countdown = view.findViewById<TextView>(R.id.widget_countdown)
                        assertEquals(android.view.Gravity.CENTER, countdown.gravity)
                        assertEquals("Countdown line count must match its presentation", if (layout == R.layout.widget_schedule_compact) 1 else 2, countdown.layout.lineCount)
                        assertTrue("All countdown lines must be visible: " + countdown.layout.height + "/" + countdown.height, countdown.layout.height <= countdown.height)
                        assertEquals(colors.onErrorContainer, countdown.currentTextColor)
                        assertTrue(contrast(colors.onErrorContainer, colors.errorContainer) >= 4.5)
                        assertTrue(contrast(colors.onContainer, colors.container) >= 4.5)
                        if (layout != R.layout.widget_schedule_compact) {
                            val badgeRect = android.graphics.Rect(); countdown.getDrawingRect(badgeRect)
                            (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(countdown, badgeRect)
                            val row = view.findViewById<View>(R.id.widget_row_1)
                            val rowRect = android.graphics.Rect(); row.getDrawingRect(rowRect)
                            view.offsetDescendantRectToMyCoords(row, rowRect)
                            assertTrue("Badge must be vertically centered in the course card", kotlin.math.abs(rowRect.exactCenterY() - badgeRect.exactCenterY()) <= 1f)
                        }
                        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                        view.draw(android.graphics.Canvas(bitmap))
                        java.io.File(context.getExternalFilesDir(null), "today-widget-" + layout + "-" + size.second + "-" + night + ".png")
                            .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        val glyphRect = android.graphics.Rect(); countdown.getDrawingRect(glyphRect)
                        (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(countdown, glyphRect)
                        val isolatedText = android.graphics.Bitmap.createBitmap(countdown.width, countdown.height, android.graphics.Bitmap.Config.ARGB_8888)
                        countdown.draw(android.graphics.Canvas(isolatedText))
                        java.io.File(context.getExternalFilesDir(null), "countdown-isolated.png").outputStream().use { isolatedText.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        isolatedText.recycle()
                        var glyphPixels = 0
                        for (y in glyphRect.top.coerceAtLeast(0) until glyphRect.bottom.coerceAtMost(height))
                            for (x in glyphRect.left.coerceAtLeast(0) until glyphRect.right.coerceAtMost(width))
                                if (bitmap.getPixel(x, y) == countdown.currentTextColor) glyphPixels++
                        assertTrue("Countdown must paint visible glyphs. rect=" + glyphRect + " baseline=" + countdown.baseline +
                            " padding=" + listOf(countdown.paddingLeft,countdown.paddingTop,countdown.paddingRight,countdown.paddingBottom) +
                            " layoutWidth=" + countdown.layout.width + " lineLeft=" + countdown.layout.getLineLeft(0) + " lineWidth=" + countdown.layout.getLineWidth(0) + " layoutText=" + countdown.layout.text + " scroll=" + countdown.scrollX + "/" + countdown.scrollY + " lineBaseline=" + countdown.layout.getLineBaseline(0) + " paint=" + countdown.paint.color + " color=" + countdown.currentTextColor +
                            " text=" + countdown.text + " pixels=" + glyphPixels, glyphPixels > 5)
                        bitmap.recycle()
                    }
                }
                instrumentation.runOnMainSync {
                    val tomorrow = course.copy(start = now + 86400000)
                    val view = ScheduleWidget.render(themed, R.layout.widget_schedule_compact, listOf(tomorrow), colors, now)
                        .apply(themed, FrameLayout(themed))
                    assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_empty).visibility)
                    assertEquals(View.GONE, view.findViewById<View>(R.id.widget_content).visibility)
                }
            }
        } finally { ThemePreferences.setTheme(context, original) }
    }

    @Test fun prepareColdWidget() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("widgetColdStart") == "true")
        val account = "widget-test-account"
        SPUtils.getInstance(Constants.SP_USER_INFO).put("account", account, true)
        val host = AppWidgetHost(context, 9272)
        val id = host.allocateAppWidgetId()
        context.getSharedPreferences("widget_test", Context.MODE_PRIVATE).edit().putInt("host_id", id).commit()
        assertTrue(AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(id, ComponentName(context, SyllabusWidget::class.java)))
        val now = System.currentTimeMillis()
        ScheduleWidgetStore.save(context, listOf(fixture(start = now + 30000), fixture("exam", now + 1800000)), account)
    }

    @Test fun cleanColdWidget() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("widgetColdCleanup") == "true")
        val id = context.getSharedPreferences("widget_test", Context.MODE_PRIVATE).getInt("host_id", -1)
        if (id >= 0) AppWidgetHost(context, 9272).deleteAppWidgetId(id)
        context.getSharedPreferences("widget_test", Context.MODE_PRIVATE).edit().clear().commit()
        SPUtils.getInstance(Constants.SP_USER_INFO).put("account", "", true)
        ScheduleWidgetStore.clearIfAccountChanged(context, "")
    }

    @Test fun installedProvidersBindAndCacheIsIndependentOfRemindersAndAccounts() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("isolatedWidgetTest") == "true")
        val account = "widget-test-account"
        SPUtils.getInstance(Constants.SP_USER_INFO).put("account", account, true)
        val host = AppWidgetHost(context, 9271)
        val manager = AppWidgetManager.getInstance(context)
        val ids = mutableListOf<Int>()
        try {
            for (provider in listOf(SyllabusWidget::class.java, CompactScheduleWidget::class.java, LargeScheduleWidget::class.java)) {
                val id = host.allocateAppWidgetId(); ids.add(id)
                assertTrue("Test launcher needs appwidget grantbind", manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, provider)))
            }
            val events = listOf(fixture().copy(teacher = "林老师", week = 6), fixture("exam", System.currentTimeMillis() + 1800000))
            ScheduleWidgetStore.save(context, events, account)
            assertEquals("exam", UpcomingSchedule.today(ScheduleWidgetStore.load(context), System.currentTimeMillis()).first().kind)
            assertEquals("林老师", ScheduleWidgetStore.load(context).first { it.kind == "course" }.teacher)
            // Native RemoteViews are pushed to every registered provider, without enabling notifications.
            ScheduleWidget.updateAll(context)
            for (id in ids) {
                val info = manager.getAppWidgetInfo(id)
                assertNotNull(info)
                instrumentation.runOnMainSync {
                    val view = host.createView(context, id, info)
                    assertNotNull(view.findViewById<TextView>(R.id.widget_title))
                    assertTrue(view.findViewById<TextView>(R.id.widget_title).text.toString().startsWith("考试 · "))
                }
            }
            SPUtils.getInstance(Constants.SP_USER_INFO).put("account", "another-account", true)
            assertTrue(ScheduleWidgetStore.load(context).isEmpty())
            ScheduleWidgetStore.clearIfAccountChanged(context, "another-account")
            assertTrue(ScheduleWidgetStore.load(context).isEmpty())
        } finally {
            ids.forEach { host.deleteAppWidgetId(it) }
            SPUtils.getInstance(Constants.SP_USER_INFO).put("account", "", true)
            ScheduleWidgetStore.clearIfAccountChanged(context, "")
        }
    }
}
