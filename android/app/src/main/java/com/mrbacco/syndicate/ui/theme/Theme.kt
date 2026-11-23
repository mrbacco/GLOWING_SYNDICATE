/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/ui/theme/Theme.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-10-20
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.ui.theme

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
import com.mrbacco.syndicate.R

// Pastel look: warm greys for backgrounds and text, pastel yellow for highlights (bonus ball,
// things that need attention) and a muted mustard where colour has to carry text or a filled
// button (matched numbers, "paid up", "won"), so it stays readable. Every scheme slot is set so
// Material does not fill the gaps with its default purple and red.
// Keep surfaceContainerHighest (card background) different from surfaceVariant: if they are
// equal, Material gives cards the faded onSurfaceVariant text colour instead of onSurface.
private val Mustard = Color(0xFF7D6318)
private val Butter = Color(0xFFFFF0B3)
private val ButterSoft = Color(0xFFF5DE8A)
private val ButterInk = Color(0xFF3A3000)
private val Khaki = Color(0xFF4D4838)
private val OliveGrey = Color(0xFF6B675E)

private val LightColors = lightColorScheme(
    primary = Mustard,
    onPrimary = Color.White,
    primaryContainer = Butter,
    onPrimaryContainer = ButterInk,
    inversePrimary = ButterSoft,
    secondary = OliveGrey,
    onSecondary = Color.White,
    secondaryContainer = Butter,
    onSecondaryContainer = ButterInk,
    tertiary = OliveGrey,
    onTertiary = Color.White,
    tertiaryContainer = Butter,
    onTertiaryContainer = ButterInk,
    error = Mustard,
    onError = Color.White,
    errorContainer = Butter,
    onErrorContainer = ButterInk,
    background = Color(0xFFF6F5F2),
    onBackground = Color(0xFF2B2A27),
    surface = Color(0xFFF6F5F2),
    onSurface = Color(0xFF2B2A27),
    surfaceVariant = Color(0xFFE7E5E0),
    onSurfaceVariant = Color(0xFF5E5C57),
    surfaceTint = Color(0xFF8C8A84),
    inverseSurface = Color(0xFF34332F),
    inverseOnSurface = Color(0xFFF2F1EC),
    outline = Color(0xFF8C8A84),
    outlineVariant = Color(0xFFD6D4CE),
    surfaceBright = Color(0xFFF6F5F2),
    surfaceDim = Color(0xFFDCDAD5),
    surfaceContainerLowest = Color(0xFFFCFBF9),
    surfaceContainerLow = Color(0xFFF1F0EC),
    surfaceContainer = Color(0xFFECEAE6),
    surfaceContainerHigh = Color(0xFFE6E4DF),
    surfaceContainerHighest = Color(0xFFE0DED9),
)

private val DarkColors = darkColorScheme(
    primary = ButterSoft,
    onPrimary = ButterInk,
    primaryContainer = Khaki,
    onPrimaryContainer = Butter,
    inversePrimary = Mustard,
    secondary = Color(0xFFCFCBC0),
    onSecondary = Color(0xFF2B2A27),
    secondaryContainer = Khaki,
    onSecondaryContainer = Butter,
    tertiary = Color(0xFFCFCBC0),
    onTertiary = Color(0xFF2B2A27),
    tertiaryContainer = Khaki,
    onTertiaryContainer = Butter,
    error = ButterSoft,
    onError = ButterInk,
    errorContainer = Khaki,
    onErrorContainer = Butter,
    background = Color(0xFF1C1C1A),
    onBackground = Color(0xFFE8E6E0),
    surface = Color(0xFF1C1C1A),
    onSurface = Color(0xFFE8E6E0),
    surfaceVariant = Color(0xFF3A3935),
    onSurfaceVariant = Color(0xFFC2C0B9),
    surfaceTint = Color(0xFF8C8A84),
    inverseSurface = Color(0xFFE8E6E0),
    inverseOnSurface = Color(0xFF34332F),
    outline = Color(0xFF8C8A84),
    outlineVariant = Color(0xFF46453F),
    surfaceBright = Color(0xFF3A3935),
    surfaceDim = Color(0xFF1C1C1A),
    surfaceContainerLowest = Color(0xFF151513),
    surfaceContainerLow = Color(0xFF21211F),
    surfaceContainer = Color(0xFF252523),
    surfaceContainerHigh = Color(0xFF2C2B28),
    surfaceContainerHighest = Color(0xFF32312E),
)

// Open Sans at its narrowest width (75), bundled in res/font (SIL OFL, see app/licenses).
// It stands in for Aptos Narrow, which is licensed with Microsoft Office and cannot be shipped.
@OptIn(ExperimentalTextApi::class)
private fun narrow(weight: FontWeight) = Font(
    R.font.open_sans,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.width(75f)),
)

private val Narrow = FontFamily(
    narrow(FontWeight.Normal),
    narrow(FontWeight.Medium),
    narrow(FontWeight.SemiBold),
    narrow(FontWeight.Bold),
)

private fun TextStyle.narrow() = copy(fontFamily = Narrow)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.narrow(),
        displayMedium = displayMedium.narrow(),
        displaySmall = displaySmall.narrow(),
        headlineLarge = headlineLarge.narrow(),
        headlineMedium = headlineMedium.narrow(),
        headlineSmall = headlineSmall.narrow(),
        titleLarge = titleLarge.narrow(),
        titleMedium = titleMedium.narrow(),
        titleSmall = titleSmall.narrow(),
        bodyLarge = bodyLarge.narrow(),
        bodyMedium = bodyMedium.narrow(),
        bodySmall = bodySmall.narrow(),
        labelLarge = labelLarge.narrow(),
        labelMedium = labelMedium.narrow(),
        labelSmall = labelSmall.narrow(),
    )
}

@Composable
fun SyndicateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
