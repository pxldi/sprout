/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.scoring

import dev.sprout.core.model.EntryStatus
import dev.sprout.core.scheduling.Occasion
import java.time.LocalDate

/** One logged day. The scorer never sees database rows — this module stays pure. */
public data class DayLog(val date: LocalDate, val status: EntryStatus)

/** How a scheduled occasion actually resolved, after slack is applied. */
public enum class OccasionOutcome {
    /** The target was met. The only outcome that advances a run. */
    COMPLETED,

    /** Deliberately skipped. Neutral. */
    SKIPPED,

    /** Missed, but a banked rest day absorbed it. Neutral, and silent by design. */
    RESTED,

    /** Missed, then earned back by showing up within 48 h. Neutral. */
    REPAIRED,

    /** Missed with no slack left. The only outcome that decays strength. */
    MISSED,

    /** Not over yet. Never judged, never a miss. */
    OPEN,
    ;

    public val isNeutral: Boolean get() = this == SKIPPED || this == RESTED || this == REPAIRED
}

public enum class StreakState {
    /** Running, or nothing has gone wrong yet. */
    ACTIVE,

    /** A miss is outstanding but still repairable. The run is held, not lost. */
    PAUSED,

    /** The repair window closed. The previous run is kept as `bestRun`, never erased. */
    BROKEN,
}

/** Rest days are earned by showing up. They are never bought — see docs/02-app-design.md. */
public enum class RestDayPolicy { EARNED, DISABLED }

public data class ResolvedOccasion(
    val occasion: Occasion,
    val outcome: OccasionOutcome,
    val completions: Int,
    /** Credit toward strength in `0.0..1.0`; partial for an unmet weekly target. */
    val credit: Double,
    val firstCompletionDate: LocalDate?,
)

/**
 * Everything the UI needs to show a habit without doing arithmetic of its own.
 *
 * Note what is *not* here: a single headline number that can hit zero. `strength` decays but
 * never resets, `bestRun` is permanent, and the recent fraction is shown next to `currentRun`
 * so no one number owns the narrative.
 */
public data class HabitProgress(
    /** 0..100, exponentially smoothed. Dents on a miss, never resets. */
    val strength: Double,
    val currentRun: Int,
    val bestRun: Int,
    /**
     * Completions over the last 30 days, and the chances that actually existed in that window.
     *
     * A fraction, never a percentage, and never over a fixed 30. A habit that is 26 days old
     * has had 26 chances; "100% of the last 30 days" would claim four days it never lived
     * through, and this app's whole argument is that its numbers mean exactly what they say.
     *
     * Neutral outcomes are in neither half: a skip was not a chance missed, and a rest day
     * spent in the background has to stay invisible here or it stops being silent.
     */
    val recentCompletions: Int,
    val recentChances: Int,
    val streakState: StreakState,
    val restDaysAvailable: Int,
    /** Set only while [streakState] is [StreakState.PAUSED]. */
    val repairDeadline: LocalDate?,
    /** The most recent "came back after a miss" day — the day the app rewards most. */
    val bounceBackOn: LocalDate?,
    val occasions: List<ResolvedOccasion>,
) {
    public fun outcomeOn(date: LocalDate): OccasionOutcome? =
        occasions.firstOrNull { date in it.occasion }?.outcome

    /** Occasions that have actually been judged — the habit's age in chances taken. */
    public val closedOccasions: Int
        get() = occasions.count { it.outcome != OccasionOutcome.OPEN }
}
