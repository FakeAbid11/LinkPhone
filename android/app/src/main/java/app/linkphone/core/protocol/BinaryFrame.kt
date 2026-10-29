package app.linkphone.core.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class BinaryFrame(
    val type: Int,
    val streamId: Long,
    val seq: Int,
    val last: Boolean,
    val flags: Int,
    val payload: ByteArray,
) {
    val keyframe: Boolean get() = (flags and BinaryCodec.FLAG_KEY_FRAME) != 0
    val codecConfig: Boolean get() = (flags and BinaryCodec.FLAG_CODEC_CONFIG) != 0
}

/**
 * 20-byte header, big endian:
 *   0 version, 1 type, 2-3 reserved, 4-11 stream id, 12-15 sequence,
 *   16-19 flags (bit 0 last, bit 1 key frame, bit 2 codec config).
 */
object BinaryCodec {
    const val HEADER_SIZE = 20
    const val VERSION = 1
    const val TYPE_PHOTO = 1
    const val TYPE_MIRROR = 2
    const val TYPE_FILE = 3
    const val TYPE_VIDEO = 4
    const val MAX_PAYLOAD = 1_900_000
    const val CHUNK_SIZE = 64 * 1024

    const val FLAG_LAST = 1
    const val FLAG_KEY_FRAME = 2
    const val FLAG_CODEC_CONFIG = 4

    fun encode(
        type: Int,
        streamId: Long,
        seq: Int,
        last: Boolean,
        payload: ByteArray,
        flags: Int = 0,
    ): ByteArray {
        require(type in TYPE_PHOTO..TYPE_VIDEO) { "invalid frame type" }
        require(payload.size <= MAX_PAYLOAD) { "frame payload too large" }
        val headerFlags = (if (last) FLAG_LAST else 0) or flags
        val buffer = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.put(VERSION.toByte())
        buffer.put(type.toByte())
        buffer.putShort(0)
        buffer.putLong(streamId)
        buffer.putInt(seq)
        buffer.putInt(headerFlags)
        buffer.put(payload)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): BinaryFrame? {
        if (bytes.size < HEADER_SIZE) return null
        if (bytes[0].toInt() != VERSION) return null
        val type = bytes[1].toInt() and 0xFF
        if (type !in TYPE_PHOTO..TYPE_VIDEO) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.position(4)
        val streamId = buffer.long
        val seq = buffer.int
        val flags = buffer.int
        return BinaryFrame(
            type = type,
            streamId = streamId,
            seq = seq,
            last = (flags and FLAG_LAST) != 0,
            flags = flags,
            payload = bytes.copyOfRange(HEADER_SIZE, bytes.size),
        )
    }
}

data class DecodedVideoPayload(val ptsUs: Long, val accessUnit: ByteArray)

/** Type-4 payload: 8-byte big-endian presentation time, then one Annex B access unit. */
object VideoPayload {
    const val HEADER_SIZE = 8

    fun encode(ptsUs: Long, accessUnit: ByteArray): ByteArray {
        require(ptsUs >= 0) { "negative presentation time" }
        val buffer = ByteBuffer.allocate(HEADER_SIZE + accessUnit.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putLong(ptsUs)
        buffer.put(accessUnit)
        return buffer.array()
    }

    fun decode(payload: ByteArray): DecodedVideoPayload? {
        if (payload.size <= HEADER_SIZE) return null
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val ptsUs = buffer.long
        if (ptsUs < 0) return null
        return DecodedVideoPayload(ptsUs, payload.copyOfRange(HEADER_SIZE, payload.size))
    }
}
