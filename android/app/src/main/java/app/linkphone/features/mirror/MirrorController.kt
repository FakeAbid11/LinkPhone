package app.linkphone.features.mirror

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Display
import android.view.WindowManager
import app.linkphone.core.model.MirrorState
import app.linkphone.core.protocol.BinaryCodec
import app.linkphone.core.util.LinkStatus
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Screen mirroring is intentionally best-effort: Android grants one virtual
 * display per consent token, stops capture when the screen locks, and requires
 * a new consent after rotation. LinkPhone surfaces those limits instead of
 * hiding them.
 */
class MirrorController(
    private val context: Context,
    private val onFrame: (ByteArray) -> Unit,
    private val onState: (MirrorState) -> Unit,
    private val onStopped: () -> Unit,
) {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var initialRotation = -1
    private var sequence = 0
    private var lastFrameAt = 0L
    private var stopping = false

    fun isActive(): Boolean = projection != null

    fun start(resultCode: Int, data: Intent) {
        if (isActive()) stop("Restarting screen mirroring")
        val manager = context.getSystemService(MediaProjectionManager::class.java)
            ?: throw IllegalStateException("MediaProjection is unavailable")
        val newProjection = manager.getMediaProjection(resultCode, data)
            ?: throw IllegalStateException("Screen capture consent was not granted")

        projection = newProjection
        try {
            val thread = HandlerThread("LinkPhoneMirror").also { it.start() }
            handlerThread = thread
            val workHandler = Handler(thread.looper)
            handler = workHandler

            val (width, height) = scaledScreenSize()
            val density = context.resources.displayMetrics.densityDpi
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            virtualDisplay = newProjection.createVirtualDisplay(
                "LinkPhone",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                workHandler,
            )
            reader.setOnImageAvailableListener({ source -> handleImage(source) }, workHandler)
            newProjection.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopInternal("Screen sharing was stopped on the phone")
                    }
                },
                workHandler,
            )

            initialRotation = currentRotation()
            registerDisplayListener(workHandler)
            LinkStatus.log("Mirror", "Mirroring started at ${width}x$height")
            onState(MirrorState(active = true, width = width, height = height))
        } catch (error: Exception) {
            stopInternal(error.message ?: "Screen sharing failed")
            throw error
        }
    }

    fun stop(message: String? = null) {
        stopInternal(message)
    }

    private fun stopInternal(message: String?) {
        if (stopping) return
        val wasActive = projection != null || imageReader != null
        if (!wasActive) {
            onState(MirrorState(active = false, message = message))
            return
        }
        stopping = true
        try {
            unregisterDisplayListener()
            imageReader?.setOnImageAvailableListener(null, null)
            virtualDisplay?.release()
            imageReader?.close()
            projection?.stop()
            projection = null
            virtualDisplay = null
            imageReader = null
            handlerThread?.quitSafely()
            handlerThread = null
            handler = null
            LinkStatus.log("Mirror", message ?: "Mirroring stopped")
            onState(MirrorState(active = false, message = message))
            onStopped()
        } finally {
            stopping = false
        }
    }

    private fun handleImage(reader: ImageReader) {
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameAt < FRAME_INTERVAL_MS) return
            val jpeg = imageToJpeg(image) ?: return
            lastFrameAt = now
            val frame = BinaryCodec.encode(
                type = BinaryCodec.TYPE_MIRROR,
                streamId = 0,
                seq = sequence++,
                last = false,
                payload = jpeg,
            )
            onFrame(frame)
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
        val scaled = if (longest > MAX_DIMENSION) {
            val ratio = MAX_DIMENSION.toFloat() / longest
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

    private fun scaledScreenSize(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        var width = metrics.widthPixels
        var height = metrics.heightPixels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = context.getSystemService(WindowManager::class.java)
                ?.currentWindowMetrics
                ?.bounds
            if (bounds != null) {
                width = bounds.width()
                height = bounds.height()
            }
        }
        val longest = max(width, height)
        if (longest > MAX_DIMENSION) {
            val ratio = MAX_DIMENSION.toFloat() / longest
            width = (width * ratio).toInt()
            height = (height * ratio).toInt()
        }
        return evenDimension(width) to evenDimension(height)
    }

    private fun evenDimension(value: Int): Int = (value.coerceAtLeast(2) / 2) * 2

    private fun currentRotation(): Int {
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        return display?.rotation ?: Configuration.ORIENTATION_UNDEFINED
    }

    private fun registerDisplayListener(workHandler: Handler) {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY && currentRotation() != initialRotation) {
                    workHandler.post {
                        stopInternal("Screen rotated. Start mirroring again to continue.")
                    }
                }
            }
        }
        manager.registerDisplayListener(listener, workHandler)
        displayListener = listener
    }

    private fun unregisterDisplayListener() {
        val listener = displayListener ?: return
        runCatching {
            context.getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(listener)
        }
        displayListener = null
    }

    private companion object {
        const val MAX_DIMENSION = 1_280
        const val FRAME_INTERVAL_MS = 190L
        const val JPEG_QUALITY = 55
    }
}
