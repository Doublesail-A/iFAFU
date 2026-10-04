package cn.ifafu.ifafu.calendar

import android.content.Context
import android.util.AtomicFile
import androidx.annotation.Keep
import cn.ifafu.ifafu.entity.User
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Keep
data class NationalDay(val name: String, val date: String, val isOffDay: Boolean)
@Keep
data class NationalHolidays(val year: Int, val papers: List<String>, val days: List<NationalDay>)

data class CalendarState(val calendar: AcademicCalendar?, val notice: String? = null) {
    val verified: Boolean get() = calendar != null
}

@Singleton
class SchoolCalendarStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val gson = Gson()
    // Public calendar requests never carry teaching-system credentials.
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
    private val mutex = Mutex()
    private val states = HashMap<String, CalendarState>()
    private val attempts = HashMap<String, Long>()
    private var catalog: Map<String, String>? = null
    private var catalogAt = 0L
    private val folder get() = File(context.filesDir, "school-calendars-v1").apply { mkdirs() }
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes = _changes.asSharedFlow()

    suspend fun resolve(school: String, year: String, term: String, force: Boolean = false): CalendarState = withContext(Dispatchers.IO) {
        require(year.matches(Regex("\\d{4}-\\d{4}")) && term in listOf("1", "2"))
        mutex.withLock {
            val key = "$school-$year-$term"
            val now = System.currentTimeMillis()
            val cached = states[key]?.calendar ?: readCalendar(key)
            if (!force && cached != null && now - cached.fetchedAt in 0 until DAY) {
                return@withLock states[key]?.takeIf { it.calendar == cached }
                    ?: CalendarState(cached, cached.notice).also { states[key] = it }
            }
            if (!force && now - (attempts[key] ?: 0L) in 0 until 60_000L) {
                return@withLock states[key] ?: CalendarState(cached, "校历暂未同步，请联网刷新；课程尚未核对假期")
            }
            attempts[key] = now
            try {
                require(school == User.FAFU) { "当前学校尚无可同步的官方校历" }
                val article = findArticle(year, term, force)
                // Older list entries link directly to an attachment.
                requireSchoolUrl(article)
                val bytes = fetch(article)
                val attachmentPage = article.substringBefore('?').matches(Regex("(?i).*\\.(pdf|docx|xlsx|xls)$"))
                val remarks = if (attachmentPage) CalendarDocuments.text(context, bytes, article) else {
                    val document = Jsoup.parse(String(bytes, Charsets.UTF_8), article)
                    require(SchoolCalendarParser.matchesTerm(document.title(), year, term)) { "校历学期不匹配" }
                    val content = document.select(".wp_articlecontent")
                    val attachment = content.select("[pdfsrc]").firstOrNull()?.absUrl("pdfsrc")
                        ?: content.select("a[href]").map { it.absUrl("href") }.firstOrNull {
                            it.substringBefore('?').matches(Regex("(?i).*\\.(pdf|docx|xlsx|xls)$"))
                        }
                    if (attachment != null) {
                        requireSchoolUrl(attachment)
                        CalendarDocuments.text(context, fetch(attachment), attachment)
                    } else content.text()
                }
                val parsed = SchoolCalendarParser.parse(remarks, year, term, article)
                val result = addNationalHolidays(parsed, force).copy(fetchedAt = now)
                saveCalendar(key, result)
                val state = CalendarState(result, result.notice)
                states[key] = state
                if (result.copy(fetchedAt = 0) != cached?.copy(fetchedAt = 0)) _changes.tryEmit(Unit)
                state
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Timber.w("校历同步失败: %s", e.message)
                val notice = if (cached != null) "校历更新暂不可用，继续使用已核对的离线校历"
                    else "校历暂未同步，请联网刷新；课程尚未核对假期"
                CalendarState(cached, listOfNotNull(notice, cached?.notice).joinToString("；")).also { states[key] = it }
            }
        }
    }

    private fun requireSchoolUrl(url: String) {
        val parsed = java.net.URI(url)
        require(parsed.scheme == "https" && parsed.host.endsWith(".fafu.edu.cn")) { "校历来源异常" }
    }

    private fun findArticle(year: String, term: String, force: Boolean): String {
        val now = System.currentTimeMillis()
        if (force || catalog == null || now - catalogAt !in 0 until DAY) {
            val result = linkedMapOf<String, String>()
            val visited = hashSetOf<String>()
            val queue = java.util.ArrayDeque<String>().apply { add("https://jwc.fafu.edu.cn/2011/list.htm") }
            while (queue.isNotEmpty() && visited.size < 20) {
                val url = queue.removeFirst()
                if (!visited.add(url)) continue
                val doc = Jsoup.parse(String(fetch(url), Charsets.UTF_8), url)
                for (a in doc.select("a[href]")) {
                    val title = a.attr("title").ifBlank { a.text() }.replace(Regex("\\s+"), "")
                    val academicYear = Regex("\\d{4}-\\d{4}").find(title)?.value
                    if (title.contains("校历") && academicYear != null) {
                        for (t in listOf("1", "2")) {
                            if (SchoolCalendarParser.matchesTerm(title, academicYear, t)) {
                                val link = a.absUrl("href")
                                requireSchoolUrl(link)
                                result.putIfAbsent("$academicYear-$t", link)
                            }
                        }
                    }
                    val next = a.absUrl("href")
                    if (next.matches(Regex("https://jwc\\.fafu\\.edu\\.cn/2011/list\\d*\\.htm")) && next !in visited) queue.add(next)
                }
            }
            require(result.isNotEmpty()) { "未找到学校校历列表" }
            catalog = result
            catalogAt = now
        }
        return catalog?.get("$year-$term") ?: error("本学期校历尚未发布")
    }

    private fun addNationalHolidays(calendar: AcademicCalendar, force: Boolean): AcademicCalendar {
        if (calendar.deferredHolidays.isEmpty()) return calendar
        val closed = calendar.closed.toMutableMap()
        val uncertain = linkedSetOf<String>()
        val pending = calendar.deferredHolidays.toMutableSet()
        val national = ArrayList<NationalDay>()
        val first = calendar.year.substringBefore('-').toInt()
        val last = calendar.year.substringAfter('-').toInt()
        // Next year's notice can also specify December dates.
        for (y in first..last) {
            try { national.addAll(national(y, force).days) }
            catch (e: Exception) { Timber.w("法定假期数据暂不可用: %s", e.message) }
        }
        val opening = LocalDate.parse(calendar.firstWeek)
        val end = opening.plusWeeks(32)
        for (day in national.distinctBy { it.date }) {
            val date = LocalDate.parse(day.date)
            if (date.isBefore(opening) || date.isAfter(end) || day.name !in calendar.deferredHolidays) continue
            pending.remove(day.name)
            if (day.isOffDay) closed[day.date] = day.name
            else if (day.date !in calendar.moves.values) uncertain.add(day.date)
        }
        val notice = buildList {
            if (pending.isNotEmpty()) add("${pending.joinToString("、")}安排尚未公布，发布后会自动同步")
            if (uncertain.isNotEmpty()) add("部分补课安排待学校确认，提醒和导出暂不包含这些日期")
        }.takeIf { it.isNotEmpty() }?.joinToString("；")
        return calendar.copy(closed = closed, uncertainDates = uncertain, notice = notice)
    }

    private fun national(year: Int, force: Boolean): NationalHolidays {
        val file = File(folder, "national-$year.json")
        if (!force && file.exists() && System.currentTimeMillis() - file.lastModified() in 0 until DAY) {
            return parseNational(file.readText(), year)
        }
        try {
            var text: String? = null
            for (url in listOf("https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/$year.json",
                "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/$year.json")) {
                try { text = String(fetch(url), Charsets.UTF_8); break } catch (_: Exception) { }
            }
            val value = text ?: error("假期公告数据暂不可用")
            val parsed = parseNational(value, year)
            atomicWrite(file, value)
            return parsed
        } catch (e: Exception) {
            if (file.exists()) return parseNational(file.readText(), year)
            throw e
        }
    }

    private fun parseNational(text: String, year: Int): NationalHolidays {
        val value = gson.fromJson(text, NationalHolidays::class.java)
        require(value.year == year && value.days != null && value.papers != null)
        for (d in value.days) LocalDate.parse(d.date)
        require(value.days.isEmpty() || value.papers.any { java.net.URI(it).host.endsWith("gov.cn") })
        return value
    }

    private fun fetch(url: String): ByteArray {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            require(response.isSuccessful) { "校历服务器响应 ${response.code}" }
            val bytes = response.body?.bytes() ?: error("校历数据为空")
            require(bytes.size in 1..12 * 1024 * 1024) { "校历文档大小无效" }
            return bytes
        }
    }

    private fun readCalendar(key: String): AcademicCalendar? = runCatching {
        val value = gson.fromJson(AtomicFile(File(folder, "$key.json")).openRead().bufferedReader().use { it.readText() }, AcademicCalendar::class.java)
        require("${value.school}-${value.year}-${value.term}" == key)
        LocalDate.parse(value.firstWeek)
        value.closed.keys.forEach { LocalDate.parse(it) }
        value.moves.forEach { (from, to) -> LocalDate.parse(from); LocalDate.parse(to) }
        value
    }.getOrNull()

    private fun saveCalendar(key: String, value: AcademicCalendar) = atomicWrite(File(folder, "$key.json"), gson.toJson(value))
    private fun atomicWrite(file: File, text: String) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(text.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }

    companion object { private const val DAY = 86_400_000L }
}
