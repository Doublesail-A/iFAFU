package cn.ifafu.ifafu.ui.timetable

import android.content.Context
import android.content.res.Configuration
import android.view.View
import cn.ifafu.ifafu.util.ColorPool
import com.google.android.material.color.utilities.Hct
import java.util.Locale

/** One persistent identity per subject; scheduling annotations never change its color. */
object CourseColorPalette {
    private val annotation = Regex(
        "^(?:[\\[【（(「『]\\s*(?:调课|补课|停课|重修)\\s*[\\]】）)」』]\\s*)+"
    )
    // Interleave distant hues, so small course sets have clear visual separation.
    private val hues = doubleArrayOf(247.5, 157.5, 22.5, 292.5, 90.0, 337.5,
        135.0, 202.5, 45.0, 180.0, 0.0, 112.5, 270.0, 67.5, 315.0, 225.0)

    @JvmStatic
    fun identity(name: String): String = name.trim().replace(annotation, "")
        .replace('（', '(').replace('）', ')')
        .replace(Regex("[\\s\\u3000]+"), "").lowercase(Locale.ROOT)

    /** Pure allocation logic, also used by regression tests. No hash-modulo collisions. */
    fun allocate(names: Collection<String>, previous: Map<String, Int>): Map<String, Int> {
        val identities = names.map(::identity).distinct().sorted()
        val assigned = linkedMapOf<String, Int>()
        val used = previous.values.filter { it >= 0 }.toMutableSet()
        val claimed = mutableSetOf<Int>()
        identities.forEach { name ->
            val index = previous[name]
            if (index != null && index >= 0 && claimed.add(index)) assigned[name] = index
        }
        identities.filterNot(assigned::containsKey).forEach { name ->
            val index = generateSequence(0) { it + 1 }.first { it !in used }
            assigned[name] = index
            used.add(index)
        }
        return assigned
    }

    @Synchronized
    fun forCourses(context: Context, names: Collection<String>): CourseColors {
        val preferences = context.applicationContext
            .getSharedPreferences("course_color_identities_v3", Context.MODE_PRIVATE)
        val previous = preferences.all.mapNotNull { (name, value) ->
            (value as? Int)?.let { name to it }
        }.toMap()
        val assigned = allocate(names, previous)
        val editor = preferences.edit()
        assigned.forEach { (name, index) -> editor.putInt(name, index) }
        editor.apply()
        return CourseColors(assigned)
    }

    internal fun hue(index: Int): Double = if (index < hues.size) hues[index]
        else (hues[index % hues.size] + (index / hues.size) * 11.0) % 360.0
}

class CourseColors internal constructor(private val assignments: Map<String, Int>) {
    private fun hue(name: String) = CourseColorPalette.hue(
        assignments[CourseColorPalette.identity(name)] ?: 0)

    // Google's HCT gives every hue the same perceptual tone. Avoid dusty brown/gray swatches.
    fun seedFor(name: String): Int = Hct.from(hue(name), 60.0, 55.0).toInt()

    fun displayColorFor(name: String, dark: Boolean): Int =
        Hct.from(hue(name), if (dark) 38.0 else 42.0, if (dark) 34.0 else 85.0).toInt()
}

class MaterialCourseColorPool(view: View, private val colors: CourseColors) : ColorPool {
    private val dark = view.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    override fun getColor(value: Any) = colors.displayColorFor(value.toString(), dark)
}
