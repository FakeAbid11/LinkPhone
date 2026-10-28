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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private enum class PermissionPrompt { Sms, Notifications, Calls, Contacts, Photos }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: LinkViewModel, onPair: () -> Unit, onSetup: () -> Unit) {
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
    val contactsGranted = remember(revision) { Permissions.contacts(context) }
    val photosGranted = remember(revision) { Permissions.photos(context) }
    val photosPartial = remember(revision) { Permissions.photosPartial(context) }
    val listenerGranted = remember(revision) { Permissions.notificationAccess(context) }
    val accessibilityGranted = remember(revision) { Permissions.accessibility(context) }
    val batteryExempt = remember(revision) { Permissions.ignoringBatteryOptimizations(context) }

    val activity = remember(context) { context.findHostActivity() }
    val photoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var smsAsked by rememberSaveable { mutableStateOf(false) }
    var notificationsAsked by rememberSaveable { mutableStateOf(false) }
    var callsAsked by rememberSaveable { mutableStateOf(false) }
    var contactsAsked by rememberSaveable { mutableStateOf(false) }
    var photosAsked by rememberSaveable { mutableStateOf(false) }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refresh() }
    val callLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refresh() }
    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
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
    var prompt by remember { mutableStateOf<PermissionPrompt?>(null) }

    LaunchedEffect(Unit) {
        fingerprint = withContext(Dispatchers.IO) { vm.identityFingerprint() }
    }

    val notificationsRuntimeRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val blocked = { granted: Boolean, asked: Boolean, permission: String ->
        !granted && asked &&
            activity?.shouldShowRequestPermissionRationale(permission) == false
    }
    val smsBlocked = blocked(smsGranted, smsAsked, Manifest.permission.READ_SMS)
    val callsBlocked = blocked(callsGranted, callsAsked, Manifest.permission.READ_PHONE_STATE)
    val contactsBlocked = blocked(contactsGranted, contactsAsked, Manifest.permission.READ_CONTACTS)
    val photosBlocked = blocked(photosGranted, photosAsked, photoPermission)
    val notificationsBlocked = notificationsRuntimeRequired &&
        blocked(notificationsGranted, notificationsAsked, Manifest.permission.POST_NOTIFICATIONS)

    val openAppSettings = {
        openSettings(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    fun launchPrompt(kind: PermissionPrompt) {
        when (kind) {
            PermissionPrompt.Sms -> {
                smsAsked = true
                smsLauncher.launch(Permissions.smsPermissions())
            }
            PermissionPrompt.Notifications -> {
                notificationsAsked = true
                if (notificationsRuntimeRequired) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            PermissionPrompt.Calls -> {
                callsAsked = true
                callLauncher.launch(
                    Permissions.callPermissions() + Permissions.optionalCallPermissions(),
                )
            }
            PermissionPrompt.Contacts -> {
                contactsAsked = true
                contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
            PermissionPrompt.Photos -> {
                photosAsked = true
                photoLauncher.launch(Permissions.photoPermissions())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("LinkPhone")
                        Text(
                            when (state) {
                                is LinkState.Connected -> "Connected"
                                is LinkState.Connecting -> "Connecting"
                                is LinkState.Error -> "Needs attention"
                                LinkState.Stopped ->
                                    if (paired == null) "Not paired" else "Disconnected"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSetup) {
                        Icon(Icons.Outlined.HelpOutline, contentDescription = "Setup guide")
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
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

            if (paired == null) {
                item { SetupCard(onSetup = onSetup, onPair = onPair) }
            }

            item {
                QuickActionsCard(
                    onSendClipboard = {
                        context.startActivity(
                            Intent(context, SendClipboardActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    onPair = onPair,
                    onNotificationAccess = {
                        openSettings(
                            context,
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                        )
                    },
                    onBattery = {
                        if (!requestBatteryExemption(context)) {
                            openSettings(
                                context,
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                            )
                        }
                    },
                    batteryExempt = batteryExempt,
                )
            }

            item {
                SectionCard(
                    title = "Permissions",
                    subtitle = "Grant only what you use. Each feature works independently.",
                    icon = Icons.Outlined.CheckCircle,
                ) {
                    if (hasTelephony) {
                        PermissionRow(
                            icon = Icons.Outlined.Chat,
                            title = "Messages",
                            detail = "Read, receive, and send SMS. Sideload only; no MMS or RCS.",
                            status = statusOf(smsGranted),
                            actionLabel = if (smsBlocked) "Open settings" else "Grant",
                            onAction = {
                                if (smsBlocked) openAppSettings() else prompt = PermissionPrompt.Sms
                            },
                        )
                        PermissionRow(
                            icon = Icons.Outlined.Call,
                            title = "Calls",
                            detail = "Call state, dialing, and answer/decline. Audio stays on the phone.",
                            status = statusOf(callsGranted),
                            actionLabel = if (callsBlocked) "Open settings" else "Grant",
                            onAction = {
                                if (callsBlocked) {
                                    openAppSettings()
                                } else {
                                    prompt = PermissionPrompt.Calls
                                }
                            },
                        )
                        PermissionRow(
                            icon = Icons.Outlined.Contacts,
                            title = "Contacts",
                            detail = "Search contacts on the PC and show caller names.",
                            status = statusOf(contactsGranted),
                            actionLabel = if (contactsBlocked) "Open settings" else "Grant",
                            onAction = {
                                if (contactsBlocked) {
                                    openAppSettings()
                                } else {
                                    prompt = PermissionPrompt.Contacts
                                }
                            },
                        )
                    }
                    PermissionRow(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = "Photos",
                        detail = if (photosPartial) {
                            "Only selected photos are shared. Grant full access in Android settings."
                        } else {
                            "Recent camera-roll photos, transcoded to JPEG for the PC."
                        },
                        status = statusOf(photosGranted),
                        actionLabel = if (photosBlocked) "Open settings" else "Grant",
                        onAction = {
                            if (photosBlocked) openAppSettings() else prompt = PermissionPrompt.Photos
                        },
                    )
                    if (notificationsRuntimeRequired) {
                        PermissionRow(
                            icon = Icons.Outlined.Notifications,
                            title = "Notification permission",
                            detail = "Shows the required ongoing connection notification.",
                            status = statusOf(notificationsGranted),
                            actionLabel = if (notificationsBlocked) "Open settings" else "Grant",
                            onAction = {
                                if (notificationsBlocked) {
                                    openAppSettings()
                                } else {
                                    prompt = PermissionPrompt.Notifications
                                }
                            },
                        )
                    }
                    PermissionRow(
                        icon = Icons.Outlined.NotificationsActive,
                        title = "Notification access",
                        detail = "Mirror notifications to the PC and dismiss them remotely.",
                        status = statusOf(listenerGranted),
                        actionLabel = "Open settings",
                        onAction = {
                            openSettings(
                                context,
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                            )
                        },
                    )
                    PermissionRow(
                        icon = Icons.Outlined.TouchApp,
                        title = "Remote taps",
                        detail = "Accessibility service used only for taps and swipes from your PC.",
                        status = statusOf(accessibilityGranted),
                        actionLabel = "Open settings",
                        onAction = {
                            openSettings(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        last = true,
                    )
                }
            }

            item {
                SectionCard(
                    title = "Background operation",
                    subtitle = "Keeps the encrypted connection alive.",
                    icon = Icons.Outlined.BatteryFull,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (batteryExempt) {
                                    "Battery optimization is off"
                                } else {
                                    "Battery optimization is on"
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                if (batteryExempt) {
                                    "LinkPhone can reconnect after a reboot."
                                } else {
                                    "Android may stop the service in the background."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        if (!batteryExempt) {
                            OutlinedButton(
                                onClick = {
                                    if (!requestBatteryExemption(context)) {
                                        openSettings(
                                            context,
                                            Intent(
                                                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                                            ),
                                        )
                                    }
                                },
                            ) {
                                Text("Allow")
                            }
                        }
                    }
                    TextButton(onClick = { showOem = !showOem }) {
                        Text(
                            if (showOem) "Hide OEM instructions" else "OEM background instructions",
                        )
                    }
                    if (showOem) {
                        Text(
                            "Xiaomi/Redmi: enable Autostart and set Battery saver to No " +
                                "restrictions. Samsung: remove LinkPhone from Sleeping apps and " +
                                "allow background activity. Oppo/Realme/OnePlus: allow Auto launch. " +
                                "Vivo/iQOO: allow Autostart. Huawei: add LinkPhone to protected " +
                                "apps. Do not force-stop the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SectionCard(
                    title = "Diagnostics",
                    subtitle = "Connection events only. Message and notification content is never logged.",
                    icon = Icons.Outlined.ErrorOutline,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { showLogs = !showLogs }) {
                            Text(if (showLogs) "Hide log" else "Show log")
                        }
                        TextButton(
                            onClick = { shareLogs(context, logs) },
                            enabled = logs.isNotEmpty(),
                        ) {
                            Text("Share")
                        }
                        TextButton(onClick = { vm.clearLogs() }, enabled = logs.isNotEmpty()) {
                            Text("Clear")
                        }
                        if (paired != null) {
                            TextButton(onClick = { showForget = true }) { Text("Forget PC") }
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

            item {
                SectionCard(
                    title = "About",
                    subtitle = "LinkPhone ${vm.appVersion}",
                    icon = Icons.Outlined.PhoneAndroid,
                ) {
                    Text(
                        "LAN-only with TLS certificate pinning. No accounts, cloud relay, or analytics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Phone identity fingerprint",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        fingerprint,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Android 10+ required. SMS and calls need a telephony device. Mirroring " +
                            "needs approval each session and stops when the phone locks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }

    prompt?.let { kind ->
        AlertDialog(
            onDismissRequest = { prompt = null },
            title = { Text(promptTitle(kind)) },
            text = { Text(promptBody(kind)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        prompt = null
                        launchPrompt(kind)
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { prompt = null }) { Text("Not now") }
            },
        )
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
    val tone = when (state) {
        is LinkState.Connected -> StatusTone.Good
        is LinkState.Connecting -> StatusTone.Neutral
        is LinkState.Error -> StatusTone.Bad
        LinkState.Stopped -> if (pairedName != null) StatusTone.Warn else StatusTone.Neutral
    }
    val statusText = when (state) {
        is LinkState.Connected -> "Connected"
        is LinkState.Connecting -> "Connecting…"
        is LinkState.Error -> "Needs attention"
        LinkState.Stopped -> if (pairedName != null) "Disconnected" else "Not paired"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(statusText, tone)
                        if (state is LinkState.Connecting) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    Text(
                        pairedName ?: "No PC paired yet",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        when {
                            state is LinkState.Connected -> state.host
                            pairedName != null -> pairedHost ?: "Paired PC"
                            else -> "Open LinkPhone on your PC to begin."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state is LinkState.Error && lastError != null) {
                Text(
                    lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Row(
                modifier = Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pairedName == null) {
                    Button(onClick = onPair) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("Pair with your PC")
                    }
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
}

@Composable
private fun SetupCard(onSetup: () -> Unit, onPair: () -> Unit) {
    SectionCard(
        title = "Finish setup",
        subtitle = "Three short steps, then you can use the phone from the PC.",
        icon = Icons.Outlined.HelpOutline,
    ) {
        Text(
            "Review what LinkPhone can do, grant only the permissions you need, and pair once " +
                "over your own Wi-Fi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onSetup) { Text("Open setup guide") }
            OutlinedButton(onClick = onPair) { Text("Pair now") }
        }
    }
}

@Composable
private fun QuickActionsCard(
    onSendClipboard: () -> Unit,
    onPair: () -> Unit,
    onNotificationAccess: () -> Unit,
    onBattery: () -> Unit,
    batteryExempt: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Quick actions",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(
                icon = Icons.Outlined.ContentPaste,
                title = "Send clipboard",
                subtitle = "Send copied text to the PC",
                onClick = onSendClipboard,
                modifier = Modifier.weight(1f),
            )
            QuickAction(
                icon = Icons.Outlined.QrCodeScanner,
                title = "Pair a PC",
                subtitle = "Scan the PC's QR code",
                onClick = onPair,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(
                icon = Icons.Outlined.NotificationsActive,
                title = "Notification access",
                subtitle = "Mirror notifications",
                onClick = onNotificationAccess,
                modifier = Modifier.weight(1f),
            )
            QuickAction(
                icon = Icons.Outlined.BatteryFull,
                title = if (batteryExempt) "Battery allowed" else "Allow background",
                subtitle = if (batteryExempt) {
                    "Service stays available"
                } else {
                    "Let the service reconnect"
                },
                onClick = onBattery,
                modifier = Modifier.weight(1f),
                enabled = !batteryExempt,
            )
        }
    }
}

private fun statusOf(granted: Boolean): PermissionStatus =
    if (granted) PermissionStatus.Allowed else PermissionStatus.NeedsAccess

private fun promptTitle(kind: PermissionPrompt): String = when (kind) {
    PermissionPrompt.Sms -> "Allow Messages?"
    PermissionPrompt.Notifications -> "Allow notifications?"
    PermissionPrompt.Calls -> "Allow Calls?"
    PermissionPrompt.Contacts -> "Allow Contacts?"
    PermissionPrompt.Photos -> "Allow Photos?"
}

private fun promptBody(kind: PermissionPrompt): String = when (kind) {
    PermissionPrompt.Sms ->
        "LinkPhone reads and sends SMS only while you use the PC. Android treats SMS as a " +
            "sensitive permission, so nothing is read until you allow it."
    PermissionPrompt.Notifications ->
        "Android requires a visible notification while the LinkPhone service is connected."
    PermissionPrompt.Calls ->
        "Call access is used to show call state and place calls from the PC. Answer/decline and " +
            "caller ID are requested at the same time and are optional."
    PermissionPrompt.Contacts ->
        "Contacts are requested on demand when you search in the Calls tab and are kept in PC " +
            "memory only."
    PermissionPrompt.Photos ->
        "Photos are read only when you open the Photos tab. Android 14 lets you share selected " +
            "photos instead of the whole camera roll."
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
        context.startActivity(
            Intent.createChooser(intent, "Share diagnostics")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
