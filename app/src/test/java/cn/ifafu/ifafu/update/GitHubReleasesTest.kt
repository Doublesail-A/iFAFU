package cn.ifafu.ifafu.update

import org.junit.Assert.*
import org.junit.Test

class GitHubReleasesTest {
    private fun release(tag: String = "v1.2.0", debugAsset: Boolean = true,
                        draft: Boolean = false, prerelease: Boolean = false): String {
        val asset = if (debugAsset) "debug" else "release"
        return """{"tag_name": "$tag", "draft": $draft, "prerelease": $prerelease,
            "html_url": "https://github.com/Doublesail-A/iFAFU/releases/tag/$tag",
            "assets": [{"name": "iFAFU-1.2.0-$asset.apk",
                "browser_download_url": "https://github.com/Doublesail-A/iFAFU/releases/download/$tag/iFAFU-1.2.0-$asset.apk"}]}"""
    }

    @Test fun independentVersionsCompareNumerically() {
        assertTrue(AppVersion.parse("1.10.0")!! > AppVersion.parse("1.9.9")!!)
        assertTrue(AppVersion.parse("v2.0.0")!! > AppVersion.parse("1.99.99")!!)
        assertEquals(AppVersion(1, 0, 0), AppVersion.parse("v1.0.0"))
        assertNull(AppVersion.parse("2147483648.0.0"))
        assertNull(AppVersion.parse("1.0"))
    }

    @Test fun retiredPreviewTagsCannotTriggerAnIndependentUpdate() {
        assertNull(GitHubReleases.parse(release("v1.4.10-md3.4"), true))
        assertNull(AppVersion.parse("1.0.0-beta"))
    }

    @Test fun debugInstallationsGetMatchingAssets() {
        val result = GitHubReleases.parse(release(), true)!!
        assertEquals(AppVersion(1, 2, 0), result.version)
        assertTrue(result.downloadUrl!!.endsWith("-debug.apk"))
        assertTrue(result.version > AppVersion.parse("1.0.0")!!)
    }

    @Test fun missingChannelUsesReleasePageInsteadOfIncompatibleApk() {
        val result = GitHubReleases.parse(release(debugAsset = false), true)!!
        assertNull(result.downloadUrl)
        assertTrue(result.pageUrl.startsWith(GitHubReleases.PROJECT))
        assertTrue(GitHubReleases.parse(release(debugAsset = false), false)!!.downloadUrl!!.endsWith("-release.apk"))
    }

    @Test fun draftsAndPreviewsAreIgnored() {
        assertNull(GitHubReleases.parse(release(draft = true), true))
        assertNull(GitHubReleases.parse(release(prerelease = true), true))
    }

    @Test fun downloadsAreRestrictedToThisProjectsReleaseAssets() {
        val unsafe = release().replace(
            "https://github.com/Doublesail-A/iFAFU/releases/download/",
            "https://example.org/download/")
        assertNull(GitHubReleases.parse(unsafe, true)!!.downloadUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun otherRepositoriesAreRejected() {
        GitHubReleases.parse(release().replace("Doublesail-A/iFAFU/releases/tag/", "woolsen/iFAFU/releases/tag/"), true)
    }
}
