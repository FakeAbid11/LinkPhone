package app.linkphone.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSignerTest {

    private val ours = setOf("AAAA")
    private val rotated = setOf("BBBB")
    private val foreign = setOf("ZZZZ")

    @Test
    fun theSameKeyMatches() {
        assertTrue(sameSigner(SignerInfo(ours, ours), SignerInfo(ours, ours)))
    }

    @Test
    fun aForeignKeyDoesNotMatch() {
        assertFalse(sameSigner(SignerInfo(ours, ours), SignerInfo(foreign, foreign)))
    }

    @Test
    fun aForwardKeyRotationMatches() {
        val candidate = SignerInfo(rotated, setOf("AAAA", "BBBB"))
        assertTrue(sameSigner(SignerInfo(ours, ours), candidate))
    }

    @Test
    fun aRollbackToARetiredKeyDoesNotMatch() {
        val installed = SignerInfo(rotated, setOf("AAAA", "BBBB"))
        val candidate = SignerInfo(ours, setOf("AAAA"))
        assertFalse(sameSigner(installed, candidate))
    }

    @Test
    fun anUnrelatedLineageDoesNotMatch() {
        val candidate = SignerInfo(rotated, setOf("CCCC", "BBBB"))
        assertFalse(sameSigner(SignerInfo(ours, ours), candidate))
    }

    @Test
    fun emptySignerSetsNeverMatch() {
        assertFalse(sameSigner(SignerInfo(emptySet(), emptySet()), SignerInfo(ours, ours)))
        assertFalse(sameSigner(SignerInfo(ours, ours), SignerInfo(emptySet(), emptySet())))
        assertFalse(sameSigner(SignerInfo(emptySet(), emptySet()), SignerInfo(emptySet(), emptySet())))
    }
}
