package app.linkphone.features.photos

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.util.Size
import app.linkphone.core.model.PhotoDto
import app.linkphone.core.util.FeaturePermissionException
import app.linkphone.core.util.LinkStatus
import app.linkphone.features.Permissions
import java.io.ByteArrayOutputStream

class PhotoRepository(private val context: Context) {
    private val resolver = context.contentResolver

    fun list(limit: Int = 200): List<PhotoDto> {
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
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val photos = ArrayList<PhotoDto>(safeLimit)
        try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media.IS_PENDING} = 0",
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val bucketIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext() && photos.size < safeLimit) {
                    val id = cursor.getLong(idIndex)
                    if (id <= 0) continue
                    photos += PhotoDto(
                        id = id.toString(),
                        name = cursor.getString(nameIndex) ?: "photo_$id",
                        mime = cursor.getString(mimeIndex) ?: "image/*",
                        size = cursor.getLong(sizeIndex),
                        date = cursor.getLong(dateIndex) * 1_000L,
                        width = cursor.getInt(widthIndex),
                        height = cursor.getInt(heightIndex),
                        bucket = if (bucketIndex >= 0) cursor.getString(bucketIndex) else null,
                    )
                }
            }
        } catch (error: Exception) {
            LinkStatus.log("Photos", "Photo query failed: ${error.javaClass.simpleName}")
        }
        return photos
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
