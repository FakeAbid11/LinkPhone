package app.linkphone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LinkPhoneColors = darkColorScheme(
    primary = Color(0xFF4FFFE4),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005045),
    onPrimaryContainer = Color(0xFF9CFFED),
    secondary = Color(0xFF8FD3FF),
    onSecondary = Color(0xFF00344D),
    background = Color(0xFF0B1018),
    onBackground = Color(0xFFE4EAF2),
    surface = Color(0xFF121A26),
    onSurface = Color(0xFFE4EAF2),
    surfaceVariant = Color(0xFF1C2736),
    onSurfaceVariant = Color(0xFFB8C4D4),
    error = Color(0xFFFFB4AB),
    outline = Color(0xFF3A485B),
)

@Composable
fun LinkPhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LinkPhoneColors, content = content)
}
