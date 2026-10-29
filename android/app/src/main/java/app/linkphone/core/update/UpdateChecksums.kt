package app.linkphone.core.update

import app.linkphone.core.crypto.toHex
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Checksum helpers for the release's SHA256SUMS.txt. The format matches the
 * output of `sha256sum`, so both "<hash>  name" and the binary "<hash> *name"
 * form are accepted.
 */

/** The lowercase SHA-256 recorded for [fileName], or null when it is not listed. */
fun parseChecksum(text: String, fileName: String): String? {
    for (line in text.split("\n")) {
        val match = CHECKSUM_PATTERN.matchEntire(line.trim()) ?: continue
        val name = match.groupValues[2].substringAfterLast('/').substringAfterLast('\\')
        if (name == fileName) return match.groupValues[1].lowercase()
    }
    return null
}

/** The lowercase SHA-256 of [file]. Blocking; call from a background dispatcher. */
fun sha256File(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHex()
}

/** True when both sides are the same 64-character hex digest. */
fun checksumsEqual(actual: String, expected: String): Boolean {
    if (!HEX_DIGEST.matches(actual) || !HEX_DIGEST.matches(expected)) return false
    return MessageDigest.isEqual(
        actual.lowercase().toByteArray(Charsets.US_ASCII),
        expected.lowercase().toByteArray(Charsets.US_ASCII),
    )
}

/** The release asset name for the Android package, e.g. "LinkPhone-0.7.0.apk". */
fun apkFileName(version: String): String {
    if (!VERSION_PATTERN.matches(version)) throw IllegalArgumentException("Invalid update version")
    return "LinkPhone-$version.apk"
}

private val CHECKSUM_PATTERN = Regex("^([a-fA-F0-9]{64})\\s+\\*?(.+?)$")
private val HEX_DIGEST = Regex("^[a-fA-F0-9]{64}$")
private val VERSION_PATTERN = Regex("^\\d+\\.\\d+\\.\\d+$")
