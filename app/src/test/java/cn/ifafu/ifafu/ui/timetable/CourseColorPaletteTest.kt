package cn.ifafu.ifafu.ui.timetable

import org.junit.Assert.*
import org.junit.Test
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.MaterialDynamicColors
import cn.ifafu.ifafu.entity.NewCourse
import cn.ifafu.ifafu.entity.SyllabusSetting
import cn.ifafu.ifafu.util.CourseSchedule
import java.util.Calendar

class CourseColorPaletteTest {
    @Test fun globalThemeHasSeparateLightAndDarkContainerTones() {
        val roles = MaterialDynamicColors()
        listOf(0xff8355dd.toInt(), 0xfff6d91f.toInt(), 0xff22aa88.toInt()).forEach { seed ->
            val light = SchemeTonalSpot(Hct.fromInt(seed), false, 0.0)
            val dark = SchemeTonalSpot(Hct.fromInt(seed), true, 0.0)
            assertEquals(90.0, Hct.fromInt(roles.primaryContainer().getArgb(light)).tone, 0.5)
            assertEquals(30.0, Hct.fromInt(roles.primaryContainer().getArgb(dark)).tone, 0.5)
            assertTrue(Hct.fromInt(roles.onPrimaryContainer().getArgb(light)).tone < 20.0)
            assertTrue(Hct.fromInt(roles.onPrimaryContainer().getArgb(dark)).tone > 80.0)
        }
    }
    @Test fun rescheduledCourseRetainsIdentity() {
        listOf("[调课]高等数学D", "【调课】 高等数学D", "（补课）高等数学D", "「调课」高等数学D")
            .forEach { assertEquals(CourseColorPalette.identity("高等数学D"), CourseColorPalette.identity(it)) }
        assertNotEquals(CourseColorPalette.identity("植物学A（双语课）"),
            CourseColorPalette.identity("植物学实验A"))
    }

    @Test fun distinctSubjectsNeverShareAnAssignmentAndRefreshIsStable() {
        val names = listOf("高等数学D", "植物学实验A", "[调课]高等数学D", "植物学A（双语课）", "英语阅读1")
        val assigned = CourseColorPalette.allocate(names, emptyMap())
        assertEquals(4, assigned.size)
        assertEquals(4, assigned.values.toSet().size)
        assertEquals(assigned, CourseColorPalette.allocate(names.reversed(), assigned))
        val expanded = CourseColorPalette.allocate(names + "体育1", assigned)
        assigned.forEach { (name, index) -> assertEquals(index, expanded[name]) }
    }

    @Test fun crowdedTermsAndPreviouslyCollidingAssignmentsAreRepaired() {
        val names = (1..30).map { "课程$it" }
        val assigned = CourseColorPalette.allocate(names, names.associateWith { 0 })
        assertEquals(names.size, assigned.values.toSet().size)
        val colors = CourseColors(assigned)
        assertEquals(names.size, names.map { colors.displayColorFor(it, false) }.toSet().size)
        assertEquals(names.size, names.map { colors.displayColorFor(it, true) }.toSet().size)
    }

    @Test fun mathAndLabRemainVisuallySeparatedInTheExistingTerm() {
        // Regression: the old 12-color overflow gave indices 14 and 8 almost
        // identical peach backgrounds, although the integer colors differed.
        val colors = CourseColors(mapOf(CourseColorPalette.identity("高等数学D") to 14,
            CourseColorPalette.identity("植物学实验A") to 8))
        listOf(false, true).forEach { dark ->
            val math = Hct.fromInt(colors.displayColorFor("高等数学D", dark))
            val lab = Hct.fromInt(colors.displayColorFor("植物学实验A", dark))
            val distance = kotlin.math.abs(math.hue - lab.hue)
            assertTrue(minOf(distance, 360.0 - distance) > 60.0)
        }
    }

    @Test fun previewsCannotStealAnExistingSubjectsColor() {
        val saved = CourseColorPalette.allocate(listOf("高等数学D", "植物学实验A"), emptyMap())
        val preview = CourseColorPalette.allocate(listOf("英语阅读1"), saved)
        assertFalse(preview.values.first() in saved.values)
        val merged = saved + preview
        assertEquals(merged, CourseColorPalette.allocate(merged.keys, merged))
    }

    @Test fun previewSwatchesStayExactWithAccessibleDarkTones() {
        val names = (0 until 95).map { "课程" + it }
        val colors = CourseColors(names.associate { CourseColorPalette.identity(it) to names.indexOf(it) })
        listOf(false, true).forEach { dark ->
            assertEquals("Collisions: " + names.groupBy { colors.displayColorFor(it, dark) }.filterValues { it.size > 1 }.values, 95, names.map { colors.displayColorFor(it, dark) }.toSet().size)
            names.forEach {
                val background = Hct.fromInt(colors.displayColorFor(it, dark))
                val text = Hct.fromInt(colors.textColorFor(it, dark))
                assertTrue(com.google.android.material.color.utilities.Contrast.ratioOfTones(background.tone, text.tone) >= 4.5)
                assertEquals(colors.displayColorFor(it, dark), colors.displayColorFor("[调课]" + it, dark))
            }
        }
        assertEquals(0xfff0bfce.toInt(), colors.displayColorFor("课程0", false))
        assertEquals(40.0, Hct.fromInt(colors.displayColorFor("课程0", true)).tone, 0.5)
        // The softer visible course color also seeds the app theme and calendars.
        assertEquals(colors.displayColorFor("课程0", false), colors.seedFor("课程0"))
        mapOf(1 to 0xffffbd72.toInt(), 6 to 0xffa7c2e1.toInt(), 7 to 0xffc0b2e4.toInt(),
            10 to 0xff92d8ce.toInt(), 12 to 0xffffb99a.toInt(), 13 to 0xffedabc2.toInt(),
            14 to 0xffddb2a3.toInt()).forEach { (index, swatch) ->
            assertEquals(swatch, colors.displayColorFor("课程" + index, false))
        }
    }

    @Test fun themeTracksTheNextLessonAcrossDaysAndWeeks() {
        fun date(day: Int, hour: Int = 0, minute: Int = 0) = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.SEPTEMBER, day, hour, minute)
        }.time
        val setting = SyllabusSetting().apply {
            beginTime = listOf(0, 800, 850)
            nodeLength = 45
        }
        val math = NewCourse(name = "高等数学D", weekday = Calendar.MONDAY,
            beginNode = 1, nodeLength = 2, weeks = sortedSetOf(1, 2))
        val lab = NewCourse(name = "植物学实验A", weekday = Calendar.TUESDAY,
            beginNode = 1, nodeLength = 2, weeks = sortedSetOf(1))
        val courses = listOf(math, lab)
        assertEquals(math, CourseSchedule.nextCourse(courses, date(6), setting, date(7, 7, 45).time))
        assertEquals(lab, CourseSchedule.nextCourse(courses, date(6), setting, date(7, 8, 15).time))
        assertEquals(lab, CourseSchedule.nextCourse(courses, date(6), setting, date(7, 9, 35).time))
        assertEquals(math, CourseSchedule.nextCourse(courses, date(6), setting, date(10).time))
        assertNull(CourseSchedule.nextCourse(courses, date(6), setting, date(14, 10).time))
    }
}
