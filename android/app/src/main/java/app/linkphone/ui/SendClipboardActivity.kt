package app.linkphone.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.linkphone.core.model.ClipboardFromPhone
import app.linkphone.core.net.ConnectionHub
import app.linkphone.core.protocol.ProtocolCodec
import app.linkphone.core.protocol.Types
import app.linkphone.core.util.SafeInput
import app.linkphone.features.clipboard.ClipboardBridge

class SendClipboardActivity : ComponentActivity() {
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinkPhoneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SendingClipboard()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true

        val text = ClipboardBridge.read(this)
        if (text.isNullOrBlank()) {
            toast("Clipboard is empty")
            finish()
            return
        }
        val trimmed = SafeInput.truncateClipboard(text)
        val sent = ConnectionHub.sendEvent(
            Types.CLIPBOARD_FROM_PHONE,
            ProtocolCodec.data(ClipboardFromPhone(trimmed)),
        )
        toast(if (sent) "Clipboard sent to PC" else "Not connected to a PC")
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 450)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun SendingClipboard() {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sending clipboard", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Reading the clipboard while this screen is focused, then sending it to your PC.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
