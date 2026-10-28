package app.linkphone

import app.linkphone.core.crypto.PairingUri
import app.linkphone.core.crypto.normalizeFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class PairingUriTest {
    private val token: String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(16) { index -> index.toByte() })
    private val fingerprint = "ab".repeat(32)

    @Test
    fun parsesAValidPairingUri() {
        val parsed = PairingUri.parse(
            "linkphone://pair?h=192.168.1.20&p=48921&f=$fingerprint&t=$token&n=My%20PC",
        )
        assertNotNull(parsed)
        val info = parsed!!
        assertEquals("192.168.1.20", info.host)
        assertEquals(48921, info.port)
        assertEquals(fingerprint, info.fingerprint)
        assertEquals(token, info.token)
        assertEquals("My PC", info.pcName)
    }

    @Test
    fun acceptsColonSeparatedFingerprints() {
        val formatted = fingerprint.chunked(2).joinToString(":")
        val parsed = PairingUri.parse(
            "linkphone://pair?h=192.168.1.20&p=48921&f=$formatted&t=$token",
        )
        assertNotNull(parsed)
        val info = parsed!!
        assertEquals(fingerprint, info.fingerprint)
        assertNull(info.pcName)
    }

    @Test
    fun rejectsWrongSchemeOrAuthority() {
        assertNull(PairingUri.parse("https://pair?h=1.2.3.4&p=1&f=$fingerprint&t=$token"))
        assertNull(PairingUri.parse("linkphone://other?h=1.2.3.4&p=1&f=$fingerprint&t=$token"))
        assertNull(PairingUri.parse("linkphone://pair"))
    }

    @Test
    fun rejectsBadPorts() {
        assertNull(PairingUri.parse("linkphone://pair?h=1.2.3.4&p=0&f=$fingerprint&t=$token"))
        assertNull(PairingUri.parse("linkphone://pair?h=1.2.3.4&p=70000&f=$fingerprint&t=$token"))
        assertNull(PairingUri.parse("linkphone://pair?h=1.2.3.4&p=abc&f=$fingerprint&t=$token"))
    }

    @Test
    fun rejectsBadFingerprintsAndTokens() {
        assertNull(PairingUri.parse("linkphone://pair?h=1.2.3.4&p=1&f=abcd&t=$token"))
        assertNull(PairingUri.parse("linkphone://pair?h=1.2.3.4&p=1&f=${"zz".repeat(32)}&t=$token"))
        assertNull(PairingUri.parse("linkphone://pair?h=1.2.3.4&p=1&f=$fingerprint&t=abc"))
        assertNull(PairingUri.parse("linkphone://pair?h=bad_host&p=1&f=$fingerprint&t=$token"))
    }

    @Test
    fun normalizesFingerprintsSafely() {
        val formatted = fingerprint.uppercase().chunked(2).joinToString(":")
        assertEquals(fingerprint, normalizeFingerprint(formatted))
        assertEquals(fingerprint, normalizeFingerprint(fingerprint.replace(":", "")))
        assertNull(normalizeFingerprint("nope"))
        assertNull(normalizeFingerprint(fingerprint.dropLast(1)))
    }
}
