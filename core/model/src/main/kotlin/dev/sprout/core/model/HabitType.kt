/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

/** See docs/02-app-design.md, "Habit types". */
public enum class HabitType {
    /** Tap to complete. gym, meditate, read. */
    DO_BOOL,

    /** Numeric with a target. water 2 L, 8,000 steps. */
    DO_NUMERIC,

    /** Daily "clean day?" check-in. no alcohol, no smoking. */
    AVOID,

    /** Counter against a ceiling. <= 2 coffees. */
    REDUCE,

    /** Never logged; used only as a cue for stacked habits. */
    ANCHOR,
}
