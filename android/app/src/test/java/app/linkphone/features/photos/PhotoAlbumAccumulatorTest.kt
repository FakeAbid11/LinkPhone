package app.linkphone.features.photos

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoAlbumAccumulatorTest {
    @Test
    fun aggregatesCountsAndNewestCover() {
        val accumulator = PhotoAlbumAccumulator()
        accumulator.add("v:external_primary:42", "Camera", photoId = 10, dateSeconds = 100)
        accumulator.add("v:external_primary:42", "Camera", photoId = 12, dateSeconds = 200)
        accumulator.add("v:external_primary:42", "Camera", photoId = 11, dateSeconds = 150)
        accumulator.add("name:Other", "Other", photoId = 7, dateSeconds = 50)

        val albums = accumulator.result()
        assertEquals(2, albums.size)
        val camera = albums.first { it.id == "v:external_primary:42" }
        assertEquals("Camera", camera.name)
        assertEquals(3, camera.count)
        assertEquals("12", camera.coverId)
        assertEquals(200_000L, camera.latest)
        assertEquals("Other", albums.last().name)
    }

    @Test
    fun updatesCoverWhenAnOlderRowArrivesLast() {
        val accumulator = PhotoAlbumAccumulator()
        accumulator.add("v:external_primary:1", "Camera", photoId = 20, dateSeconds = 300)
        accumulator.add("v:external_primary:1", "Camera", photoId = 21, dateSeconds = 100)

        val album = accumulator.result().single()
        assertEquals("20", album.coverId)
        assertEquals(300_000L, album.latest)
        assertEquals(2, album.count)
    }

    @Test
    fun ignoresInvalidPhotoIds() {
        val accumulator = PhotoAlbumAccumulator()
        accumulator.add("v:external_primary:1", "Camera", photoId = 0, dateSeconds = 10)
        assertEquals(0, accumulator.result().size)
    }
}
