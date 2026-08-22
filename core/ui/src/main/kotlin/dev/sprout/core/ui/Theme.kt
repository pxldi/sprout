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
 * Brand fallback palette: moss green primary on warm sand surfaces.
 *
 * Deliberately contains no red. A missed day is *not* an error state — misses render in
 * [MissNeutral] (see docs/02-app-design.md, "Visual identity").
 */
public val MossGreen: Color = Color(0xFF4C6B45)
public val MossGreenLight: Color = Color(0xFFB6D3AC)
public val WarmSand: Color = Color(0xFFF6F1E4)
public val WarmSandDark: Color = Color(0xFF1B1C18)
public val MissNeutral: Color = Color(0xFF8E918C)

private val BrandLight: ColorScheme = lightColorScheme(
    primary = MossGreen,
    onPrimary = Color.White,
    primaryContainer = MossGreenLight,
    onPrimaryContainer = Color(0xFF0B2007),
    background = WarmSand,
    surface = WarmSand,
    onSurface = Color(0xFF1B1C18),
    outline = MissNeutral,
)

private val BrandDark: ColorScheme = darkColorScheme(
    primary = MossGreenLight,
    onPrimary = Color(0xFF1E361A),
    primaryContainer = Color(0xFF35522F),
    onPrimaryContainer = MossGreenLight,
    background = WarmSandDark,
    surface = WarmSandDark,
    onSurface = Color(0xFFE3E3DB),
    outline = MissNeutral,
)

/** True-black surfaces for OLED panels; opt-in from Settings. */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
)

@Composable
public fun SproutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
