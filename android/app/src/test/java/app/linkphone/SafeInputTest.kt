package app.linkphone

import app.linkphone.core.util.SafeInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeInputTest {
    @Test
    fun acceptsOrdinaryDialNumbers() {
        assertEquals("+1 (555) 010-1000", SafeInput.normalizeDialNumber("+1 (555) 010-1000"))
        assertEquals("*#06#", SafeInput.normalizeDialNumber("*#06#"))
        assertEquals("123", SafeInput.normalizeDialNumber("123"))
    }

    @Test
    fun rejectsUnsafeDialNumbers() {
        assertNull(SafeInput.normalizeDialNumber("abc"))
        assertNull(SafeInput.normalizeDialNumber("++"))
        assertNull(SafeInput.normalizeDialNumber("1".repeat(31)))
        assertNull(SafeInput.normalizeDialNumber(""))
        assertNull(SafeInput.normalizeDialNumber("123; rm -rf"))
    }

    @Test
    fun onlyAllowsHttpAndHttpsUrls() {
        assertTrue(SafeInput.isSafeHttpUrl("https://example.com/path?q=1"))
        assertTrue(SafeInput.isSafeHttpUrl("http://192.168.1.4:8080"))
        assertFalse(SafeInput.isSafeHttpUrl("javascript:alert(1)"))
        assertFalse(SafeInput.isSafeHttpUrl("file:///etc/passwd"))
        assertFalse(SafeInput.isSafeHttpUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(SafeInput.isSafeHttpUrl("https://exa mple.com"))
        assertFalse(SafeInput.isSafeHttpUrl(null))
    }

    @Test
    fun sanitizesFileNamesWithoutPathTraversal() {
        assertEquals("evil.txt", SafeInput.sanitizeFileName("../../evil.txt", "fallback"))
        assertEquals("evil.jpg", SafeInput.sanitizeFileName("..\\..\\evil.jpg", "fallback"))
        assertEquals("fallback", SafeInput.sanitizeFileName("...", "fallback"))
        assertEquals("fallback", SafeInput.sanitizeFileName("", "fallback"))
        val cleaned = SafeInput.sanitizeFileName("a/b\\c:d*e?.jpg", "fallback")
        assertFalse(cleaned.contains('/'))
        assertFalse(cleaned.contains('\\'))
        assertFalse(cleaned.contains(':'))
    }

    @Test
    fun normalizesSmsAddressesAndClipboardText() {
        assertEquals("+15550101000", SafeInput.normalizeSmsAddress("  +15550101000 "))
        assertNull(SafeInput.normalizeSmsAddress("   "))
        assertNull(SafeInput.normalizeSmsAddress("a".repeat(65)))
        assertEquals("abc", SafeInput.truncateClipboard("abcdef", 3))
        assertEquals("abcdef", SafeInput.truncateClipboard("abcdef", 10))
    }

    @Test
    fun validatesNormalizedCoordinates() {
        assertTrue(SafeInput.validNormalizedCoordinate(0f))
        assertTrue(SafeInput.validNormalizedCoordinate(1f))
        assertTrue(SafeInput.validNormalizedCoordinate(0.5f))
        assertFalse(SafeInput.validNormalizedCoordinate(-0.01f))
        assertFalse(SafeInput.validNormalizedCoordinate(1.01f))
        assertFalse(SafeInput.validNormalizedCoordinate(Float.NaN))
    }
}
