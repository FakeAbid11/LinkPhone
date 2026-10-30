package app.linkphone.core.net

/**
 * One IPv4 address the phone holds, with the prefix length the system reported.
 *
 * Deliberately a plain data class rather than `android.net.LinkAddress`: the
 * subnet arithmetic has to run in the JVM unit tests, and anything reaching for
 * an Android type there silently returns 0/null instead of failing.
 */
data class LocalAddress(val address: String, val prefixLength: Int) {
    fun asCidr(): String = "$address/$prefixLength"
}

/** Every IPv4 link the phone currently holds, plus whether a tunnel is up. */
data class PhoneNetwork(
    val addresses: List<LocalAddress> = emptyList(),
    val vpnActive: Boolean = false,
) {
    /** "192.168.1.23/24, 10.8.0.2/32", or "none". Used verbatim in copy and logs. */
    fun summary(): String = addresses.joinToString(", ") { it.asCidr() }.ifEmpty { "none" }
}

enum class SubnetVerdict {
    /** Some local link contains the PC's address. */
    REACHABLE,

    /** The phone has IPv4 links, none of which contains the PC's address. */
    NO_ROUTE,

    /** Not enough information: the target is not an IPv4 literal, or no links. */
    UNKNOWN,
}

data class SubnetReport(
    val verdict: SubnetVerdict,
    val target: String,
    val phone: PhoneNetwork,
    /** The local link that contains the target, when the verdict is REACHABLE. */
    val matched: LocalAddress? = null,
)

/**
 * Answers one question: does any of this phone's links contain the PC's address?
 *
 * This is a *negative* check only. A mismatch proves the OS will return
 * ENETUNREACH; a match does not prove reachability, because containment is not a
 * routing table — a carrier CGNAT link, a corporate /8, or a full-tunnel VPN can
 * all "contain" an address the phone cannot reach. So [SubnetVerdict.NO_ROUTE] is
 * only reported on positive evidence, and the pairing attempt runs regardless.
 */
object SubnetCheck {
    fun parseQuad(text: String): Int? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        var value = 0
        for (part in parts) {
            if (part.isEmpty() || part.length > 3) return null
            // "01" is ambiguous and never appears in a real address; rejecting it
            // also keeps the parser from accepting octal-looking input.
            if (part.length > 1 && part[0] == '0') return null
            var octet = 0
            for (character in part) {
                if (character !in '0'..'9') return null
                octet = octet * 10 + (character - '0')
            }
            if (octet > 255) return null
            value = (value shl 8) or octet
        }
        return value
    }

    /** Plain concatenation on purpose: String.format would localise the digits. */
    fun formatQuad(value: Int): String {
        val a = (value ushr 24) and 0xFF
        val b = (value ushr 16) and 0xFF
        val c = (value ushr 8) and 0xFF
        val d = value and 0xFF
        return "$a.$b.$c.$d"
    }

    fun contains(local: LocalAddress, target: Int): Boolean {
        if (local.prefixLength !in 0..32) return false
        val localValue = parseQuad(local.address) ?: return false
        // JVM shift counts are taken modulo 32, so `-1 shl 32` silently evaluates
        // to `-1 shl 0`: a /0 would become an exact /32 match and every address
        // would look reachable. Branch instead of shifting by the full width.
        val mask = if (local.prefixLength == 0) 0 else -1 shl (32 - local.prefixLength)
        return (localValue and mask) == (target and mask)
    }

    fun evaluate(target: String, phone: PhoneNetwork): SubnetReport {
        val targetValue = parseQuad(target)
            ?: return SubnetReport(SubnetVerdict.UNKNOWN, target, phone)
        if (phone.addresses.isEmpty()) {
            return SubnetReport(SubnetVerdict.UNKNOWN, target, phone)
        }
        val matched = phone.addresses.firstOrNull { contains(it, targetValue) }
        return SubnetReport(
            verdict = if (matched != null) SubnetVerdict.REACHABLE else SubnetVerdict.NO_ROUTE,
            target = target,
            phone = phone,
            matched = matched,
        )
    }
}
