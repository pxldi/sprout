/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

/**
 * What the user did on a scheduled occasion.
 *
 * [SKIP] is a deliberate, guilt-free opt-out (illness, travel). It is *neutral*: it neither
 * advances nor breaks anything. [MISS] is the absence of a log. Nothing here ever resets a
 * score to zero — see docs/02-app-design.md, principle 3.
 */
public enum class EntryStatus {
    /** Completed in full. */
    DONE,

    /** Completed at the habit's "smallest version that still counts". Counts as done. */
    DONE_MIN,

    /** Deliberately skipped. Holds strength and the streak constant. */
    SKIP,

    /** Not done. Decays strength; may be absorbed by a rest day or repaired within 48 h. */
    MISS,

    /** An AVOID/REDUCE habit's slip, logged on purpose. Treated as a miss for scoring. */
    LAPSE,
    ;

    public val isCompletion: Boolean get() = this == DONE || this == DONE_MIN
}
