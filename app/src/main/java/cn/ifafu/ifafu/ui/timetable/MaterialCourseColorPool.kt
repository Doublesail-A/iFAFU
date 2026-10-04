package cn.ifafu.ifafu.ui.timetable

import android.content.Context
import android.content.res.Configuration
import android.view.View
import cn.ifafu.ifafu.util.ColorPool
import com.google.android.material.color.utilities.ColorUtils
import com.google.android.material.color.utilities.Contrast
import com.google.android.material.color.utilities.TonalPalette
import java.util.Locale

/** Persistent subject identities with classic Material hue families and native tonal palettes. */
object CourseColorPalette {
    private val annotation = Regex(
        "^(?:[\\[【（(「『]\\s*(?:调课|补课|停课|重修)\\s*[\\]】）)」』]\\s*)+"
    )
    // Restore the supplied preview's actual swatches. Persistent identities still
    // keep a rescheduled lesson together with its original subject.
    private val references = intArrayOf(
        0xfff0bfce.toInt(), // Soft rose
        0xffffbd72.toInt(), // Orange: supplied preview
        0xffcfb8da.toInt(), // Mauve
        0xffa2d6bf.toInt(), // Mint green
        0xffddbfab.toInt(), // Sand
        0xffb7bce6.toInt(), // Indigo
        0xffa7c2e1.toInt(), // Blue: supplied preview
        0xffc0b2e4.toInt(), // Lavender: supplied preview
        0xffa9d4ac.toInt(), // Green; visually separated from math
        0xffb3d6e7.toInt(), // Sky blue
        0xff92d8ce.toInt(), // Teal: supplied preview
        0xffd6ddb0.toInt(), // Lime
        0xffffb99a.toInt(), // Coral: supplied preview
        0xffedabc2.toInt(), // Pink: supplied preview
        0xffddb2a3.toInt(), // Peach: supplied preview
        0xffbfd59e.toInt(), // Light green
        0xffc0c8d2.toInt(), // Blue grey
        0xffedd393.toInt(), // Soft yellow
        0xffc9c5cf.toInt(), // Neutral
    )
    // Only Google MCU generates the dark and overflow tones.
    private val palettes = references.map { TonalPalette.fromInt(it) }
    private val lightTones = intArrayOf(0, 84, 68, 90, 60)
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
        return if (!dark && round == 0) references[family]
        else palettes[family].tone(if (dark) darkTones[round] else lightTones[round])
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
