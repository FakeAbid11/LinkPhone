package app.linkphone.features.mirror

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.SystemClock
import android.view.Surface
import app.linkphone.core.protocol.BinaryCodec
import app.linkphone.core.util.LinkStatus
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * The original mirror path: low-rate JPEG snapshots. Kept as the fallback for
 * phones without a hardware AVC encoder and for the PC's Compatible mode.
 */
class JpegSink(
    override val width: Int,
    override val height: Int,
    private val handler: Handler,
    private val onFrame: (ByteArray) -> Unit,
) : MirrorSink {
    override val kind: String = MirrorCodecChoice.JPEG
    override val surface: Surface get() = reader.surface

    private val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    private var lastFrameAt = 0L
    private var sequence = 0
    private var started = false
    private var released = false

    override fun start() {
        if (started || released) return
        started = true
        reader.setOnImageAvailableListener({ source -> handleImage(source) }, handler)
    }

    override fun requestKeyframe() = Unit

    override fun release() {
        if (released) return
        released = true
        runCatching { reader.setOnImageAvailableListener(null, null) }
        runCatching { reader.close() }
    }

    private fun handleImage(source: ImageReader) {
        if (released) return
        val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameAt < FRAME_INTERVAL_MS) return
            val jpeg = imageToJpeg(image) ?: return
            lastFrameAt = now
            onFrame(
                BinaryCodec.encode(
                    type = BinaryCodec.TYPE_MIRROR,
                    streamId = 0,
                    seq = sequence++,
                    last = false,
                    payload = jpeg,
                ),
            )
        } catch (error: Throwable) {
            LinkStatus.log("Mirror", "Frame conversion failed: ${error.javaClass.simpleName}")
        } finally {
            image.close()
        }
    }

    private fun imageToJpeg(image: Image): ByteArray? {
        if (image.width <= 0 || image.height <= 0) return null
        val plane = image.planes.firstOrNull() ?: return null
        val pixelStride = plane.pixelStride
        if (pixelStride <= 0) return null
        val rowStride = plane.rowStride
        val paddedWidth = rowStride / pixelStride
        if (paddedWidth < image.width) return null

        plane.buffer.rewind()
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        val cropped = if (paddedWidth == image.width) {
            padded
        } else {
            Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        }
        if (cropped !== padded) padded.recycle()

        val longest = max(cropped.width, cropped.height)
        val scaled = if (longest > MirrorSizing.JPEG_MAX_EDGE) {
            val ratio = MirrorSizing.JPEG_MAX_EDGE.toFloat() / longest
            Bitmap.createScaledBitmap(
                cropped,
                (cropped.width * ratio).toInt().coerceAtLeast(1),
                (cropped.height * ratio).toInt().coerceAtLeast(1),
                true,
            ).also { cropped.recycle() }
        } else {
            cropped
        }

        return try {
            ByteArrayOutputStream().use { output ->
                if (scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    output.toByteArray()
                } else {
                    null
                }
            }
        } finally {
            scaled.recycle()
        }
    }

    private companion object {
        const val FRAME_INTERVAL_MS = 190L
        const val JPEG_QUALITY = 55
    }
}
