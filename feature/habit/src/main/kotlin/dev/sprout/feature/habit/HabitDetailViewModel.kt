/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sprout.core.database.repository.EntryRepository
import dev.sprout.core.database.repository.HabitRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

public data class HabitDetailUiState(
    /** Null until the habit has been read, and after it stops existing. */
    val detail: HabitDetail? = null,
    val isLoading: Boolean = true,
    /** The habit is gone. The screen leaves rather than showing an empty shell of one. */
    val finished: Boolean = false,
)

/**
 * One habit, in full: the curve, the calendar, the runs, and the plan behind them.
 *
 * Observed rather than loaded once. A notification action can log a day while this screen is
 * open, and deleting the habit from the edit screen has to take this one with it — both arrive
 * as a new emission rather than as a stale page the user has to back out of.
 */
@HiltViewModel
public class HabitDetailViewModel @Inject constructor(
    habits: HabitRepository,
    entries: EntryRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val habitId: String = checkNotNull(savedState.get<String>(HABIT_ID_ARG)) {
        "$HABIT_ID_ARG missing from the route"
    }

    public val uiState: StateFlow<HabitDetailUiState> = combine(
        habits.observe(habitId),
        entries.observeForHabit(habitId),
    ) { habit, logged ->
        if (habit == null) {
            HabitDetailUiState(isLoading = false, finished = true)
        } else {
            HabitDetailUiState(
                detail = detailOf(
                    habit = habit,
                    entries = logged,
                    today = LocalDate.now(clock),
                    startedOn = habit.createdAt.atZone(clock.zone).toLocalDate(),
                ),
                isLoading = false,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HabitDetailUiState(),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
