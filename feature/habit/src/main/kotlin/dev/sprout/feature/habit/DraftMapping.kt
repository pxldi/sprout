/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import dev.sprout.core.model.Habit
import dev.sprout.core.model.HabitType
import dev.sprout.core.model.Reminder
import dev.sprout.core.model.ScheduleRule
import java.time.Instant
import java.time.LocalDate

/**
 * Writes the six answers onto a habit that already exists.
 *
 * `copy` rather than a constructor, and not as a style preference. A habit carries fields the
 * questions never ask about — the habit it is anchored to, its bundle, its colour, its place in
 * the list, when it was created — and rebuilding one from a draft would erase every one of them
 * the first time the user changed its name. Creation goes through here too, so the two paths
 * cannot drift apart on something as quiet as whether a blank field is stored as "" or null.
 */
internal fun HabitDraft.applyTo(habit: Habit, today: LocalDate): Habit = habit.copy(
    name = name.trim(),
    type = type,
    schedule = scheduleRule(anchor = today),
    identityPhrase = identityPhrase.trimToNull(),
    minimumVersion = minimumVersion.trimToNull(),
    cue = cue.trimToNull(),
    copingPlan = copingPlan.trimToNull(),
    // Cleared when the habit stops being measurable, so a unit left over from an earlier answer
    // cannot outlive the target it belonged to.
    unit = unit.trimToNull().takeIf { type == HabitType.DO_NUMERIC },
    target = targetValue.takeIf { type == HabitType.DO_NUMERIC },
)

/**
 * A habit that did not exist a moment ago.
 *
 * [position] appends it to the end of the list; without it every habit lands at 0 and Today's
 * tie-break sort becomes arbitrary. The three constructor arguments are placeholders that
 * [applyTo] overwrites on the same line — [Habit] requires them, and the draft answers them.
 */
internal fun HabitDraft.toNewHabit(now: Instant, today: LocalDate, position: Int): Habit =
    applyTo(
        Habit(
            name = "",
            type = type,
            schedule = ScheduleRule.Daily,
            position = position,
            createdAt = now,
            updatedAt = now,
        ),
        today,
    )

/**
 * Writes the reminder answers onto a reminder that already exists.
 *
 * Keeps its id, which is what makes an edit an edit: a delete-and-recreate would leave a
 * tombstone behind for every time somebody nudged the time by five minutes, and every other
 * device would have to learn about all of them.
 */
internal fun HabitDraft.applyTo(reminder: Reminder): Reminder = reminder.copy(
    time = reminderTime,
    daysMask = reminderDaysMask(),
    enabled = true,
)

internal fun HabitDraft.toNewReminder(habitId: String, now: Instant): Reminder = applyTo(
    Reminder(habitId = habitId, time = reminderTime, createdAt = now, updatedAt = now),
)

/** Blank optional fields are stored as null, never as "" — one absent value, not two. */
private fun String.trimToNull(): String? = trim().ifBlank { null }
