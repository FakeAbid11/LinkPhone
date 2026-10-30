package app.linkphone.features.sms

import app.linkphone.core.model.SmsMessageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsPayloadTest {

    private fun message(index: Int, bodySize: Int = 1_000) = SmsMessageDto(
        id = index.toString(),
        address = "+15550101$index",
        body = "x".repeat(bodySize),
        date = 1_700_000_000_000L + index,
        type = 1,
        read = false,
    )

    /** Stands in for the JSON encoding the service measures. */
    private fun sizeOf(messages: List<SmsMessageDto>): Int =
        messages.sumOf { it.body.length + it.address.length + 64 }

    @Test
    fun keepsEverythingWhenItFits() {
        val messages = List(10) { message(it, 100) }
        assertEquals(messages, SmsPayload.fit(messages, ::sizeOf, maxBytes = 100_000))
    }

    @Test
    fun dropsTheOldestMessagesAndKeepsTheNewest() {
        val messages = List(100) { message(it) }

        val fitted = SmsPayload.fit(messages, ::sizeOf, maxBytes = 10_000)

        assertTrue(fitted.isNotEmpty())
        assertTrue(sizeOf(fitted) <= 10_000)
        assertEquals(messages.last().id, fitted.last().id)
        // Everything kept is a suffix: the dropped ones are the oldest.
        assertEquals(messages.takeLast(fitted.size).map { it.id }, fitted.map { it.id })
    }

    @Test
    fun returnsAnEmptyListWhenASingleMessageIsTooLarge() {
        val messages = listOf(message(0, bodySize = 50_000))

        assertTrue(SmsPayload.fit(messages, ::sizeOf, maxBytes = 1_000).isEmpty())
    }

    @Test
    fun handlesAnEmptyList() {
        assertTrue(SmsPayload.fit(emptyList(), ::sizeOf, maxBytes = 1_000).isEmpty())
    }

    @Test
    fun usesTheRealLimitByDefault() {
        assertTrue(SmsPayload.MAX_BYTES < 1_048_576)
    }
}
