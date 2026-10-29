package app.linkphone.features.mirror

import app.linkphone.core.protocol.BinaryCodec
import app.linkphone.core.protocol.VideoPayload
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class VideoFrameCodecTest {
    @Test
    fun roundTripsVideoFramesWithFlags() {
        val accessUnit = byteArrayOf(0, 0, 0, 1, 0x65, 0x88.toByte())
        val payload = VideoPayload.encode(10_000, accessUnit)
        val encoded = BinaryCodec.encode(
            type = BinaryCodec.TYPE_VIDEO,
            streamId = 0,
            seq = 7,
            last = false,
            payload = payload,
            flags = BinaryCodec.FLAG_KEY_FRAME,
        )
        val decoded = BinaryCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(BinaryCodec.TYPE_VIDEO, decoded!!.type)
        assertTrue(decoded.keyframe)
        assertFalse(decoded.codecConfig)
        assertFalse(decoded.last)
        val video = VideoPayload.decode(decoded.payload)
        assertEquals(10_000L, video!!.ptsUs)
        assertArrayEquals(accessUnit, video.accessUnit)
    }

    @Test
    fun carriesTheCodecConfigFlag() {
        val encoded = BinaryCodec.encode(
            type = BinaryCodec.TYPE_VIDEO,
            streamId = 0,
            seq = 0,
            last = false,
            payload = VideoPayload.encode(0, byteArrayOf(1, 2, 3)),
            flags = BinaryCodec.FLAG_CODEC_CONFIG,
        )
        val decoded = BinaryCodec.decode(encoded)!!
        assertTrue(decoded.codecConfig)
        assertFalse(decoded.keyframe)
    }

    @Test
    fun rejectsUnknownTypesAndOversizedPayloads() {
        assertThrows(IllegalArgumentException::class.java) {
            BinaryCodec.encode(5, 0, 0, false, ByteArray(2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BinaryCodec.encode(
                BinaryCodec.TYPE_VIDEO,
                0,
                0,
                false,
                ByteArray(BinaryCodec.MAX_PAYLOAD + 1),
            )
        }
    }
}
