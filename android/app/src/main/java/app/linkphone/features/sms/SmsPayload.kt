package app.linkphone.features.sms

import app.linkphone.core.model.SmsMessageDto

/**
 * Keeps an sms.list response inside the protocol's JSON limit.
 *
 * The PC closes the session when a single message is larger than 1 MiB, and the
 * phone treats that close as retryable, so an oversized list would loop:
 * connect, request the list, get closed, reconnect. Trimming on the phone is
 * what breaks that loop. Messages arrive newest first, so the oldest are the
 * ones dropped.
 */
object SmsPayload {
    /** The PC accepts 1 MiB; leave room for the envelope and the field names. */
    const val MAX_BYTES = 700_000

    fun fit(
        messages: List<SmsMessageDto>,
        sizeOf: (List<SmsMessageDto>) -> Int,
        maxBytes: Int = MAX_BYTES,
    ): List<SmsMessageDto> {
        if (messages.isEmpty()) return messages
        if (sizeOf(messages) <= maxBytes) return messages

        // Binary search for the largest newest-first suffix that fits, so a
        // 500-message list costs about nine encodings instead of five hundred.
        var low = 0
        var high = messages.size - 1
        var best = 0
        while (low <= high) {
            val keep = (low + high) / 2
            val candidate = messages.subList(messages.size - keep, messages.size)
            if (sizeOf(candidate) <= maxBytes) {
                best = keep
                low = keep + 1
            } else {
                high = keep - 1
            }
        }
        return messages.subList(messages.size - best, messages.size).toList()
    }
}
