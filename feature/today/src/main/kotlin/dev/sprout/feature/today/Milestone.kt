/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import dev.sprout.core.model.Entry
import java.time.LocalDate

/**
 * The two moments in a habit's life that get more than a sentence.
 *
 * Counted in completions — not in days elapsed, and not in the scorer's closed occasions. Both of
 * those can be run up by missing, and a card congratulating somebody for the passage of time is
 * exactly the hollow number this app refuses everywhere else. Sixty-six *repetitions* is also what
 * the research counted; sixty-six days containing forty misses is not the same claim.
 *
 * Deliberately a different clock from the one `PlantStage.INGRAINED` runs on, which needs strength
 * as well as time served. They are different claims and both are true: this one is "you have done
 * this sixty-six times", the other is "it is strong, and has been for long enough to count".
 *
 * Note what the wording therefore cannot say: "day 7". At its seventh completion a Mon/Wed/Fri
 * habit is a fortnight old, and the card would be lying on the one day it is trying hardest.
 */
public enum class Milestone(public val completions: Int) {
    /** A week's worth of repetitions, however long they took to collect. */
    SEVEN(A_WEEKS_WORTH),

    /** The number behind "66 days", counted the way the study counted it. */
    SIXTY_SIX(LALLY_MEDIAN),
}

private const val A_WEEKS_WORTH = 7

/** Lally et al. 2010: the median repetitions to automaticity, across a very wide range. */
private const val LALLY_MEDIAN = 66

/**
 * The milestone today's completion just reached, if it reached one.
 *
 * Derived, never remembered. The card shows while the count *is* the milestone, so it arrives on
 * the day and is gone by the next completion without anything being written down — and so nothing
 * has to be forgotten when a habit is deleted, and nothing can be lost by clearing app data.
 * Un-ticking takes it away and re-ticking brings the same one back, the same "state, not event"
 * rule the shine line follows.
 *
 * Requires today to be a completion, so a card can never land on a day that went badly.
 */
internal fun milestoneFor(entries: List<Entry>, today: LocalDate): Milestone? {
    if (entries.none { it.date == today && it.status.isCompletion }) return null
    // Bounded at today: a day logged ahead of the calendar has not been lived through yet, and
    // must not be what tips the count over.
    val done = entries.count { it.status.isCompletion && !it.date.isAfter(today) }
    return Milestone.entries.firstOrNull { it.completions == done }
}
