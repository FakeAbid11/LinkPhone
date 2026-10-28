package app.linkphone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.linkphone.core.crypto.PairingUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairScreen(vm: LinkViewModel, onScan: () -> Unit, onBack: () -> Unit) {
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("48921") }
    var fingerprint by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var pasteMessage by remember { mutableStateOf<String?>(null) }

    val working = pairing is PairingUiState.Working

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair with PC") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(
                    title = "Scan the QR code",
                    subtitle = "Fastest and safest: the code carries the PC address, certificate, and token.",
                    icon = Icons.Outlined.QrCodeScanner,
                ) {
                    Text(
                        "On the PC, open LinkPhone and choose Pair a phone. Then scan the code with " +
                            "this camera.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onScan,
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan QR code")
                    }
                }
            }

            item {
                SectionCard(
                    title = "Enter details manually",
                    subtitle = "Use this if the camera is unavailable. The code expires after five minutes.",
                    icon = Icons.Outlined.Keyboard,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                val raw = clipboard.getText()?.text
                                val info = raw?.let { PairingUri.parse(it) }
                                if (info == null) {
                                    pasteMessage = "The clipboard does not contain a LinkPhone " +
                                        "pairing link."
                                } else {
                                    host = info.host
                                    port = info.port.toString()
                                    fingerprint = info.fingerprint
                                    token = info.token
                                    pasteMessage = "Pairing details pasted."
                                }
                            },
                            enabled = !working,
                        ) {
                            Text("Paste pairing link")
                        }
                    }
                    if (pasteMessage != null) {
                        Text(
                            pasteMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("PC address") },
                        placeholder = { Text("192.168.1.20") },
                        singleLine = true,
                        enabled = !working,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { value ->
                            port = value.filter { character -> character.isDigit() }.take(5)
                        },
                        label = { Text("Port") },
                        singleLine = true,
                        enabled = !working,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    OutlinedTextField(
                        value = fingerprint,
                        onValueChange = { fingerprint = it.take(100) },
                        label = { Text("Certificate fingerprint") },
                        placeholder = { Text("AA:BB:… (64 hex characters)") },
                        singleLine = true,
                        enabled = !working,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it.trim().take(64) },
                        label = { Text("Pairing code") },
                        singleLine = true,
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    Button(
                        onClick = {
                            vm.pairManual(
                                host = host,
                                port = port.toIntOrNull() ?: 0,
                                fingerprint = fingerprint,
                                token = token,
                            )
                        },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    ) {
                        Text("Pair")
                    }
                }
            }

            when (val current = pairing) {
                PairingUiState.Idle -> Unit
                is PairingUiState.Working -> item {
                    SectionCard(title = "Pairing", icon = Icons.Outlined.QrCodeScanner) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(current.message, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            "Approve the phone on your PC if the request appears there.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
                is PairingUiState.Error -> item {
                    SectionCard(title = "Pairing failed", icon = Icons.Outlined.ErrorOutline) {
                        Text(
                            current.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = { vm.resetPairingState() }) { Text("Try again") }
                            OutlinedButton(onClick = onBack) { Text("Back to home") }
                        }
                    }
                }
                is PairingUiState.Success -> item {
                    SectionCard(title = "Paired", icon = Icons.Outlined.CheckCircle) {
                        Text(
                            "This phone is paired with ${current.pc.pcName}.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "The connection starts automatically. Grant the feature permissions you " +
                                "want, then use LinkPhone from the PC.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Button(
                            onClick = {
                                vm.resetPairingState()
                                onBack()
                            },
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}
