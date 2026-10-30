package app.linkphone.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ThemeContrastTest {
    private fun channel(value: Int): Double {
        val unit = value / 255.0
        return if (unit <= 0.03928) unit / 12.92 else ((unit + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Long): Double {
        val red = channel(((color shr 16) and 0xFF).toInt())
        val green = channel(((color shr 8) and 0xFF).toInt())
        val blue = channel((color and 0xFF).toInt())
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }

    private fun ratio(first: Long, second: Long): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun assertContrast(name: String, foreground: Long, background: Long, minimum: Double) {
        val value = ratio(foreground, background)
        assertTrue(
            "$name contrast was ${"%.2f".format(value)}:1, expected at least $minimum:1",
            value >= minimum,
        )
    }

    @Test
    fun darkPaletteMeetsWcag() {
        assertContrast(
            "dark body text",
            LinkPhonePalette.DarkOnBackground,
            LinkPhonePalette.DarkBackground,
            4.5,
        )
        assertContrast(
            "dark text on cards",
            LinkPhonePalette.DarkOnBackground,
            LinkPhonePalette.DarkSurface,
            4.5,
        )
        assertContrast(
            "dark secondary text",
            LinkPhonePalette.DarkOnSurfaceVariant,
            LinkPhonePalette.DarkSurfaceVariant,
            4.5,
        )
        assertContrast(
            "dark primary text",
            LinkPhonePalette.DarkPrimary,
            LinkPhonePalette.DarkSurface,
            4.5,
        )
        assertContrast(
            "dark button label",
            LinkPhonePalette.DarkOnPrimary,
            LinkPhonePalette.DarkPrimary,
            4.5,
        )
        assertContrast(
            "dark secondary text",
            LinkPhonePalette.DarkSecondary,
            LinkPhonePalette.DarkSurface,
            4.5,
        )
        assertContrast(
            "dark primary container",
            LinkPhonePalette.DarkOnPrimaryContainer,
            LinkPhonePalette.DarkPrimaryContainer,
            4.5,
        )
        assertContrast(
            "dark secondary container",
            LinkPhonePalette.DarkOnSecondaryContainer,
            LinkPhonePalette.DarkSecondaryContainer,
            4.5,
        )
        assertContrast(
            "dark tertiary container",
            LinkPhonePalette.DarkOnTertiaryContainer,
            LinkPhonePalette.DarkTertiaryContainer,
            4.5,
        )
        assertContrast(
            "dark error container",
            LinkPhonePalette.DarkOnErrorContainer,
            LinkPhonePalette.DarkErrorContainer,
            4.5,
        )
        assertContrast(
            "dark outline",
            LinkPhonePalette.DarkOutline,
            LinkPhonePalette.DarkSurface,
            3.0,
        )
    }

    @Test
    fun darkSurfaceContainersMeetWcag() {
        val containers = listOf(
            "lowest" to LinkPhonePalette.DarkSurfaceContainerLowest,
            "low" to LinkPhonePalette.DarkSurfaceContainerLow,
            "container" to LinkPhonePalette.DarkSurfaceContainer,
            "high" to LinkPhonePalette.DarkSurfaceContainerHigh,
            "highest" to LinkPhonePalette.DarkSurfaceContainerHighest,
            "dim" to LinkPhonePalette.DarkSurfaceDim,
            "bright" to LinkPhonePalette.DarkSurfaceBright,
        )
        for ((name, background) in containers) {
            assertContrast(
                "dark body text on surface $name",
                LinkPhonePalette.DarkOnBackground,
                background,
                4.5,
            )
        }
    }

    @Test
    fun inverseSurfacesMeetWcag() {
        assertContrast(
            "dark inverse text",
            LinkPhonePalette.DarkInverseOnSurface,
            LinkPhonePalette.DarkInverseSurface,
            4.5,
        )
        assertContrast(
            "dark inverse action label",
            LinkPhonePalette.DarkInversePrimary,
            LinkPhonePalette.DarkInverseSurface,
            4.5,
        )
    }
}
