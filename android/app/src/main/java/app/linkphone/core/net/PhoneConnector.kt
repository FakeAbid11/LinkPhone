package app.linkphone.core.net

import app.linkphone.core.crypto.AuthPayload
import app.linkphone.core.crypto.DeviceIdentity
import app.linkphone.core.crypto.PinnedTls
import app.linkphone.core.model.AuthChallenge
import app.linkphone.core.model.AuthResponse
import app.linkphone.core.model.Hello
import app.linkphone.core.prefs.PairedPc
import app.linkphone.core.protocol.BinaryCodec
import app.linkphone.core.protocol.BinaryFrame
import app.linkphone.core.protocol.Envelope
import app.linkphone.core.protocol.ProtocolCodec
import app.linkphone.core.protocol.Types
import app.linkphone.core.protocol.decodeData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.ByteString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

class LinkAuthException(
    message: String,
    val code: String? = null,
) : Exception(message)

class LinkProtocolException(message: String) : Exception(message)

class PhoneConnector(
    private val identity: DeviceIdentity,
    private val phoneId: String,
    private val phoneName: String,
    private val appVersion: String,
    private val onMessage: (Envelope) -> Unit,
    private val onBinary: (BinaryFrame) -> Unit,
    private val onClosed: (String) -> Unit,
) {
    @Volatile
    private var client: OkHttpClient? = null

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var stopping = false

    private val closeNotified = AtomicBoolean(false)

    suspend fun connect(paired: PairedPc, host: String, port: Int): PhoneSession {
        val authorized = CompletableDeferred<PhoneSession>()
        val httpClient = PinnedTls.client(paired.fingerprint)
        client = httpClient

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val hello = Hello(
                    deviceId = phoneId,
                    phoneName = phoneName,
                    appVersion = appVersion,
                    publicKeySpki = identity.publicKeySpkiBase64(),
                )
                webSocket.send(
                    ProtocolCodec.encode(
                        ProtocolCodec.event(Types.HELLO, ProtocolCodec.data(hello)),
                    ),
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val envelope = try {
                    ProtocolCodec.decode(text)
                } catch (error: Exception) {
                    failPending(authorized, LinkProtocolException("Malformed message from PC"))
                    runCatching { webSocket.close(1002, "protocol error") }
                    return
                }

                when (envelope.type) {
                    Types.AUTH_CHALLENGE -> handleChallenge(webSocket, paired, envelope, authorized)
                    Types.AUTH_OK -> {
                        if (!authorized.isCompleted) {
                            authorized.complete(PhoneSession(webSocket, httpClient))
                        }
                    }
                    Types.AUTH_ERROR -> {
                        val message = envelope.error?.message ?: "The PC rejected this phone"
                        failPending(
                            authorized,
                            LinkAuthException(message, envelope.error?.code),
                        )
                        runCatching { webSocket.close(1008, "authentication failed") }
                    }
                    else -> if (authorized.isCompleted) {
                        runCatching { onMessage(envelope) }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (!authorized.isCompleted) return
                val frame = BinaryCodec.decode(bytes.toByteArray()) ?: return
                runCatching { onBinary(frame) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                failPending(authorized, IOException(reason.ifBlank { "PC closed the connection" }))
                closeNotified.compareAndSet(false, true).let { first ->
                    if (first) notifyClosed(reason.ifBlank { "PC closed the connection" })
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                failPending(authorized, IOException(reason.ifBlank { "PC closed the connection" }))
                closeNotified.compareAndSet(false, true).let { first ->
                    if (first) notifyClosed(reason.ifBlank { "PC closed the connection" })
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val message = t.message ?: "Connection to the PC failed"
                failPending(authorized, IOException(message, t))
                closeNotified.compareAndSet(false, true).let { first ->
                    if (first) notifyClosed(message)
                }
            }
        }

        val webSocket = httpClient.newWebSocket(
            Request.Builder().url("wss://$host:$port/ws").build(),
            listener,
        )
        socket = webSocket

        return try {
            withTimeout(15_000) { authorized.await() }
        } catch (timeout: TimeoutCancellationException) {
            stopping = true
            runCatching { webSocket.cancel() }
            throw IOException("Timed out connecting to the PC at $host:$port")
        } catch (error: Throwable) {
            stopping = true
            runCatching { webSocket.cancel() }
            throw error
        }
    }

    fun shutdown() {
        stopping = true
        runCatching { socket?.close(1000, "LinkPhone disconnecting") }
        runCatching { socket?.cancel() }
        client?.let { PinnedTls.close(it) }
    }

    private fun handleChallenge(
        webSocket: WebSocket,
        paired: PairedPc,
        envelope: Envelope,
        authorized: CompletableDeferred<PhoneSession>,
    ) {
        try {
            val challenge = envelope.decodeData<AuthChallenge>()
            if (challenge.pcId != paired.pcId) {
                throw LinkAuthException(
                    "The PC identity does not match the paired PC",
                    "IDENTITY_CHANGED",
                )
            }
            if (challenge.nonce.isBlank() || challenge.nonce.length > 256) {
                throw LinkProtocolException("Invalid authentication challenge")
            }
            val payload = AuthPayload.build(paired.pcId, phoneId, challenge.nonce)
            val signature = Base64.getEncoder().encodeToString(identity.sign(payload))
            webSocket.send(
                ProtocolCodec.encode(
                    ProtocolCodec.event(
                        Types.AUTH_RESPONSE,
                        ProtocolCodec.data(AuthResponse(signature)),
                    ),
                ),
            )
        } catch (error: Exception) {
            failPending(
                authorized,
                if (error is LinkAuthException || error is LinkProtocolException) error
                else LinkAuthException(error.message ?: "Could not answer the PC challenge"),
            )
            runCatching { webSocket.close(1008, "authentication failed") }
        }
    }

    private fun failPending(authorized: CompletableDeferred<PhoneSession>, error: Throwable) {
        if (!authorized.isCompleted) authorized.completeExceptionally(error)
    }

    private fun notifyClosed(message: String) {
        if (stopping) return
        onClosed(message)
    }
}
