package cn.ifafu.ifafu.ui.timetable

import android.content.Context
import android.content.res.Configuration
import android.view.View
import cn.ifafu.ifafu.util.ColorPool
import com.google.android.material.color.utilities.ColorUtils
import com.google.android.material.color.utilities.Contrast
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot
import java.util.Locale

/** Persistent subject identities with classic Material hue families and native tonal palettes. */
object CourseColorPalette {
    private val annotation = Regex(
        "^(?:[\\[【（(「『]\\s*(?:调课|补课|停课|重修)\\s*[\\]】）)」』]\\s*)+"
    )
    // Classic Material 300 references retain distinct hue families.
    // Google's SchemeTonalSpot supplies the restrained chroma; no custom color
    // extraction, RGB blending or HCT hue/chroma formula is used.
    // Tone 70 keeps course blocks stronger than pale tone-90 theme containers.
    private val references = intArrayOf(
        0xff64b5f6.toInt(), // Blue
        0xffffb74d.toInt(), // Orange
        0xffba68c8.toInt(), // Purple
        0xff4db6ac.toInt(), // Teal
        0xffa1887f.toInt(), // Brown
        0xff7986cb.toInt(), // Indigo
        0xff81c784.toInt(), // Green
        0xffff8a65.toInt(), // Deep Orange
        0xff4dd0e1.toInt(), // Cyan
        0xff4fc3f7.toInt(), // Light Blue
        0xffffd54f.toInt(), // Amber
        0xffdce775.toInt(), // Lime
        0xffe57373.toInt(), // Red
        0xff9575cd.toInt(), // Deep Purple
        0xfff06292.toInt(), // Pink
        0xffaed581.toInt(), // Light Green
        0xff90a4ae.toInt(), // Blue Grey
        0xfffff176.toInt(), // Yellow
        0xffe0e0e0.toInt(), // Grey
    )
    private val palettes = references.mapIndexed { index, reference ->
        val scheme = SchemeTonalSpot(Hct.fromInt(reference), false, 0.0)
        // Achromatic references use native neutral roles instead of inventing
        // a hue from a grey seed, which can duplicate a cyan course color.
        when (index) {
            16 -> scheme.secondaryPalette // Blue Grey
            18 -> scheme.neutralPalette // Grey
            else -> scheme.primaryPalette
        }
    }
    private val lightTones = intArrayOf(70, 75, 65, 80, 60)
    private val darkTones = intArrayOf(40, 45, 35, 50, 30)

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

    internal fun color(index: Int, dark: Boolean): Int {
        val family = index % palettes.size
        val round = (index / palettes.size) % lightTones.size
        return palettes[family].tone(if (dark) darkTones[round] else lightTones[round])
    }

    internal fun foreground(background: Int): Int {
        val tone = ColorUtils.lstarFromArgb(background)
        // MCU's WCAG contrast calculation chooses legible ink without tinting
        // or blending the course background into a pale theme container.
        return if (Contrast.ratioOfTones(tone, 100.0) >= Contrast.ratioOfTones(tone, 0.0))
            0xffffffff.toInt() else 0xff000000.toInt()
    }
}

class CourseColors internal constructor(private val assignments: Map<String, Int>) {
    private fun index(name: String) = assignments[CourseColorPalette.identity(name)] ?: 0
    fun seedFor(name: String): Int = CourseColorPalette.color(index(name), false)
    fun displayColorFor(name: String, dark: Boolean): Int = CourseColorPalette.color(index(name), dark)
    fun textColorFor(name: String, dark: Boolean): Int = CourseColorPalette.foreground(displayColorFor(name, dark))
}

class MaterialCourseColorPool(view: View, private val colors: CourseColors) : ColorPool {
    private val dark = view.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    override fun getColor(value: Any) = colors.displayColorFor(value.toString(), dark)
    fun getForegroundColor(value: Any) = colors.textColorFor(value.toString(), dark)
}
