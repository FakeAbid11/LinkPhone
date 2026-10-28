package app.linkphone.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object LinkPhonePalette {
    const val DarkBackground = 0xFF0B1018
    const val DarkSurface = 0xFF121A26
    const val DarkSurfaceVariant = 0xFF1C2736
    const val DarkOnBackground = 0xFFE6EDF7
    const val DarkOnSurfaceVariant = 0xFFC3CEDD
    const val DarkPrimary = 0xFF4FFFE4
    const val DarkOnPrimary = 0xFF003730
    const val DarkOutline = 0xFF5A6A80

    const val LightBackground = 0xFFF7FAFC
    const val LightSurface = 0xFFFFFFFF
    const val LightSurfaceVariant = 0xFFE4EBF3
    const val LightOnBackground = 0xFF12202E
    const val LightOnSurfaceVariant = 0xFF44566A
    const val LightPrimary = 0xFF00695C
    const val LightOnPrimary = 0xFFFFFFFF
    const val LightOutline = 0xFF73839A
}

private val DarkScheme = darkColorScheme(
    primary = Color(LinkPhonePalette.DarkPrimary),
    onPrimary = Color(LinkPhonePalette.DarkOnPrimary),
    primaryContainer = Color(0xFF005045),
    onPrimaryContainer = Color(0xFF9CFFED),
    secondary = Color(0xFF8FD3FF),
    onSecondary = Color(0xFF00344D),
    secondaryContainer = Color(0xFF0D3B55),
    onSecondaryContainer = Color(0xFFCBE7FF),
    tertiary = Color(0xFFFFD166),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5A4310),
    onTertiaryContainer = Color(0xFFFFE7B0),
    background = Color(LinkPhonePalette.DarkBackground),
    onBackground = Color(LinkPhonePalette.DarkOnBackground),
    surface = Color(LinkPhonePalette.DarkSurface),
    onSurface = Color(LinkPhonePalette.DarkOnBackground),
    surfaceVariant = Color(LinkPhonePalette.DarkSurfaceVariant),
    onSurfaceVariant = Color(LinkPhonePalette.DarkOnSurfaceVariant),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(LinkPhonePalette.DarkOutline),
    outlineVariant = Color(0xFF2C3A4D),
)

private val LightScheme = lightColorScheme(
    primary = Color(LinkPhonePalette.LightPrimary),
    onPrimary = Color(LinkPhonePalette.LightOnPrimary),
    primaryContainer = Color(0xFFB2DFD8),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF1F5F86),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE5FF),
    onSecondaryContainer = Color(0xFF001D31),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF2C1D00),
    background = Color(LinkPhonePalette.LightBackground),
    onBackground = Color(LinkPhonePalette.LightOnBackground),
    surface = Color(LinkPhonePalette.LightSurface),
    onSurface = Color(LinkPhonePalette.LightOnBackground),
    surfaceVariant = Color(LinkPhonePalette.LightSurfaceVariant),
    onSurfaceVariant = Color(LinkPhonePalette.LightOnSurfaceVariant),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(LinkPhonePalette.LightOutline),
    outlineVariant = Color(0xFFC2CEDC),
)

private val LinkTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)

private val LinkShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun LinkPhoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = LinkTypography,
        shapes = LinkShapes,
        content = content,
    )
}
