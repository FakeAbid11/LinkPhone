package app.linkphone.core.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class UpdateException(message: String) : Exception(message)

data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long? = null,
)

data class ReleaseInfo(
    val version: String,
    val notes: String?,
    val pageUrl: String,
    val apk: ReleaseAsset,
    val checksums: ReleaseAsset,
)

@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("draft") val draft: Boolean? = null,
    @SerialName("prerelease") val prerelease: Boolean? = null,
    @SerialName("assets") val assets: List<GitHubAssetDto>? = null,
)

@Serializable
internal data class GitHubAssetDto(
    @SerialName("name") val name: String? = null,
    @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
    @SerialName("size") val size: Long? = null,
)

/**
 * Parses the body of GitHub's "latest release" endpoint. Drafts, prereleases
 * and unparsable tags count as "no release" so the phone stays on the current
 * version instead of showing an error.
 */
fun parseRelease(json: String): ReleaseInfo? {
    val release = try {
        RELEASE_JSON.decodeFromString<GitHubReleaseDto>(json)
    } catch (error: Exception) {
        throw UpdateException("GitHub returned an invalid update response")
    }
    if (release.draft == true || release.prerelease == true) return null
    val version = release.tagName?.let { normalizeVersion(it) } ?: return null
    val pageUrl = release.htmlUrl?.takeIf { it.isNotBlank() }
        ?: throw UpdateException("GitHub did not provide a release page")

    val assets = release.assets.orEmpty()
    val apkName = apkFileName(version)
    val apk = assets.firstOrNull {
        it.name == apkName && !it.browserDownloadUrl.isNullOrBlank()
    }
    val checksums = assets.firstOrNull {
        it.name == CHECKSUMS_ASSET && !it.browserDownloadUrl.isNullOrBlank()
    }
    if (apk == null || checksums == null) {
        throw UpdateException(
            "The latest release is missing the Android APK or SHA256SUMS.txt",
        )
    }

    return ReleaseInfo(
        version = version,
        notes = release.body?.take(MAX_NOTES),
        pageUrl = pageUrl,
        apk = ReleaseAsset(apkName, apk.browserDownloadUrl!!, apk.size),
        checksums = ReleaseAsset(CHECKSUMS_ASSET, checksums.browserDownloadUrl!!, checksums.size),
    )
}

interface UpdateClient {
    /** The newest published release, or null when there is none. */
    suspend fun latestRelease(): ReleaseInfo?

    /** Downloads a small text asset such as SHA256SUMS.txt. */
    suspend fun readText(url: String, maxBytes: Int = MAX_TEXT_BYTES): String

    /** Streams [url] into [target] and reports progress from 0 to 100. */
    suspend fun download(
        url: String,
        target: File,
        maxBytes: Int,
        onProgress: (Int) -> Unit,
    )
}

/**
 * Reads the project's releases straight from GitHub. Redirects are followed by
 * hand so a release can never downgrade the connection to plain HTTP.
 */
class GitHubUpdateClient(
    private val apiUrl: String = DEFAULT_API_URL,
    private val userAgent: String = USER_AGENT,
    private val http: OkHttpClient = defaultHttpClient(),
) : UpdateClient {

    override suspend fun latestRelease(): ReleaseInfo? =
        parseRelease(readText(apiUrl, MAX_JSON_BYTES))

    override suspend fun readText(url: String, maxBytes: Int): String =
        execute(url) { response ->
            val stream = response.body?.byteStream()
                ?: throw UpdateException("GitHub returned an empty update response")
            val bytes = stream.readAtMost(maxBytes + 1)
            if (bytes.size > maxBytes) {
                throw UpdateException("The update response is larger than expected")
            }
            bytes.toString(Charsets.UTF_8)
        }

    override suspend fun download(
        url: String,
        target: File,
        maxBytes: Int,
        onProgress: (Int) -> Unit,
    ) {
        val limit = minOf(maxBytes, MAX_DOWNLOAD_BYTES)
        try {
            execute(url) { response ->
                target.parentFile?.mkdirs()
                val stream = response.body?.byteStream()
                    ?: throw UpdateException("GitHub returned an empty update download")
                val total = response.body?.contentLength()?.takeIf { it > 0 } ?: 0L
                var received = 0L
                stream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            received += read
                            if (received > limit) {
                                throw UpdateException("The update download is larger than expected")
                            }
                            output.write(buffer, 0, read)
                            val percent = if (total > 0) {
                                (received * 100 / total).toInt()
                            } else {
                                (received * 100 / limit).toInt()
                            }
                            onProgress(percent.coerceIn(0, 99))
                        }
                    }
                }
                onProgress(100)
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private suspend fun <T> execute(url: String, block: (Response) -> T): T =
        withContext(Dispatchers.IO) { executeOnIo(url, block) }

    private suspend fun <T> executeOnIo(url: String, block: (Response) -> T): T {
        var current = url
        var redirects = 0
        while (true) {
            val response = open(current)
            if (response.isRedirect) {
                redirects += 1
                val location = response.header("Location")
                response.close()
                if (redirects > MAX_REDIRECTS) {
                    throw UpdateException("Too many update redirects")
                }
                if (location.isNullOrBlank()) {
                    throw UpdateException("GitHub redirected without a destination")
                }
                current = resolve(current, location)
                if (!current.startsWith("https://")) {
                    throw UpdateException("Update URLs must use HTTPS")
                }
                continue
            }
            if (!response.isSuccessful) {
                val code = response.code
                val limited = code == 403 && response.header("X-RateLimit-Remaining") == "0"
                response.close()
                if (limited) {
                    throw UpdateException(
                        "GitHub's update API rate limit was reached. Try again later.",
                    )
                }
                throw UpdateException("GitHub returned HTTP $code")
            }
            return try {
                block(response)
            } finally {
                response.close()
            }
        }
    }

    private suspend fun open(url: String): Response = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) throw UpdateException("Update URLs must use HTTPS")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/vnd.github+json, application/octet-stream")
            .build()
        try {
            http.newCall(request).await()
        } catch (error: IOException) {
            throw UpdateException(error.message ?: "The update request failed")
        }
    }

    private fun resolve(base: String, location: String): String {
        val baseUrl = base.toHttpUrlOrNull() ?: throw UpdateException("Invalid update URL")
        return baseUrl.resolve(location)?.toString() ?: throw UpdateException("Invalid update URL")
    }

    companion object {
        const val DEFAULT_API_URL =
            "https://api.github.com/repos/FakeAbid11/LinkPhone/releases/latest"
        const val USER_AGENT = "LinkPhone-Updater"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}

private val RELEASE_JSON = Json { ignoreUnknownKeys = true }

private const val MAX_JSON_BYTES = 2 * 1024 * 1024
private const val MAX_TEXT_BYTES = 128 * 1024
private const val MAX_DOWNLOAD_BYTES = 300 * 1024 * 1024
private const val MAX_REDIRECTS = 5
private const val CHECKSUMS_ASSET = "SHA256SUMS.txt"
private const val MAX_NOTES = 4_000

private val Response.isRedirect: Boolean
    get() = code == 301 || code == 302 || code == 303 || code == 307 || code == 308

private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
    val buffer = ByteArray(16 * 1024)
    val out = ByteArrayOutputStream()
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        out.write(buffer, 0, read)
        if (out.size() >= maxBytes) break
    }
    return out.toByteArray()
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) {
                continuation.resumeWith(Result.success(response))
            } else {
                response.close()
            }
        }
    })
}
