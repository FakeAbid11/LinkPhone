package app.linkphone.features.mirror

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Display
import app.linkphone.core.model.MirrorState
import app.linkphone.core.net.ConnectionHub
import app.linkphone.core.util.LinkStatus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Screen mirroring is intentionally best-effort: Android grants one virtual
 * display per consent token, stops capture when the screen locks, and requires
 * a new consent after rotation. LinkPhone surfaces those limits instead of
 * hiding them.
 *
 * H.264 is preferred when both the phone and the PC support it; the JPEG sink
 * remains the fallback and the PC's Compatible mode.
 */
class MirrorController(
    private val context: Context,
    private val onFrame: (ByteArray) -> Unit,
    private val onState: (MirrorState) -> Unit,
    private val onStopped: () -> Unit,
) {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var sink: MirrorSink? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var initialRotation = -1

    @Volatile
    private var stopping = false
    @Volatile
    private var suppressProjectionStop = false

    fun isActive(): Boolean = projection != null

    fun start(
        resultCode: Int,
        data: Intent,
        requestedCodec: String = MirrorCodecChoice.JPEG,
        requestedFps: Int = MirrorCodecChoice.FPS_30,
    ) {
        if (isActive()) stop("Restarting screen mirroring")
        val manager = context.getSystemService(MediaProjectionManager::class.java)
            ?: throw IllegalStateException("MediaProjection is unavailable")
        val newProjection = manager.getMediaProjection(resultCode, data)
            ?: throw IllegalStateException("Screen capture consent was not granted")

        projection = newProjection
        suppressProjectionStop = false
        try {
            val thread = HandlerThread("LinkPhoneMirror").also { it.start() }
            handlerThread = thread
            val workHandler = Handler(thread.looper)
            handler = workHandler

            // Android 14+ rejects capture when the callback is not registered before
            // createVirtualDisplay() starts the MediaProjection.
            newProjection.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        if (!suppressProjectionStop) {
                            stopInternal("Screen sharing was stopped on the phone")
                        }
                    }
                },
                workHandler,
            )

            val screen = screenSize()
            val codec = MirrorCodecChoice.resolve(
                requestedCodec,
                MirrorEncoderSelector.avcEncoderAvailable(),
            )
            val fps = MirrorCodecChoice.normalizeFps(requestedFps)
            var selected: MirrorSink? = null
            if (codec == MirrorCodecChoice.H264) {
                selected = runCatching {
                    onHandler {
                        H264Sink.create(
                            handler = workHandler,
                            screenWidth = screen.first,
                            screenHeight = screen.second,
                            fps = fps,
                            onFrame = onFrame,
                            onFatal = { message -> workHandler.post { stopInternal(message) } },
                            queueSize = ConnectionHub::queueSize,
                        )
                    }
                }.getOrNull()
                if (selected == null) {
                    LinkStatus.log("Mirror", "No usable hardware AVC encoder; using JPEG")
                }
            }
            if (selected == null) {
                val (width, height) = MirrorSizing.jpegSize(screen.first, screen.second)
                selected = onHandler {
                    JpegSink(width, height, workHandler, onFrame)
                }
            }

            val activeSink = selected ?: error("No mirror sink available")
            sink = activeSink
            activeSink.start()
            virtualDisplay = newProjection.createVirtualDisplay(
                "LinkPhone",
                activeSink.width,
                activeSink.height,
                context.resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                activeSink.surface,
                null,
                workHandler,
            )

            initialRotation = currentRotation()
            registerDisplayListener(workHandler)
            LinkStatus.log(
                "Mirror",
                "Mirroring started at ${activeSink.width}x${activeSink.height} (${activeSink.kind})",
            )
            onState(
                MirrorState(
                    active = true,
                    width = activeSink.width,
                    height = activeSink.height,
                    codec = activeSink.kind,
                    fps = activeSink.fps,
                ),
            )
        } catch (error: Exception) {
            stopInternal(error.message ?: "Screen sharing failed")
            throw error
        }
    }

    fun stop(message: String? = null) {
        stopInternal(message)
    }

    fun requestKeyframe() {
        val current = handler ?: return
        current.post { sink?.requestKeyframe() }
    }

    private fun stopInternal(message: String?) {
        if (stopping) return
        val current = handler
        if (current != null && Looper.myLooper() != current.looper) {
            val stopped = runCatching {
                onHandler { stopOnHandler(message) }
            }.isSuccess
            if (stopped) return
        }
        stopOnHandler(message)
    }

    private fun stopOnHandler(message: String?) {
        if (stopping) return
        val wasActive = projection != null || sink != null
        if (!wasActive) {
            onState(MirrorState(active = false, message = message))
            return
        }
        stopping = true
        try {
            unregisterDisplayListener()
            virtualDisplay?.release()
            virtualDisplay = null
            sink?.release()
            sink = null
            suppressProjectionStop = true
            projection?.stop()
            projection = null
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

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        // The app window is smaller than the display on a device with a cutout,
        // and the virtual display must cover the real screen or the mirror is
        // cropped at the edges.
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            val metrics = android.util.DisplayMetrics()
            display.getRealMetrics(metrics)
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                return metrics.widthPixels to metrics.heightPixels
            }
        }
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels.coerceAtLeast(2) to metrics.heightPixels.coerceAtLeast(2)
    }

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

    private fun <T> onHandler(block: () -> T): T {
        val current = handler ?: return block()
        if (Looper.myLooper() == current.looper) return block()
        var result: Result<T>? = null
        val latch = CountDownLatch(1)
        if (!current.post {
                result = runCatching(block)
                latch.countDown()
            }
        ) {
            error("Mirror thread is unavailable")
        }
        if (!latch.await(5, TimeUnit.SECONDS)) error("Mirror setup timed out")
        return result?.getOrThrow() ?: error("Mirror setup failed")
    }
}
