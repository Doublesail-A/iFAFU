package cn.ifafu.ifafu.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.util.*

@Entity
class GlobalSetting {
    @PrimaryKey
    var account: String = ""
    var theme = THEME_SYSTEM

    @Ignore
    constructor(account: String) {
        this.account = account
    }

    constructor()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val setting = other as GlobalSetting
        return theme == setting.theme &&
                account == setting.account
    }

    override fun hashCode(): Int {
        return Objects.hash(account, theme)
    }

    companion object {
        /** Follow the device's light/dark mode and dynamic color source. */
        const val THEME_SYSTEM = 0
        /** Legado-inspired green tonal palette. */
        const val THEME_GREEN = 1
        /** Legado-inspired blue tonal palette. */
        const val THEME_BLUE = 2
        /** Legado-inspired rose tonal palette. */
        const val THEME_ROSE = 3

        /** Generate the Material 3 palette from the next course's classic timetable color. */
        const val THEME_COURSE = 4
        /** Palette extracted from the custom timetable wallpaper by Google's quantizer. */
        const val THEME_WALLPAPER = 5

        // Kept as migration aliases for data written by the public 1.4.x build.
        @Deprecated("Use THEME_SYSTEM")
        const val THEME_NEW = THEME_SYSTEM
        @Deprecated("Use THEME_GREEN")
        const val THEME_OLD = THEME_GREEN
    }
}
