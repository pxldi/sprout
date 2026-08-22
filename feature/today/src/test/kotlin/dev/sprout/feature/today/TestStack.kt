/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import androidx.test.core.app.ApplicationProvider
import dev.sprout.core.database.inMemoryRepositories
import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.Habit
import dev.sprout.core.model.HabitType
import dev.sprout.core.model.Reminder
import dev.sprout.core.model.ScheduleRule
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** 2026-01-05 is a Monday — schedule tests depend on knowing which day of the week "today" is. */
internal val TEST_TODAY: LocalDate = LocalDate.of(2026, 1, 5)
private val TEST_INSTANT: Instant = TEST_TODAY.atTime(9, 0).toInstant(ZoneOffset.UTC)

/** A real database and real repositories on a frozen clock. No mocks: the seams are the point. */
internal class TestStack {
    val clock: Clock = Clock.fixed(TEST_INSTANT, ZoneOffset.UTC)

    private val repositories =
        inMemoryRepositories(ApplicationProvider.getApplicationContext(), clock)

    val habits = repositories.habits
    val entries = repositories.entries
    val reminders = repositories.reminders

    fun close() = repositories.close()

    fun addHabit(
        name: String = "Morning run",
        type: HabitType = HabitType.DO_BOOL,
        schedule: ScheduleRule = ScheduleRule.Daily,
        position: Int = 0,
    ): Habit = runBlocking {
        habits.save(
            Habit(
                name = name,
                type = type,
                schedule = schedule,
                minimumVersion = "Put the shoes on",
                position = position,
                createdAt = TEST_INSTANT,
                updatedAt = TEST_INSTANT,
            ),
        )
    }

    fun addReminder(habitId: String, at: LocalTime): Reminder = runBlocking {
        reminders.save(
            Reminder(
                habitId = habitId,
                time = at,
                createdAt = TEST_INSTANT,
                updatedAt = TEST_INSTANT,
            ),
        )
    }

    /** Marks the habit done on each of the given offsets back from today. */
    fun logDaysAgo(habitId: String, vararg daysAgo: Int) = runBlocking {
        daysAgo.forEach {
            entries.log(habitId, TEST_TODAY.minusDays(it.toLong()), EntryStatus.DONE)
        }
    }
}
