package app.linkphone.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
    @Test
    fun stripsTheTagPrefix() {
        assertEquals("0.4.1", normalizeVersion("v0.4.1"))
        assertEquals("0.4.1", normalizeVersion("  v0.4.1  "))
        assertEquals("0.4.1", normalizeVersion("0.4.1"))
    }

    @Test
    fun dropsPreReleaseSuffixes() {
        assertEquals("0.4.0", normalizeVersion("0.4.0-beta.1"))
        assertEquals("0.4.0", normalizeVersion("v0.4.0+build7"))
    }

    @Test
    fun dropsLeadingZeros() {
        assertEquals("1.2.3", normalizeVersion("01.2.3"))
    }

    @Test
    fun rejectsValuesThatAreNotVersions() {
        assertNull(normalizeVersion("nope"))
        assertNull(normalizeVersion("1.2"))
        assertNull(normalizeVersion("1.2.3.4"))
        assertNull(normalizeVersion(""))
    }

    @Test
    fun comparesNumericallyNotLexically() {
        assertTrue(compareVersions("0.10.0", "0.9.9") > 0)
        assertTrue(compareVersions("0.9.9", "0.10.0") < 0)
        assertTrue(compareVersions("1.0.0", "0.99.99") > 0)
        assertEquals(0, compareVersions("0.7.0", "v0.7.0"))
    }

    @Test
    fun rejectsInvalidVersionsOnComparison() {
        try {
            compareVersions("nope", "1.0.0")
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.contains("nope") == true)
        }
        try {
            compareVersions("1.0.0", "nope")
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.contains("nope") == true)
        }
    }

    @Test
    fun detectsNewerVersions() {
        assertTrue(isNewerVersion("0.7.1", "0.7.0"))
        assertTrue(isNewerVersion("0.10.0", "0.9.9"))
        assertFalse(isNewerVersion("0.7.0", "0.7.0"))
        assertFalse(isNewerVersion("0.6.1", "0.7.0"))
    }
}
