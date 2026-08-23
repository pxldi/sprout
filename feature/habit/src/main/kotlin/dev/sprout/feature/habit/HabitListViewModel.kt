/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sprout.core.database.repository.HabitRepository
import dev.sprout.core.database.repository.ReminderRepository
import dev.sprout.core.model.Habit
import dev.sprout.core.model.Reminder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime
import javax.inject.Inject

/** One row: the habit, and the two things worth showing about it without opening it. */
public data class HabitSummary(val habit: Habit, val reminderAt: LocalTime?)

public data class HabitListUiState(
    val active: List<HabitSummary> = emptyList(),
    val archived: List<HabitSummary> = emptyList(),
    val isLoading: Boolean = true,
) {
    public val isEmpty: Boolean get() = !isLoading && active.isEmpty() && archived.isEmpty()
}

/**
 * Every habit there is, including the ones Today does not show.
 *
 * Today is deliberately only what is due today, which leaves two kinds of habit with no way in:
 * a Mon/Wed/Fri habit on a Tuesday, and anything archived. Without this screen, archiving would
 * be a one-way disappearance and a habit would be uneditable most days of the week.
 */
@HiltViewModel
public class HabitListViewModel @Inject constructor(
    habits: HabitRepository,
    reminders: ReminderRepository,
) : ViewModel() {

    public val uiState: StateFlow<HabitListUiState> = combine(
        habits.observeAll(),
        reminders.observeEnabled(),
    ) { all, enabled ->
        val byHabit = enabled.groupBy { it.habitId }
        HabitListUiState(
            active = all.filterNot { it.isArchived }.map { it.summarise(byHabit) },
            archived = all.filter { it.isArchived }.map { it.summarise(byHabit) },
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HabitListUiState(),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * The earliest reminder, of however many there are.
 *
 * An archived habit keeps its reminder rows — nothing fires for it, but unarchiving has to bring
 * it back exactly as it was — so the time shown here is what the habit *would* do, not a promise
 * about tomorrow morning. The archived heading above it is what says so.
 */
private fun Habit.summarise(byHabit: Map<String, List<Reminder>>): HabitSummary =
    HabitSummary(habit = this, reminderAt = byHabit[id]?.minOfOrNull { it.time })
