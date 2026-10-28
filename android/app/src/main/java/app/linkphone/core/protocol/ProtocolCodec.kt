package app.linkphone.core.protocol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID

object ProtocolCodec {
    const val MAX_JSON_BYTES = 1_048_576

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = false
    }

    fun encode(envelope: Envelope): String = json.encodeToString(Envelope.serializer(), envelope)

    fun decode(text: String): Envelope {
        require(text.length <= MAX_JSON_BYTES) { "message exceeds JSON limit" }
        val envelope = json.decodeFromString(Envelope.serializer(), text)
        require(envelope.v == Envelope.PROTOCOL_VERSION) {
            "unsupported protocol version ${envelope.v}"
        }
        require(envelope.id.isNotBlank() && envelope.id.length <= 128) { "invalid message id" }
        require(envelope.type.isNotBlank() && envelope.type.length <= 64) { "invalid message type" }
        return envelope
    }

    inline fun <reified T> data(value: T): JsonElement = json.encodeToJsonElement(value)

    fun event(type: String, data: JsonElement? = null): Envelope =
        Envelope(id = UUID.randomUUID().toString(), type = type, data = data)

    fun response(request: Envelope, data: JsonElement? = null): Envelope =
        Envelope(
            id = UUID.randomUUID().toString(),
            type = "response",
            replyTo = request.id,
            ok = true,
            data = data,
        )

    fun failure(request: Envelope, code: String, message: String): Envelope =
        Envelope(
            id = UUID.randomUUID().toString(),
            type = "response",
            replyTo = request.id,
            ok = false,
            error = WireError(code = code, message = message),
        )
}

inline fun <reified T> Envelope.decodeData(): T =
    ProtocolCodec.json.decodeFromJsonElement(data ?: error("message has no data"))
