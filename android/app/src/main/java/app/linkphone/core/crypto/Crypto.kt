package app.linkphone.core.crypto

import java.security.MessageDigest

fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

fun ByteArray.toHex(): String = buildString(size * 2) {
    for (byte in this@toHex) {
        val value = byte.toInt() and 0xFF
        append(HEX[value ushr 4])
        append(HEX[value and 0x0F])
    }
}

private const val HEX = "0123456789abcdef"

fun sha256Hex(bytes: ByteArray): String = bytes.sha256().toHex()

/** Accepts upper/lowercase hex with optional colon or space separators. */
fun normalizeFingerprint(input: String?): String? {
    if (input == null) return null
    val cleaned = input.replace(":", "").replace(" ", "").trim().lowercase()
    if (cleaned.length != 64) return null
    if (cleaned.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
    return cleaned
}

fun formatFingerprint(fingerprintHex: String): String =
    fingerprintHex.chunked(2).joinToString(":").uppercase()

object AuthPayload {
    const val PREFIX = "linkphone-auth-v1"

    fun build(pcId: String, phoneId: String, nonceBase64: String): String =
        "$PREFIX|$pcId|$phoneId|$nonceBase64"
}
