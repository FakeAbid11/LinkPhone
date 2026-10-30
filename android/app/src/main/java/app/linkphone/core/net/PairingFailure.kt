package app.linkphone.core.net

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Why a pairing attempt failed, in the terms the person holding the phone can act on.
 *
 * The raw text used to be shown verbatim, which produced "Failed to connect to
 * /192.168.0.111:48921" — OkHttp's own ConnectException wording. It names the
 * layer but says nothing about what to do, and it cannot be told apart from a
 * firewall drop ("failed to connect to … after 8000ms") without knowing the
 * convention. Classification happens here, by exception *type*, so the copy
 * never depends on a third-party message string.
 */
enum class PairingFailureKind {
    /** The address answered with a refusal: nothing is listening there. */
    REFUSED,

    /** The phone has no route to that address at all. */
    UNREACHABLE,

    /** Nothing came back before the connect timeout: packets are being dropped. */
    TIMEOUT,

    /** Something answered, but not with the certificate the QR pins. */
    TLS_PIN,

    /** The PC understood the request and said no (expired, used, declined, busy). */
    AUTH,

    /** The PC answered with something this app version cannot read. */
    PROTOCOL,

    /** The socket closed after connecting but before pairing finished. */
    CLOSED,

    /** The host name in the code could not be resolved. */
    DNS,

    /** Anything else. */
    UNKNOWN,
    ;

    /**
     * True when the TCP layer never produced a working server, so a different
     * address for the same PC might still work. Auth, TLS and protocol failures
     * are excluded on purpose: retrying those at another address would only
     * repeat the same rejection.
     */
    val isConnectLayer: Boolean
        get() = this == REFUSED || this == UNREACHABLE || this == TIMEOUT || this == DNS
}

/**
 * A pairing failure ready to render.
 *
 * @property message one actionable sentence, safe to show as the headline
 * @property detail the raw exception text, kept for support and for the
 *   "Copy detail" affordance; null when there is nothing worth showing
 * @property checklist short "what to check" bullets; empty when the PC already
 *   wrote the guidance (see PAIRING_DECLINE_MESSAGES in the desktop server)
 */
data class PairingFailure(
    val kind: PairingFailureKind,
    val message: String,
    val detail: String? = null,
    val checklist: List<String> = emptyList(),
    /** Where the PC's address sits relative to this phone's links; null when we could not tell. */
    val subnet: SubnetReport? = null,
)

object PairingFailureClassifier {
    private const val MAX_CAUSE_DEPTH = 8
    private const val MAX_DETAIL_CHARS = 200

    /**
     * Maps a pairing exception onto a [PairingFailure].
     *
     * OkHttp reports a connect failure as a [ConnectException] whose cause is the
     * socket error, and a TLS pin mismatch as an [SSLHandshakeException] wrapping
     * the trust manager's [CertificateException], so the whole cause chain is
     * inspected rather than just the outermost throwable.
     */
    fun classify(
        error: Throwable,
        host: String,
        port: Int,
        phone: PhoneNetwork? = null,
    ): PairingFailure {
        val chain = causeChain(error)
        val kind = when {
            chain.any { it.isTlsFailure() } -> PairingFailureKind.TLS_PIN
            chain.any { it is NoRouteToHostException } -> PairingFailureKind.UNREACHABLE
            chain.any { it is ConnectException } -> PairingFailureKind.REFUSED
            // InterruptedIOException also covers OkHttp's ConnectTimeoutException,
            // which is not a SocketTimeoutException.
            chain.any { it is InterruptedIOException } -> PairingFailureKind.TIMEOUT
            chain.any { it is UnknownHostException } -> PairingFailureKind.DNS
            chain.any { it is LinkAuthException } -> PairingFailureKind.AUTH
            chain.any { it is LinkProtocolException } -> PairingFailureKind.PROTOCOL
            chain.any { it is LinkConnectionClosedException } -> PairingFailureKind.CLOSED
            else -> PairingFailureKind.UNKNOWN
        }
        // Only UNREACHABLE can act on it. Every other kind has a cause the phone's
        // links cannot explain, and its copy is already specific.
        val subnet = phone
            ?.takeIf { kind == PairingFailureKind.UNREACHABLE }
            ?.let { SubnetCheck.evaluate(host, it) }
        return PairingFailure(
            kind = kind,
            message = messageFor(kind, error, host, port, subnet),
            detail = describe(error),
            checklist = checklistFor(kind, host, port, subnet),
            subnet = subnet,
        )
    }

    fun shouldRetryWithDiscovery(failure: PairingFailure): Boolean = failure.kind.isConnectLayer

    private fun messageFor(
        kind: PairingFailureKind,
        error: Throwable,
        host: String,
        port: Int,
        subnet: SubnetReport?,
    ): String = when (kind) {
        PairingFailureKind.REFUSED -> "The PC at $host:$port refused the connection."
        PairingFailureKind.TIMEOUT -> "The PC at $host:$port did not answer in time."
        PairingFailureKind.UNREACHABLE -> unreachableMessage(subnet, host)
        PairingFailureKind.DNS -> "The address $host could not be resolved."
        PairingFailureKind.TLS_PIN ->
            "The device at $host:$port presented a different certificate than this code expects."
        PairingFailureKind.PROTOCOL -> "The PC answered with something LinkPhone could not read."
        PairingFailureKind.CLOSED -> "The PC closed the connection before pairing finished."
        // The PC writes these itself ("This pairing code expired…", "Pairing was
        // declined on the PC."), so its wording is better than anything generic.
        PairingFailureKind.AUTH -> error.message ?: "The PC rejected this pairing request."
        // A genuinely unexpected error: its own message is more useful than
        // "Pairing failed", and the detail line carries the rest.
        PairingFailureKind.UNKNOWN ->
            error.message?.takeIf { it.isNotBlank() } ?: "Pairing failed."
    }

