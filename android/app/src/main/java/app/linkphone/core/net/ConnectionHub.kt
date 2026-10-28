package app.linkphone.core.net

import app.linkphone.core.protocol.Envelope
import app.linkphone.core.protocol.ProtocolCodec
import app.linkphone.core.protocol.Types
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.ByteString.Companion.toByteString
import java.util.concurrent.atomic.AtomicReference

class PhoneSession internal constructor(
    private val socket: WebSocket,
    internal val client: OkHttpClient,
) {
    fun send(text: String): Boolean = socket.send(text)

    fun sendBinary(bytes: ByteArray): Boolean = socket.send(bytes.toByteString())
}

/**
 * Process-wide bridge used by the notification listener, SMS observer, share
 * activity and Quick Settings tile to reach the active authenticated session.
 */
object ConnectionHub {
    private val session = AtomicReference<PhoneSession?>(null)

    fun attach(value: PhoneSession) {
        session.getAndSet(value)?.let { old -> runCatching { old.client.connectionPool.evictAll() } }
    }

    fun detach(value: PhoneSession?) {
        if (value != null) session.compareAndSet(value, null) else session.set(null)
    }

    fun isConnected(): Boolean = session.get() != null

    fun sendEvent(type: String, data: JsonElement? = null): Boolean =
        sendEnvelope(ProtocolCodec.event(type, data))

    fun sendEnvelope(envelope: Envelope): Boolean {
        val active = session.get() ?: return false
        return runCatching { active.send(ProtocolCodec.encode(envelope)) }.getOrDefault(false)
    }

    fun sendBinary(bytes: ByteArray): Boolean {
        val active = session.get() ?: return false
        return runCatching { active.sendBinary(bytes) }.getOrDefault(false)
    }

    fun sendPing() {
        sendEvent(Types.PING)
    }
}
