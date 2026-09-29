package cn.ifafu.ifafu.schedule

import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** RFC 5545 UTF-8 escaping and byte-safe 75-octet folding. */
object CalendarExport {
    fun escape(value: String) = value.replace("\\", "\\\\")
        .replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n")
        .replace(";", "\\;").replace(",", "\\,")

    fun fold(line: String): String {
        val result = StringBuilder()
        var bytes = 0
        var offset = 0
        while (offset < line.length) {
            val point = line.codePointAt(offset)
            offset += Character.charCount(point)
            val part = String(Character.toChars(point))
            val size = part.toByteArray(Charsets.UTF_8).size
            if (bytes + size > 75) { result.append("\r\n "); bytes = 1 }
            result.append(part); bytes += size
        }
        return result.toString()
    }

    fun ics(events: List<ScheduleEvent>, name: String = "iFAFU 课表", stamp: Long = System.currentTimeMillis()): String {
        val format = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        fun time(value: Long) = format.format(Date(value))
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//iFAFU//Material Calendar//ZH",
            "CALSCALE:GREGORIAN", "METHOD:PUBLISH", "X-WR-CALNAME:" + escape(name))
        events.firstOrNull()?.let {
            val hex = "#%06X".format(Locale.ROOT, it.color and 0xffffff)
            lines += "X-APPLE-CALENDAR-COLOR:" + hex
            lines += "X-IFAFU-COLOR:" + hex
        }
        events.forEach { event ->
            lines += listOf("BEGIN:VEVENT", "UID:" + event.uid + "@ifafu.local",
                "DTSTAMP:" + time(stamp), "DTSTART:" + time(event.start))
            if (event.end > event.start) lines += "DTEND:" + time(event.end)
            lines += listOf("SUMMARY:" + escape(event.title), "LOCATION:" + escape(event.location),
                "DESCRIPTION:" + escape(event.description), "X-IFAFU-COLOR:#%06X".format(Locale.ROOT, event.color and 0xffffff),
                "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:-PT15M", "DESCRIPTION:" + escape(event.title),
                "END:VALARM", "END:VEVENT")
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n", postfix = "\r\n") { fold(it) }
    }

    fun zip(events: List<ScheduleEvent>, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            events.groupBy { it.subject }.entries.forEachIndexed { index, (_, lessons) ->
                val title = lessons.first().title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
                zip.putNextEntry(ZipEntry((index + 1).toString() + "-" + title + ".ics"))
                zip.write(ics(lessons, "iFAFU · " + lessons.first().title).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("导入说明.txt"))
            zip.write(("每个 ICS 是一门课程，包含全部上课周次、地点、教师及颜色元数据。\n" +
                "Google 日历网页版不会读取 ICS 颜色。请为每门课建立独立日历，导入对应文件并设置日历颜色。\n" +
                "Android 上推荐使用 iFAFU 的“导入本机彩色日历”，由应用直接设置每门课的日历颜色。\n" +
                "再次导入本机日历会更新本学期 iFAFU 创建的事件；文件导入是否去重由目标软件决定。").toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
}
