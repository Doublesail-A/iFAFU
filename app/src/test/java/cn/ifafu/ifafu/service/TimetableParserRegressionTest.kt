package cn.ifafu.ifafu.service

import cn.ifafu.ifafu.service.parser.TimetableParser
import org.junit.Assert.*
import org.junit.Test

class TimetableParserRegressionTest {
    private fun html(course: String, changes: String = "") = """
        <table id="Table1"><tr><td>星期</td></tr><tr><td>早晨</td></tr>
        <tr><td>上午</td><td>第1节</td><td>$course</td></tr></table>
        <table id="DBGrid"><tr><th>编号</th><th>课程</th><th>原安排</th><th>新安排</th></tr>$changes</table>
    """.trimIndent()

    @Test fun teachingSystemStopRangeIncludesLastWeek() {
        val courses = TimetableParser().parse(html(
            "数学<br>周一第1,2节{第1-10周}<br>教师<br>A101<font>停1001</font>",
            "<tr><td>停1001</td><td>数学</td><td>周1第1节连续2节{第5-6周}/A101/教师</td><td></td></tr>"))
        assertEquals(setOf(1,2,3,4,7,8,9,10), courses.single().weeks)
    }

    @Test fun teachingSystemMakeupPreservesNameRoomAndTeacher() {
        val courses = TimetableParser().parse(html(
            "数学<br>周一第1,2节{第1-10周}<br>教师<br>A101<font>调10012</font>",
            "<tr><td>调10012</td><td>数学</td><td>周1第1节连续2节{第6周}/A101/教师</td><td>周6第3节连续2节{第6周}/B202/新教师</td></tr>"))
        val regular = courses.single { it.weekday == 1 }
        val moved = courses.single { it.weekday == 6 }
        assertFalse(regular.weeks.contains(6))
        assertEquals(setOf(6), moved.weeks)
        assertEquals("数学", moved.name)
        assertEquals("B202", moved.classroom)
        assertEquals("新教师", moved.teacher)
        assertEquals(3, moved.beginNode)
    }

    @Test fun oddAndEvenAdjustmentsFilterEveryWeek() {
        val odd = TimetableParser.Change.Ting("停001", "数学", "周1第1节连续2节{第1-8周|单周}/A101/教师")
        val even = TimetableParser.Change.Ting("停002", "数学", "周1第1节连续2节{第1-8周|双周}/A101/教师")
        assertEquals(listOf(1,3,5,7), odd.beforeInfo.weeks)
        assertEquals(listOf(2,4,6,8), even.beforeInfo.weeks)
    }

    @Test fun adjacentLessonsMergeOnlyWhenWeekSetsMatch() {
        val courses = TimetableParser().parse(html(
            "数学<br>周一第1,2节{第1-8周}<br>教师<br>A101<br><br>数学<br>周一第3,4节{第1-8周}<br>教师<br>A101"))
        assertEquals(1, courses.size)
        assertEquals(4, courses.single().nodeLength)
    }
}
