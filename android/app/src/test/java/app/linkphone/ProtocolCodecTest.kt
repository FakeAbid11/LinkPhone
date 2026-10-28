package app.linkphone

import app.linkphone.core.model.CallDialRequest
import app.linkphone.core.model.ContactDto
import app.linkphone.core.model.ContactListResult
import app.linkphone.core.model.Pong
import app.linkphone.core.model.SmsOpenThreadRequest
import app.linkphone.core.protocol.BinaryCodec
import app.linkphone.core.protocol.Envelope
import app.linkphone.core.protocol.ProtocolCodec
import app.linkphone.core.protocol.Types
import app.linkphone.core.protocol.decodeData
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCodecTest {
    @Test
    fun roundTripsAnEnvelopeAndPayload() {
        val envelope = ProtocolCodec.event(Types.PONG, ProtocolCodec.data(Pong(1234L)))
        val decoded = ProtocolCodec.decode(ProtocolCodec.encode(envelope))
        assertEquals(Types.PONG, decoded.type)
        assertEquals(envelope.id, decoded.id)
        assertEquals(1234L, decoded.decodeData<Pong>().timestamp)
    }

    @Test
    fun ignoresUnknownFields() {
        val decoded = ProtocolCodec.decode(
            """{"v":1,"id":"one","type":"ping","ts":0,"extra":"ignored"}""",
        )
        assertEquals("one", decoded.id)
        assertEquals(Types.PING, decoded.type)
    }

    @Test
    fun rejectsUnsupportedVersionsAndOversizedMessages() {
        assertThrows(IllegalArgumentException::class.java) {
            ProtocolCodec.decode("""{"v":2,"id":"one","type":"ping","ts":0}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProtocolCodec.decode("x".repeat(ProtocolCodec.MAX_JSON_BYTES + 1))
        }
    }

    @Test
    fun encodesAndDecodesBinaryFrames() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val encoded = BinaryCodec.encode(
            type = BinaryCodec.TYPE_PHOTO,
            streamId = 42L,
            seq = 7,
            last = true,
            payload = payload,
        )
        val decoded = BinaryCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(BinaryCodec.TYPE_PHOTO, decoded!!.type)
        assertEquals(42L, decoded.streamId)
        assertEquals(7, decoded.seq)
        assertTrue(decoded.last)
        assertArrayEqualsBytes(payload, decoded.payload)
    }

    @Test
    fun rejectsMalformedBinaryFrames() {
        assertNull(BinaryCodec.decode(ByteArray(4)))
        val encoded = BinaryCodec.encode(BinaryCodec.TYPE_MIRROR, 0L, 0, false, ByteArray(2))
        encoded[1] = 9
        assertNull(BinaryCodec.decode(encoded))
        assertThrows(IllegalArgumentException::class.java) {
            BinaryCodec.encode(
                BinaryCodec.TYPE_PHOTO,
                1L,
                0,
                false,
                ByteArray(BinaryCodec.MAX_PAYLOAD + 1),
            )
        }
    }

    @Test
    fun callDialRequestCarriesAnOptionalSim() {
        val encoded = ProtocolCodec.json.encodeToString(
            CallDialRequest.serializer(),
            CallDialRequest("+15550101000", 2),
        )
        assertTrue(encoded.contains("\"subId\":2"))
        val decoded = ProtocolCodec.json.decodeFromString(CallDialRequest.serializer(), encoded)
        assertEquals(2, decoded.subId)

        val defaultEncoded = ProtocolCodec.json.encodeToString(
            CallDialRequest.serializer(),
            CallDialRequest("+15550101000"),
        )
        assertFalse(defaultEncoded.contains("subId"))
    }

    @Test
    fun smsOpenThreadRequestRoundTrips() {
        val encoded = ProtocolCodec.json.encodeToString(
            SmsOpenThreadRequest.serializer(),
            SmsOpenThreadRequest("+15550101000"),
        )
        assertTrue(encoded.contains("+15550101000"))
        val decoded = ProtocolCodec.json.decodeFromString(
            SmsOpenThreadRequest.serializer(),
            encoded,
        )
        assertEquals("+15550101000", decoded.address)
    }

    @Test
    fun contactListResultRoundTrips() {
        val encoded = ProtocolCodec.json.encodeToString(
            ContactListResult.serializer(),
            ContactListResult(
                listOf(
                    ContactDto(
                        id = "42",
                        name = "Ada",
                        number = "+15550101000",
                        label = "Mobile",
                    ),
                ),
            ),
        )
        val decoded = ProtocolCodec.json.decodeFromString(
            ContactListResult.serializer(),
            encoded,
        )
        assertEquals(1, decoded.contacts.size)
        assertEquals("Ada", decoded.contacts[0].name)
        assertEquals("+15550101000", decoded.contacts[0].number)
    }

    @Test
    fun envelopeVersionIsStable() {
        assertEquals(1, Envelope.PROTOCOL_VERSION)
        assertFalse(ProtocolCodec.encode(ProtocolCodec.event(Types.PING)).isBlank())
    }

    private fun assertArrayEqualsBytes(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index -> assertEquals(expected[index], actual[index]) }
    }
}
