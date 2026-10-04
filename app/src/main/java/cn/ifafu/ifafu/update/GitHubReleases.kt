package cn.ifafu.ifafu.update

import com.google.gson.JsonParser

data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion) = compareValuesBy(this, other,
        AppVersion::major, AppVersion::minor, AppVersion::patch)
    override fun toString() = listOf(major, minor, patch).joinToString(".")

    companion object {
        fun parse(value: String): AppVersion? {
            val parts = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(value)?.groupValues ?: return null
            val numbers = parts.drop(1).map { it.toIntOrNull() ?: return null }
            return AppVersion(numbers[0], numbers[1], numbers[2])
        }
    }
}

data class ProjectRelease(val version: AppVersion, val pageUrl: String, val downloadUrl: String?)

object GitHubReleases {
    const val PROJECT = "https://github.com/Doublesail-A/iFAFU"
    const val RELEASES = PROJECT + "/releases/latest"
    const val API = "https://api.github.com/repos/Doublesail-A/iFAFU/releases/latest"

    fun parse(json: String, debug: Boolean): ProjectRelease? {
        val data = JsonParser().parse(json).asJsonObject
        if (data.get("draft")?.asBoolean == true || data.get("prerelease")?.asBoolean == true) return null
        // Retired 1.4.10-md3.* preview tags do not belong to the independent version line.
        val version = AppVersion.parse(data.get("tag_name")?.asString.orEmpty()) ?: return null
        val page = data.get("html_url")?.asString.orEmpty()
        require(page.startsWith(PROJECT + "/releases/tag/")) { "Unexpected release repository" }
        val channel = if (debug) "debug" else "release"
        val apk = data.getAsJsonArray("assets")?.firstOrNull {
            val asset = it.asJsonObject
            val name = asset.get("name")?.asString.orEmpty()
            name.endsWith("-" + channel + ".apk") || name.endsWith("_" + channel + ".apk")
        }?.asJsonObject?.get("browser_download_url")?.asString
            ?.takeIf { it.startsWith(PROJECT + "/releases/download/") }
        return ProjectRelease(version, page, apk)
    }
}
