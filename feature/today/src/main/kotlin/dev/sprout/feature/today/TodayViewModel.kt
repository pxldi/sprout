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
import dev.sprout.core.datastore.ShineHistory
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
public class TodayViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val entries: EntryRepository,
    reminders: ReminderRepository,
    private val shine: ShineHistory,
    private val clock: Clock,
) : ViewModel() {

    private val today: LocalDate get() = LocalDate.now(clock)

    /**
     * Lines already written down this process, as `habitId|kind|date`.
     *
     * The praise is derived state, recomputed from the log on every emission, but remembering it
     * is a write — and that write feeds the very flow the line is derived from. Without this the
     * two would chase each other: record, re-emit, record again. One write per habit per kind per
     * day is all that is wanted, and the stored value is the same on a second run anyway, so a
     * process that restarts and writes once more costs nothing.
     */
    private val recorded = mutableSetOf<String>()

    public val uiState: StateFlow<TodayUiState> = combine(
        habits.observeActive(),
        entries.observeAllByHabit(),
        reminders.observeEnabled(),
        shine.shown,
    ) { activeHabits, entriesByHabit, enabledReminders, shownLines ->
        val now = LocalDateTime.now(clock)
        val date = now.toLocalDate()
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
                        shown = shownLines,
                    ).also { remember(it, date) }
                }
                .sortedWith(compareBy({ it.reminderAt ?: LocalTime.MAX }, { it.habit.position })),
            // Until noon: a morning is when last night's forgotten tick is still remembered, and
            // by the afternoon the list would be about a day nobody is thinking of any more.
            yesterday = if (now.toLocalTime().isBefore(LocalTime.NOON)) {
                yesterdayItems(activeHabits, entriesByHabit, date, clock.zone)
            } else {
                emptyList()
            },
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
     * Sets a day before today from its own row, or clears it when [status] is null.
     *
     * The row carries its date, so a tap logs the day the user was looking at even if midnight
     * has passed since the screen was drawn.
     */
    public fun setDay(habitId: String, date: LocalDate, status: EntryStatus?) {
        if (!date.isBefore(today)) return
        viewModelScope.launch {
            if (status == null) entries.clear(habitId, date) else entries.log(habitId, date, status)
        }
    }

    /** Writes down that a line was said, once, so it is not said again for a fortnight. */
    private fun remember(item: TodayItem, date: LocalDate) {
        val line = item.shine ?: return
        if (!recorded.add("${ShineHistory.keyOf(item.habit.id, line.kind)}|$date")) return
        viewModelScope.launch { shine.record(item.habit.id, line.kind, date) }
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

/**
 * Habits due yesterday that are unlogged, or that were set for yesterday this morning.
 *
 * Only habits due on a single day. A 3x/week habit owes the week, not the day, so an unticked
 * yesterday is not a day it left open. A habit created today did not exist yesterday.
 */
private fun yesterdayItems(
    habits: List<Habit>,
    entriesByHabit: Map<String, List<Entry>>,
    today: LocalDate,
    zone: ZoneId,
): List<YesterdayItem> {
    val yesterday = today.minusDays(1)
    return habits
        .filter { it.createdAt.atZone(zone).toLocalDate().isBefore(today) }
        .filter { OccasionCalendar.occasionOn(it.schedule, yesterday)?.isSingleDay == true }
        .mapNotNull { habit ->
            val entry = entriesByHabit[habit.id]?.firstOrNull { it.date == yesterday }
            val setThisMorning = entry != null && entry.updatedAt.atZone(zone).toLocalDate() == today
            if (entry == null || setThisMorning) YesterdayItem(habit, yesterday, entry?.status) else null
        }
}

private fun Habit.isScheduledOn(date: LocalDate): Boolean =
    OccasionCalendar.occasionOn(schedule, date) != null

private fun Habit.toItem(
    entries: List<Entry>,
    reminder: LocalTime?,
    date: LocalDate,
    shown: Map<String, LocalDate>,
): TodayItem {
    val progress = HabitScorer.evaluate(
        rule = schedule,
        entries = entries.map { DayLog(it.date, it.status) },
        today = date,
    )
    val todayEntry = entries.firstOrNull { it.date == date }
    val note = progress.gentleNote(date, todayEntry?.status)
    val milestone = milestoneFor(entries, date)
    return TodayItem(
        habit = this,
        progress = progress,
        todayStatus = todayEntry?.status,
        todayNote = todayEntry?.note,
        reminderAt = reminder,
        gentleNote = note,
        // The one thing here that does not take its turn. A comeback that is also the seventh
        // completion gets the line *and* the card, because they are different registers and
        // because a milestone is unrepeatable: a line held back today is true again next week,
        // whereas by tomorrow the count is eight and this card is gone for good.
        milestone = milestone,
        // At most one *line* under a row, and coming back after a miss outranks everything —
        // it is the state the research says to reward, and praise for a third Tuesday must not
        // be what crowds it out. The card outranks the line for the same reason it does not
        // wait its turn: it already says more, and it only ever gets today.
        shine = if (note == null && milestone == null && todayEntry?.status?.isCompletion == true) {
            shineFor(id, progress, entries, date, shown)
        } else {
            null
        },
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
