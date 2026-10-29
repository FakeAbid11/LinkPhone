package app.linkphone.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Hello(
    val deviceId: String,
    val phoneName: String,
    val platform: String = "android",
    val appVersion: String,
    val publicKeySpki: String,
    val protocol: Int = 1,
)

@Serializable
data class AuthChallenge(
    val nonce: String,
    val pcId: String,
    val pcName: String? = null,
)

@Serializable
data class AuthResponse(val signature: String)

@Serializable
data class AuthOk(
    val pcId: String,
    val pcName: String,
    val protocol: Int = 1,
)

@Serializable
data class PairRequest(
    val token: String,
    val deviceId: String,
    val name: String,
    val platform: String = "android",
    val appVersion: String,
    val publicKeySpki: String,
)

@Serializable
data class PairOk(
    val pcId: String,
    val pcName: String,
)

@Serializable
data class SmsMessageDto(
    val id: String,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean,
    val subId: Int? = null,
)

@Serializable
data class SmsListResult(val messages: List<SmsMessageDto>)

@Serializable
data class SmsListRequest(val limit: Int = 500)

@Serializable
data class SmsSendRequest(
    val address: String,
    val body: String,
    val subId: Int? = null,
)

@Serializable
data class SmsSendResult(
    val accepted: Boolean,
    val parts: Int,
    val subId: Int? = null,
)

@Serializable
data class SmsOpenThreadRequest(val address: String)

@Serializable
data class SmsChanged(val timestamp: Long)

@Serializable
data class NotificationDto(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val category: String? = null,
    val ongoing: Boolean = false,
    val time: Long,
)

@Serializable
data class NotificationRemoved(val key: String)

@Serializable
data class PhotoDto(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val date: Long,
    val width: Int,
    val height: Int,
    val bucket: String? = null,
    val volume: String? = null,
)

@Serializable
data class PhotoListResult(
    val photos: List<PhotoDto>,
    val hasMore: Boolean = false,
)

@Serializable
data class PhotoListRequest(
    val limit: Int = 200,
    val bucket: String? = null,
    val beforeDate: Long? = null,
    val beforeId: String? = null,
)

@Serializable
data class PhotoAlbumDto(
    val id: String,
    val name: String,
    val count: Int,
    val coverId: String,
    val latest: Long,
)

@Serializable
data class PhotoAlbumListResult(val albums: List<PhotoAlbumDto>)

@Serializable
data class NotificationDismissRequest(val key: String)

@Serializable
data class StreamEnd(val streamId: Long)

@Serializable
data class OkResult(val ok: Boolean = true)

@Serializable
data class PhotoFetchRequest(
    val id: String,
    val kind: String = "thumb",
    val volume: String? = null,
)

@Serializable
data class PhotoStreamMeta(
    val streamId: Long,
    val photoId: String,
    val kind: String,
    val mime: String,
    val size: Long,
)

@Serializable
data class ContactDto(
    val id: String,
    val name: String,
    val number: String,
    val label: String? = null,
)

@Serializable
data class ContactListRequest(val limit: Int = 1_000)

@Serializable
data class ContactListResult(val contacts: List<ContactDto>)

@Serializable
data class CallState(
    val state: String,
    val number: String? = null,
    val contactName: String? = null,
    val timestamp: Long,
)

@Serializable
data class CallDialRequest(
    val number: String,
    val subId: Int? = null,
)

@Serializable
data class ClipboardSetRequest(val text: String)

@Serializable
data class ClipboardFromPhone(val text: String)

@Serializable
data class HandoffShare(
    val kind: String,
    val text: String? = null,
    val url: String? = null,
)

@Serializable
data class HandoffFileMeta(
    val streamId: Long,
    val name: String,
    val mime: String,
    val size: Long,
)

@Serializable
data class HandoffOpenRequest(val url: String)

@Serializable
data class MirrorState(
    val active: Boolean,
    val width: Int = 0,
    val height: Int = 0,
    val message: String? = null,
)

@Serializable
data class MirrorTouchRequest(
    val action: String,
    val x: Float,
    val y: Float,
    val x2: Float? = null,
    val y2: Float? = null,
    val durationMs: Int = 60,
)

@Serializable
data class SimInfo(
    val subscriptionId: Int,
    val label: String,
    val slotIndex: Int,
)

@Serializable
data class PhoneInfo(
    val model: String,
    val androidVersion: String,
    val appVersion: String,
    val capabilities: List<String>,
    val subscriptions: List<SimInfo> = emptyList(),
)

@Serializable
data class Pong(val timestamp: Long)

object Capabilities {
    const val SMS = "sms"
    const val SMS_OPEN_THREAD = "sms.openThread"
    const val CONTACTS = "contacts"
    const val NOTIFICATIONS = "notifications"
    const val PHOTOS = "photos"
    const val PHOTOS_PARTIAL = "photos.partial"
    const val PHOTOS_ALBUMS = "photos.albums"
    const val CALLS = "calls"
    const val CALLS_ANSWER = "calls.answer"
    const val CALLS_CALLER_ID = "calls.callerId"
    const val CALLS_SIM_SELECTION = "calls.simSelection"
    const val MIRROR = "mirror"
    const val MIRROR_INPUT = "mirror.input"
    const val CLIPBOARD = "clipboard"
    const val HANDOFF = "handoff"
}
