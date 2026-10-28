package app.linkphone.core.crypto

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

data class PairingInfo(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val token: String,
    val pcName: String?,
)

object PairingUri {
    private const val SCHEME = "linkphone://"
    private val HOST_PATTERN = Regex("^[A-Za-z0-9.\\-]{1,253}$")

    fun parse(raw: String): PairingInfo? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith(SCHEME, ignoreCase = true)) return null
        val rest = trimmed.substring(SCHEME.length)
        val queryIndex = rest.indexOf('?')
        if (queryIndex <= 0) return null
        val authority = rest.substring(0, queryIndex).substringBefore('/')
        if (!authority.equals("pair", ignoreCase = true)) return null

        val params = HashMap<String, String>()
        for (part in rest.substring(queryIndex + 1).split('&')) {
            if (part.isBlank()) continue
            val separator = part.indexOf('=')
            if (separator <= 0) continue
            val key = decode(part.substring(0, separator)) ?: continue
            val value = decode(part.substring(separator + 1)) ?: continue
            params[key] = value
        }

        val host = params["h"]?.trim() ?: return null
        if (!HOST_PATTERN.matches(host)) return null
        val port = params["p"]?.toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        val fingerprint = normalizeFingerprint(params["f"]) ?: return null
        val token = params["t"] ?: return null
        val tokenBytes = runCatching { Base64.getUrlDecoder().decode(token) }.getOrNull() ?: return null
        if (tokenBytes.size != 16) return null
        val name = params["n"]?.trim()?.takeIf { it.isNotEmpty() }?.take(80)

        return PairingInfo(
            host = host,
            port = port,
            fingerprint = fingerprint,
            token = token,
            pcName = name,
        )
    }

    private fun decode(value: String): String? = try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        null
    }
}
