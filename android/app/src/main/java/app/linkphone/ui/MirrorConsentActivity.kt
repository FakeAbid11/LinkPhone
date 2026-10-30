package app.linkphone.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import app.linkphone.core.model.MirrorState
import app.linkphone.core.net.ConnectionHub
import app.linkphone.core.protocol.ProtocolCodec
import app.linkphone.core.protocol.Types
import app.linkphone.features.mirror.MirrorCodecChoice
import app.linkphone.service.LinkConnectionService

class MirrorConsentActivity : ComponentActivity() {
    private var handled = false

    /** The codec and frame rate this consent was requested with. */
    private var codec: String = MirrorCodecChoice.JPEG
    private var fps: Int = MirrorCodecChoice.FPS_30

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            handled = true
            ContextCompat.startForegroundService(
                this,
                Intent(this, LinkConnectionService::class.java)
                    .setAction(LinkConnectionService.ACTION_MIRROR_START)
                    .putExtra(LinkConnectionService.EXTRA_RESULT_CODE, result.resultCode)
                    .putExtra(LinkConnectionService.EXTRA_RESULT_DATA, data)
                    .putExtra(LinkConnectionService.EXTRA_MIRROR_CODEC, codec)
                    .putExtra(LinkConnectionService.EXTRA_MIRROR_FPS, fps),
            )
            finish()
        } else {
            reportCancelled("Screen sharing was not approved")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        codec = intent.getStringExtra(LinkConnectionService.EXTRA_MIRROR_CODEC)
            ?: MirrorCodecChoice.JPEG
        fps = intent.getIntExtra(LinkConnectionService.EXTRA_MIRROR_FPS, MirrorCodecChoice.FPS_30)
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
                    MirrorConsentContent(
                        onAllow = ::requestCapture,
                        onCancel = { reportCancelled("Screen sharing was cancelled") },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (!handled && !isChangingConfigurations) {
            sendCancelled("Screen sharing was cancelled")
            Toast.makeText(this, "Screen sharing was cancelled", Toast.LENGTH_SHORT).show()
        }
        super.onDestroy()
    }

    private fun sendCancelled(message: String) {
        if (handled) return
        handled = true
        if (ConnectionHub.isConnected()) {
            ConnectionHub.sendEvent(
                Types.MIRROR_STATE,
                ProtocolCodec.data(MirrorState(active = false, message = message)),
            )
        }
    }

    private fun reportCancelled(message: String) {
        sendCancelled(message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun requestCapture() {
        if (!ConnectionHub.isConnected()) {
            handled = true
            Toast.makeText(this, "Connect to your PC first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            reportCancelled("Screen capture is unavailable")
            return
        }
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice())
        } else {
            manager.createScreenCaptureIntent()
        }
        projectionLauncher.launch(intent)
    }
}

@Composable
private fun MirrorConsentContent(onAllow: () -> Unit, onCancel: () -> Unit) {
    ConsentScaffold(
        title = "Allow screen sharing",
        message = "Your PC asked to view this screen. Android requires your approval for every " +
            "session. You can choose a single app on Android 14 and later.",
        detail = "Screen capture stops when the phone locks. Secure windows and DRM content " +
            "appear black. Audio is never captured.",
        icon = MsIcons.ScreenShare,
        primaryLabel = "Choose what to share",
        onPrimary = onAllow,
        secondaryLabel = "Cancel",
        onSecondary = onCancel,
    )
}
