package app.linkphone.core.util

import java.net.URI

class FeaturePermissionException(message: String) : Exception(message)

object SafeInput {
    private val DIAL_PATTERN = Regex("^[+0-9*#()\\-\\s]{1,30}$")
    private val FILE_PATTERN = Regex("[^A-Za-z0-9 ._()\\-]")

    fun normalizeDialNumber(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > 30) return null
        if (!DIAL_PATTERN.matches(trimmed)) return null
        if (trimmed.none { it.isDigit() } && !trimmed.contains('*') && !trimmed.contains('#')) {
            return null
        }
        return trimmed
    }

    fun normalizeSmsAddress(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.length > 64) return null
        if (trimmed.any { it.isISOControl() }) return null
        return trimmed
    }

    /** Only http(s) URLs are allowed to cross the bridge and be opened. */
    fun isSafeHttpUrl(raw: String?): Boolean {
        if (raw == null) return false
        val value = raw.trim()
        if (value.isEmpty() || value.length > 4096) return false
        if (value.any { it.isWhitespace() || it.isISOControl() }) return false
        return try {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    fun sanitizeFileName(raw: String?, fallback: String): String {
        val base = raw?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        val cleaned = FILE_PATTERN.replace(base, "_").trim('.', ' ').take(120)
        return cleaned.ifBlank { fallback }
    }

    fun truncateClipboard(text: String, maxChars: Int = 32_000): String =
        if (text.length <= maxChars) text else text.substring(0, maxChars)

    fun validNormalizedCoordinate(value: Float): Boolean =
        value.isFinite() && value in 0f..1f
}
