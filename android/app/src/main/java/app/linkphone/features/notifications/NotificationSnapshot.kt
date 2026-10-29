package app.linkphone.features.notifications

import app.linkphone.core.model.NotificationDto

/**
 * The text shown for a notification. The order matches what the phone's shade
 * shows: the short text first, then the expanded text, then the message list.
 */
object NotificationText {
    fun pick(text: String?, bigText: String?, lines: List<String>?): String? {
        val joined = lines
            ?.filter { it.isNotBlank() }
            ?.joinToString("\n")
            ?.takeIf { it.isNotBlank() }
        return sequenceOf(text, bigText, joined)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
    }
}

/**
 * The notifications worth sending to the PC: newest first, without LinkPhone's
 * own notifications, capped so the payload stays small.
 */
object NotificationSnapshot {
    fun select(
        items: List<NotificationDto>,
        ownPackage: String,
        limit: Int,
    ): List<NotificationDto> = items
        .filter { it.packageName != ownPackage }
        .sortedByDescending { it.time }
        .take(limit.coerceAtLeast(0))
}
