package cn.ifafu.ifafu.ui.timetable

import android.content.Context
import android.content.res.Configuration
import android.view.View
import cn.ifafu.ifafu.util.ColorPool
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Score
import com.google.android.material.color.utilities.MaterialDynamicColors
import java.util.Locale

/** One persistent identity per subject; scheduling annotations never change its color. */
object CourseColorPalette {
    private val annotation = Regex(
        "^(?:[\\[【（(「『]\\s*(?:调课|补课|停课|重修)\\s*[\\]】）)」』]\\s*)+"
    )
    // Material reference color families are inputs, never hand-tuned UI colors.
    // Google MCU chooses chroma, light/dark tone and the matching label role.
    private val references = intArrayOf(0xff6750a4.toInt(), 0xff009688.toInt(),
        0xffe91e63.toInt(), 0xff3f51b5.toInt(), 0xffff9800.toInt(),
        0xff4caf50.toInt(), 0xff9c27b0.toInt(), 0xff03a9f4.toInt(),
        0xffff5722.toInt(), 0xffcddc39.toInt(), 0xff673ab7.toInt(),
        0xff00bcd4.toInt(), 0xfff44336.toInt(), 0xff8bc34a.toInt(),
        0xff2196f3.toInt(), 0xffffc107.toInt(), 0xff795548.toInt(),
        0xff607d8b.toInt(), 0xffffeb3b.toInt())

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

    // Official Score selects separated source hues; merely different RGB values
    // are insufficient when pale containers look alike. No custom hue-distance rule.
    private val seeds: List<Int> by lazy {
        val roles = MaterialDynamicColors()
        val candidates = linkedMapOf<Int, Int>()
        references.forEach { reference ->
            val hct = Hct.fromInt(reference)
            listOf(SchemeTonalSpot(hct, false, 0.0), SchemeExpressive(hct, false, 0.0),
                SchemeVibrant(hct, false, 0.0)).forEach { scheme ->
                listOf(roles.primary(), roles.secondary(), roles.tertiary()).forEach {
                    candidates[it.getArgb(scheme)] = 1
                }
            }
        }
        Score.score(candidates, 16, 0xff6750a4.toInt(), true)
    }
    internal fun seed(index: Int): Int = seeds[index % seeds.size]
    internal fun family(index: Int): Int = index / seeds.size
}

class CourseColors internal constructor(private val assignments: Map<String, Int>) {
    private data class Token(val light: Int, val dark: Int, val lightText: Int, val darkText: Int, val seed: Int)
    // Generate each persistent slot in order, including slots absent from this view.
    // If MCU quantizes two similar source hues into one color, try another official
    // role/scheme. This avoids collisions without inventing HCT hue/chroma/tone math.
    private val tokens: List<Token> = synchronized(tokenCache) {
        tokenCache.getOrPut(assignments.values.maxOrNull() ?: 0) { buildList {
        val usedLight = mutableSetOf<Int>()
        val usedDark = mutableSetOf<Int>()
        val usedSeeds = mutableSetOf<Int>()
        val roles = MaterialDynamicColors()
        for (index in 0..(assignments.values.maxOrNull() ?: 0)) {
            val source = Hct.fromInt(CourseColorPalette.seed(index))
            var selected: Token? = null
            for (variant in 0..2) {
                fun scheme(dark: Boolean): DynamicScheme = when (variant) {
                    1 -> SchemeExpressive(source, dark, 0.0)
                    2 -> SchemeVibrant(source, dark, 0.0)
                    else -> SchemeTonalSpot(source, dark, 0.0)
                }
                for (offset in 0..2) {
                    val family = (CourseColorPalette.family(index) + offset) % 3
                    val background = when (family) { 1 -> roles.tertiaryContainer(); 2 -> roles.secondaryContainer(); else -> roles.primaryContainer() }
                    val foreground = when (family) { 1 -> roles.onTertiaryContainer(); 2 -> roles.onSecondaryContainer(); else -> roles.onPrimaryContainer() }
                    val accent = when (family) { 1 -> roles.tertiary(); 2 -> roles.secondary(); else -> roles.primary() }
                    val light = scheme(false); val dark = scheme(true)
                    val candidate = Token(background.getArgb(light), background.getArgb(dark),
                        foreground.getArgb(light), foreground.getArgb(dark), accent.getArgb(light))
                    if (candidate.light !in usedLight && candidate.dark !in usedDark && candidate.seed !in usedSeeds) {
                        selected = candidate; break
                    }
                }
                if (selected != null) break
            }
            // Finite reference palette; unusually large histories reuse an official
            // family. A normal semester has far fewer slots than this palette.
            val token = selected ?: Token(roles.primaryContainer().getArgb(SchemeTonalSpot(source, false, 0.0)),
                roles.primaryContainer().getArgb(SchemeTonalSpot(source, true, 0.0)),
                roles.onPrimaryContainer().getArgb(SchemeTonalSpot(source, false, 0.0)),
                roles.onPrimaryContainer().getArgb(SchemeTonalSpot(source, true, 0.0)),
                roles.primary().getArgb(SchemeTonalSpot(source, false, 0.0)))
            add(token); usedLight.add(token.light); usedDark.add(token.dark); usedSeeds.add(token.seed)
        }
    }
        }
    }
    private companion object { val tokenCache = mutableMapOf<Int, List<Token>>() }
    private fun token(name: String) = tokens[assignments[CourseColorPalette.identity(name)] ?: 0]
    fun seedFor(name: String): Int = token(name).seed
    fun displayColorFor(name: String, dark: Boolean): Int = token(name).let { if (dark) it.dark else it.light }
    fun textColorFor(name: String, dark: Boolean): Int = token(name).let { if (dark) it.darkText else it.lightText }
}

class MaterialCourseColorPool(view: View, private val colors: CourseColors) : ColorPool {
    private val dark = view.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    override fun getColor(value: Any) = colors.displayColorFor(value.toString(), dark)
    fun getForegroundColor(value: Any) = colors.textColorFor(value.toString(), dark)
}
