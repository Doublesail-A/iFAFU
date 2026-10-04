package cn.ifafu.ifafu.calendar

import cn.ifafu.ifafu.entity.User
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Parses published arrangements, not an annual list embedded in the application. */
object SchoolCalendarParser {
    private val names = listOf("元旦", "春节", "清明节", "劳动节", "端午节", "中秋节", "国庆节", "寒假", "暑假", "校运会", "运动会")
    private val range = Regex("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日?(?:至|-|—|～|~)(?:(\\d{1,2})月)?(\\d{1,2})日?")
    private val day = Regex("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日")
    private val makeup = Regex("(\\d{1,2})月(\\d{1,2})日上(\\d{1,2})月(\\d{1,2})日的?(?:课课程|课程|课)")

    fun matchesTerm(title: String, year: String, term: String): Boolean {
        val text = title.replace(Regex("\\s+"), "").replace("－", "-").replace("—", "-")
        val number = if (term == "1") "一" else "二"
        return text.contains(year) && Regex("第(?:$term|$number)学期").containsMatchIn(text)
    }

    fun parse(remarks: String, year: String, term: String, source: String = ""): AcademicCalendar {
        require(year.matches(Regex("\\d{4}-\\d{4}")) && term in listOf("1", "2"))
        val startYear = year.substringBefore('-').toInt()
        val endYear = year.substringAfter('-').toInt()
        require(endYear == startYear + 1)
        val text = remarks.replace(Regex("\\s+"), "")
            .replace(Regex("[（(](?:周|星期)[一二三四五六日天][）)]"), "").replace('－', '-').replace('：', ':')
        fun date(month: Int, d: Int, explicitYear: String = ""): LocalDate = LocalDate.of(
            explicitYear.toIntOrNull() ?: if (term == "1" && month >= 8) startYear else endYear, month, d)
        val opening = Regex("(\\d{1,2})月(\\d{1,2})日:?(?:学生|老生|全体学生)(?:正式)?上课")
            .find(text) ?: error("校历没有可核对的第一周")
        val firstClass = date(opening.groupValues[1].toInt(), opening.groupValues[2].toInt())
        val firstWeek = firstClass.minusDays((firstClass.dayOfWeek.value % 7).toLong())
        val closed = linkedMapOf<String, String>()
        val moves = linkedMapOf<String, String>()
        val deferred = linkedSetOf<String>()
        val parsedRanges = mutableListOf<IntRange>()
        fun nameAt(index: Int, end: Int): String? {
            val suffix = text.substring(end, (end + 24).coerceAtMost(text.length)).takeWhile { !it.isDigit() }
            val suffixName = names.firstOrNull { Regex("^[,:]?$it").containsMatchIn(suffix) }
            if (suffixName != null) return suffixName
            val prefix = text.substring((index - 30).coerceAtLeast(0), index)
            val labels = names.joinToString("|")
            return Regex("((?:$labels)(?:[、,](?:$labels))*)(?:时间)?[:]?$")
                .find(prefix)?.groupValues?.get(1)
        }
        fun addClosed(from: LocalDate, until: LocalDate, name: String) {
            require(!until.isBefore(from) && ChronoUnit.DAYS.between(from, until) <= 100) { "假期日期范围无效" }
            var current = from
            while (!current.isAfter(until)) { closed[current.toString()] = name; current = current.plusDays(1) }
        }
        for (m in range.findAll(text)) {
            val name = nameAt(m.range.first, m.range.last + 1) ?: continue
            val g = m.groupValues
            val from = date(g[2].toInt(), g[3].toInt(), g[1])
            val month = g[4].toIntOrNull() ?: g[2].toInt()
            var until = date(month, g[5].toInt(), g[1])
            if (until.isBefore(from) && month < from.monthValue) until = until.plusYears(1)
            addClosed(from, until, name)
            parsedRanges.add(m.range)
        }
        for (m in day.findAll(text)) {
            if (parsedRanges.any { m.range.first in it }) continue
            val name = nameAt(m.range.first, m.range.last + 1) ?: continue
            val suffix = text.substring(m.range.last + 1, (m.range.last + 30).coerceAtMost(text.length))
            // A festival date alone (e.g. 春节/元宵节) is not a published closure.
            if (!suffix.take(16).contains("放假")) continue
            val g = m.groupValues
            val from = date(g[2].toInt(), g[3].toInt(), g[1])
            var begin = from
            var end = from
            if (suffix.contains("与周末连休")) {
                if (from.dayOfWeek.value == 1) begin = from.minusDays(2)
                if (from.dayOfWeek.value == 5) end = from.plusDays(2)
            }
            addClosed(begin, end, name)
        }
        for (m in makeup.findAll(text)) {
            val g = m.groupValues
            val destination = date(g[1].toInt(), g[2].toInt()).toString()
            val original = date(g[3].toInt(), g[4].toInt()).toString()
            require(original !in moves || moves[original] == destination) { "校历调课安排冲突" }
            moves[original] = destination
        }
        require(Regex("的(?:课课程|课程|课)").findAll(text).count() == makeup.findAll(text).count()) { "校历调课表述尚未识别" }
        val holidayNames = names.filter { it !in listOf("寒假", "暑假", "校运会", "运动会") }.joinToString("|")
        for (m in Regex("((?:$holidayNames)(?:[、,](?:$holidayNames))*)放假安排以国务院[^。;]*通知为准").findAll(text)) {
            names.filterTo(deferred) { m.groupValues[1].contains(it) }
        }
        deferred.removeAll(closed.values.flatMap { it.split('、', ',') }.toSet())
        // Reject partially parsed announcements instead of silently retaining
        // ordinary lessons for an unrecognized holiday date expression.
        for (name in names) {
            if (Regex("$name[^。;]{0,60}放假").containsMatchIn(text)) {
                require(name in deferred || closed.values.any { it.contains(name) }) { "校历假期表述尚未识别" }
            }
        }
        require(closed.isNotEmpty() || deferred.isNotEmpty()) { "校历假期安排尚未识别" }
        return AcademicCalendar(User.FAFU, year, term, firstWeek.toString(), closed, moves, deferred, source = source)
    }
}
