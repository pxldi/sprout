/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.scheduling

import java.time.LocalDate

/**
 * One chance to do the habit.
 *
 * Streaks and strength are computed over occasions, never over calendar days — otherwise a
 * `3x/week` habit would "break" every Tuesday. For day-based schedules an occasion is a single
 * day requiring one completion; for [dev.sprout.core.model.ScheduleRule.TimesPerWeek] it is a
 * whole week requiring [requiredCompletions].
 */
public data class Occasion(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val requiredCompletions: Int,
) {
    init {
        require(!endInclusive.isBefore(start)) { "Occasion end $endInclusive precedes start $start" }
        require(requiredCompletions >= 1) { "requiredCompletions must be >= 1" }
    }

    public val isSingleDay: Boolean get() = start == endInclusive

    public operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && !date.isAfter(endInclusive)

    /** True once the occasion is over and can be judged. An open occasion is never a miss. */
    public fun isClosedOn(today: LocalDate): Boolean = endInclusive.isBefore(today)
}
