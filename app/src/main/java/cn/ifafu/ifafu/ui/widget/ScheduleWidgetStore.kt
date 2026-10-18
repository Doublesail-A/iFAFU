package cn.ifafu.ifafu.ui.widget

import android.content.Context
import cn.ifafu.ifafu.constant.Constants
import cn.ifafu.ifafu.schedule.ScheduleEvent
import cn.ifafu.ifafu.schedule.ScheduleEvents
import com.blankj.utilcode.util.SPUtils
import org.json.JSONArray
import org.json.JSONObject

object ScheduleWidgetStore {
    private data class Cache(val account: String, val json: String, val events: List<ScheduleEvent>)
    @Volatile private var cache: Cache? = null
    private fun prefs(context: Context) = context.getSharedPreferences("desktop_schedule_v1", Context.MODE_PRIVATE)

    /** Called on the repository's IO dispatcher, even when reminders are disabled. */
    fun save(context: Context, events: List<ScheduleEvent>, account: String) {
        val now = System.currentTimeMillis()
        val array = JSONArray()
        events.filter { (it.start > now || cn.ifafu.ifafu.schedule.TodaySchedule.active(it, now)) && it.kind in cn.ifafu.ifafu.schedule.TodaySchedule.kinds }
            .distinctBy { it.uid }.sortedBy { it.start }.forEach {
                array.put(JSONObject().put("id", it.uid).put("title", it.title)
                    .put("location", it.location).put("start", it.start).put("end", it.end)
                    .put("color", it.color).put("kind", it.kind).put("teacher", it.teacher).put("week", it.week))
            }
        prefs(context).edit().putString("account", ScheduleEvents.key(account))
            .putString("events", array.toString()).commit()
        cache = null
        ScheduleWidget.updateAll(context)
    }

    fun clearIfAccountChanged(context: Context, account: String) {
        if (prefs(context).getString("account", "") != ScheduleEvents.key(account)) {
            prefs(context).edit().clear().commit()
            cache = null
            ScheduleWidget.updateAll(context)
        }
    }

    fun load(context: Context): List<ScheduleEvent> = runCatching {
        val account = SPUtils.getInstance(Constants.SP_USER_INFO).getString("account", "")
        if (account.isBlank() || prefs(context).getString("account", "") != ScheduleEvents.key(account))
            return@runCatching emptyList()
        val accountKey = ScheduleEvents.key(account)
        val json = prefs(context).getString("events", "[]")!!
        cache?.takeIf { it.account == accountKey && it.json == json }?.let { return@runCatching it.events }
        val array = JSONArray(json)
        val events = (0 until array.length()).map {
            val obj = array.getJSONObject(it)
            ScheduleEvent(obj.getString("id"), "", obj.getString("title"), obj.getString("location"),
                "", obj.getLong("start"), obj.getLong("end"), obj.getInt("color"), obj.getString("kind"), teacher = obj.optString("teacher"), week = obj.optInt("week"))
        }
        cache = Cache(accountKey, json, events)
        events
    }.getOrDefault(emptyList())
}
