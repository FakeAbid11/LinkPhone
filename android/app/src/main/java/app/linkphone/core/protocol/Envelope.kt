package app.linkphone.core.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WireError(
    val code: String,
    val message: String,
)

/**
 * Every control message on the wire is one of these. Binary photo/mirror/file
 * payloads use a separate frame format.
 */
@Serializable
data class Envelope(
    val v: Int = PROTOCOL_VERSION,
    val id: String,
    val type: String,
    val replyTo: String? = null,
    val ok: Boolean? = null,
    val data: JsonElement? = null,
    val error: WireError? = null,
    val ts: Long = System.currentTimeMillis(),
) {
    companion object {
        const val PROTOCOL_VERSION = 1
    }
}

object Types {
    // handshake and pairing
    const val HELLO = "hello"
    const val AUTH_CHALLENGE = "auth.challenge"
    const val AUTH_RESPONSE = "auth.response"
    const val AUTH_OK = "auth.ok"
    const val AUTH_ERROR = "auth.error"
    const val PAIR_REQUEST = "pair.request"
    const val PAIR_PENDING = "pair.pending"
    const val PAIR_OK = "pair.ok"
    const val PAIR_REJECTED = "pair.rejected"

    // app channels
    const val PHONE_INFO = "phone.info"
    const val SMS_LIST = "sms.list"
    const val SMS_SEND = "sms.send"
    const val SMS_CHANGED = "sms.changed"
    const val NOTIF_POST = "notif.post"
    const val NOTIF_REMOVED = "notif.removed"
    const val NOTIF_DISMISS = "notif.dismiss"
    const val PHOTO_LIST = "photo.list"
    const val PHOTO_FETCH = "photo.fetch"
    const val PHOTO_STREAM_END = "photo.streamEnd"
    const val CALL_STATE = "call.state"
    const val CALL_DIAL = "call.dial"
    const val CALL_ANSWER = "call.answer"
    const val CALL_DECLINE = "call.decline"
    const val CLIPBOARD_SET = "clipboard.set"
    const val CLIPBOARD_FROM_PHONE = "clipboard.fromPhone"
    const val HANDOFF_SHARE = "handoff.share"
    const val HANDOFF_FILE = "handoff.file"
    const val HANDOFF_FILE_END = "handoff.fileEnd"
    const val HANDOFF_OPEN = "handoff.open"
    const val MIRROR_START = "mirror.start"
    const val MIRROR_STOP = "mirror.stop"
    const val MIRROR_STATE = "mirror.state"
    const val MIRROR_TOUCH = "mirror.touch"
    const val PING = "ping"
    const val PONG = "pong"
}

object ErrorCodes {
    const val BAD_REQUEST = "BAD_REQUEST"
    const val PERMISSION_DENIED = "PERMISSION_DENIED"
    const val UNKNOWN_DEVICE = "UNKNOWN_DEVICE"
    const val PAIRING_REQUIRED = "PAIRING_REQUIRED"
    const val PAIRING_REJECTED = "PAIRING_REJECTED"
    const val AUTH_FAILED = "AUTH_FAILED"
    const val BUSY = "BUSY"
    const val UNSUPPORTED = "UNSUPPORTED"
    const val NOT_FOUND = "NOT_FOUND"
    const val TOO_LARGE = "TOO_LARGE"
    const val INTERNAL = "INTERNAL"
}
