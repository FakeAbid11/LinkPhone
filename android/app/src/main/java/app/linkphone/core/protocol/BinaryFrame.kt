package app.linkphone.core.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class BinaryFrame(
    val type: Int,
    val streamId: Long,
    val seq: Int,
    val last: Boolean,
    val payload: ByteArray,
)

/**
 * 20-byte header, big endian:
 *   0 version, 1 type, 2-3 reserved, 4-11 stream id, 12-15 sequence, 16-19 flags (bit 0 = last).
 */
object BinaryCodec {
    const val HEADER_SIZE = 20
    const val VERSION = 1
    const val TYPE_PHOTO = 1
    const val TYPE_MIRROR = 2
    const val TYPE_FILE = 3
    const val MAX_PAYLOAD = 1_900_000
    const val CHUNK_SIZE = 64 * 1024

    fun encode(
        type: Int,
        streamId: Long,
        seq: Int,
        last: Boolean,
        payload: ByteArray,
    ): ByteArray {
        require(type in TYPE_PHOTO..TYPE_FILE) { "invalid frame type" }
        require(payload.size <= MAX_PAYLOAD) { "frame payload too large" }
        val buffer = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.put(VERSION.toByte())
        buffer.put(type.toByte())
        buffer.putShort(0)
        buffer.putLong(streamId)
        buffer.putInt(seq)
        buffer.putInt(if (last) 1 else 0)
        buffer.put(payload)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): BinaryFrame? {
        if (bytes.size < HEADER_SIZE) return null
        if (bytes[0].toInt() != VERSION) return null
        val type = bytes[1].toInt() and 0xFF
        if (type !in TYPE_PHOTO..TYPE_FILE) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.position(4)
        val streamId = buffer.long
        val seq = buffer.int
        val flags = buffer.int
        return BinaryFrame(
            type = type,
            streamId = streamId,
            seq = seq,
            last = (flags and 1) == 1,
            payload = bytes.copyOfRange(HEADER_SIZE, bytes.size),
        )
    }
}
