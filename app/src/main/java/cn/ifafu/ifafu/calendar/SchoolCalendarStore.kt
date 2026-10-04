package cn.ifafu.ifafu.calendar

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@androidx.annotation.Keep
data class CalendarData(val version: Int, val calendars: List<AcademicCalendar>)

/** Small date records, generated in GitHub Actions from public school documents. */
@Singleton
class SchoolCalendarStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    private val mutex = Mutex()
    private val file get() = File(context.filesDir, "school-calendar-data.json")
    private var loaded: CalendarData? = null
    private var attemptedAt = 0L
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes = _changes.asSharedFlow()

    suspend fun resolve(school: String, year: String, term: String, force: Boolean = false): AcademicCalendar? = withContext(Dispatchers.IO) {
        if (school != "FAFU") return@withContext null
        mutex.withLock {
            if (loaded == null) loaded = runCatching {
                decode(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
            }.getOrElse { decode(context.assets.open("school-calendars.json").bufferedReader().use { it.readText() }) }
            val now = System.currentTimeMillis()
            if (force || (now - file.lastModified() !in 0 until DAY && now - attemptedAt !in 0 until 60_000L)) {
                attemptedAt = now
                for (url in URLS) {
                    try {
                        val text = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                            check(response.isSuccessful)
                            response.body?.string() ?: error("Empty calendar data")
                        }
                        val updated = decode(text)
                        val atomic = AtomicFile(file)
                        val stream = atomic.startWrite()
                        try { stream.write(text.toByteArray()); atomic.finishWrite(stream) }
                        catch (e: Exception) { atomic.failWrite(stream); throw e }
                        val changed = updated != loaded
                        loaded = updated
                        if (changed) _changes.tryEmit(Unit)
                        break
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { Timber.w("Calendar data refresh unavailable: %s", e.message) }
                }
            }
            loaded?.calendars?.firstOrNull { it.school == school && it.year == year && it.term == term }
        }
    }

    private fun decode(text: String): CalendarData {
        require(text.length in 1..1_000_000)
        val data = gson.fromJson(text, CalendarData::class.java)
        require(data.version == 1 && data.calendars.isNotEmpty())
        require(data.calendars.distinctBy { Triple(it.school, it.year, it.term) }.size == data.calendars.size)
        for (c in data.calendars) {
            require(c.year.matches(Regex("\\d{4}-\\d{4}")) && c.term in listOf("1", "2"))
            LocalDate.parse(c.firstWeek)
            for (h in c.holidays) {
                require(h.days in 0..100)
                LocalDate.parse(h.from)
                h.changes.forEach { (from, to) -> LocalDate.parse(from); LocalDate.parse(to) }
            }
        }
        return data
    }

    companion object {
        private const val DAY = 86_400_000L
        private val URLS = listOf(
            "https://cdn.jsdelivr.net/gh/Doublesail-A/iFAFU@codex/material-you-redesign/data/school-calendars.json",
            "https://raw.githubusercontent.com/Doublesail-A/iFAFU/codex/material-you-redesign/data/school-calendars.json",
        )
    }
}
