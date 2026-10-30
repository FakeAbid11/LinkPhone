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

/** The PC accepted the socket and then closed it before pairing finished. */
class LinkConnectionClosedException(message: String) : IOException(message)

class PairingClient(
    private val identity: DeviceIdentity,
    private val phoneId: String,
    private val phoneName: String,
    private val appVersion: String,
    /** Used to find the PC again when the address in the code no longer answers. */
    private val discovery: PcDiscovery? = null,
    private val onLog: (String) -> Unit = {},
) {
    /**
     * Pairs with the PC named by the QR.
     *
     * The QR carries a five-minute code and one address. If that address no
     * longer answers — the PC took a new DHCP lease, or the code froze a VPN or
     * Hyper-V adapter — the first attempt fails at the TCP layer. In that case
     * the PC is looked up over mDNS by the certificate fingerprint from the QR
     * and the pairing is retried once at the address it actually holds. The
     * fingerprint is the same one the socket is pinned to, so the trust anchor
     * still comes only from the QR.
     */
    suspend fun pair(info: PairingInfo, onStatus: (String) -> Unit = {}): PairedPc {
        onLog("Connecting to ${info.host}:${info.port}")
        val first = runCatching { attempt(info, info.host, info.port, onStatus) }
        first.getOrNull()?.let { return it }

        val error = first.exceptionOrNull() ?: LinkProtocolException("Pairing failed")
        val failure = PairingFailureClassifier.classify(error, info.host, info.port)
        onLog("Connect failed (${failure.kind}): ${failure.detail ?: failure.message}")

        // Bound to a local before the null check: smart casts on properties are
        // dropped inside a lambda, and the lookup below runs inside runCatching.
        val resolver = discovery
        if (!PairingFailureClassifier.shouldRetryWithDiscovery(failure) || resolver == null) {
            throw error
        }

        onStatus("Looking for the PC on this network…")
        val found = runCatching { resolver.findByFingerprint(info.fingerprint) }.getOrNull()
        if (found == null) {
            onLog("mDNS found no PC advertising this certificate fingerprint")
            throw error
        }
        if (found.host == info.host && found.port == info.port) {
            onLog("mDNS resolved to the same address ${found.host}:${found.port}")
            throw error
        }

        onLog("mDNS found the PC at ${found.host}:${found.port}; retrying once")
        onStatus("Found the PC at ${found.host}. Reconnecting…")
        return attempt(info, found.host, found.port, onStatus)
    }

    /**
     * One pairing handshake against [host]:[port].
     *
     * Kept free of retry logic so the orchestrator above stays readable, and so
     * a retry cannot accidentally reuse a stale socket.
     */
    private suspend fun attempt(
        info: PairingInfo,
        host: String,
        port: Int,
        onStatus: (String) -> Unit,
    ): PairedPc {
        val result = CompletableDeferred<PairedPc>()
        val client = PinnedTls.client(info.fingerprint)
        val request = Request.Builder().url("wss://$host:$port/ws").build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStatus("Connected to $host. Waiting for the PC to accept…")
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
                                // The address that actually answered, not the one
                                // the code carried: a fallback must be remembered
                                // or the next reconnect repeats the same failure.
                                host = host,
                                port = port,
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

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (!result.isCompleted) {
                    result.completeExceptionally(
                        LinkConnectionClosedException(
                            reason.ifBlank { "The PC closed the pairing connection" },
                        ),
                    )
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!result.isCompleted) {
                    result.completeExceptionally(
                        LinkConnectionClosedException(
                            reason.ifBlank { "The PC closed the pairing connection" },
                        ),
                    )
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
