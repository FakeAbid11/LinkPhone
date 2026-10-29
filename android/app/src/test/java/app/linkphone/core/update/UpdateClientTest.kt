package app.linkphone.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateClientTest {

    @Test
    fun parsesAReleaseFixture() {
        val release = parseRelease(releaseJson())
        assertEquals("0.7.0", release?.version)
        assertEquals("Release notes", release?.notes)
        assertEquals("https://example.com/releases/v0.7.0", release?.pageUrl)
        assertEquals("LinkPhone-0.7.0.apk", release?.apk?.name)
        assertEquals("https://example.com/LinkPhone-0.7.0.apk", release?.apk?.url)
        assertEquals(4_096L, release?.apk?.size ?: -1L)
        assertEquals("SHA256SUMS.txt", release?.checksums?.name)
        assertEquals("https://example.com/SHA256SUMS.txt", release?.checksums?.url)
    }

    @Test
    fun ignoresDraftsAndPrereleases() {
        assertNull(parseRelease(releaseJson(draft = true)))
        assertNull(parseRelease(releaseJson(prerelease = true)))
    }

    @Test
    fun ignoresTagsThatAreNotVersions() {
        assertNull(parseRelease(releaseJson(tagName = "nightly")))
        assertNull(parseRelease(releaseJson(tagName = null)))
    }

    @Test
    fun rejectsAReleaseWithoutAPageUrl() {
        val error = assertThrows(UpdateException::class.java) {
            parseRelease(releaseJson(htmlUrl = null))
        }
        assertEquals("GitHub did not provide a release page", error.message)
    }

    @Test
    fun rejectsAReleaseWithoutTheApk() {
        val error = assertThrows(UpdateException::class.java) {
            parseRelease(releaseJson(assets = CHECKSUMS_ONLY))
        }
        assertEquals(
            "The latest release is missing the Android APK or SHA256SUMS.txt",
            error.message,
        )
    }

    @Test
    fun rejectsAReleaseWithoutTheChecksums() {
        val error = assertThrows(UpdateException::class.java) {
            parseRelease(releaseJson(assets = APK_ONLY))
        }
        assertEquals(
            "The latest release is missing the Android APK or SHA256SUMS.txt",
            error.message,
        )
    }

    @Test
    fun rejectsAssetsWithoutADownloadUrl() {
        val assets = """
            {"name": "LinkPhone-0.7.0.apk", "browser_download_url": "", "size": 10},
            {"name": "SHA256SUMS.txt", "browser_download_url": "https://example.com/SHA256SUMS.txt"}
        """.trimIndent()
        assertThrows(UpdateException::class.java) { parseRelease(releaseJson(assets = assets)) }
    }

    @Test
    fun truncatesLongReleaseNotes() {
        val notes = "a".repeat(5_000)
        val release = parseRelease(releaseJson(body = notes))
        assertEquals(4_000, release?.notes?.length ?: 0)
    }

    @Test
    fun ignoresUnknownFields() {
        val release = parseRelease(
            """
            {
              "id": 12345,
              "author": {"login": "someone", "id": 7},
              "tag_name": "v0.7.0",
              "body": "Release notes",
              "html_url": "https://example.com/releases/v0.7.0",
              "draft": false,
              "prerelease": false,
              "assets": [$APK_AND_CHECKSUMS]
            }
            """.trimIndent(),
        )
        assertEquals("0.7.0", release?.version)
    }

    @Test
    fun rejectsMalformedJson() {
        val error = assertThrows(UpdateException::class.java) { parseRelease("{not json") }
        assertEquals("GitHub returned an invalid update response", error.message)
    }

    @Test
    fun keepsTheAssetSizeWhenPresent() {
        val release = parseRelease(releaseJson())
        assertTrue((release?.apk?.size ?: 0L) > 0L)
    }

    private fun releaseJson(
        tagName: String? = "v0.7.0",
        body: String? = "Release notes",
        htmlUrl: String? = "https://example.com/releases/v0.7.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = APK_AND_CHECKSUMS,
    ): String {
        val fields = mutableListOf<String>()
        if (tagName != null) fields += "\"tag_name\": \"$tagName\""
        if (body != null) fields += "\"body\": \"$body\""
        if (htmlUrl != null) fields += "\"html_url\": \"$htmlUrl\""
        fields += "\"draft\": $draft"
        fields += "\"prerelease\": $prerelease"
        fields += "\"assets\": [$assets]"
        return "{${fields.joinToString(",")}}"
    }

    private companion object {
        const val APK_AND_CHECKSUMS = """
            {"name": "LinkPhone-0.7.0.apk", "browser_download_url": "https://example.com/LinkPhone-0.7.0.apk", "size": 4096},
            {"name": "SHA256SUMS.txt", "browser_download_url": "https://example.com/SHA256SUMS.txt", "size": 96}
        """

        const val APK_ONLY = """
            {"name": "LinkPhone-0.7.0.apk", "browser_download_url": "https://example.com/LinkPhone-0.7.0.apk", "size": 4096}
        """

        const val CHECKSUMS_ONLY = """
            {"name": "SHA256SUMS.txt", "browser_download_url": "https://example.com/SHA256SUMS.txt", "size": 96}
        """
    }
}
