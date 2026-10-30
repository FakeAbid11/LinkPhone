package app.linkphone.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 colour roles for LinkPhone, dark only.
 *
 * The values are the same tokens the Windows app declares in styles.css, so a
 * feature looks identical on both screens. There is no wallpaper-derived
 * (Material You) palette and no light scheme: the PC app has no light theme, and
 * following the phone's system setting would make the two apps disagree.
 * ThemeContrastTest checks the pairs the UI actually draws text with.
 */
internal object LinkPhonePalette {
    const val DarkBackground = 0xFF101418
    const val DarkSurface = 0xFF1C2024
    const val DarkSurfaceVariant = 0xFF42474E
    const val DarkOnBackground = 0xFFE1E2E8
    const val DarkOnSurfaceVariant = 0xFFC2C7CF
    const val DarkPrimary = 0xFFA9C7FF
    const val DarkOnPrimary = 0xFF003258
    const val DarkPrimaryContainer = 0xFF00497D
    const val DarkOnPrimaryContainer = 0xFFD1E4FF
    const val DarkSecondary = 0xFFB9C7DA
    const val DarkOnSecondary = 0xFF233240
    const val DarkSecondaryContainer = 0xFF394857
    const val DarkOnSecondaryContainer = 0xFFD5E3F7
    const val DarkTertiary = 0xFFEBC38D
    const val DarkOnTertiary = 0xFF432C00
    const val DarkTertiaryContainer = 0xFF5C4200
    const val DarkOnTertiaryContainer = 0xFFFFDFA6
    const val DarkError = 0xFFFFB4AB
    const val DarkOnError = 0xFF690005
    const val DarkErrorContainer = 0xFF93000A
    const val DarkOnErrorContainer = 0xFFFFDAD6
    const val DarkOutline = 0xFF8C9199
    const val DarkOutlineVariant = 0xFF42474E
    const val DarkSurfaceDim = 0xFF101418
    const val DarkSurfaceBright = 0xFF363A3E
    const val DarkSurfaceContainerLowest = 0xFF0B0F13
    const val DarkSurfaceContainerLow = 0xFF181C20
    const val DarkSurfaceContainer = 0xFF1C2024
    const val DarkSurfaceContainerHigh = 0xFF262A2F
    const val DarkSurfaceContainerHighest = 0xFF31353A
    const val DarkInverseSurface = 0xFFE1E2E8
    const val DarkInverseOnSurface = 0xFF2D3135
    const val DarkInversePrimary = 0xFF0061A4
    const val DarkScrim = 0xFF000000
}

private val DarkScheme = darkColorScheme(
    primary = Color(LinkPhonePalette.DarkPrimary),
    onPrimary = Color(LinkPhonePalette.DarkOnPrimary),
    primaryContainer = Color(LinkPhonePalette.DarkPrimaryContainer),
    onPrimaryContainer = Color(LinkPhonePalette.DarkOnPrimaryContainer),
    secondary = Color(LinkPhonePalette.DarkSecondary),
    onSecondary = Color(LinkPhonePalette.DarkOnSecondary),
    secondaryContainer = Color(LinkPhonePalette.DarkSecondaryContainer),
    onSecondaryContainer = Color(LinkPhonePalette.DarkOnSecondaryContainer),
    tertiary = Color(LinkPhonePalette.DarkTertiary),
    onTertiary = Color(LinkPhonePalette.DarkOnTertiary),
    tertiaryContainer = Color(LinkPhonePalette.DarkTertiaryContainer),
    onTertiaryContainer = Color(LinkPhonePalette.DarkOnTertiaryContainer),
    background = Color(LinkPhonePalette.DarkBackground),
    onBackground = Color(LinkPhonePalette.DarkOnBackground),
    surface = Color(LinkPhonePalette.DarkSurface),
    onSurface = Color(LinkPhonePalette.DarkOnBackground),
    surfaceVariant = Color(LinkPhonePalette.DarkSurfaceVariant),
    onSurfaceVariant = Color(LinkPhonePalette.DarkOnSurfaceVariant),
    error = Color(LinkPhonePalette.DarkError),
    onError = Color(LinkPhonePalette.DarkOnError),
    errorContainer = Color(LinkPhonePalette.DarkErrorContainer),
    onErrorContainer = Color(LinkPhonePalette.DarkOnErrorContainer),
    outline = Color(LinkPhonePalette.DarkOutline),
    outlineVariant = Color(LinkPhonePalette.DarkOutlineVariant),
    surfaceDim = Color(LinkPhonePalette.DarkSurfaceDim),
    surfaceBright = Color(LinkPhonePalette.DarkSurfaceBright),
    surfaceContainerLowest = Color(LinkPhonePalette.DarkSurfaceContainerLowest),
    surfaceContainerLow = Color(LinkPhonePalette.DarkSurfaceContainerLow),
    surfaceContainer = Color(LinkPhonePalette.DarkSurfaceContainer),
    surfaceContainerHigh = Color(LinkPhonePalette.DarkSurfaceContainerHigh),
    surfaceContainerHighest = Color(LinkPhonePalette.DarkSurfaceContainerHighest),
    inverseSurface = Color(LinkPhonePalette.DarkInverseSurface),
    inverseOnSurface = Color(LinkPhonePalette.DarkInverseOnSurface),
    inversePrimary = Color(LinkPhonePalette.DarkInversePrimary),
    scrim = Color(LinkPhonePalette.DarkScrim),
)

/** Material 3 type scale, tuned to the system UI font. */
private val LinkTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

/** The Material 3 shape scale (4, 8, 12, 16, 28 dp). */
private val LinkShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LinkPhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = LinkTypography,
        shapes = LinkShapes,
        content = content,
    )
}
