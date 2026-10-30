package app.linkphone.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.linkphone.features.Permissions
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

class QrScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            LinkPhoneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ScannerScreen(
                        onDecoded = { raw ->
                            setResult(RESULT_OK, Intent().putExtra(EXTRA_RAW, raw))
                            finish()
                        },
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_RAW = "app.linkphone.extra.QR_RAW"
    }
}

@Composable
private fun ScannerScreen(onDecoded: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(Permissions.camera(context)) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCamera = granted
        permanentlyDenied = !granted &&
            context.findHostActivity()
                ?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
    }

    // The system dialog is requested from the "Allow camera" button below, so
    // the user sees why the camera is needed before Android asks.
    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCamera) {
            CameraScanner(onDecoded = onDecoded)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp)
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f),
                        MaterialTheme.shapes.large,
                    )
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        "Scan the pairing QR on your PC",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(LinkPhonePalette.DarkOnBackground),
                    )
                    Text(
                        "The code expires five minutes after you create it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(LinkPhonePalette.DarkOnSurfaceVariant),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedButton(onClick = onClose, modifier = Modifier.padding(top = 10.dp)) {
                        Text("Cancel")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Camera access is needed", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "LinkPhone scans the pairing QR locally. No photo or video leaves the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow camera")
                }
                if (permanentlyDenied) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Open app settings")
                    }
                }
                OutlinedButton(onClick = onClose, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun CameraScanner(onDecoded: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var retry by remember { mutableStateOf(0) }
    val executor = remember(retry) { Executors.newSingleThreadExecutor() }
    val found = remember { AtomicBoolean(false) }
    val lastAnalysis = remember { AtomicLong(0L) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var hasFlash by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, retry) {
        cameraError = null
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(1280, 720),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                    ),
                                )
                                .build(),
                        )
                        .build()
                    analysis.setAnalyzer(executor) { image ->
                        analyze(
                            image = image,
                            found = found,
                            lastAnalysis = lastAnalysis,
                            onText = { text ->
                                mainHandler.post { onDecoded(text) }
                            },
                        )
                    }
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                    hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
                } catch (error: Exception) {
                    cameraError = error.message ?: "Camera could not be started"
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            runCatching { if (providerFuture.isDone) providerFuture.get().unbindAll() }
            camera = null
            executor.shutdownNow()
        }
    }

    LaunchedEffect(torchOn, camera) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (hasFlash) {
            IconButton(
                onClick = { torchOn = !torchOn },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f),
                        MaterialTheme.shapes.small,
                    ),
            ) {
                Icon(
                    if (torchOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                    contentDescription = if (torchOn) "Turn torch off" else "Turn torch on",
                    tint = Color(LinkPhonePalette.DarkOnBackground),
                )
            }
        }
        cameraError?.let { message ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(28.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Camera error", style = MaterialTheme.typography.titleLarge)
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { retry += 1 }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Try again")
                }
            }
        }
    }
}

private fun analyze(
    image: ImageProxy,
    found: AtomicBoolean,
    lastAnalysis: AtomicLong,
    onText: (String) -> Unit,
) {
    if (found.get()) {
        image.close()
        return
    }
    val now = System.currentTimeMillis()
    if (abs(now - lastAnalysis.get()) < 120) {
        image.close()
        return
    }
    lastAnalysis.set(now)

    try {
        val plane = image.planes.firstOrNull()
        if (plane == null || image.width <= 0 || image.height <= 0) return
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height
        val luminance = ByteArray(width * height)
        var output = 0
        for (row in 0 until height) {
            val rowStart = row * rowStride
            for (column in 0 until width) {
                val index = rowStart + column * pixelStride
                if (index >= buffer.limit()) break
                luminance[output++] = buffer.get(index)
            }
        }
        if (output < luminance.size) return

        val source = PlanarYUVLuminanceSource(
            luminance,
            width,
            height,
            0,
            0,
            width,
            height,
            false,
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                    DecodeHintType.CHARACTER_SET to "UTF-8",
                ),
            )
        }
        val result = runCatching { reader.decodeWithState(bitmap) }.getOrNull()
        reader.reset()
        if (result?.text?.isNotBlank() == true) {
            found.set(true)
            onText(result.text)
        }
    } catch (_: Exception) {
        // A single undecodable frame is normal.
    } finally {
        image.close()
    }
}
