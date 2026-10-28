package app.linkphone.core.net

import app.linkphone.core.crypto.DeviceIdentity
import app.linkphone.core.crypto.PairingInfo
import app.linkphone.core.crypto.PinnedTls
import app.linkphone.core.model.PairOk
import app.linkphone.core.model.PairRequest
import app.linkphone.core.prefs.PairedPc
import app.linkphone.core.protocol.ProtocolCodec
import app.linkphone.core.protocol.Types
import app.linkphone.core.protocol.decodeData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException

class PairingClient(
    private val identity: DeviceIdentity,
    private val phoneId: String,
    private val phoneName: String,
    private val appVersion: String,
) {
    suspend fun pair(info: PairingInfo, onStatus: (String) -> Unit = {}): PairedPc {
        val result = CompletableDeferred<PairedPc>()
        val client = PinnedTls.client(info.fingerprint)
        val request = Request.Builder().url("wss://${info.host}:${info.port}/ws").build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStatus("Connected to ${info.host}. Waiting for the PC to accept…")
                val payload = PairRequest(
                    token = info.token,
                    deviceId = phoneId,
                    name = phoneName,
                    appVersion = appVersion,
                    publicKeySpki = identity.publicKeySpkiBase64(),
                )
                webSocket.send(
                    ProtocolCodec.encode(
                        ProtocolCodec.event(Types.PAIR_REQUEST, ProtocolCodec.data(payload)),
                    ),
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val envelope = try {
                    ProtocolCodec.decode(text)
                } catch (error: Exception) {
                    result.completeExceptionally(LinkProtocolException("Malformed pairing response"))
                    return
                }
                when (envelope.type) {
                    Types.PAIR_PENDING -> onStatus("Approve LinkPhone on your PC…")
                    Types.PAIR_OK -> {
                        val ok = envelope.decodeData<PairOk>()
                        if (ok.pcId.isBlank() || ok.pcName.isBlank()) {
                            result.completeExceptionally(LinkProtocolException("Invalid pairing response"))
                            return
                        }
                        result.complete(
                            PairedPc(
                                pcId = ok.pcId,
                                pcName = ok.pcName,
                                host = info.host,
                                port = info.port,
                                fingerprint = info.fingerprint,
                            ),
                        )
                        runCatching { webSocket.close(1000, "paired") }
                    }
                    Types.PAIR_REJECTED, Types.AUTH_ERROR -> {
                        result.completeExceptionally(
                            LinkAuthException(envelope.error?.message ?: "The PC rejected pairing"),
                        )
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                result.completeExceptionally(IOException(t.message ?: "Pairing connection failed", t))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!result.isCompleted) {
                    result.completeExceptionally(IOException("The PC closed the pairing connection"))
                }
            }
        }

        val webSocket = client.newWebSocket(request, listener)
        return try {
            withTimeout(120_000) { result.await() }
        } catch (_: TimeoutCancellationException) {
            throw IOException("Pairing timed out. Start pairing again on the PC.")
        } finally {
            runCatching { webSocket.close(1000, "done") }
            PinnedTls.close(client)
        }
    }
}
