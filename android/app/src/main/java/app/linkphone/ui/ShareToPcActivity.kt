package app.linkphone.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.linkphone.core.model.HandoffFileMeta
import app.linkphone.core.model.HandoffShare
import app.linkphone.core.model.StreamEnd
import app.linkphone.core.net.ConnectionHub
import app.linkphone.core.protocol.BinaryCodec
import app.linkphone.core.protocol.ProtocolCodec
import app.linkphone.core.protocol.Types
import app.linkphone.core.util.SafeInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.random.Random

class ShareToPcActivity : ComponentActivity() {
    private var status by mutableStateOf("Preparing…")
    private var isError by mutableStateOf(false)
    private var finished by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinkPhoneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ShareStatus(
                        status = status,
                        isError = isError,
                        finished = finished,
                        onClose = { finish() },
                    )
                }
            }
        }
        handleShare()
    }

    private fun handleShare() {
        if (intent.action != Intent.ACTION_SEND) {
            fail("Nothing to send")
            return
        }
        if (!ConnectionHub.isConnected()) {
            fail("Not connected. Open LinkPhone on your PC and pair first.")
            return
        }

        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            sendText(text)
            return
        }

        @Suppress("DEPRECATION")
        val stream = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        if (stream == null) {
            fail("This content cannot be sent")
            return
        }
        sendStream(stream)
    }

    private fun sendText(text: String) {
        val limited = text.take(20_000)
        val kind = if (SafeInput.isSafeHttpUrl(limited)) "url" else "text"
        val payload = HandoffShare(
            kind = kind,
            text = limited,
            url = if (kind == "url") limited else null,
        )
        val sent = ConnectionHub.sendEvent(
            Types.HANDOFF_SHARE,
            ProtocolCodec.data(payload),
        )
        if (sent) succeed("Sent to your PC") else fail("Could not send to the PC")
    }

    private fun sendStream(uri: Uri) {
        lifecycleScope.launch {
            status = "Reading file…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                    require(mime.startsWith("image/")) { "Only images can be shared to the PC" }
                    val (name, _) = queryNameAndSize(uri)
                    val bytes = readLimited(uri, MAX_FILE_BYTES)
                        ?: error("The file is larger than 20 MB")
                    Triple(name, mime, bytes)
                }
            }
            result.fold(
                onSuccess = { (name, mime, bytes) ->
                    sendFile(name, mime, bytes)
                },
                onFailure = { error ->
                    fail(error.message ?: "Could not read the file")
                },
            )
        }
    }

    private fun sendFile(name: String, mime: String, bytes: ByteArray) {
        val streamId = Random.nextLong(1, Long.MAX_VALUE)
        val meta = HandoffFileMeta(
            streamId = streamId,
            name = SafeInput.sanitizeFileName(name, "shared-image.jpg"),
            mime = mime,
            size = bytes.size.toLong(),
        )
        if (!ConnectionHub.sendEvent(Types.HANDOFF_FILE, ProtocolCodec.data(meta))) {
            fail("Could not send to the PC")
            return
        }

        status = "Sending ${bytes.size / 1024} KB…"
        lifecycleScope.launch(Dispatchers.IO) {
            var offset = 0
            var sequence = 0
            while (offset < bytes.size) {
                val end = min(offset + BinaryCodec.CHUNK_SIZE, bytes.size)
                val last = end == bytes.size
                val frame = BinaryCodec.encode(
                    BinaryCodec.TYPE_FILE,
                    streamId,
                    sequence++,
                    last,
                    bytes.copyOfRange(offset, end),
                )
                if (!ConnectionHub.sendBinary(frame)) {
                    withContext(Dispatchers.Main) { fail("The connection closed while sending") }
                    return@launch
                }
                offset = end
            }
            ConnectionHub.sendEvent(
                Types.HANDOFF_FILE_END,
                ProtocolCodec.data(StreamEnd(streamId)),
            )
            withContext(Dispatchers.Main) { succeed("Image sent to your PC") }
        }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long?> {
        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0) ?: "shared-image"
                    val size = if (!cursor.isNull(1)) cursor.getLong(1) else null
                    name to size
                } else {
                    "shared-image" to null
                }
            } ?: ("shared-image" to null)
        }.getOrDefault("shared-image" to null)
    }

    private fun readLimited(uri: Uri, limit: Long): ByteArray? {
        val input = contentResolver.openInputStream(uri) ?: return null
        return input.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) return null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun succeed(message: String) {
        status = message
        isError = false
        finished = true
        lifecycleScope.launch {
            delay(1_200)
            finish()
        }
    }

    private fun fail(message: String) {
        status = message
        isError = true
        finished = true
    }

    private companion object {
        const val MAX_FILE_BYTES = 20L * 1024 * 1024
    }
}

@Composable
private fun ShareStatus(
    status: String,
    isError: Boolean,
    finished: Boolean,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            if (isError) "Could not send" else "Continue on PC",
            style = MaterialTheme.typography.headlineSmall,
        )
        if (!finished) CircularProgressIndicator()
        Text(
            status,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (finished) {
            Button(onClick = onClose) { Text("Close") }
        }
    }
}
