/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.Habit
import dev.sprout.core.scoring.HabitProgress
import java.time.LocalDate
import java.time.LocalTime

/**
 * One row on Today.
 *
 * [gentleNote] is the app's most load-bearing string. It appears when yesterday was missed and it
 * is the *only* place a miss is mentioned — stated flatly, pointed at today, never at fault.
 * See the copy rules in docs/02-app-design.md.
 */
public data class TodayItem(
    val habit: Habit,
    val progress: HabitProgress,
    /** Null when the day has not been logged. That is a blank slate, not a failure. */
    val todayStatus: EntryStatus?,
    /** What the user wrote about today, if anything. Their words, shown back to them verbatim. */
    val todayNote: String?,
    val reminderAt: LocalTime?,
    val gentleNote: GentleNote?,
    /**
     * The one specific true thing to say about today's completion, if there is one.
     *
     * Null far more often than not: null when the day is not completed, null when [gentleNote]
     * has something to say instead, and null when everything true has already been said this
     * fortnight. Silence is the correct output — see [ShineLine].
     */
    val shine: ShineLine? = null,
    /**
     * The seventh or sixty-sixth completion, on the day it happens. Null on all the other days.
     *
     * Outranks [shine], which is a sentence about the same completion; see [Milestone].
     */
    val milestone: Milestone? = null,
) {
    public val isDone: Boolean get() = todayStatus?.isCompletion == true
    public val isSkipped: Boolean get() = todayStatus == EntryStatus.SKIP

    /**
     * Whether today can be annotated.
     *
     * A note hangs off a logged day. Writing one for a day with no row would have to invent a
     * row, and this app reads the absence of one as the miss — see `EntryRepository.note`.
     */
    public val canNote: Boolean get() = todayStatus != null
}

/** The small set of things Today is allowed to say about a miss. There is no fourth option. */
public enum class GentleNote {
    /**
     * Yesterday was missed and there is no run to pick back up.
     * "One miss changes almost nothing — today does."
     */
    MISSED_YESTERDAY,

    /**
     * A run is paused and still repairable. Framed as available, never as a countdown, and
     * distinct from [MISSED_YESTERDAY] only when there is a run worth mentioning — otherwise
     * "your run is paused" is a boast about a run of zero.
     */
    REPAIRABLE,

    /** They came back today after missing. The most rewarded state in the app. */
    BOUNCED_BACK,
}

public data class TodayUiState(
    val date: LocalDate,
    val items: List<TodayItem> = emptyList(),
    /** Whether *any* habit exists, scheduled today or not. */
    val hasAnyHabits: Boolean = false,
    /**
     * Whether a live habit has a reminder switched on.
     *
     * Only here so the screen knows whether losing notification access is worth mentioning. An
     * archived habit's leftover reminder does not count — nothing was going to fire for it
     * anyway, and warning about it would be warning about nothing.
     */
    val hasReminders: Boolean = false,
    val isLoading: Boolean = true,
) {
    public val doneCount: Int get() = items.count { it.isDone }

    /** Nothing exists yet. The only state that gets the "Day 1 of 66" welcome. */
    public val isFirstRun: Boolean get() = !isLoading && !hasAnyHabits

    /**
     * Habits exist; none of them fall on today.
     *
     * Kept strictly apart from [isFirstRun]. Creating a Mon/Wed/Fri habit on a Saturday used to
     * land the user back on "add the first habit", which reads as though the habit they just
     * made had failed to save.
     */
    public val nothingScheduled: Boolean get() = !isLoading && hasAnyHabits && items.isEmpty()
}

/**
 * Everything Today can do, in one place.
 *
 * These travel together through every layer of the screen, and passing them individually made the
 * signatures grow a parameter each time the screen learned a new trick.
 */
public data class TodayActions(
    val onAddHabit: () -> Unit = {},
    val onManageHabits: () -> Unit = {},
    val onToggle: (String) -> Unit = {},
    val onSkip: (String) -> Unit = {},
    val onMinimum: (String) -> Unit = {},
    val onClear: (String) -> Unit = {},
    /** Opens the habit's own screen. Editing lives one tap further in, on that screen. */
    val onOpen: (String) -> Unit = {},
    /** Writes, rewrites or clears today's note. Blank text removes it. */
    val onNote: (String, String) -> Unit = { _, _ -> },
)
