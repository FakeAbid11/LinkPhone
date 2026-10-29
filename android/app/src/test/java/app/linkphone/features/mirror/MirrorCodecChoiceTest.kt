package app.linkphone.features.mirror

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorCodecChoiceTest {
    @Test
    fun defaultsToJpegWhenThePcDoesNotAsk() {
        assertEquals(MirrorCodecChoice.JPEG, MirrorCodecChoice.normalize(null))
        assertEquals(MirrorCodecChoice.JPEG, MirrorCodecChoice.resolve(null, h264Available = true))
    }

    @Test
    fun usesH264OnlyWhenThePhoneCanEncodeIt() {
        assertEquals(MirrorCodecChoice.H264, MirrorCodecChoice.resolve("h264", h264Available = true))
        assertEquals(MirrorCodecChoice.JPEG, MirrorCodecChoice.resolve("h264", h264Available = false))
    }

    @Test
    fun treatsUnknownValuesAsCompatibleMode() {
        assertEquals(MirrorCodecChoice.JPEG, MirrorCodecChoice.normalize("vp9"))
        assertEquals(MirrorCodecChoice.H264, MirrorCodecChoice.normalize(" H264 "))
    }
}
