package nl.angryuncle.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import nl.angryuncle.R

/** Family-dinner palette: mustard walls, paper-cream tablecloth, dark-brown ink, one furious red. */
object UnclePalette {
    val Mustard = Color(0xFFFFC94A)
    val Cream = Color(0xFFFFF4E3)
    val Ink = Color(0xFF3B2A20)
    val Fury = Color(0xFFE0332B)

    // Dark mode: the same dinner after the lights went out.
    val NightInk = Color(0xFF241812)
    val NightTable = Color(0xFF3A2A1F)
    val NightFury = Color(0xFFFF5A47)
}

@OptIn(ExperimentalTextApi::class)
val UncleFont = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(
            R.font.fredoka,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

private fun Typography.withFamily(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

private val Light = lightColorScheme(
    primary = UnclePalette.Fury,
    onPrimary = UnclePalette.Cream,
    secondary = UnclePalette.Ink,
    onSecondary = UnclePalette.Cream,
    background = UnclePalette.Mustard,
    onBackground = UnclePalette.Ink,
    surface = UnclePalette.Cream,
    onSurface = UnclePalette.Ink,
    surfaceVariant = UnclePalette.Cream,
    onSurfaceVariant = UnclePalette.Ink,
    secondaryContainer = UnclePalette.Cream,
    onSecondaryContainer = UnclePalette.Ink,
    outline = UnclePalette.Ink,
)

private val Dark = darkColorScheme(
    primary = UnclePalette.NightFury,
    onPrimary = UnclePalette.NightInk,
    secondary = UnclePalette.Mustard,
    onSecondary = UnclePalette.NightInk,
    background = UnclePalette.NightInk,
    onBackground = UnclePalette.Cream,
    surface = UnclePalette.NightTable,
    onSurface = UnclePalette.Cream,
    surfaceVariant = UnclePalette.NightTable,
    onSurfaceVariant = UnclePalette.Cream,
    secondaryContainer = UnclePalette.NightTable,
    onSecondaryContainer = UnclePalette.Cream,
    outline = UnclePalette.Mustard,
)

@Composable
fun AngryUncleTheme(content: @Composable () -> Unit) {
    // Deliberately no dynamic colour: the palette is the identity of the app.
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography().withFamily(UncleFont),
        content = content,
    )
}
