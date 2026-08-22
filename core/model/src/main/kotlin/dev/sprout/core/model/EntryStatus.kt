/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

/**
 * What the user logged on a given day.
 *
 * There is deliberately no `MISS` member. A miss is the *absence* of an entry, and it is derived
 * by the scorer as [dev.sprout.core.scoring.OccasionOutcome.MISSED]. Storing it as well would let
 * the two representations disagree — a row saying MISS on a day that also has a DONE row.
 *
 * [SKIP] is a deliberate, guilt-free opt-out (illness, travel). It is neutral: it neither advances
 * nor breaks anything.
 */
public enum class EntryStatus {
    /** Completed in full. */
    DONE,

    /** Completed at the habit's "smallest version that still counts". Counts as done. */
    DONE_MIN,

    /** Deliberately skipped. Holds strength and the run constant. */
    SKIP,

    /** An AVOID/REDUCE habit's slip, logged on purpose. Scored as a miss. */
    LAPSE,
    ;

    public val isCompletion: Boolean get() = this == DONE || this == DONE_MIN
}
