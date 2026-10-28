package app.linkphone.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.linkphone.core.util.LinkState
import app.linkphone.core.util.LinkStatus
import app.linkphone.features.Permissions
import app.linkphone.features.PhoneInfoBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: LinkViewModel, onPair: () -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val paired by vm.paired.collectAsStateWithLifecycle()
    val revision by vm.permissionRevision.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val lastError by LinkStatus.lastError.collectAsStateWithLifecycle()

    val hasTelephony = remember { PhoneInfoBuilder.hasTelephony(context) }
    val smsGranted = remember(revision) { Permissions.sms(context) }
    val notificationsGranted = remember(revision) { Permissions.notifications(context) }
    val callsGranted = remember(revision) { Permissions.calls(context) }
    val photosGranted = remember(revision) { Permissions.photos(context) }
    val photosPartial = remember(revision) { Permissions.photosPartial(context) }
    val listenerGranted = remember(revision) { Permissions.notificationAccess(context) }
    val accessibilityGranted = remember(revision) { Permissions.accessibility(context) }
    val batteryExempt = remember(revision) { Permissions.ignoringBatteryOptimizations(context) }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refresh() }
    val callLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refresh() }
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refresh() }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refresh() }

    var showForget by remember { mutableStateOf(false) }
    var showOem by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var fingerprint by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        fingerprint = withContext(Dispatchers.IO) { vm.identityFingerprint() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LinkPhone") },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                ConnectionCard(
                    state = state,
                    pairedName = paired?.pcName,
                    pairedHost = paired?.host,
                    lastError = lastError,
                    onPair = onPair,
                    onConnect = { vm.start() },
                    onDisconnect = { vm.disconnect() },
                )
            }
            item {
                LinkCard(title = "Clipboard") {
                    Text(
                        "Android only allows clipboard reads while an app is focused. Use this button, " +
                            "the Quick Settings tile, or the notification action to send text to the PC.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(context, SendClipboardActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("Send clipboard to PC")
                    }
                }
            }
            item {
                PermissionsCard(
                    hasTelephony = hasTelephony,
                    smsGranted = smsGranted,
                    notificationsGranted = notificationsGranted,
                    notificationsRuntimeRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                    callsGranted = callsGranted,
                    photosGranted = photosGranted,
                    photosPartial = photosPartial,
                    listenerGranted = listenerGranted,
                    accessibilityGranted = accessibilityGranted,
                    onRequestSms = { smsLauncher.launch(Permissions.smsPermissions()) },
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRequestCalls = {
                        callLauncher.launch(
                            Permissions.callPermissions() + Permissions.optionalCallPermissions(),
                        )
                    },
                    onRequestPhotos = { photoLauncher.launch(Permissions.photoPermissions()) },
                    onOpenListenerSettings = {
                        openSettings(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onOpenAccessibility = {
                        openSettings(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
            }
            item {
                BackgroundCard(
                    batteryExempt = batteryExempt,
                    showOem = showOem,
                    onToggleOem = { showOem = !showOem },
                    onAllowBackground = {
                        if (!requestBatteryExemption(context)) {
                            openSettings(
                                context,
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                            )
                        }
                    },
                )
            }
            item {
                DiagnosticsCard(
                    logs = logs,
                    showLogs = showLogs,
                    onToggleLogs = { showLogs = !showLogs },
                    onShare = { shareLogs(context, logs) },
                    onClear = { vm.clearLogs() },
                    onForget = { showForget = true },
                    hasPairing = paired != null,
                )
            }
            item {
                AboutCard(version = vm.appVersion, fingerprint = fingerprint)
            }
        }
    }

    if (showForget) {
        AlertDialog(
            onDismissRequest = { showForget = false },
            title = { Text("Forget this PC?") },
            text = {
                Text(
                    "The phone will stop connecting and remove the saved PC details. " +
                        "You can pair again with a new QR code.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForget = false
                        vm.forget()
                    },
                ) {
                    Text("Forget")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForget = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ConnectionCard(
    state: LinkState,
    pairedName: String?,
    pairedHost: String?,
    lastError: String?,
    onPair: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val statusColor = when (state) {
        is LinkState.Connected -> MaterialTheme.colorScheme.primary
        is LinkState.Connecting -> MaterialTheme.colorScheme.secondary
        is LinkState.Error -> MaterialTheme.colorScheme.error
        LinkState.Stopped -> MaterialTheme.colorScheme.outline
    }
    val statusText = when (state) {
        is LinkState.Connected -> "Connected"
        is LinkState.Connecting -> "Connecting"
        is LinkState.Error -> "Needs attention"
        LinkState.Stopped -> "Off"
    }

    LinkCard(title = "Connection") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(10.dp).background(statusColor, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(statusText, style = MaterialTheme.typography.titleMedium)
            if (state is LinkState.Connecting) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        Text(
            if (pairedName != null) "PC: $pairedName • ${pairedHost ?: ""}" else "No PC paired yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (state is LinkState.Error && lastError != null) {
            Text(
                lastError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (state is LinkState.Connected) {
            Text(
                state.host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (pairedName == null) {
                Button(onClick = onPair) { Text("Pair with your PC") }
            } else {
                if (state is LinkState.Connected) {
                    OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                } else {
                    Button(onClick = onConnect) { Text("Connect") }
                    OutlinedButton(onClick = onDisconnect) { Text("Stop") }
                }
                TextButton(onClick = onPair) { Text("Pair a different PC") }
            }
        }
    }
}

@Composable
private fun PermissionsCard(
    hasTelephony: Boolean,
    smsGranted: Boolean,
    notificationsGranted: Boolean,
    notificationsRuntimeRequired: Boolean,
    callsGranted: Boolean,
    photosGranted: Boolean,
    photosPartial: Boolean,
    listenerGranted: Boolean,
    accessibilityGranted: Boolean,
    onRequestSms: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestCalls: () -> Unit,
    onRequestPhotos: () -> Unit,
    onOpenListenerSettings: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    LinkCard(title = "Permissions") {
        Text(
            "Grant only what you use. Each feature works independently and can be revoked at any time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (hasTelephony) {
            PermissionRow(
                title = "Messages",
                detail = "Read, receive and send SMS. Not available on Google Play; sideload only.",
                granted = smsGranted,
                action = "Grant",
                onAction = onRequestSms,
            )
        }
        if (notificationsRuntimeRequired) {
            PermissionRow(
                title = "Notification permission",
                detail = "Shows the required ongoing connection notification.",
                granted = notificationsGranted,
                action = "Grant",
                onAction = onRequestNotifications,
            )
        }
        if (hasTelephony) {
            PermissionRow(
                title = "Calls",
                detail = "Call state, dialing, answer/decline. Caller ID needs call-log access. Audio stays on the phone.",
                granted = callsGranted,
                action = "Grant",
                onAction = onRequestCalls,
            )
        }
        PermissionRow(
            title = "Photos",
            detail = if (photosPartial) {
                "Only selected photos are shared. Grant full access in system settings for more."
            } else {
                "Recent camera-roll photos. Android 14 can limit access to selected photos."
            },
            granted = photosGranted,
            action = "Grant",
            onAction = onRequestPhotos,
        )
        PermissionRow(
            title = "Notification access",
            detail = "Mirror notifications to the PC and dismiss them remotely.",
            granted = listenerGranted,
            action = "Open settings",
            onAction = onOpenListenerSettings,
        )
        PermissionRow(
            title = "Remote taps",
            detail = "Accessibility service used only to inject taps/swipes into the mirrored screen.",
            granted = accessibilityGranted,
            action = "Open settings",
            onAction = onOpenAccessibility,
            last = true,
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    granted: Boolean,
    action: String,
    onAction: () -> Unit,
    last: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        if (granted) {
            Text(
                "Allowed",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
    if (!last) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    }
}

@Composable
private fun BackgroundCard(
    batteryExempt: Boolean,
    showOem: Boolean,
    onToggleOem: () -> Unit,
    onAllowBackground: () -> Unit,
) {
    LinkCard(title = "Background operation") {
        Text(
            "Android keeps the connection alive with a foreground service and a persistent notification. " +
                "That notification cannot be hidden. If the system stops the service, LinkPhone reconnects " +
                "when it restarts or when you open the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (batteryExempt) {
                Text(
                    "Battery optimization is off for LinkPhone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    "Battery optimization may stop the service",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onAllowBackground) { Text("Allow") }
            }
        }
        TextButton(onClick = onToggleOem) {
            Text(if (showOem) "Hide OEM instructions" else "OEM background instructions")
        }
        if (showOem) {
            Text(
                "Xiaomi/Redmi: enable Autostart and set Battery saver to No restrictions. " +
                    "Samsung: remove LinkPhone from Sleeping apps and enable background activity. " +
                    "Oppo/Realme/OnePlus: allow Auto launch and background activity. " +
                    "Vivo/iQOO: allow Autostart and high background power usage. " +
                    "Huawei: add LinkPhone to protected apps. " +
                    "Do not force-stop the app: force-stop disables boot restart until it is opened again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun DiagnosticsCard(
    logs: List<String>,
    showLogs: Boolean,
    onToggleLogs: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onForget: () -> Unit,
    hasPairing: Boolean,
) {
    LinkCard(title = "Diagnostics") {
        Text(
            "Connection events only. Message bodies, notification text and pairing tokens are never logged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onToggleLogs) {
                Text(if (showLogs) "Hide log" else "Show log")
            }
            TextButton(onClick = onShare, enabled = logs.isNotEmpty()) { Text("Share") }
            TextButton(onClick = onClear, enabled = logs.isNotEmpty()) { Text("Clear") }
            if (hasPairing) {
                TextButton(onClick = onForget) { Text("Forget PC") }
            }
        }
        if (showLogs) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small,
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (logs.isEmpty()) {
                        Text("No events yet", style = MaterialTheme.typography.bodySmall)
                    } else {
                        logs.takeLast(40).forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutCard(version: String, fingerprint: String) {
    LinkCard(title = "About") {
        Text("LinkPhone $version", style = MaterialTheme.typography.bodyMedium)
        Text(
            "LAN-only • TLS with certificate pinning • no accounts or cloud relay",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "Phone identity fingerprint",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            fingerprint,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Android 10+ required. SMS/call features need a telephony device. " +
                "Screen mirroring requires approval each session and stops when the screen locks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun LinkCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

private fun openSettings(context: android.content.Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun requestBatteryExemption(context: android.content.Context): Boolean = runCatching {
    context.startActivity(
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
}.getOrDefault(false)

private fun shareLogs(context: android.content.Context, logs: List<String>) {
    val text = logs.joinToString("\n").ifBlank { "No LinkPhone events logged." }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "LinkPhone diagnostics")
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share diagnostics").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
