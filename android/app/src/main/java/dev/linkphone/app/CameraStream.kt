package dev.linkphone.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

data class CameraState(
    val streaming: Boolean = false,
    val facing: String = "back",
    val quality: String = "720p",
    val fps: Int = 15,
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

    fun start(context: Context, facing: String, quality: String) {
        val cleanFacing = if (facing == "front") "front" else "back"
        val cleanQuality = if (quality == "480p") "480p" else "720p"
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
        _state.value = CameraState(
            streaming = true,
            facing = cleanFacing,
            quality = cleanQuality
        )
        val intent = Intent(context, CameraStreamService::class.java).apply {
            action = CameraStreamService.ACTION_START
            putExtra(CameraStreamService.EXTRA_FACING, cleanFacing)
            putExtra(CameraStreamService.EXTRA_QUALITY, cleanQuality)
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

    private fun sendState() {
        val current = _state.value
        LinkCore.clientOrNull()?.sendCameraState(
            current.streaming,
            current.facing,
            current.quality,
            current.fps,
            current.error
        )
    }
}

/**
 * CameraX needs a LifecycleOwner; the service is one, so the stream survives the screen
 * locking (the activity only gates starting it).
 */
class CameraStreamService : LifecycleService() {

    private val executor = Executors.newSingleThreadExecutor()
    private val jpegBuffer = ByteArrayOutputStream(256 * 1024)
    private var provider: ProcessCameraProvider? = null
    private var facing = "back"
    private var quality = "720p"
    private var frameSeq = 0
    private var lastFrameAt = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        facing = intent?.getStringExtra(EXTRA_FACING) ?: facing
        quality = intent?.getStringExtra(EXTRA_QUALITY) ?: quality
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        )
        bind()
        return START_NOT_STICKY
    }

    private fun bind() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val cameraProvider = runCatching { future.get() }.getOrNull()
            if (cameraProvider == null) {
                CameraHub.reportError("unsupported")
                stopSelf()
                return@addListener
            }
            val selector = if (facing == "front") {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            val resolution = if (quality == "480p") Size(854, 480) else Size(1280, 720)
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(resolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(executor) { image -> analyze(image) }
            provider = cameraProvider
            runCatching {
                if (!cameraProvider.hasCamera(selector)) error("camera is unavailable")
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, analysis)
            }.onFailure {
                Log.w(TAG, "cannot bind camera", it)
                CameraHub.reportError("busy")
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < FRAME_INTERVAL_MS) {
            image.close()
            return
        }
        lastFrameAt = now
        var source: Bitmap? = null
        var frame: Bitmap? = null
        try {
            source = image.toBitmap()
            frame = rotate(source, image.imageInfo.rotationDegrees)
            if (frame !== source) source.recycle()
            jpegBuffer.reset()
            val current = frame ?: return
            if (!current.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpegBuffer)) return
            frameSeq += 1
            LinkCore.clientOrNull()?.sendCameraFrame(
                frameSeq,
                System.currentTimeMillis(),
                jpegBuffer.toByteArray()
            )
        } catch (e: Exception) {
            Log.w(TAG, "camera frame", e)
        } finally {
            frame?.recycle()
            source?.recycle()
            image.close()
        }
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
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

    override fun onDestroy() {
        runCatching { provider?.unbindAll() }
        provider = null
        executor.shutdown()
        CameraHub.onServiceStopped()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LinkPhone"
        private const val CHANNEL_ID = "linkphone_camera"
        private const val NOTIFICATION_ID = 7
        private const val FRAME_INTERVAL_MS = 66L
        private const val JPEG_QUALITY = 70
        const val ACTION_START = "dev.linkphone.app.CAMERA_START"
        const val ACTION_STOP = "dev.linkphone.app.CAMERA_STOP"
        const val EXTRA_FACING = "facing"
        const val EXTRA_QUALITY = "quality"
    }
}
