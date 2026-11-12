package dev.linkphone.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CameraState(
    val streaming: Boolean = false,
    val facing: String = "back",
    val quality: String = "1080p",
    val fps: Int = 30,
    val width: Int = 0,
    val height: Int = 0,
    val codec: String = "h264",
    val error: String? = null
)

/**
 * Owns the camera session. The foreground service only runs while the link is up; the
 * desktop can start it only while the phone app is visible (Android 14 rule).
 */
object CameraHub {
    private const val TAG = "LinkPhone"

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    @Volatile private var service: CameraStreamService? = null

    fun start(context: Context, facing: String, quality: String, fps: Int) {
        val cleanFacing = if (facing == "front") "front" else "back"
        val cleanQuality = when (quality) {
            "480p", "720p", "1080p" -> quality
            else -> "1080p"
        }
        val cleanFps = if (fps == 60 && cleanQuality != "480p") 60 else 30
        if (!LinkCore.appVisible) {
            reportError("open_app")
            return
        }
        val client = LinkCore.clientOrNull()
        if (client == null || !client.isReady) {
            reportError("disconnected")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            reportError("permission")
            return
        }
        if (!client.supportsCameraH264) {
            reportError("update_pc")
            return
        }
        _state.value = CameraState(
            streaming = true,
            facing = cleanFacing,
            quality = cleanQuality,
            fps = cleanFps
        )
        val intent = Intent(context, CameraStreamService::class.java).apply {
            action = CameraStreamService.ACTION_START
            putExtra(CameraStreamService.EXTRA_FACING, cleanFacing)
            putExtra(CameraStreamService.EXTRA_QUALITY, cleanQuality)
            putExtra(CameraStreamService.EXTRA_FPS, cleanFps)
        }
        runCatching { ContextCompat.startForegroundService(context, intent) }
            .onFailure {
                Log.w(TAG, "cannot start camera service", it)
                reportError("busy")
            }
        sendState()
    }

    fun stop(reason: String? = null) {
        val context = LinkCore.contextOrNull()
        if (context != null) {
            runCatching { context.stopService(Intent(context, CameraStreamService::class.java)) }
        }
        val wasStreaming = _state.value.streaming
        _state.value = _state.value.copy(streaming = false, error = reason)
        if (wasStreaming || reason != null) sendState()
    }

    fun onServiceStopped() {
        if (!_state.value.streaming) return
        _state.value = _state.value.copy(streaming = false)
        sendState()
    }

    fun reportError(code: String) {
        _state.value = _state.value.copy(streaming = false, error = code)
        sendState()
    }

    fun requestKeyframe() {
        service?.requestKeyframe()
    }

    internal fun attachService(running: CameraStreamService?) {
        service = running
    }

    internal fun updateActive(quality: String, fps: Int, width: Int, height: Int) {
        _state.value = _state.value.copy(
            streaming = true,
            quality = quality,
            fps = fps,
            width = width,
            height = height,
            codec = "h264",
            error = null
        )
        sendState()
    }

    private fun sendState() {
        val current = _state.value
        LinkCore.clientOrNull()?.sendCameraState(
            current.streaming,
            current.facing,
            current.quality,
            current.fps,
            current.width,
            current.height,
            current.error
        )
    }
}

/**
 * CameraX needs a LifecycleOwner; the service is one, so the stream survives the screen
 * locking (the activity only gates starting it). A hardware H.264 encoder is fed by a
 * Preview surface, and CameraX falls back through lower settings if a vendor rejects them.
 */
class CameraStreamService : LifecycleService() {

    private data class Attempt(val quality: String, val fps: Int)

    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var encoder: H264Encoder? = null
    private var facing = "back"
    private var attempts: List<Attempt> = emptyList()
    private var attemptIndex = 0

    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val permissionWatchdog = object : Runnable {
        override fun run() {
            if (!hasCameraPermission()) {
                runCatching { provider?.unbindAll() }
                CameraHub.reportError("permission")
                stopSelf()
                return
            }
            watchdogHandler.postDelayed(this, WATCHDOG_MS)
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    override fun onCreate() {
        super.onCreate()
        CameraHub.attachService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopStreaming()
            stopSelf()
            return START_NOT_STICKY
        }
        // A camera-type foreground service without the grant throws on API 34+.
        if (!hasCameraPermission()) {
            CameraHub.reportError("permission")
            stopSelf()
            return START_NOT_STICKY
        }
        facing = intent?.getStringExtra(EXTRA_FACING) ?: facing
        val quality = intent?.getStringExtra(EXTRA_QUALITY) ?: "1080p"
        val fps = intent?.getIntExtra(EXTRA_FPS, 30) ?: 30
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        )
        attempts = buildAttempts(quality, fps)
        attemptIndex = 0
        startAttempt()
        watchdogHandler.removeCallbacks(permissionWatchdog)
        watchdogHandler.postDelayed(permissionWatchdog, WATCHDOG_MS)
        return START_NOT_STICKY
    }

