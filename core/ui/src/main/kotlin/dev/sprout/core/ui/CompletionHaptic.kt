/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The small thump that confirms a habit was completed.
 *
 * Not Compose's own [androidx.compose.ui.hapticfeedback.HapticFeedback]: its vocabulary in this
 * Compose version is `LongPress` and `TextHandleMove`, and neither of them means "that worked" —
 * a long-press thump on a checkbox reads as though something was held down by mistake. The
 * platform has had a constant for exactly this since API 30; below that the closest honest thing
 * is the light tick a key press makes.
 *
 * Fired on completing only. Un-ticking and skipping are corrections and decisions, not
 * achievements, and buzzing for them would make the buzz mean nothing.
 */
@Composable
public fun rememberCompletionHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                },
            )
        }
    }
}
