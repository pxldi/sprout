/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sprout.core.database.repository.HabitRepository
import dev.sprout.core.database.repository.ReminderRepository
import dev.sprout.core.datastore.ShineHistory
import dev.sprout.core.model.Habit
import dev.sprout.core.model.Reminder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** The nav argument every route into this screen must carry. */
public const val HABIT_ID_ARG: String = "habitId"

public data class HabitEditUiState(
    val draft: HabitDraft = HabitDraft(),
    /**
     * The draft as it was loaded.
     *
     * Kept beside the live one so the screen can tell whether leaving would lose anything. Held
     * as a whole draft rather than a flag because a flag would stay set after the user undid
     * their own change, and then ask them to confirm discarding nothing.
     */
    val savedDraft: HabitDraft = HabitDraft(),
    /** The stored name, which the delete dialog needs even after the field has been edited. */
    val storedName: String = "",
    val isLoading: Boolean = true,
    val isArchived: Boolean = false,
    val isWorking: Boolean = false,
    /** Set once there is nothing left to show. The screen leaves on this, not on the tap. */
    val finished: Boolean = false,
) {
    public val canSave: Boolean get() = !isLoading && !isWorking && draft.isComplete
    public val isDirty: Boolean get() = draft != savedDraft
}

/** Archive, restore and delete. Grouped because they travel together and none of them edit. */
public data class HabitEditActions(
    val onArchive: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

/**
 * Editing a habit that already exists.
 *
 * Deliberately not the creation wizard with a different title. A wizard is right when there is
 * nothing there yet and the questions are the point; it is wrong when somebody came to move a
 * reminder by ten minutes and has to walk past five screens they already answered. The two flows
 * share their fields and their mapping, and nothing else.
 */
@HiltViewModel
public class HabitEditViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val reminders: ReminderRepository,
    private val shine: ShineHistory,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val habitId: String = checkNotNull(savedState.get<String>(HABIT_ID_ARG)) {
        "$HABIT_ID_ARG missing from the route"
    }

    /**
     * The rows as they were read, kept so that saving can `copy` them.
     *
     * Not held in the UI state: nothing draws them, and the whole reason they exist is that they
     * carry fields the draft does not model.
     */
    private var habit: Habit? = null
    private var reminder: Reminder? = null

    private val _uiState = MutableStateFlow(HabitEditUiState())
    public val uiState: StateFlow<HabitEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    public fun edit(change: (HabitDraft) -> HabitDraft) {
        _uiState.update { it.copy(draft = change(it.draft)) }
    }

    public fun save() {
        val state = _uiState.value
        val existing = habit
        if (existing == null || !state.canSave) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val draft = state.draft
            habits.save(draft.applyTo(existing, today = LocalDate.now(clock)))
            saveReminder(draft)
            finish()
        }
    }

    /** Off Today, out of the way, and its reminder stops firing — but nothing is lost. */
    public fun archive(): Unit = once { habits.archive(habitId) }

    public fun unarchive(): Unit = once { habits.unarchive(habitId) }

    /**
     * Tombstones the habit and everything hanging off it.
     *
     * Kept behind a confirmation because it is the only action here the user cannot walk back:
     * archiving hides a habit, this ends it, along with every day they ever logged against it.
     */
    public fun delete(): Unit = once {
        habits.delete(habitId)
        // The only moment a habit is really gone. Archiving must not do this: an unarchived
        // habit that is congratulated all over again for its first completion has not earned it.
        shine.forget(habitId)
    }

    private suspend fun load() {
        val loaded = habits.find(habitId)
        // Deleted from another screen, or a route built by hand. Nothing to edit and nothing to
        // say about it, so the screen leaves the way it would after a save.
        if (loaded == null) {
            _uiState.update { it.copy(isLoading = false, finished = true) }
            return
        }
        // At most one reminder per habit today. `firstOrNull` rather than a crash on a second,
        // because sync and import can both produce one and an edit screen is a bad place to find
        // out — the extras are left alone, which is the least surprising thing to do with them.
        val existing = reminders.observeForHabit(habitId).first().firstOrNull()
        habit = loaded
        reminder = existing
        _uiState.update {
            it.copy(
                draft = HabitDraft.of(loaded, existing),
                savedDraft = HabitDraft.of(loaded, existing),
                storedName = loaded.name,
                isLoading = false,
                isArchived = loaded.isArchived,
            )
        }
    }

    /**
     * A reminder switched off is disabled, not deleted.
     *
     * The time they chose is worth keeping: switching it back on next month should offer 7:00
     * again rather than the app's default. It also spares every other device a tombstone for
     * something that was only paused.
     */
    private suspend fun saveReminder(draft: HabitDraft) {
        val existing = reminder
        when {
            draft.reminderEnabled -> reminders.save(
                existing?.let { draft.applyTo(it) }
                    ?: draft.toNewReminder(habitId, clock.instant()),
            )
            existing?.enabled == true -> reminders.save(existing.copy(enabled = false))
        }
    }

    /**
     * Runs one of the three one-way actions, then leaves.
     *
     * They all share the same guard: the screen is on its way out after any of them, and a
     * second tap landing before the navigation does must not archive a habit somebody just
     * deleted.
     */
    private fun once(action: suspend () -> Unit) {
        val state = _uiState.value
        if (state.isLoading || state.isWorking || state.finished) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            action()
            finish()
        }
    }

    private fun finish() {
        _uiState.update { it.copy(isWorking = false, finished = true) }
    }
}
