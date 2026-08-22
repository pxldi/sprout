/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The brand palette: moss green on warm sand, with bark and bloom as the warm accents.
 *
 * Fixed by default rather than wallpaper-derived. The plant metaphor is the product — a garden
 * that renders in whatever hue someone's wallpaper happens to be is a garden with no identity,
 * and Material You would happily hand this app a red primary, which the design rules forbid.
 *
 * Every role is specified. Anything left out of [lightColorScheme] falls back to Material's
 * baseline *purple*, so a half-filled scheme means purple chips and purple selection states
 * sitting next to a green ring.
 *
 * There is no red for a missed day anywhere in here. [MissNeutral] is what a miss looks like;
 * `error` exists only for genuine errors, like a form field that cannot be saved.
 */
public val MossGreen: Color = Color(0xFF4C6B45)
public val MossGreenLight: Color = Color(0xFFB6D3AC)
public val WarmSand: Color = Color(0xFFF6F1E4)
public val WarmSandDark: Color = Color(0xFF1B1C18)
public val MissNeutral: Color = Color(0xFF8E918C)

private val BrandLight: ColorScheme = lightColorScheme(
    primary = MossGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E8C6),
    onPrimaryContainer = Color(0xFF10200B),

    // Bark: the warm, woody counterweight to all the green.
    secondary = Color(0xFF8C6E52),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0DFCB),
    onSecondaryContainer = Color(0xFF2E1F10),

    // Bloom: reserved for the small celebratory moments, never for anything corrective.
    tertiary = Color(0xFFB0705F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7DDD2),
    onTertiaryContainer = Color(0xFF3A1710),

    background = WarmSand,
    onBackground = Color(0xFF1B1C18),
    surface = WarmSand,
    onSurface = Color(0xFF1B1C18),
    surfaceVariant = Color(0xFFE6E0D0),
    onSurfaceVariant = Color(0xFF4A4A42),
    surfaceContainerLowest = Color(0xFFFFFDF6),
    surfaceContainerLow = Color(0xFFF9F5E9),
    surfaceContainer = Color(0xFFF2EDDF),
    surfaceContainerHigh = Color(0xFFECE7D8),
    surfaceContainerHighest = Color(0xFFE6E1D2),

    outline = MissNeutral,
    outlineVariant = Color(0xFFCFC9B8),

    // Muted brick, not signal red. Loud enough to read as "fix this", quiet enough that it
    // could never be mistaken for the app's opinion of the user.
    error = Color(0xFF9A4B3F),
    onError = Color.White,
    errorContainer = Color(0xFFF7DAD5),
    onErrorContainer = Color(0xFF3B0A05),
)

private val BrandDark: ColorScheme = darkColorScheme(
    primary = MossGreenLight,
    onPrimary = Color(0xFF1E361A),
    primaryContainer = Color(0xFF35522F),
    onPrimaryContainer = Color(0xFFD3E8C6),

    secondary = Color(0xFFD9BFA3),
    onSecondary = Color(0xFF3B2A18),
    secondaryContainer = Color(0xFF503A26),
    onSecondaryContainer = Color(0xFFF0DFCB),

    tertiary = Color(0xFFE8B6A3),
    onTertiary = Color(0xFF44231A),
    tertiaryContainer = Color(0xFF5C3628),
    onTertiaryContainer = Color(0xFFF7DDD2),

    background = WarmSandDark,
    onBackground = Color(0xFFE3E3DB),
    surface = WarmSandDark,
    onSurface = Color(0xFFE3E3DB),
    surfaceVariant = Color(0xFF44483F),
    onSurfaceVariant = Color(0xFFC5C8BB),
    surfaceContainerLowest = Color(0xFF131513),
    surfaceContainerLow = Color(0xFF1B1C18),
    surfaceContainer = Color(0xFF20221D),
    surfaceContainerHigh = Color(0xFF2A2C27),
    surfaceContainerHighest = Color(0xFF353731),

    outline = MissNeutral,
    outlineVariant = Color(0xFF44483F),

    error = Color(0xFFE7A79C),
    onError = Color(0xFF57170F),
    errorContainer = Color(0xFF77291E),
    onErrorContainer = Color(0xFFF7DAD5),
)

/** True-black surfaces for OLED panels; opt-in from Settings. */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
)

/**
 * [dynamicColor] defaults to *off*: the brand palette is the identity, and Material You is an
 * opt-in for people who want the app to match their wallpaper.
 *
 * [darkTheme] still follows the system. That is not the same choice — a light-only app is
 * unreadable in bed and burns an OLED panel, and Android's own dark-mode contrast enforcement
 * would override the palette anyway.
 */
@Composable
public fun SproutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && supportsDynamic -> dynamicLightColorScheme(context)
        darkTheme -> BrandDark
        else -> BrandLight
    }
    MaterialTheme(
        colorScheme = if (darkTheme && amoled) scheme.toAmoled() else scheme,
        content = content,
    )
}
