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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

public data class HabitCreationUiState(
    val draft: HabitDraft = HabitDraft(),
    val step: CreationStep = CreationStep.WHAT,
    val isSaving: Boolean = false,
    /** Set once the habit is stored. The screen navigates away on this, not on the tap. */
    val savedHabitId: String? = null,
) {
    public val canAdvance: Boolean get() = draft.canLeave(step)
}

@HiltViewModel
public class HabitCreationViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val reminders: ReminderRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitCreationUiState())
    public val uiState: StateFlow<HabitCreationUiState> = _uiState.asStateFlow()

    public fun edit(change: (HabitDraft) -> HabitDraft) {
        _uiState.update { it.copy(draft = change(it.draft)) }
    }

    public fun next() {
        _uiState.update { state ->
            if (!state.canAdvance || state.step.isLast) state
            else state.copy(step = CreationStep.entries[state.step.ordinal + 1])
        }
    }

    /** Returns false when there is nowhere further back to go, so the caller can leave instead. */
    public fun back(): Boolean {
        val state = _uiState.value
        if (state.step.isFirst) return false
        _uiState.update { it.copy(step = CreationStep.entries[it.step.ordinal - 1]) }
        return true
    }

    /**
     * Writes the habit, and its reminder if one was set. At most once, ever.
     *
     * The guard covers both a tap while the write is in flight *and* a tap after it finished:
     * the write can complete faster than the navigation away from the screen, so guarding only
     * on [HabitCreationUiState.isSaving] would still let a double tap create two habits. Unlike
     * a double-tapped checkbox, that is not something the user can undo without noticing first.
     */
    public fun save() {
        val state = _uiState.value
        if (state.isSaving || state.savedHabitId != null || !state.draft.isComplete) return
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val draft = state.draft
            val now = clock.instant()
            val saved = habits.save(
                draft.toNewHabit(
                    now = now,
                    today = LocalDate.now(clock),
                    position = habits.observeActive().first().size,
                ),
            )
            if (draft.reminderEnabled) {
                reminders.save(draft.toNewReminder(habitId = saved.id, now = now))
            }
            _uiState.update { it.copy(isSaving = false, savedHabitId = saved.id) }
        }
    }
}
