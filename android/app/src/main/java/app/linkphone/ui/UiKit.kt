package app.linkphone.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class StatusTone { Good, Warn, Bad, Neutral }

enum class PermissionStatus { Allowed, NeedsAccess, Unavailable }

/**
 * A Material 3 filled card. Surfaces step up the tonal elevation ladder instead
 * of drawing drop shadows, which is how Material 3 separates content in both
 * light and dark themes.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    IconBadge(icon)
                    Spacer(Modifier.width(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    size: Int = 40,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(container, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.55f).dp),
        )
    }
}

@Composable
fun StatusPill(text: String, tone: StatusTone) {
    val background = when (tone) {
        StatusTone.Good -> MaterialTheme.colorScheme.primaryContainer
        StatusTone.Warn -> MaterialTheme.colorScheme.tertiaryContainer
        StatusTone.Bad -> MaterialTheme.colorScheme.errorContainer
        StatusTone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val foreground = when (tone) {
        StatusTone.Good -> MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.Warn -> MaterialTheme.colorScheme.onTertiaryContainer
        StatusTone.Bad -> MaterialTheme.colorScheme.onErrorContainer
        StatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = background, contentColor = foreground, shape = CircleShape) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun Notice(
    title: String,
    body: String,
    tone: StatusTone = StatusTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val background = when (tone) {
        StatusTone.Good -> MaterialTheme.colorScheme.primaryContainer
        StatusTone.Warn -> MaterialTheme.colorScheme.tertiaryContainer
        StatusTone.Bad -> MaterialTheme.colorScheme.errorContainer
        StatusTone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val foreground = when (tone) {
        StatusTone.Good -> MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.Warn -> MaterialTheme.colorScheme.onTertiaryContainer
        StatusTone.Bad -> MaterialTheme.colorScheme.onErrorContainer
        StatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = background,
        contentColor = foreground,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
fun PermissionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    status: PermissionStatus,
    onAction: () -> Unit,
    actionLabel: String = "Grant",
    last: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            container = MaterialTheme.colorScheme.secondaryContainer,
            size = 36,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        when (status) {
            PermissionStatus.Allowed -> StatusPill("Allowed", StatusTone.Good)
            PermissionStatus.Unavailable -> Text(
                "Not available",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PermissionStatus.NeedsAccess -> FilledTonalButton(
                onClick = onAction,
                modifier = Modifier.semantics {
                    contentDescription = "$actionLabel $title"
                },
            ) {
                Text(actionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (!last) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun QuickAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Card(
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            IconBadge(
                icon,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                container = MaterialTheme.colorScheme.secondaryContainer,
                size = 44,
            )
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
fun ConsentScaffold(
    title: String,
    message: String,
    icon: ImageVector,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    detail: String? = null,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBadge(icon, size = 56)
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
                Text(primaryLabel)
            }
            OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                Text(secondaryLabel)
            }
        }
    }
}

@Composable
fun BulletRow(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        IconBadge(
            icon,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            container = MaterialTheme.colorScheme.secondaryContainer,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.padding(top = 2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun ProgressScaffold(
    title: String,
    message: String,
    icon: ImageVector,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            IconBadge(icon, size = 56)
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

fun Context.findHostActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

fun Context.openSystemSettings(action: String, data: android.net.Uri? = null) {
    runCatching {
        startActivity(
            Intent(action).setData(data).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Opens the system prompt that exempts LinkPhone from battery optimisation. */
fun Context.requestIgnoreBatteryOptimizations(): Boolean = runCatching {
    startActivity(
        Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            android.net.Uri.parse("package:$packageName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
}.getOrDefault(false)