    private fun buildAttempts(quality: String, fps: Int): List<Attempt> {
        val list = mutableListOf<Attempt>()
        list.add(Attempt(quality, fps))
        if (fps == 60) list.add(Attempt(quality, 30))
        if (quality == "1080p") {
            list.add(Attempt("720p", 30))
            list.add(Attempt("480p", 30))
        } else if (quality == "720p") {
            list.add(Attempt("480p", 30))
        }
        return list.distinct()
    }

    private fun startAttempt() {
        val attempt = attempts.getOrNull(attemptIndex)
        if (attempt == null) {
            CameraHub.reportError("unsupported")
            stopSelf()
            return
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val cameraProvider = runCatching { future.get() }.getOrNull()
            if (cameraProvider == null) {
                nextAttempt()
                return@addListener
            }
            val selector = if (facing == "front") {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            val builder = Preview.Builder().setTargetResolution(sizeOf(attempt.quality))
            runCatching { builder.setTargetFrameRate(Range(attempt.fps, attempt.fps)) }
            runCatching {
                Camera2Interop.Extender(builder).setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(attempt.fps, attempt.fps)
                )
            }
            val useCase = builder.build()
            useCase.setSurfaceProvider(ContextCompat.getMainExecutor(this)) { request ->
                provideSurface(request, attempt)
            }
            runCatching {
                if (!cameraProvider.hasCamera(selector)) error("camera unavailable")
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, useCase)
            }.onSuccess {
                encoder?.stop()
                encoder = null
                provider = cameraProvider
                preview = useCase
            }.onFailure {
                Log.w(TAG, "cannot bind ${attempt.quality}@${attempt.fps}", it)
                nextAttempt()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun provideSurface(request: SurfaceRequest, attempt: Attempt) {
        encoder?.stop()
        encoder = null
        val size = request.resolution
        val codec = H264Encoder(
            width = size.width,
            height = size.height,
            fps = attempt.fps,
            bitrate = bitrateFor(attempt.quality, attempt.fps),
            onConfig = { w, h, f, avcC ->
                LinkCore.clientOrNull()?.sendCameraConfig(w, h, f, avcC)
            },
            onSample = { flags, seq, pts, data ->
                LinkCore.clientOrNull()?.sendCameraFrame(flags, seq, pts, data) ?: false
            },
            onError = { code ->
                CameraHub.reportError(code)
                stopSelf()
            }
        )
        if (!codec.configureAndStart()) {
            codec.stop()
            request.willNotProvideSurface()
            nextAttempt()
            return
        }
        encoder = codec
        CameraHub.updateActive(attempt.quality, attempt.fps, size.width, size.height)
        request.provideSurface(codec.inputSurface!!, ContextCompat.getMainExecutor(this)) {
            codec.stop()
            if (encoder === codec) encoder = null
        }
    }

    private fun nextAttempt() {
        attemptIndex += 1
        encoder?.stop()
        encoder = null
        runCatching { provider?.unbindAll() }
        preview = null
        startAttempt()
    }

    fun requestKeyframe() {
        encoder?.requestKeyframe()
    }

    private fun stopStreaming() {
        watchdogHandler.removeCallbacks(permissionWatchdog)
        encoder?.stop()
        encoder = null
        runCatching { provider?.unbindAll() }
        provider = null
        preview = null
    }

    override fun onDestroy() {
        CameraHub.attachService(null)
        stopStreaming()
        CameraHub.onServiceStopped()
        super.onDestroy()
    }

    private fun sizeOf(quality: String): Size = when (quality) {
        "480p" -> Size(854, 480)
        "720p" -> Size(1280, 720)
        else -> Size(1920, 1080)
    }

    private fun bitrateFor(quality: String, fps: Int): Int = when (quality) {
        "1080p" -> if (fps > 30) 14_000_000 else 8_000_000
        "720p" -> if (fps > 30) 6_000_000 else 4_000_000
        else -> 2_000_000
    }

    private fun buildNotification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Camera streaming", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, CameraStreamService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle("Camera streaming")
            .setContentText("Sending video to your PC")
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Stop", stop)
            .build()
    }

    companion object {
        private const val TAG = "LinkPhone"
        private const val CHANNEL_ID = "linkphone_camera"
        private const val NOTIFICATION_ID = 7
        private const val WATCHDOG_MS = 5_000L
        const val ACTION_START = "dev.linkphone.app.CAMERA_START"
        const val ACTION_STOP = "dev.linkphone.app.CAMERA_STOP"
        const val EXTRA_FACING = "facing"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_FPS = "fps"
    }
}
