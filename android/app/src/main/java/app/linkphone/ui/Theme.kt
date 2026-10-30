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
    const val DarkBackground = 0xFF0B0F19
    const val DarkSurface = 0xFF141B2B
    const val DarkSurfaceVariant = 0xFF1E283D
    const val DarkOnBackground = 0xFFE7ECF5
    const val DarkOnSurfaceVariant = 0xFFC3CCE0
    const val DarkPrimary = 0xFF8B93FF
    const val DarkOnPrimary = 0xFF0B0F19
    const val DarkPrimaryContainer = 0xFF3A3F8F
    const val DarkOnPrimaryContainer = 0xFFE1E3FF
    const val DarkSecondary = 0xFF9DB8FF
    const val DarkOnSecondary = 0xFF0B1830
    const val DarkSecondaryContainer = 0xFF263A66
    const val DarkOnSecondaryContainer = 0xFFD8E2FF
    const val DarkTertiary = 0xFFFFCF7A
    const val DarkOnTertiary = 0xFF3A2A00
    const val DarkTertiaryContainer = 0xFF564214
    const val DarkOnTertiaryContainer = 0xFFFFE2AF
    const val DarkError = 0xFFFFB4AB
    const val DarkOnError = 0xFF690005
    const val DarkErrorContainer = 0xFF93000A
    const val DarkOnErrorContainer = 0xFFFFDAD6
    const val DarkOutline = 0xFF5D6A85
    const val DarkOutlineVariant = 0xFF2C3650
    const val DarkSurfaceDim = 0xFF0B0F19
    const val DarkSurfaceBright = 0xFF2A3448
    const val DarkSurfaceContainerLowest = 0xFF0E1422
    const val DarkSurfaceContainerLow = 0xFF111726
    const val DarkSurfaceContainer = 0xFF171F31
    const val DarkSurfaceContainerHigh = 0xFF1C2537
    const val DarkSurfaceContainerHighest = 0xFF212C42
    const val DarkInverseSurface = 0xFFE7ECF5
    const val DarkInverseOnSurface = 0xFF141A28
    const val DarkInversePrimary = 0xFF4F46E5
    const val DarkScrim = 0xFF000000

    const val LightBackground = 0xFFF7F8FC
    const val LightSurface = 0xFFFFFFFF
    const val LightSurfaceVariant = 0xFFE7EAF3
    const val LightOnBackground = 0xFF141A28
    const val LightOnSurfaceVariant = 0xFF46506A
    const val LightPrimary = 0xFF4F46E5
    const val LightOnPrimary = 0xFFFFFFFF
    const val LightPrimaryContainer = 0xFFDFE0FF
    const val LightOnPrimaryContainer = 0xFF11144A
    const val LightSecondary = 0xFF3A5FA8
    const val LightOnSecondary = 0xFFFFFFFF
    const val LightSecondaryContainer = 0xFFD9E3FF
    const val LightOnSecondaryContainer = 0xFF0A1C3A
    const val LightTertiary = 0xFF8A5A00
    const val LightOnTertiary = 0xFFFFFFFF
    const val LightTertiaryContainer = 0xFFFFDEA6
    const val LightOnTertiaryContainer = 0xFF2C1D00
    const val LightError = 0xFFB3261E
    const val LightOnError = 0xFFFFFFFF
    const val LightErrorContainer = 0xFFFFDAD6
    const val LightOnErrorContainer = 0xFF410002
    const val LightOutline = 0xFF737E94
    const val LightOutlineVariant = 0xFFC6CDDC
    const val LightSurfaceDim = 0xFFDDE2EC
    const val LightSurfaceBright = 0xFFFFFFFF
    const val LightSurfaceContainerLowest = 0xFFFFFFFF
    const val LightSurfaceContainerLow = 0xFFF4F6FB
    const val LightSurfaceContainer = 0xFFEEF1F7
    const val LightSurfaceContainerHigh = 0xFFE8ECF4
    const val LightSurfaceContainerHighest = 0xFFE2E7F0
    const val LightInverseSurface = 0xFF2A3242
    const val LightInverseOnSurface = 0xFFE7ECF5
    const val LightInversePrimary = 0xFF8B93FF
    const val LightScrim = 0xFF000000
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

private val LightScheme = lightColorScheme(
    primary = Color(LinkPhonePalette.LightPrimary),
    onPrimary = Color(LinkPhonePalette.LightOnPrimary),
    primaryContainer = Color(LinkPhonePalette.LightPrimaryContainer),
    onPrimaryContainer = Color(LinkPhonePalette.LightOnPrimaryContainer),
    secondary = Color(LinkPhonePalette.LightSecondary),
    onSecondary = Color(LinkPhonePalette.LightOnSecondary),
    secondaryContainer = Color(LinkPhonePalette.LightSecondaryContainer),
    onSecondaryContainer = Color(LinkPhonePalette.LightOnSecondaryContainer),
    tertiary = Color(LinkPhonePalette.LightTertiary),
    onTertiary = Color(LinkPhonePalette.LightOnTertiary),
    tertiaryContainer = Color(LinkPhonePalette.LightTertiaryContainer),
    onTertiaryContainer = Color(LinkPhonePalette.LightOnTertiaryContainer),
    background = Color(LinkPhonePalette.LightBackground),
    onBackground = Color(LinkPhonePalette.LightOnBackground),
    surface = Color(LinkPhonePalette.LightSurface),
    onSurface = Color(LinkPhonePalette.LightOnBackground),
    surfaceVariant = Color(LinkPhonePalette.LightSurfaceVariant),
    onSurfaceVariant = Color(LinkPhonePalette.LightOnSurfaceVariant),
    error = Color(LinkPhonePalette.LightError),
    onError = Color(LinkPhonePalette.LightOnError),
    errorContainer = Color(LinkPhonePalette.LightErrorContainer),
    onErrorContainer = Color(LinkPhonePalette.LightOnErrorContainer),
    outline = Color(LinkPhonePalette.LightOutline),
    outlineVariant = Color(LinkPhonePalette.LightOutlineVariant),
    surfaceDim = Color(LinkPhonePalette.LightSurfaceDim),
    surfaceBright = Color(LinkPhonePalette.LightSurfaceBright),
    surfaceContainerLowest = Color(LinkPhonePalette.LightSurfaceContainerLowest),
    surfaceContainerLow = Color(LinkPhonePalette.LightSurfaceContainerLow),
    surfaceContainer = Color(LinkPhonePalette.LightSurfaceContainer),
    surfaceContainerHigh = Color(LinkPhonePalette.LightSurfaceContainerHigh),
    surfaceContainerHighest = Color(LinkPhonePalette.LightSurfaceContainerHighest),
    inverseSurface = Color(LinkPhonePalette.LightInverseSurface),
    inverseOnSurface = Color(LinkPhonePalette.LightInverseOnSurface),
    inversePrimary = Color(LinkPhonePalette.LightInversePrimary),
    scrim = Color(LinkPhonePalette.LightScrim),
)

private val LinkTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 50.sp,
        lineHeight = 58.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.4).sp,
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.25).sp,
    ),
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
