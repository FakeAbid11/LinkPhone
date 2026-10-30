package app.linkphone.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.linkphone.features.clipboard.ClipboardBridge
import app.linkphone.service.LinkConnectionService

class ClipboardApplyActivity : ComponentActivity() {
    private var applied = false

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
                    ClipboardApplying()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || applied) return
        applied = true
        val text = intent.getStringExtra(LinkConnectionService.EXTRA_CLIPBOARD_TEXT)
        if (text.isNullOrEmpty()) {
            finish()
            return
        }
        val ok = ClipboardBridge.write(this, text)
        Toast.makeText(
            this,
            if (ok) "Text copied from PC" else "Could not write to the clipboard",
            Toast.LENGTH_SHORT,
        ).show()
        finish()
    }
}

@Composable
private fun ClipboardApplying() {
    ProgressScaffold(
        title = "Copying text from PC",
        message = "Android only allows clipboard access while an app is in the foreground. " +
            "This screen closes automatically when the text is copied.",
        icon = MsIcons.ContentPaste,
    )
}
