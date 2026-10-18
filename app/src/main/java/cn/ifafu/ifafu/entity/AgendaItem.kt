package cn.ifafu.ifafu.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "agenda_item", indices = [Index("account")])
data class AgendaItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val account: String,
    val title: String,
    val date: String,
    val startMinute: Int,
    val endMinute: Int,
    val location: String = "",
    val notes: String = "",
    val color: Int = 0xffc0b2e4.toInt(),
)
