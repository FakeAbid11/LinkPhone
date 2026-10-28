package app.linkphone

import app.linkphone.core.crypto.AuthPayload
import app.linkphone.core.crypto.formatFingerprint
import app.linkphone.core.crypto.normalizeFingerprint
import app.linkphone.core.crypto.sha256Hex
import app.linkphone.core.crypto.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CryptoTest {
    @Test
    fun computesTheKnownSha256Vector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".toByteArray(Charsets.UTF_8)),
        )
    }

    @Test
    fun formatsHexLowercase() {
        assertEquals("000f10ff", byteArrayOf(0x00, 0x0F, 0x10, -1).toHex())
    }

    @Test
    fun formatsAndNormalizesFingerprints() {
        val hex = "ab".repeat(32)
        assertEquals("AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB", formatFingerprint(hex))
        assertEquals(hex, normalizeFingerprint(formatFingerprint(hex)))
        assertEquals(hex, normalizeFingerprint(hex.uppercase()))
        assertNull(normalizeFingerprint("not-a-fingerprint"))
        assertNull(normalizeFingerprint(null))
    }

    @Test
    fun buildsTheCanonicalAuthPayload() {
        assertEquals(
            "linkphone-auth-v1|pc-1|phone-1|bm9uY2U=",
            AuthPayload.build("pc-1", "phone-1", "bm9uY2U="),
        )
    }
}
