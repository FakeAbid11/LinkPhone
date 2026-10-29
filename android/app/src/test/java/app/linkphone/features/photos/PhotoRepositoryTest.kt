package app.linkphone.features.photos

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoRepositoryTest {
    @Test
    fun samplesLargeImagesDownToTheTargetDimension() {
        assertEquals(2, PhotoRepository.calculateInSampleSize(4_000, 3_000, 2_048))
        assertEquals(8, PhotoRepository.calculateInSampleSize(12_000, 9_000, 2_048))
        assertEquals(4, PhotoRepository.calculateInSampleSize(8_000, 6_000, 2_048))
    }

    @Test
    fun keepsImagesAtOrBelowTheTargetDimension() {
        assertEquals(1, PhotoRepository.calculateInSampleSize(2_048, 1_536, 2_048))
        assertEquals(1, PhotoRepository.calculateInSampleSize(800, 600, 2_048))
    }

    @Test
    fun handlesInvalidDimensions() {
        assertEquals(1, PhotoRepository.calculateInSampleSize(0, 600, 2_048))
        assertEquals(1, PhotoRepository.calculateInSampleSize(800, 600, 0))
    }
}
