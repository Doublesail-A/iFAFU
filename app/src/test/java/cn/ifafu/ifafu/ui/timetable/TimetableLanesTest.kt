package cn.ifafu.ifafu.ui.timetable

import org.junit.Assert.*
import org.junit.Test

class TimetableLanesTest {
    private fun block(id: Int, start: Int, end: Int, agenda: Boolean = true, day: Int = 2) = TimetableLanes.Block(id, day, start, end, agenda)
    @Test fun overlappingAgendaUsesNarrowLaneAndLeavesCourseReadable() {
        val result = TimetableLanes.arrange(listOf(block(1,480,575,false),block(2,500,540))).associateBy { it.id }
        assertTrue(result.getValue(1).width > .65f)
        assertEquals(1f, result.values.sumOf { it.width.toDouble() }.toFloat(), .001f)
        assertTrue(result.getValue(1).left + result.getValue(1).width <= result.getValue(2).left + .001f)
    }
    @Test fun simultaneousEventsStaySeparateButAdjacentEventsReuseSpace() {
        val input = listOf(block(1,480,600,false),block(2,500,540),block(3,520,560),block(4,600,630),block(5,500,540,day=3))
        val result = TimetableLanes.arrange(input).associateBy { it.id }
        input.forEach { a -> input.filter { it.id != a.id && it.day == a.day && it.start < a.end && a.start < it.end }.forEach { b ->
            val x = result.getValue(a.id); val y = result.getValue(b.id)
            assertTrue(x.left + x.width <= y.left + .001f || y.left + y.width <= x.left + .001f)
        } }
        assertEquals(1f, result.getValue(4).width, .001f)
        assertEquals(1f, result.getValue(5).width, .001f)
    }
}
