package app.linkphone.features.mirror

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.Surface
import app.linkphone.core.protocol.BinaryCodec
import app.linkphone.core.protocol.VideoPayload
import app.linkphone.core.util.LinkStatus

/**
 * Hardware H.264 capture: MediaCodec surface input, 30 fps, 6 Mbps. The encoder
 * writes Annex B access units; SPS/PPS are kept and prepended to every key frame
 * so the PC decoder can resync without a separate out-of-band negotiation.
 */
class H264Sink private constructor(
    private val codec: MediaCodec,
    private val choice: EncoderChoice,
    private val handler: Handler,
    private val onFrame: (ByteArray) -> Unit,
    private val onFatal: (String) -> Unit,
    private val queueSize: () -> Long,
) : MirrorSink {
    override val kind: String = MirrorCodecChoice.H264
    override val width: Int = choice.width
    override val height: Int = choice.height
    override val surface: Surface get() = inputSurface ?: error("H.264 sink is not ready")

    private var inputSurface: Surface? = null
    private var config: ByteArray? = null
    private var configSent = false
    private var sequence = 0
    private var lastPtsUs = -1L
    private var released = false
    private var failed = false
    private var needsSync = false
    private var lastDropLogAt = 0L

    private fun configure(constantBitrate: Boolean) {
        codec.setCallback(
            object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit

                override fun onOutputBufferAvailable(
                    codec: MediaCodec,
                    index: Int,
                    info: MediaCodec.BufferInfo,
                ) {
                    this@H264Sink.handleOutput(codec, index, info)
                }

                override fun onError(codec: MediaCodec, error: MediaCodec.CodecException) {
                    this@H264Sink.fail(
                        "The phone's hardware encoder stopped (${codecErrorName(error)}). " +
                            "Restart mirroring in Compatible mode.",
                    )
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    val csd = AvcConfig.normalizeConfig(
                        this@H264Sink.readBuffer(format, "csd-0"),
                        this@H264Sink.readBuffer(format, "csd-1"),
                    )
                    if (csd != null) this@H264Sink.config = csd
                }
            },
            handler,
        )
        codec.configure(
            MirrorEncoderSelector.formatFor(choice, constantBitrate),
            null,
            null,
            MediaCodec.CONFIGURE_FLAG_ENCODE,
        )
        inputSurface = codec.createInputSurface()
        codec.start()
    }

    override fun start() = Unit

    override fun requestKeyframe() {
        if (released || failed) return
        runCatching {
            codec.setParameters(
                Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                },
            )
        }.onFailure {
            LinkStatus.log("Mirror", "Keyframe request failed: ${it.javaClass.simpleName}")
        }
    }

    override fun release() {
        if (released) return
        released = true
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { inputSurface?.release() }
        inputSurface = null
    }

    private fun handleOutput(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
        if (released) return
        try {
            if (info.size <= 0) return
            val buffer = codec.getOutputBuffer(index) ?: return
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)
            val data = ByteArray(info.size)
            buffer.get(data)

            if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                config = AvcConfig.normalizeConfig(data) ?: config
                return
            }
            if (data.isEmpty() || failed) return

            val keyFrame = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
            val currentConfig = config
            if (keyFrame && currentConfig != null) {
                sendConfigFrame(currentConfig)
            }

            if (!keyFrame) {
                if (needsSync) {
                    if (queueSize() < QUEUE_RESUME_BYTES) {
                        needsSync = false
                        requestKeyframe()
                    }
                    return
                }
                if (queueSize() > MAX_QUEUE_BYTES) {
                    needsSync = true
                    logDrop()
                    return
                }
            }

            val framed = if (keyFrame) {
                AvcConfig.prependIfMissing(data, currentConfig)
            } else {
                data
            }
            if (framed.size > BinaryCodec.MAX_PAYLOAD - VideoPayload.HEADER_SIZE) {
                fail("A mirror frame exceeded the transfer limit. Restart in Compatible mode.")
                return
            }

            val pts = maxOf(info.presentationTimeUs, lastPtsUs + 1).coerceAtLeast(0)
            lastPtsUs = pts
            val flags = if (keyFrame) BinaryCodec.FLAG_KEY_FRAME else 0
            onFrame(
                BinaryCodec.encode(
                    type = BinaryCodec.TYPE_VIDEO,
                    streamId = 0,
                    seq = sequence++,
                    last = false,
                    payload = VideoPayload.encode(pts, framed),
                    flags = flags,
                ),
            )
            if (keyFrame) needsSync = false
        } catch (error: Throwable) {
            LinkStatus.log("Mirror", "H.264 frame failed: ${error.javaClass.simpleName}")
        } finally {
            runCatching { codec.releaseOutputBuffer(index, false) }
        }
    }

    private fun sendConfigFrame(csd: ByteArray) {
        if (configSent) return
        configSent = true
        onFrame(
            BinaryCodec.encode(
                type = BinaryCodec.TYPE_VIDEO,
                streamId = 0,
                seq = sequence++,
                last = false,
                payload = VideoPayload.encode(0, csd),
                flags = BinaryCodec.FLAG_CODEC_CONFIG,
            ),
        )
    }

    private fun fail(message: String) {
        if (failed || released) return
        failed = true
        LinkStatus.log("Mirror", message)
        onFatal(message)
    }

    private fun logDrop() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDropLogAt < 5_000) return
        lastDropLogAt = now
        LinkStatus.log("Mirror", "Wi-Fi is behind; dropping H.264 delta frames")
    }

    private fun readBuffer(format: MediaFormat, key: String): ByteArray? {
        val source = runCatching { format.getByteBuffer(key) }.getOrNull() ?: return null
        val buffer = source.duplicate()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    companion object {
        private const val MAX_QUEUE_BYTES = 1_048_576L
        private const val QUEUE_RESUME_BYTES = 262_144L

        fun create(
            handler: Handler,
            screenWidth: Int,
            screenHeight: Int,
            onFrame: (ByteArray) -> Unit,
            onFatal: (String) -> Unit,
            queueSize: () -> Long,
        ): H264Sink? {
            val choice = MirrorEncoderSelector.choose(screenWidth, screenHeight) ?: return null
            for (constantBitrate in booleanArrayOf(true, false)) {
                var codec: MediaCodec? = null
                var sink: H264Sink? = null
                try {
                    val created = MediaCodec.createByCodecName(choice.name)
                    codec = created
                    val createdSink = H264Sink(created, choice, handler, onFrame, onFatal, queueSize)
                    sink = createdSink
                    createdSink.configure(constantBitrate)
                    LinkStatus.log(
                        "Mirror",
                        "H.264 encoder started at ${choice.width}x${choice.height} " +
                            "(${if (constantBitrate) "CBR" else "VBR"})",
                    )
                    return sink
                } catch (error: Exception) {
                    if (sink != null) {
                        runCatching { sink.release() }
                    } else {
                        runCatching { codec?.release() }
                    }
                    LinkStatus.log(
                        "Mirror",
                        "AVC ${if (constantBitrate) "CBR" else "VBR"} setup failed: " +
                            error.javaClass.simpleName,
                    )
                }
            }
            return null
        }
    }
}

private fun codecErrorName(error: MediaCodec.CodecException): String =
    error.message?.takeIf { it.isNotBlank() } ?: "code ${error.errorCode}"
