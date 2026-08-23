/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sprout.core.database.repository.EntryRepository
import dev.sprout.core.database.repository.HabitRepository
import dev.sprout.core.database.repository.ReminderRepository
import dev.sprout.core.model.Entry
import dev.sprout.core.model.EntrySource
import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.Habit
import dev.sprout.core.model.Reminder
import dev.sprout.core.scheduling.OccasionCalendar
import dev.sprout.core.scoring.DayLog
import dev.sprout.core.scoring.HabitProgress
import dev.sprout.core.scoring.HabitScorer
import dev.sprout.core.scoring.OccasionOutcome
import dev.sprout.core.scoring.StreakState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
public class TodayViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val entries: EntryRepository,
    reminders: ReminderRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today: LocalDate get() = LocalDate.now(clock)

    public val uiState: StateFlow<TodayUiState> = combine(
        habits.observeActive(),
        entries.observeAllByHabit(),
        reminders.observeEnabled(),
    ) { activeHabits, entriesByHabit, enabledReminders ->
        val date = today
        TodayUiState(
            date = date,
            hasAnyHabits = activeHabits.isNotEmpty(),
            hasReminders = enabledReminders.any { reminder ->
                activeHabits.any { it.id == reminder.habitId }
            },
            items = activeHabits
                .filter { it.isScheduledOn(date) }
                .map { habit ->
                    habit.toItem(
                        entries = entriesByHabit[habit.id].orEmpty(),
                        reminder = enabledReminders.earliestFor(habit.id, date),
                        date = date,
                    )
                }
                .sortedWith(compareBy({ it.reminderAt ?: LocalTime.MAX }, { it.habit.position })),
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TodayUiState(date = LocalDate.now(clock)),
    )

    public fun complete(habitId: String, source: EntrySource = EntrySource.MANUAL) {
        log(habitId, EntryStatus.DONE, source)
    }

    /** "Ten minutes counts." Logging the smallest version is a completion, not a lesser one. */
    public fun completeMinimum(habitId: String, source: EntrySource = EntrySource.MANUAL) {
        log(habitId, EntryStatus.DONE_MIN, source)
    }

    public fun skip(habitId: String) {
        log(habitId, EntryStatus.SKIP, EntrySource.MANUAL)
    }

    /** Un-logs today. Reversing a mistaken tap must be as easy as making it. */
    public fun clear(habitId: String) {
        viewModelScope.launch { entries.clear(habitId, today) }
    }

    public fun toggle(habitId: String) {
        viewModelScope.launch { entries.toggle(habitId, today) }
    }

    /**
     * Saves the user's own words about today, or clears them when the text is blank.
     *
     * Only ever an annotation: it cannot log a day, and does not change the status of one that
     * is logged. Writing "shoulder hurt" must not decide on the user's behalf what that day was.
     */
    public fun note(habitId: String, text: String) {
        viewModelScope.launch { entries.note(habitId, today, text) }
    }

    private fun log(habitId: String, status: EntryStatus, source: EntrySource) {
        viewModelScope.launch { entries.log(habitId, today, status, source = source) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun Habit.isScheduledOn(date: LocalDate): Boolean =
    OccasionCalendar.occasionOn(schedule, date) != null

private fun Habit.toItem(entries: List<Entry>, reminder: LocalTime?, date: LocalDate): TodayItem {
    val progress = HabitScorer.evaluate(
        rule = schedule,
        entries = entries.map { DayLog(it.date, it.status) },
        today = date,
    )
    val todayEntry = entries.firstOrNull { it.date == date }
    return TodayItem(
        habit = this,
        progress = progress,
        todayStatus = todayEntry?.status,
        todayNote = todayEntry?.note,
        reminderAt = reminder,
        gentleNote = progress.gentleNote(date, todayEntry?.status),
    )
}

/**
 * Picks at most one thing to say about a miss.
 *
 * Order matters: coming back wins over everything, because the day after a miss is the day
 * the research says to reward. A paused run is described as repairable, never as expiring.
 */
private fun HabitProgress.gentleNote(date: LocalDate, todayStatus: EntryStatus?): GentleNote? = when {
    bounceBackOn == date -> GentleNote.BOUNCED_BACK
    todayStatus != null -> null
    streakState == StreakState.PAUSED && currentRun > 0 -> GentleNote.REPAIRABLE
    streakState == StreakState.PAUSED || missedOn(date.minusDays(1)) -> GentleNote.MISSED_YESTERDAY
    else -> null
}

private fun HabitProgress.missedOn(date: LocalDate): Boolean =
    outcomeOn(date) == OccasionOutcome.MISSED

private fun List<Reminder>.earliestFor(habitId: String, date: LocalDate): LocalTime? =
    filter { it.habitId == habitId && it.firesOn(date.dayOfWeek) }
        .minOfOrNull { it.time }
