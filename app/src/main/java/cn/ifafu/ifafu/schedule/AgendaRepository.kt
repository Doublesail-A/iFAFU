package cn.ifafu.ifafu.schedule

import cn.ifafu.ifafu.db.JiaowuDatabase
import cn.ifafu.ifafu.entity.AgendaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgendaRepository @Inject constructor(private val db: JiaowuDatabase) {
    fun account() = db.userDao.getUsingAccount().orEmpty()
    fun observe(account: String) = db.agendaDao.observe(account)
    suspend fun all() = db.agendaDao.all(account())
    suspend fun find(id: String) = db.agendaDao.find(id, account())
    suspend fun save(item: AgendaItem) = withContext(Dispatchers.IO) {
        require(item.account.isNotBlank() && item.account == account()) { "请先登录当前账号" }
        AgendaEvents.validate(item)
        db.agendaDao.save(item)
    }
    suspend fun delete(id: String) = db.agendaDao.delete(id, account())
}

object AgendaEvents {
    val swatches = intArrayOf(0xffc0b2e4.toInt(), 0xffa7c2e1.toInt(), 0xff92d8ce.toInt(),
        0xffa9d4ac.toInt(), 0xffffbd72.toInt(), 0xffedabc2.toInt(), 0xffffb99a.toInt())
    fun validate(item: AgendaItem) {
        require(item.title.isNotBlank()) { "请填写事项名称" }
        LocalDate.parse(item.date)
        require(item.startMinute in 0..1439 && item.endMinute in 1..1440 && item.endMinute > item.startMinute) {
            "结束时间需要晚于开始时间"
        }
    }
    fun event(item: AgendaItem, zone: ZoneId = ZoneId.systemDefault()): ScheduleEvent {
        validate(item)
        val day = LocalDate.parse(item.date).atStartOfDay()
        return ScheduleEvent("agenda-" + item.id, "agenda-" + item.id, item.title, item.location,
            item.notes, day.plusMinutes(item.startMinute.toLong()).atZone(zone).toInstant().toEpochMilli(),
            day.plusMinutes(item.endMinute.toLong()).atZone(zone).toInstant().toEpochMilli(), item.color,
            "agenda", "agenda-" + ScheduleEvents.key(item.account))
    }
}
