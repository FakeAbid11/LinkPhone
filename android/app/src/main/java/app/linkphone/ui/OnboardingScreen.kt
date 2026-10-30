package app.linkphone.ui

import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.linkphone.features.Permissions

private const val PAGE_COUNT = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    vm: LinkViewModel,
    onPair: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val revision by vm.permissionRevision.collectAsStateWithLifecycle()
    val activity = remember(context) { context.findHostActivity() }

    var page by rememberSaveable { mutableStateOf(0) }
    var requestRound by rememberSaveable { mutableStateOf(0) }

    val grantLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        requestRound += 1
        vm.refresh()
    }

    val granted = remember(revision) { grantedPermissions(context) }
    val missingRuntime = OnboardingPermissions.features.any { granted[it.id] != true }

    // After a prompt was declined twice Android stops showing it, so the row
    // points at the app settings instead of silently doing nothing.
    val blocked = remember(revision, requestRound) {
        if (requestRound == 0) {
            emptySet()
        } else {
            OnboardingPermissions.features
                .filter { granted[it.id] != true }
                .filter { spec ->
                    val permission = OnboardingPermissions.requestFor(spec.id).firstOrNull()
                        ?: return@filter false
                    activity?.shouldShowRequestPermissionRationale(permission) == false
                }
                .map { it.id }
                .toSet()
        }
    }

    BackHandler(enabled = page > 0) { page -= 1 }

    fun openSpecial(id: OnboardingPermission) {
        when (id) {
            OnboardingPermission.NotificationAccess ->
                context.openSystemSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            OnboardingPermission.RemoteTaps ->
                context.openSystemSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            OnboardingPermission.Battery ->
                context.requestIgnoreBatteryOptimizations()
            else -> Unit
        }
    }

    fun openAppSettings() {
        context.openSystemSettings(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            packageUri(context),
        )
    }

    fun grant(id: OnboardingPermission) {
        val permissions = OnboardingPermissions.requestFor(id)
        if (permissions.isNotEmpty()) grantLauncher.launch(permissions.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("LinkPhone")
                        Text(
                            "Step ${page + 1} of $PAGE_COUNT",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (page < PAGE_COUNT - 1) {
                        TextButton(onClick = onFinish) { Text("Skip") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val forward = targetState > initialState
                    val enter = slideInHorizontally(tween(220)) {
                        if (forward) it / 6 else -it / 6
                    } + fadeIn(tween(220))
                    val exit = slideOutHorizontally(tween(220)) {
                        if (forward) -it / 6 else it / 6
                    } + fadeOut(tween(220))
                    enter togetherWith exit
                },
                label = "onboarding",
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    when (current) {
                        0 -> WelcomePage()
                        1 -> PermissionsPage(
                            granted = granted,
                            blocked = blocked,
                            onGrant = ::grant,
                            onOpenSpecial = ::openSpecial,
                            onOpenAppSettings = ::openAppSettings,
                        )
                        else -> PairPage()
                    }
                }
            }

            Footer(
                page = page,
                missingRuntime = missingRuntime,
                onPage = { page = it },
                onGrantAll = { grantLauncher.launch(OnboardingPermissions.grantAll.toTypedArray()) },
                onPair = onPair,
                onFinish = onFinish,
            )
        }
    }
}

