package app.linkphone.features.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MirrorSizingTest {
    @Test
    fun scalesPortraitScreensToA720By1600Stream() {
        assertEquals(720 to 1600, MirrorSizing.scaleToLongEdge(1080, 2400, 1600))
        assertEquals(
            720 to 1600,
            MirrorSizing.chooseVideoSize(1080, 2400, 16, 16) { _, _ -> true },
        )
    }

    @Test
    fun scalesLandscapeScreens() {
        assertEquals(1600 to 720, MirrorSizing.scaleToLongEdge(2400, 1080, 1600))
    }

    @Test
    fun stepsDownWhenTheEncoderRejectsTheLargestSize() {
        val size = MirrorSizing.chooseVideoSize(1080, 2400, 16, 16) { width, _ -> width == 640 }
        assertEquals(640 to 1440, size)
    }

    @Test
    fun returnsNullWhenNothingIsSupported() {
        assertNull(MirrorSizing.chooseVideoSize(1080, 2400, 16, 16) { _, _ -> false })
    }

    @Test
    fun keepsTheJpegPathAt1280() {
        assertEquals(576 to 1280, MirrorSizing.jpegSize(1080, 2400))
        assertEquals(1280 to 720, MirrorSizing.jpegSize(1920, 1080))
    }

    @Test
    fun alignsDownToEncoderSteps() {
        assertEquals(896, MirrorSizing.alignDown(900, 16))
        assertEquals(704, MirrorSizing.alignDown(719, 16))
    }
}
