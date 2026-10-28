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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.linkphone.core.net.ConnectionHub
import app.linkphone.service.LinkConnectionService

class MirrorConsentActivity : ComponentActivity() {
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, LinkConnectionService::class.java)
                    .setAction(LinkConnectionService.ACTION_MIRROR_START)
                    .putExtra(LinkConnectionService.EXTRA_RESULT_CODE, result.resultCode)
                    .putExtra(LinkConnectionService.EXTRA_RESULT_DATA, data),
            )
        } else {
            Toast.makeText(this, "Screen sharing was not started", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinkPhoneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MirrorConsentContent(
                        onAllow = ::requestCapture,
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private fun requestCapture() {
        if (!ConnectionHub.isConnected()) {
            Toast.makeText(this, "Connect to your PC first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            Toast.makeText(this, "Screen capture is unavailable", Toast.LENGTH_SHORT).show()
            finish()
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
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Allow screen sharing", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Your PC asked to view this screen. Android requires your approval for every session. " +
                "You can choose a single app on Android 14 and later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Screen capture stops when the phone locks. Secure windows and DRM content appear black. " +
                "Audio is never captured.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAllow) { Text("Choose what to share") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
