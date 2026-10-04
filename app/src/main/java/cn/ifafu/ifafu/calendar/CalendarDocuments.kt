package cn.ifafu.ifafu.calendar

import android.content.Context
import android.graphics.RectF
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import com.tom_roush.pdfbox.text.TextPosition
import jxl.Workbook
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Standard readers for the formats actually published by the school. */
object CalendarDocuments {
    fun text(context: Context, bytes: ByteArray, url: String): String = when (url.substringBefore('?').substringAfterLast('.').lowercase()) {
        "pdf" -> pdf(context, bytes)
        "docx" -> word(bytes)
        "xlsx" -> excel(bytes)
        "xls" -> legacyExcel(bytes)
        else -> Jsoup.parse(String(bytes, Charsets.UTF_8)).select(".wp_articlecontent").text()
    }

    private fun pdf(context: Context, bytes: ByteArray): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        return PDDocument.load(bytes).use { document ->
            document.pages.mapIndexed { index, page ->
                val positions = ArrayList<TextPosition>()
                val collector = object : PDFTextStripper() {
                    override fun processTextPosition(text: TextPosition) {
                        positions.add(text)
                        super.processTextPosition(text)
                    }
                }
                collector.startPage = index + 1
                collector.endPage = index + 1
                collector.getText(document)
                // Locate the weekday header to crop the remark column. Reading
                // the whole page interleaves timetable numbers into date ranges.
                val saturday = positions.filter { it.unicode == "六" }.minByOrNull { it.yDirAdj }
                    ?: error("校历表格没有可识别的日期栏")
                val friday = positions.firstOrNull { it.unicode == "五" && kotlin.math.abs(it.yDirAdj - saturday.yDirAdj) < 3 && it.xDirAdj < saturday.xDirAdj }
                    ?: error("校历日期栏位置无法识别")
                val center = saturday.xDirAdj + saturday.widthDirAdj / 2
                val previous = friday.xDirAdj + friday.widthDirAdj / 2
                val left = center + (center - previous) / 2
                PDFTextStripperByArea().apply {
                    sortByPosition = true
                    addRegion("remarks", RectF(left, 0f, page.cropBox.width, page.cropBox.height))
                    extractRegions(page)
                }.getTextForRegion("remarks")
            }.joinToString("\n")
        }
    }

    private fun entries(bytes: ByteArray): Map<String, String> {
        val files = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.endsWith(".xml")) {
                    val data = zip.readBytes()
                    require(data.size <= 8 * 1024 * 1024) { "校历文档过大" }
                    files[entry.name] = String(data, Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        return files
    }

    private fun xml(value: String) = Jsoup.parse(value, "", Parser.xmlParser())
    private fun word(bytes: ByteArray): String {
        val document = xml(entries(bytes)["word/document.xml"] ?: error("校历 Word 文档无内容"))
        val rows = document.getElementsByTag("w:tr")
        require(rows.isNotEmpty()) { "校历 Word 文档没有表格" }
        return rows.mapNotNull { row -> row.children().lastOrNull { it.tagName() == "w:tc" } }
            .joinToString("\n") { cell ->
                cell.getElementsByTag("w:p").joinToString("\n") { p ->
                    p.getElementsByTag("w:t").joinToString("") { it.wholeText() }
                }
            }
    }

    private fun excel(bytes: ByteArray): String {
        val files = entries(bytes)
        val strings = files["xl/sharedStrings.xml"]?.let { xml(it).getElementsByTag("si").map { si ->
            si.getElementsByTag("t").joinToString("") { it.wholeText() }
        } }.orEmpty()
        return files.filterKeys { it.startsWith("xl/worksheets/sheet") }.values.joinToString("\n") { value ->
            val cells = xml(value).getElementsByTag("c")
            fun valueOf(cell: org.jsoup.nodes.Element): String = when (cell.attr("t")) {
                "s" -> strings.getOrNull(cell.getElementsByTag("v").text().toIntOrNull() ?: -1).orEmpty()
                "inlineStr" -> cell.getElementsByTag("t").text()
                else -> cell.getElementsByTag("v").text()
            }
            val header = cells.firstOrNull { valueOf(it).replace(" ", "").contains("备注") }
                ?: error("校历表格没有备注列")
            val column = header.attr("r").takeWhile { it.isLetter() }
            cells.filter { it.attr("r").takeWhile { c -> c.isLetter() } == column }
                .joinToString("\n") { valueOf(it) }
        }
    }

    private fun legacyExcel(bytes: ByteArray): String {
        val book = Workbook.getWorkbook(ByteArrayInputStream(bytes))
        try {
            return book.sheets.joinToString("\n") { sheet ->
                val header = (0 until sheet.rows).flatMap { y -> (0 until sheet.columns).map { x -> sheet.getCell(x, y) } }
                    .firstOrNull { it.contents.replace(" ", "").contains("备注") }
                    ?: error("校历表格没有备注列")
                (header.row until sheet.rows).joinToString("\n") { sheet.getCell(header.column, it).contents }
            }
        } finally { book.close() }
    }
}
