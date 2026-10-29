package app.linkphone.core.update

/**
 * Version helpers mirrored from the Windows updater so both surfaces agree on
 * what "newer" means.
 */

/** Strips a leading "v" and any pre-release suffix. Null when not major.minor.patch. */
fun normalizeVersion(value: String): String? {
    val trimmed = value.trim().removePrefix("v").removePrefix("V")
    val match = VERSION_PATTERN.matchEntire(trimmed) ?: return null
    val major = match.groupValues[1].toIntOrNull() ?: return null
    val minor = match.groupValues[2].toIntOrNull() ?: return null
    val patch = match.groupValues[3].toIntOrNull() ?: return null
    return "$major.$minor.$patch"
}

/** Numeric comparison of two versions. Negative when left is older. */
fun compareVersions(left: String, right: String): Int {
    val a = versionParts(left)
    val b = versionParts(right)
    for (index in 0..2) {
        if (a[index] != b[index]) return if (a[index] > b[index]) 1 else -1
    }
    return 0
}

/** True when [candidate] is strictly newer than [current]. */
fun isNewerVersion(candidate: String, current: String): Boolean =
    compareVersions(candidate, current) > 0

private fun versionParts(value: String): IntArray {
    val normalized = normalizeVersion(value)
        ?: throw IllegalArgumentException("Invalid version: $value")
    val parts = normalized.split('.')
    return intArrayOf(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
}

private val VERSION_PATTERN = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$")
