package app.linkphone.core.util

import kotlin.math.min
import kotlin.random.Random

class Backoff(
    private val baseMs: Long = 1_000,
    private val maxMs: Long = 60_000,
    private val jitterRatio: Double = 0.2,
    private val random: Random = Random.Default,
) {
    private var attempt = 0

    fun nextDelayMs(): Long {
        val exponent = min(attempt, 16)
        val ceiling = min(maxMs, baseMs shl exponent)
        attempt += 1
        val jitter = (ceiling * jitterRatio).toLong()
        if (jitter <= 0) return ceiling
        val low = (ceiling - jitter).coerceAtLeast(baseMs)
        val high = (ceiling + jitter).coerceAtMost(maxMs + jitter)
        return low + random.nextLong(high - low + 1)
    }

    fun reset() {
        attempt = 0
    }
}
