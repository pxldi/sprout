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
    val reminderAt: LocalTime?,
    val gentleNote: GentleNote?,
) {
    public val isDone: Boolean get() = todayStatus?.isCompletion == true
    public val isSkipped: Boolean get() = todayStatus == EntryStatus.SKIP
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
    val isLoading: Boolean = true,
) {
    public val doneCount: Int get() = items.count { it.isDone }
    public val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}

/**
 * Everything Today can do, in one place.
 *
 * These five travel together through every layer of the screen, and passing them individually
 * made the signatures grow a parameter each time the screen learned a new trick.
 */
public data class TodayActions(
    val onAddHabit: () -> Unit = {},
    val onToggle: (String) -> Unit = {},
    val onSkip: (String) -> Unit = {},
    val onMinimum: (String) -> Unit = {},
    val onClear: (String) -> Unit = {},
)
