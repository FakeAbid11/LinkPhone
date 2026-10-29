package app.linkphone.features.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoQueryTest {
    @Test
    fun pendingFilterIsAlwaysPresent() {
        val query = PhotoQuery.selection(null, null, null)
        assertTrue(query.selection.contains("is_pending = 0"))
        assertEquals(0, query.args.size)
    }

    @Test
    fun volumeBucketUsesBoundVolumeAndId() {
        val query = PhotoQuery.selection("v:external_primary:42", null, null)
        assertTrue(query.selection.contains("volume_name = ?"))
        assertTrue(query.selection.contains("bucket_id = ?"))
        assertEquals(listOf("external_primary", "42"), query.args.toList())
    }

    @Test
    fun bucketIdOnlyUsesTheNumericBucket() {
        val query = PhotoQuery.selection("b:42", null, null)
        assertTrue(query.selection.contains("bucket_id = ?"))
        assertEquals(listOf("42"), query.args.toList())
    }

    @Test
    fun nameFallbackOnlyMatchesRowsWithoutABucketId() {
        val query = PhotoQuery.selection("name:Other", null, null)
        assertTrue(query.selection.contains("bucket_display_name = ?"))
        assertTrue(query.selection.contains("bucket_id IS NULL"))
        assertEquals(listOf("Other"), query.args.toList())
    }

    @Test
    fun keysetUsesSecondsAndBothTieBreakers() {
        val query = PhotoQuery.selection(
            bucket = "v:external_primary:42",
            beforeDateMs = 1_700_000_123_999,
            beforeId = "99",
        )
        assertTrue(query.selection.contains(PhotoQuery.DATE_EXPRESSION))
        assertEquals(
            listOf("external_primary", "42", "1700000123", "1700000123", "99"),
            query.args.toList(),
        )
    }

    @Test
    fun ignoresInvalidPagingValues() {
        val query = PhotoQuery.selection(null, 0, "not-a-number")
        assertEquals(0, query.args.size)
        assertFalse(query.selection.contains("DATE_ADDED <"))
    }
}
