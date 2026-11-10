package dev.linkphone.app

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateRelease(
    val version: String,
    val url: String,
    val digest: String?,
    val size: Long
)

class UpdateHttpException(val code: Int) : Exception("HTTP $code")

/** Shared GitHub Releases check used by the in-app updater and the background worker. */
object UpdateChecker {
    internal const val USER_AGENT = "LinkPhone-Android-Updater"
    private const val REPO = "FakeAbid11/linkphone"
    private const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"
    private const val RELEASE_API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val APK_NAME = "linkphone-android.apk"
    private val TAG_PATTERN = Regex("^v?\\d+(?:\\.\\d+){1,3}$")

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // The release page redirect costs no API quota. The unauthenticated GitHub API
    // is limited to 60 requests per hour per IP, which carrier NAT shares.
    private val noRedirect = http.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun fetchLatest(): UpdateRelease {
        runCatching { fromRedirect() }.getOrNull()?.let { return it }
        return fromApi()
    }

    private fun fromRedirect(): UpdateRelease {
        val request = Request.Builder()
            .url(RELEASES_PAGE)
            .header("Accept", "text/html")
            .header("User-Agent", USER_AGENT)
            .build()
        noRedirect.newCall(request).execute().use { response ->
            if (response.code !in setOf(301, 302, 303, 307, 308)) {
                throw UpdateHttpException(response.code)
            }
            val location = response.header("Location")
                ?: throw UpdateHttpException(response.code)
            val target = response.request.url.resolve(location)
                ?: throw IllegalStateException("Release redirect is not a URL")
            val segments = target.pathSegments
            if (target.scheme != "https" || target.host != "github.com" ||
                segments.size != 5 ||
                !segments[0].equals("FakeAbid11", ignoreCase = true) ||
                !segments[1].equals("linkphone", ignoreCase = true) ||
                segments[2] != "releases" || segments[3] != "tag"
            ) {
                throw IllegalStateException("Unexpected release redirect")
            }
            val tag = segments[4]
            if (tag.length > 64 || !TAG_PATTERN.matches(tag)) {
                throw IllegalStateException("Release tag is not a version")
            }
            val apkUrl = HttpUrl.Builder()
                .scheme("https")
                .host("github.com")
                .addPathSegments("$REPO/releases/download")
                .addPathSegment(tag)
                .addPathSegment(APK_NAME)
                .build()
                .toString()
            return UpdateRelease(
                version = tag.removePrefix("v"),
                url = apkUrl,
                digest = sidecarDigest(apkUrl),
                size = headSize(apkUrl)
            )
        }
    }

    /** The workflow publishes `<apk>.sha256`; a missing sidecar just skips the hash check. */
    private fun sidecarDigest(apkUrl: String): String? = runCatching {
        val request = Request.Builder()
            .url("$apkUrl.sha256")
            .header("User-Agent", USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body ?: return@use null
            if (body.contentLength() > 512) return@use null
            Regex("^([0-9a-fA-F]{64})\\b")
                .find(body.string().take(512))
                ?.groupValues?.get(1)
                ?.lowercase()
                ?.let { "sha256:$it" }
        }
    }.getOrNull()

    private fun headSize(apkUrl: String): Long = runCatching {
        val request = Request.Builder()
            .url(apkUrl)
            .head()
            .header("User-Agent", USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use -1L
            response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 } ?: -1L
        }
    }.getOrDefault(-1L)

    private fun fromApi(): UpdateRelease {
        val request = Request.Builder()
            .url(RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UpdateHttpException(response.code)
            val root = JSONObject(response.body?.string() ?: error("Empty update response"))
            val version = root.optString("tag_name").removePrefix("v")
            if (version.isBlank()) error("Release has no version")
            val assets = root.optJSONArray("assets") ?: error("Release has no files")
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                if (asset.optString("name") != APK_NAME) continue
                val url = asset.optString("browser_download_url")
                if (!url.startsWith("https://github.com/")) {
                    error("Release APK has an invalid download URL")
                }
                return UpdateRelease(
                    version = version,
                    url = url,
                    digest = asset.optString("digest").takeIf { it.isNotBlank() },
                    size = asset.optLong("size", -1L)
                )
            }
            error("Release does not contain $APK_NAME")
        }
    }

    fun isNewer(candidate: String, current: String): Boolean {
        val left = versionParts(candidate)
        val right = versionParts(current)
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun versionParts(value: String): List<Int> =
        value.removePrefix("v").split('.').map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
}
