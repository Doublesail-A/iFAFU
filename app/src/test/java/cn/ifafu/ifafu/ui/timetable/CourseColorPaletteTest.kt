package cn.ifafu.ifafu.ui.timetable

import org.junit.Assert.*
import org.junit.Test
import com.google.android.material.color.utilities.Hct
import cn.ifafu.ifafu.entity.NewCourse
import cn.ifafu.ifafu.entity.SyllabusSetting
import cn.ifafu.ifafu.util.CourseSchedule
import java.util.Calendar

class CourseColorPaletteTest {
    @Test fun rescheduledCourseRetainsIdentity() {
        listOf("[调课]高等数学D", "【调课】 高等数学D", "（补课）高等数学D")
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

    @Test fun previewsCannotStealAnExistingSubjectsColor() {
        val saved = CourseColorPalette.allocate(listOf("高等数学D", "植物学实验A"), emptyMap())
        val preview = CourseColorPalette.allocate(listOf("英语阅读1"), saved)
        assertFalse(preview.values.first() in saved.values)
        val merged = saved + preview
        assertEquals(merged, CourseColorPalette.allocate(merged.keys, merged))
    }

    @Test fun equalPerceptualToneKeepsLabelsConsistentAcrossHues() {
        val names = (1..12).map { "课程$it" }
        val colors = CourseColors(CourseColorPalette.allocate(names, emptyMap()))
        names.forEach {
            assertEquals(85.0, Hct.fromInt(colors.displayColorFor(it, false)).tone, 0.5)
            assertEquals(34.0, Hct.fromInt(colors.displayColorFor(it, true)).tone, 0.5)
            assertEquals(colors.seedFor(it), colors.seedFor("[调课]$it"))
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
