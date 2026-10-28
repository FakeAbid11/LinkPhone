package app.linkphone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    onPair: () -> Unit,
    onFinish: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(0) }
    BackHandler(enabled = page > 0) { page -= 1 }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.Lock)
                Spacer(Modifier.width(10.dp))
                Text("LinkPhone", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (page < 2) {
                    TextButton(onClick = onFinish) { Text("Skip") }
                }
            }

            Spacer(Modifier.height(34.dp))

            when (page) {
                0 -> WelcomePage()
                1 -> PermissionsPage()
                else -> PairPage()
            }

            Spacer(Modifier.height(30.dp))
            PageDots(page)
            Spacer(Modifier.height(22.dp))

            when (page) {
                0 -> Button(onClick = { page = 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Get started")
                }
                1 -> {
                    Button(onClick = { page = 2 }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue")
                    }
                    TextButton(onClick = { page = 2 }, modifier = Modifier.fillMaxWidth()) {
                        Text("Not now")
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
                    TextButton(onClick = { page = 1 }, modifier = Modifier.fillMaxWidth()) {
                        Text("Back")
                    }
                }
            }

            Text(
                "Encrypted on your local network. No account, no cloud relay.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
            )
        }
    }
}

@Composable
private fun WelcomePage() {
    Text("Your phone, on your PC", style = MaterialTheme.typography.headlineMedium)
    Text(
        "LinkPhone connects both devices over your own Wi-Fi so you can use the phone from a " +
            "keyboard and a larger screen.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    Column(
        modifier = Modifier.padding(top = 24.dp),
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
private fun PermissionsPage() {
    Text("Grant only what you use", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Every feature asks separately, and Android shows each system prompt. You can revoke any " +
            "permission later without breaking the others.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    Column(
        modifier = Modifier.padding(top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BulletRow(Icons.Outlined.Chat, "Messages", "Read, receive, and send SMS.")
        BulletRow(Icons.Outlined.Call, "Calls", "Call state, dialing, and answer or decline.")
        BulletRow(Icons.Outlined.Contacts, "Contacts", "Search names on the PC and identify callers.")
        BulletRow(Icons.Outlined.PhotoLibrary, "Photos", "Share recent images with the PC.")
        BulletRow(Icons.Outlined.Notifications, "Notifications", "Mirror notifications over the PC.")
        BulletRow(
            Icons.Outlined.TouchApp,
            "Remote taps",
            "Accessibility service for taps and swipes on the mirrored screen.",
        )
    }
    Text(
        "Nothing is granted from this screen. You will see the system prompts on the next step.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp),
    )
}

@Composable
private fun PairPage() {
    Text("Pair with your PC", style = MaterialTheme.typography.headlineSmall)
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
            "The code expires five minutes after it is created.",
        )
        BulletRow(
            Icons.Outlined.Lock,
            "Pinned certificate",
            "The QR includes the PC fingerprint; the phone refuses any other certificate.",
        )
    }
}

@Composable
private fun PageDots(page: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
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
