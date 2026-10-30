package app.linkphone.core.net

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingFailureTest {
    private val host = "192.168.0.111"
    private val port = 48921

    @Test
    fun refusedConnectionExplainsWhatToCheck() {
        val failure = classify(ConnectException("Failed to connect to /$host:$port"))

        assertEquals(PairingFailureKind.REFUSED, failure.kind)
        assertTrue(failure.message.contains("$host:$port"))
        assertTrue(failure.checklist.any { it.contains("ipconfig") })
        assertTrue(failure.checklist.any { it.contains("Firewall") })
        assertTrue(failure.checklist.any { it.contains("VPN") })
        assertNotNull(failure.detail)
        assertTrue(failure.detail!!.contains("Failed to connect to /$host:$port"))
    }

    @Test
    fun connectTimeoutIsNotReportedAsRefused() {
        // OkHttp's ConnectTimeoutException is an InterruptedIOException, not a
        // SocketTimeoutException; both have to land on TIMEOUT.
        assertEquals(
            PairingFailureKind.TIMEOUT,
            classify(SocketTimeoutException("failed to connect to /$host:$port after 8000ms")).kind,
        )
        assertEquals(
            PairingFailureKind.TIMEOUT,
            classify(InterruptedIOException("timeout")).kind,
        )
        assertTrue(
            classify(InterruptedIOException("timeout")).checklist.any { it.contains("dropping") },
        )
    }

    @Test
    fun noRouteIsUnreachable() {
        val failure = classify(NoRouteToHostException("No route to host"))
        assertEquals(PairingFailureKind.UNREACHABLE, failure.kind)
        assertTrue(failure.checklist.any { it.contains("mobile data") })
    }

    @Test
    fun unknownHostIsDns() {
        val failure = classify(UnknownHostException("desktop-pc"))
        assertEquals(PairingFailureKind.DNS, failure.kind)
        assertTrue(failure.checklist.any { it.contains("numeric IPv4") })
    }

    @Test
    fun pinMismatchIsTlsAndIsNotRetried() {
        val handshake = SSLHandshakeException("Chain validation failed").apply {
            initCause(
                CertificateException(
                    "PC certificate fingerprint does not match the paired fingerprint",
                ),
            )
        }
        val failure = classify(handshake)

        assertEquals(PairingFailureKind.TLS_PIN, failure.kind)
        assertTrue(failure.checklist.any { it.contains("new code") })
        assertTrue(failure.detail!!.contains("fingerprint"))
        assertFalse(PairingFailureClassifier.shouldRetryWithDiscovery(failure))
    }

    @Test
    fun findsTheCauseThroughWrappers() {
        val wrapped = IOException("wrapped", ConnectException("Failed to connect to /$host:$port"))
        assertEquals(PairingFailureKind.REFUSED, classify(wrapped).kind)
    }

    @Test
    fun serverAuthTextIsPassedThroughUnchanged() {
        val text = "This pairing code expired. Create a new one on the PC."
        val failure = classify(LinkAuthException(text, "PAIRING_REJECTED"))

        assertEquals(PairingFailureKind.AUTH, failure.kind)
        assertEquals(text, failure.message)
        // The PC already wrote the guidance; a generic checklist would contradict it.
        assertTrue(failure.checklist.isEmpty())
        assertFalse(PairingFailureClassifier.shouldRetryWithDiscovery(failure))
    }

    @Test
    fun protocolAndClosedAreDistinctKinds() {
        assertEquals(PairingFailureKind.PROTOCOL, classify(LinkProtocolException("bad frame")).kind)
        assertEquals(
            PairingFailureKind.CLOSED,
            classify(LinkConnectionClosedException("The PC closed the pairing connection")).kind,
        )
    }

    @Test
    fun unknownThrowableKeepsItsMessageAndDetail() {
        val failure = classify(IllegalStateException("boom"))

        assertEquals(PairingFailureKind.UNKNOWN, failure.kind)
        assertEquals("boom", failure.message)
        assertNotNull(failure.detail)
        assertTrue(failure.checklist.isEmpty())
    }

    @Test
    fun onlyConnectLayerFailuresAreRetried() {
        val retryable = setOf(
            PairingFailureKind.REFUSED,
            PairingFailureKind.UNREACHABLE,
            PairingFailureKind.TIMEOUT,
            PairingFailureKind.DNS,
        )
        for (kind in PairingFailureKind.entries) {
            val failure = PairingFailure(kind, "message")
            assertEquals(
                "$kind retry eligibility",
                kind in retryable,
                PairingFailureClassifier.shouldRetryWithDiscovery(failure),
            )
        }
    }

    @Test
    fun noRouteNamesBothSidesWhenThePhoneHasAddresses() {
        val phone = PhoneNetwork(listOf(LocalAddress("192.168.1.23", 24)))
        val failure = classify(NoRouteToHostException("No route to host"), phone)

        assertEquals(PairingFailureKind.UNREACHABLE, failure.kind)
        assertEquals(SubnetVerdict.NO_ROUTE, failure.subnet?.verdict)
        assertTrue(failure.message.contains("192.168.1.23/24"))
        assertTrue(failure.message.contains(host))
        assertTrue(failure.checklist.any { it.contains("VPN") })
        assertTrue(failure.checklist.any { it.contains("VMware") })
    }

    @Test
    fun noRouteFallsBackToGenericCopyWithoutLocalAddresses() {
        // phone = null must reproduce the pre-subnet output byte for byte.
        val failure = classify(NoRouteToHostException("No route to host"))

        assertEquals(PairingFailureKind.UNREACHABLE, failure.kind)
        assertEquals("This phone has no route to $host.", failure.message)
        assertNull(failure.subnet)
        assertTrue(failure.checklist.any { it.contains("mobile data") })
    }

    @Test
    fun reachableVerdictBlamesTheVpnNotTheNetwork() {
        // Containment is not reachability: a VPN can capture the LAN while the
        // phone still holds an address on the PC's subnet.
        val phone = PhoneNetwork(listOf(LocalAddress("192.168.0.42", 24)), vpnActive = true)
        val failure = classify(NoRouteToHostException("No route to host"), phone)

        assertEquals(SubnetVerdict.REACHABLE, failure.subnet?.verdict)
        assertTrue(failure.message.contains("192.168.0.42/24"))
        assertTrue(failure.message.contains("VPN"))
        assertTrue(failure.checklist.first().contains("A VPN is active on this phone."))
    }

    @Test
    fun otherKindsCarryNoSubnet() {
        val phone = PhoneNetwork(listOf(LocalAddress("192.168.1.23", 24)))
        val failure = classify(ConnectException("Failed to connect to /$host:$port"), phone)

        assertEquals(PairingFailureKind.REFUSED, failure.kind)
        assertNull(failure.subnet)
    }

    @Test
    fun detailIsSingleLineAndCapped() {
        val noisy = IllegalStateException("line one\nline two\t" + "x".repeat(500))
        val detail = classify(noisy).detail

        assertNotNull(detail)
        assertFalse(detail!!.contains('\n'))
        assertFalse(detail.contains('\t'))
        assertTrue(detail.length <= 200)
    }

    private fun classify(error: Throwable, phone: PhoneNetwork? = null): PairingFailure =
        PairingFailureClassifier.classify(error, host, port, phone)
}
