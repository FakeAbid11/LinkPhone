package app.linkphone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairScreen(vm: LinkViewModel, onScan: () -> Unit, onBack: () -> Unit) {
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("48921") }
    var fingerprint by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }

    val working = pairing is PairingUiState.Working

    BackHandler(onBack = onBack)

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
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LinkCard(title = "Scan the QR code") {
                    Text(
                        "On the PC, open LinkPhone and choose Pair a phone. The QR code contains " +
                            "the PC address, its certificate fingerprint and a one-time code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onScan,
                        enabled = !working,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("Scan QR code")
                    }
                }
            }

            item {
                LinkCard(title = "Enter details manually") {
                    Text(
                        "Use this if the camera is unavailable. The code expires five minutes after " +
                            "it is created on the PC.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("PC address") },
                        placeholder = { Text("192.168.1.20") },
                        singleLine = true,
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        enabled = !working,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = fingerprint,
                        onValueChange = { fingerprint = it.take(100) },
                        label = { Text("Certificate fingerprint") },
                        placeholder = { Text("AA:BB:… (64 hex characters)") },
                        singleLine = true,
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it.trim().take(64) },
                        label = { Text("Pairing code") },
                        singleLine = true,
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("Pair")
                    }
                }
            }

            item {
                when (val current = pairing) {
                    PairingUiState.Idle -> Unit
                    is PairingUiState.Working -> LinkCard(title = "Pairing") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(current.message, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            "Approve the phone on your PC if prompted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    is PairingUiState.Error -> LinkCard(title = "Pairing failed") {
                        Text(
                            current.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(
                            onClick = { vm.resetPairingState() },
                            modifier = Modifier.padding(top = 10.dp),
                        ) {
                            Text("Dismiss")
                        }
                    }
                    is PairingUiState.Success -> LinkCard(title = "Paired") {
                        Text(
                            "This phone is paired with ${current.pc.pcName}.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "The connection starts automatically. You can close this screen and grant " +
                                "the feature permissions you want.",
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
