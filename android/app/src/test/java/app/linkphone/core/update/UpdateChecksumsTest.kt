package app.linkphone.core.update

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateChecksumsTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun findsTheDigestOfTheRequestedFile() {
        val text = buildString {
            appendLine("${"a".repeat(64)}  LinkPhone-Setup-0.6.1.exe")
            appendLine("${"b".repeat(64)}  LinkPhone-0.7.0.apk")
            appendLine("${"c".repeat(64)}  LinkPhone-Portable-0.6.1.exe")
        }
        assertEquals("b".repeat(64), parseChecksum(text, "LinkPhone-0.7.0.apk"))
    }

    @Test
    fun stripsDirectoryPrefixes() {
        val forward = "${"d".repeat(64)}  dist/LinkPhone-0.7.0.apk"
        val backward = "${"e".repeat(64)}  dist\\LinkPhone-0.7.0.apk"
        assertEquals("d".repeat(64), parseChecksum(forward, "LinkPhone-0.7.0.apk"))
        assertEquals("e".repeat(64), parseChecksum(backward, "LinkPhone-0.7.0.apk"))
    }

    @Test
    fun acceptsTheBinaryMarker() {
        val text = "${"f".repeat(64)} *LinkPhone-0.7.0.apk"
        assertEquals("f".repeat(64), parseChecksum(text, "LinkPhone-0.7.0.apk"))
    }

    @Test
    fun returnsLowercaseDigests() {
        val text = "${"ABCDEF0123456789".repeat(4)}  LinkPhone-0.7.0.apk"
        assertEquals("abcdef0123456789".repeat(4), parseChecksum(text, "LinkPhone-0.7.0.apk"))
    }

    @Test
    fun ignoresMalformedLinesAndMissingFiles() {
        val text = buildString {
            appendLine("not a checksum line")
            appendLine("${"a".repeat(63)}  LinkPhone-0.7.0.apk")
            appendLine("${"b".repeat(64)}  LinkPhone-0.7.1.apk")
        }
        assertNull(parseChecksum(text, "LinkPhone-0.7.0.apk"))
        assertEquals("b".repeat(64), parseChecksum(text, "LinkPhone-0.7.1.apk"))
        assertNull(parseChecksum(text, "LinkPhone-0.9.9.apk"))
    }

    @Test
    fun comparesDigestsSafely() {
        val digest = "a".repeat(64)
        assertTrue(checksumsEqual(digest, digest))
        assertTrue(checksumsEqual(digest.uppercase(), digest))
        assertFalse(checksumsEqual(digest, "b".repeat(64)))
        assertFalse(checksumsEqual(digest, "a".repeat(63)))
        assertFalse(checksumsEqual(digest, "z".repeat(64)))
        assertFalse(checksumsEqual("", digest))
    }

    @Test
    fun hashesFilesWithSha256() {
        val file = folder.newFile("payload.bin")
        file.writeBytes("abc".toByteArray())
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256File(file),
        )
    }

    @Test
    fun hashesEmptyFiles() {
        val file = folder.newFile("empty.bin")
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256File(file),
        )
    }

    @Test
    fun namesTheReleaseAsset() {
        assertEquals("LinkPhone-0.7.0.apk", apkFileName("0.7.0"))
        assertEquals("LinkPhone-0.10.0.apk", apkFileName("0.10.0"))
    }

    @Test
    fun rejectsInvalidReleaseVersions() {
        try {
            apkFileName("0.7")
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.contains("Invalid") == true)
        }
    }

    @Test
    fun readsBackADigestWrittenBySha256sum() {
        val file = folder.newFile("release.apk")
        file.writeBytes("linkphone".toByteArray())
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
        val sums = "$digest  release.apk"
        val parsed = parseChecksum(sums, "release.apk")
        assertEquals(digest, parsed)
        assertTrue(parsed != null && checksumsEqual(sha256File(file), parsed))
    }
}
