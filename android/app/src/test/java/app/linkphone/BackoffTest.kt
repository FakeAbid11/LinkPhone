package app.linkphone

import app.linkphone.core.util.Backoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BackoffTest {
    @Test
    fun staysWithinTheConfiguredBounds() {
        val backoff = Backoff(baseMs = 1_000, maxMs = 60_000, random = Random(7))
        repeat(60) {
            val delay = backoff.nextDelayMs()
            assertTrue("delay $delay too small", delay >= 1_000)
            assertTrue("delay $delay too large", delay <= 60_000)
        }
    }

    @Test
    fun growsWithAttempts() {
        val backoff = Backoff(baseMs = 1_000, maxMs = 60_000, random = Random(11))
        val first = backoff.nextDelayMs()
        repeat(12) { backoff.nextDelayMs() }
        val later = backoff.nextDelayMs()
        assertTrue("expected $later > $first", later > first)
    }

    @Test
    fun resetReturnsToTheInitialRange() {
        val backoff = Backoff(baseMs = 1_000, maxMs = 60_000, random = Random(3))
        repeat(10) { backoff.nextDelayMs() }
        backoff.reset()
        val delay = backoff.nextDelayMs()
        assertTrue(delay in 1_000..1_200)
    }

    @Test
    fun producesDeterministicSequencesForAGivenSeed() {
        val first = Backoff(baseMs = 1_000, maxMs = 60_000, random = Random(99))
        val second = Backoff(baseMs = 1_000, maxMs = 60_000, random = Random(99))
        repeat(8) {
            assertEquals(first.nextDelayMs(), second.nextDelayMs())
        }
    }
}