@Composable
private fun Footer(
    page: Int,
    missingRuntime: Boolean,
    onPage: (Int) -> Unit,
    onGrantAll: () -> Unit,
    onPair: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 18.dp),
    ) {
        PageDots(page)
        Spacer(Modifier.height(14.dp))
        when (page) {
            0 -> {
                Button(onClick = { onPage(1) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Get started")
                }
            }
            1 -> {
                Button(
                    onClick = { if (missingRuntime) onGrantAll() else onPage(2) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (missingRuntime) "Grant all" else "Continue")
                }
                TextButton(onClick = { onPage(2) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (missingRuntime) "Not now" else "Next")
                }
            }
            else -> {
                Button(onClick = onPair, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan QR code")
                }
                TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text("Pair later")
                }
                TextButton(onClick = { onPage(1) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    HeroMark()
    Spacer(Modifier.height(18.dp))
    Text(
        "Your phone, on your PC",
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        "LinkPhone connects both devices over your own Wi-Fi so you can use the phone from a " +
            "keyboard and a larger screen.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    Column(
        modifier = Modifier.padding(top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        BulletRow(
            Icons.Outlined.Chat,
            "Messages on the PC",
            "Read conversations and send SMS with a full keyboard.",
        )
        BulletRow(
            Icons.Outlined.Call,
            "Call control",
            "Dial, answer, and choose a SIM. Audio stays on the phone.",
        )
        BulletRow(
            Icons.Outlined.PhotoLibrary,
            "Photos and notifications",
            "Browse recent photos and see phone notifications on the PC.",
        )
        BulletRow(
            Icons.Outlined.ContentPaste,
            "Clipboard handoff",
            "Move links and text between both devices on request.",
        )
    }
}

@Composable
private fun PermissionsPage(
    granted: Map<OnboardingPermission, Boolean>,
    blocked: Set<OnboardingPermission>,
    onGrant: (OnboardingPermission) -> Unit,
    onOpenSpecial: (OnboardingPermission) -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Text(
        "Grant only what you use",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        "Each feature asks separately and you can revoke any of it later without breaking the " +
            "others.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )

    Spacer(Modifier.height(18.dp))
    SectionCard(title = "What LinkPhone uses") {
        OnboardingPermissions.features.forEachIndexed { index, spec ->
            PermissionRow(
                icon = iconFor(spec.id),
                title = spec.title,
                detail = spec.detail,
                status = if (granted[spec.id] == true) {
                    PermissionStatus.Allowed
                } else {
                    PermissionStatus.NeedsAccess
                },
                actionLabel = if (spec.id in blocked) "Open settings" else "Grant",
                onAction = {
                    if (spec.id in blocked) onOpenAppSettings() else onGrant(spec.id)
                },
                last = index == OnboardingPermissions.features.lastIndex,
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionCard(
        title = "Special access",
        subtitle = "These open a system screen instead of a popup.",
    ) {
        OnboardingPermissions.specialAccess.forEachIndexed { index, spec ->
            PermissionRow(
                icon = iconFor(spec.id),
                title = spec.title,
                detail = spec.detail,
                status = if (granted[spec.id] == true) {
                    PermissionStatus.Allowed
                } else {
                    PermissionStatus.NeedsAccess
                },
                actionLabel = if (granted[spec.id] == true) "Review" else "Open",
                onAction = { onOpenSpecial(spec.id) },
                last = index == OnboardingPermissions.specialAccess.lastIndex,
            )
        }
    }

    Text(
        "Nothing here is a trap: every row can stay off and you can turn any of it on later from " +
            "the Home screen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp),
    )
}

@Composable
private fun PairPage() {
    Text(
        "Pair with your PC",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        "Open LinkPhone on the PC, choose Pair a phone, then scan the QR code with this phone.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    Column(
        modifier = Modifier.padding(top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BulletRow(
            Icons.Outlined.QrCodeScanner,
            "One-time code",
            "The code expires five minutes after it is created. The camera is asked for now.",
        )
        BulletRow(
            Icons.Outlined.Lock,
            "Pinned certificate",
            "The QR includes the PC fingerprint; the phone refuses any other certificate.",
        )
    }
}

@Composable
private fun HeroMark() {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                RoundedCornerShape(22.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun PageDots(page: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(PAGE_COUNT) { index ->
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(if (index == page) 22.dp else 7.dp)
                    .background(
                        if (index == page) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

private fun iconFor(id: OnboardingPermission): ImageVector = when (id) {
    OnboardingPermission.Messages -> Icons.Outlined.Chat
    OnboardingPermission.Calls -> Icons.Outlined.Call
    OnboardingPermission.Contacts -> Icons.Outlined.Contacts
    OnboardingPermission.Photos -> Icons.Outlined.PhotoLibrary
    OnboardingPermission.Notifications -> Icons.Outlined.Notifications
    OnboardingPermission.NotificationAccess -> Icons.Outlined.NotificationsActive
    OnboardingPermission.RemoteTaps -> Icons.Outlined.TouchApp
    OnboardingPermission.Battery -> Icons.Outlined.BatteryFull
}

private fun grantedPermissions(context: Context): Map<OnboardingPermission, Boolean> = mapOf(
    OnboardingPermission.Messages to Permissions.sms(context),
    OnboardingPermission.Calls to Permissions.calls(context),
    OnboardingPermission.Contacts to Permissions.contacts(context),
    OnboardingPermission.Photos to Permissions.photos(context),
    OnboardingPermission.Notifications to Permissions.notifications(context),
    OnboardingPermission.NotificationAccess to Permissions.notificationAccess(context),
    OnboardingPermission.RemoteTaps to Permissions.accessibility(context),
    OnboardingPermission.Battery to Permissions.ignoringBatteryOptimizations(context),
)

private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")
