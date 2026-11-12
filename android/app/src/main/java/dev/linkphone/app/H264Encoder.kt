package dev.linkphone.app

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hardware H.264 encoder fed by a camera surface. Emits an avcC config once and
 * AVCC (length-prefixed) samples afterwards, so WebCodecs can decode them.
 */
class H264Encoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val bitrate: Int,
    private val onConfig: (width: Int, height: Int, fps: Int, avcC: ByteArray) -> Unit,
    private val onSample: (flags: Int, seq: Int, ptsUs: Long, data: ByteArray) -> Boolean,
    private val onError: (String) -> Unit
) {
    companion object {
        const val FLAG_KEYFRAME = 1
        private const val TAG = "LinkPhone"
        private const val MIME = "video/avc"
        private const val NAL_SPS = 7
        private const val NAL_PPS = 8
        private const val NAL_IDR = 5

        // H.264 level_idc values.
        private const val LEVEL_3_0 = 0x1E
        private const val LEVEL_3_1 = 0x1F
        private const val LEVEL_3_2 = 0x20
        private const val LEVEL_4_0 = 0x28
        private const val LEVEL_4_2 = 0x2A

        fun levelFor(width: Int, height: Int, fps: Int): Int = when {
            width >= 1920 || height >= 1080 -> if (fps > 30) LEVEL_4_2 else LEVEL_4_0
            width >= 1280 || height >= 720 -> if (fps > 30) LEVEL_3_2 else LEVEL_3_1
            else -> LEVEL_3_0
        }
    }

    var inputSurface: Surface? = null
        private set

    private val thread = HandlerThread("linkphone-h264").apply { start() }
    private val handler = Handler(thread.looper)
    private val stopped = AtomicBoolean(false)
    private var codec: MediaCodec? = null
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    private var config: ByteArray? = null
    private var configSent = false
    private var needSync = false
    private var frameSeq = 0

    fun configureAndStart(): Boolean = tryStart(true) || tryStart(false)

    private fun tryStart(includeProfile: Boolean): Boolean {
        var created: MediaCodec? = null
        return try {
            val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                )
                if (includeProfile) {
                    setInteger(
                        MediaFormat.KEY_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
                    )
                    setInteger(MediaFormat.KEY_LEVEL, levelFor(width, height, fps))
                }
            }
            created = MediaCodec.createEncoderByType(MIME)
            created.setCallback(callback, handler)
            created.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = created.createInputSurface()
            created.start()
            codec = created
            true
        } catch (e: Exception) {
            Log.w(TAG, "cannot start H.264 encoder", e)
            runCatching { created?.release() }
            runCatching { inputSurface?.release() }
            inputSurface = null
            false
        }
    }

    fun requestKeyframe() {
        val current = codec ?: return
        runCatching {
            current.setParameters(
                Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }
            )
        }
        config?.let { onConfig(width, height, fps, it) }
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { inputSurface?.release() }
        inputSurface = null
        thread.quitSafely()
    }

    private val callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            // Surface input: no byte-buffer input is offered.
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            try {
                val buffer = codec.getOutputBuffer(index) ?: return
                if (info.size > 0) {
                    val data = ByteArray(info.size)
                    buffer.position(info.offset)
                    buffer.get(data)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        collectCsd(data)
                    } else {
                        emit(data, info.presentationTimeUs, info.flags)
                    }
                }
                codec.releaseOutputBuffer(index, false)
            } catch (e: Exception) {
                Log.w(TAG, "encoder output", e)
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.w(TAG, "encoder error", e)
            onError("encoder")
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            format.getByteBuffer("csd-0")?.let { collectCsd(it.toBytes()) }
            format.getByteBuffer("csd-1")?.let { collectCsd(it.toBytes()) }
        }
    }

    private fun ByteBuffer.toBytes(): ByteArray {
        val copy = duplicate()
        val bytes = ByteArray(copy.remaining())
        copy.get(bytes)
        return bytes
    }

    private fun collectCsd(data: ByteArray) {
        for (nal in splitNals(data)) {
            when (nalType(nal)) {
                NAL_SPS -> sps = nal
                NAL_PPS -> pps = nal
            }
        }
        maybeSendConfig()
    }

    private fun maybeSendConfig() {
        if (configSent) return
        val currentSps = sps ?: return
        val currentPps = pps ?: return
        if (currentSps.size < 4 || currentPps.isEmpty()) return
        val avcC = buildAvcC(currentSps, currentPps)
        config = avcC
        configSent = true
        onConfig(width, height, fps, avcC)
    }

    private fun emit(data: ByteArray, ptsUs: Long, bufferFlags: Int) {
        val nals = splitNals(data)
        if (nals.isEmpty()) return
        if (!configSent) {
            maybeSendConfig()
            if (!configSent) return
        }
        val keyframe = (bufferFlags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 ||
            nals.any { nalType(it) == NAL_IDR }
        if (needSync && !keyframe) return
        if (keyframe && needSync) {
            config?.let { onConfig(width, height, fps, it) }
            needSync = false
        }
        frameSeq += 1
        val sent = onSample(
            if (keyframe) FLAG_KEYFRAME else 0,
            frameSeq,
            ptsUs,
            toAvcc(nals)
        )
        if (!sent) {
            needSync = true
            requestKeyframe()
        }
    }

    private fun nalType(nal: ByteArray): Int =
        if (nal.isEmpty()) 0 else nal[0].toInt() and 0x1F

    /** Accepts Annex-B or length-prefixed output and returns bare NAL units. */
    private fun splitNals(data: ByteArray): List<ByteArray> {
        val annexB = data.size >= 3 &&
            data[0].toInt() == 0 && data[1].toInt() == 0 &&
            (data[2].toInt() == 1 || (data.size >= 4 && data[2].toInt() == 0 && data[3].toInt() == 1))
        if (annexB) {
            val nals = mutableListOf<ByteArray>()
            var start = -1
            var i = 0
            while (i + 2 < data.size) {
                val sc4 = i + 3 < data.size && data[i].toInt() == 0 && data[i + 1].toInt() == 0 &&
                    data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1
                val sc3 = data[i].toInt() == 0 && data[i + 1].toInt() == 0 &&
                    data[i + 2].toInt() == 1
                if (sc4 || sc3) {
                    if (start >= 0 && i > start) nals.add(data.copyOfRange(start, i))
                    i += if (sc4) 4 else 3
                    start = i
                } else {
                    i += 1
                }
            }
            if (start in 0 until data.size) nals.add(data.copyOfRange(start, data.size))
            return nals.filter { it.isNotEmpty() }
        }

        var offset = 0
        val prefixed = mutableListOf<ByteArray>()
        while (offset + 4 <= data.size) {
            val length = ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
            offset += 4
            if (length <= 0 || offset + length > data.size) return listOf(data)
            prefixed.add(data.copyOfRange(offset, offset + length))
            offset += length
        }
        return if (offset == data.size && prefixed.isNotEmpty()) prefixed else listOf(data)
    }

    private fun toAvcc(nals: List<ByteArray>): ByteArray {
        var size = 0
        for (nal in nals) size += 4 + nal.size
        val out = ByteBuffer.allocate(size)
        for (nal in nals) {
            out.putInt(nal.size)
            out.put(nal)
        }
        return out.array()
    }

    private fun buildAvcC(currentSps: ByteArray, currentPps: ByteArray): ByteArray {
        val out = ByteBuffer.allocate(11 + currentSps.size + currentPps.size)
        out.put(1)
        out.put(currentSps[1])
        out.put(currentSps[2])
        out.put(currentSps[3])
        out.put(0xFF.toByte())
        out.put(0xE1.toByte())
        out.putShort(currentSps.size.toShort())
        out.put(currentSps)
        out.put(1)
        out.putShort(currentPps.size.toShort())
        out.put(currentPps)
        return out.array()
    }
}
