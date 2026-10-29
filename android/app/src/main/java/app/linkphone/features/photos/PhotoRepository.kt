package app.linkphone.features.photos

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.util.Size
import app.linkphone.core.model.PhotoAlbumDto
import app.linkphone.core.model.PhotoDto
import app.linkphone.core.util.FeaturePermissionException
import app.linkphone.core.util.LinkStatus
import app.linkphone.features.Permissions
import java.io.ByteArrayOutputStream

data class PhotoPage(
    val photos: List<PhotoDto>,
    val hasMore: Boolean,
)

class PhotoRepository(private val context: Context) {
    private val resolver = context.contentResolver

    fun list(
        limit: Int = 200,
        bucket: String? = null,
        beforeDate: Long? = null,
        beforeId: String? = null,
    ): PhotoPage {
        if (!Permissions.photos(context)) {
            throw FeaturePermissionException("Photo access is not granted")
        }
        val safeLimit = limit.coerceIn(1, 500)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val query = PhotoQuery.selection(bucket, beforeDate, beforeId)
        val photos = ArrayList<PhotoDto>(safeLimit)
        var hasMore = false
        try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                query.selection,
                query.args,
                PhotoQuery.SORT_ORDER,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dateModifiedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val bucketIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    if (id <= 0) continue
                    if (photos.size == safeLimit) {
                        hasMore = true
                        break
                    }
                    photos += PhotoDto(
                        id = id.toString(),
                        name = cursor.getString(nameIndex) ?: "photo_$id",
                        mime = cursor.getString(mimeIndex) ?: "image/*",
                        size = cursor.getLong(sizeIndex),
                        date = readDateSeconds(cursor, dateAddedIndex, dateModifiedIndex) * 1_000L,
                        width = cursor.getInt(widthIndex),
                        height = cursor.getInt(heightIndex),
                        bucket = if (bucketIndex >= 0) cursor.getString(bucketIndex) else null,
                    )
                }
            }
        } catch (error: Exception) {
            LinkStatus.log("Photos", "Photo query failed: ${error.javaClass.simpleName}")
        }
        return PhotoPage(photos, hasMore)
    }

    fun albums(): List<PhotoAlbumDto> {
        if (!Permissions.photos(context)) {
            throw FeaturePermissionException("Photo access is not granted")
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.VOLUME_NAME,
        )
        val query = PhotoQuery.selection(null, null, null)
        val accumulator = PhotoAlbumAccumulator()
        var rows = 0
        try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                query.selection,
                query.args,
                PhotoQuery.SORT_ORDER,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dateModifiedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val bucketIdIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val volumeIndex = cursor.getColumnIndex(MediaStore.Images.Media.VOLUME_NAME)
                while (cursor.moveToNext()) {
                    if (rows >= MAX_ALBUM_ROWS) {
                        LinkStatus.log("Photos", "Album scan capped at $MAX_ALBUM_ROWS photos")
                        break
                    }
                    rows += 1
                    val id = cursor.getLong(idIndex)
                    if (id <= 0) continue
                    val name = if (bucketNameIndex >= 0) {
                        cursor.getString(bucketNameIndex)?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    } ?: "Other"
                    val bucketId = if (bucketIdIndex >= 0 && !cursor.isNull(bucketIdIndex)) {
                        cursor.getLong(bucketIdIndex)
                    } else {
                        null
                    }
                    val volume = if (volumeIndex >= 0) {
                        cursor.getString(volumeIndex)?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                    val albumId = when {
                        bucketId != null && volume != null -> "v:$volume:$bucketId"
                        bucketId != null -> "b:$bucketId"
                        else -> "name:$name"
                    }
                    accumulator.add(
                        albumId = albumId,
                        name = name,
                        photoId = id,
                        dateSeconds = readDateSeconds(cursor, dateAddedIndex, dateModifiedIndex),
                    )
                }
            }
        } catch (error: Exception) {
            LinkStatus.log("Photos", "Album query failed: ${error.javaClass.simpleName}")
        }
        return accumulator.result()
    }

    fun loadJpeg(id: String, kind: String): ByteArray? {
        if (!Permissions.photos(context)) {
            throw FeaturePermissionException("Photo access is not granted")
        }
        val numericId = id.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, numericId)
        val thumbnail = kind == "thumb"
        return try {
            if (thumbnail) loadThumbnail(uri) else loadScaled(uri, MAX_FULL_DIMENSION)
        } catch (error: OutOfMemoryError) {
            LinkStatus.log("Photos", "Out of memory decoding photo $numericId")
            null
        } catch (error: Exception) {
            LinkStatus.log("Photos", "Photo decode failed: ${error.javaClass.simpleName}")
            null
        }
    }

    private fun readDateSeconds(cursor: Cursor, addedIndex: Int, modifiedIndex: Int): Long {
        val added = if (addedIndex >= 0) cursor.getLong(addedIndex) else 0L
        if (added > 0L) return added
        return if (modifiedIndex >= 0) cursor.getLong(modifiedIndex) else 0L
    }

    private fun loadThumbnail(uri: android.net.Uri): ByteArray? {
        val bitmap = resolver.loadThumbnail(uri, Size(512, 512), null) ?: return null
        return bitmap.use { compressJpeg(it, 80) }
    }

    private fun loadScaled(uri: android.net.Uri, maxDimension: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        var scaled: Bitmap? = null
        return try {
            val longest = maxOf(decoded.width, decoded.height)
            val output = if (longest > maxDimension) {
                val ratio = maxDimension.toFloat() / longest
                val width = (decoded.width * ratio).toInt().coerceAtLeast(1)
                val height = (decoded.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, width, height, true).also { scaled = it }
            } else {
                decoded
            }
            compressJpeg(output, 90)
        } finally {
            if (scaled != null && scaled !== decoded) scaled?.recycle()
            decoded.recycle()
        }
    }

    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        return try {
            if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                output.toByteArray()
            } else {
                null
            }
        } finally {
            output.close()
        }
    }

    companion object {
        const val MAX_FULL_DIMENSION = 2_048
        private const val MAX_ALBUM_ROWS = 50_000

        fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
            if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
            var sample = 1
            while (maxOf(width, height) / (sample * 2) >= maxDimension) {
                sample *= 2
            }
            return sample
        }
    }
}

private inline fun <T> Bitmap.use(block: (Bitmap) -> T): T = try {
    block(this)
} finally {
    recycle()
}
