/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val FULL_CIRCLE_DEGREES = 360f
private const val START_AT_TWELVE_OCLOCK = -90f
private const val TRACK_ALPHA = 0.18f
private const val GROW_MILLIS = 600

/**
 * A habit's strength, 0..100.
 *
 * Note what it cannot do: the arc never turns red, never empties, and never animates *downwards*
 * fast. A miss shows up as a slightly shorter arc next time the screen is opened — a dent, which
 * is what the score actually is.
 *
 * The arc is decorative; [label] carries the meaning for screen readers, so callers supply a full
 * sentence ("Morning run, strength 74 of 100") rather than letting TalkBack read a bare number.
 */
@Composable
public fun StrengthRing(
    strength: Double,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    content: (@Composable () -> Unit)? = null,
) {
    val target = (strength / MAX_STRENGTH).toFloat().coerceIn(0f, 1f)
    // Screenshot tests need a settled frame, not an animation in progress.
    val fraction = if (LocalInspectionMode.current) {
        target
    } else {
        animateFloatAsState(target, tween(GROW_MILLIS), label = "strength").value
    }
    val track = color.copy(alpha = TRACK_ALPHA)

    Box(
        modifier = modifier.size(size).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = this.size.minDimension * STROKE_FRACTION)
            val inset = stroke.width / 2
            val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = FULL_CIRCLE_DEGREES,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = color,
                startAngle = START_AT_TWELVE_OCLOCK,
                sweepAngle = FULL_CIRCLE_DEGREES * fraction,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
        if (content != null) {
            content()
        } else {
            Text(
                text = "${strength.roundToInt()}",
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current,
            )
        }
    }
}

private const val MAX_STRENGTH = 100.0
private const val STROKE_FRACTION = 0.12f