    private fun checklistFor(
        kind: PairingFailureKind,
        host: String,
        port: Int,
        subnet: SubnetReport?,
    ): List<String> = when (kind) {
        PairingFailureKind.REFUSED -> listOf(
            "Check that LinkPhone is running on the PC and a fresh code is on screen.",
            "Check that $host is the PC's current Wi-Fi address (run ipconfig on the PC). " +
                "A code goes stale when the PC changes network.",
            "Allow LinkPhone through Windows Firewall on Private networks (TCP $port).",
            "Turn off any VPN on the phone, and use the same Wi-Fi rather than a guest network.",
        )

        PairingFailureKind.TIMEOUT -> listOf(
            "Something is dropping the traffic instead of refusing it: Windows Firewall, " +
                "a router with client isolation, or a VPN.",
            "Allow LinkPhone through Windows Firewall on Private networks (TCP $port).",
            "Turn off any VPN on either device, and avoid guest Wi-Fi.",
            "Check that $host is the PC's current Wi-Fi address (run ipconfig on the PC).",
        )

        PairingFailureKind.UNREACHABLE -> unreachableChecklist(subnet, host, port)

        PairingFailureKind.DNS -> listOf(
            "Enter the PC's numeric IPv4 address instead of a computer name.",
        )

        PairingFailureKind.TLS_PIN -> listOf(
            "Either the PC's identity was reset, or another device now holds that address.",
            "Create a new code on the PC and scan it again.",
        )

        PairingFailureKind.PROTOCOL -> listOf(
            "Update LinkPhone on both the PC and the phone so they use the same version.",
        )

        PairingFailureKind.CLOSED -> listOf(
            "Create a new code on the PC and scan it again.",
        )

        PairingFailureKind.AUTH, PairingFailureKind.UNKNOWN -> emptyList()
    }

    /**
     * The message for a phone that could not reach the PC's address.
     *
     * The generic sentence names the symptom; with the phone's own links we can
     * name the fault instead — "you are on 192.168.1.23/24, the PC is on
     * 192.168.0.111". That is the difference between a user checking ipconfig and
     * a user guessing.
     */
    private fun unreachableMessage(subnet: SubnetReport?, host: String): String {
        if (subnet == null) return "This phone has no route to $host."
        return when (subnet.verdict) {
            SubnetVerdict.NO_ROUTE ->
                "This phone is on ${subnet.phone.summary()}, but the PC's address $host " +
                    "is on a different subnet."

            // Containment is not reachability: a VPN that captures the LAN still
            // leaves the phone holding an address on the PC's subnet, and a Wi-Fi
            // the phone considers internet-less can lose the route to cellular.
            SubnetVerdict.REACHABLE ->
                "This phone has an address on ${subnet.matched?.asCidr() ?: subnet.phone.summary()} " +
                    "but the system still reported no route to $host — check for a VPN, and " +
                    "turn mobile data off."

            SubnetVerdict.UNKNOWN -> "This phone has no route to $host."
        }
    }

    private fun unreachableChecklist(
        subnet: SubnetReport?,
        host: String,
        port: Int,
    ): List<String> {
        // Nothing measured, or not measurable: keep the original advice.
        if (subnet == null || subnet.verdict == SubnetVerdict.UNKNOWN) {
            return listOf(
                "The phone is on a different network: mobile data, guest Wi-Fi, or a VPN.",
                "Join the same Wi-Fi as the PC and turn off any VPN.",
                "Check that $host is the PC's current Wi-Fi address (run ipconfig on the PC).",
            )
        }
        val vpnNote = if (subnet.phone.vpnActive) "A VPN is active on this phone. " else ""
        if (subnet.verdict == SubnetVerdict.REACHABLE) {
            return listOf(
                vpnNote + "Turn it off completely, not just disconnect, then try again.",
                "Turn mobile data off while pairing: a phone that thinks the Wi-Fi has no " +
                    "internet keeps cellular as its route.",
                "Allow LinkPhone through Windows Firewall on Private networks (TCP $port).",
            )
        }
        return listOf(
            vpnNote + "Turn off any VPN on this phone completely, not just disconnect, " +
                "then try again.",
            "On the PC run ipconfig (or Get-NetIPAddress) and check that its Wi-Fi address " +
                "is in the same subnet as this phone's.",
            "If the PC shows a 192.168.x address on a VMware, VirtualBox, or vEthernet " +
                "adapter, the code may carry that adapter instead of Wi-Fi. Create a new " +
                "code, or enter the Wi-Fi address by hand.",
            "Join the same Wi-Fi as the PC; guest networks isolate clients from each other.",
        )
    }

    private fun causeChain(error: Throwable): List<Throwable> {
        val chain = ArrayList<Throwable>(4)
        var current: Throwable? = error
        while (current != null && chain.size < MAX_CAUSE_DEPTH) {
            chain += current
            // A self-referencing cause would otherwise spin forever.
            current = current.cause?.takeIf { it !== current }
        }
        return chain
    }

    /**
     * A TLS failure shows up either as the handshake exception or as the
     * CertificateException the pinning trust manager throws.
     */
    private fun Throwable.isTlsFailure(): Boolean =
        this is SSLHandshakeException ||
            this is SSLPeerUnverifiedException ||
            this is CertificateException

    /** Outermost and root text, single line, capped — enough to search for. */
    private fun describe(error: Throwable): String? {
        val outer = error.toString()
        val root = causeChain(error).last().toString()
        val joined = if (root == outer) outer else "$outer <- $root"
        return joined
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_DETAIL_CHARS)
            .ifBlank { null }
    }
}
