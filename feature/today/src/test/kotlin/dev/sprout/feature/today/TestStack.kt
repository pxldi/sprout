/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import androidx.test.core.app.ApplicationProvider
import dev.sprout.core.database.inMemoryRepositories
import dev.sprout.core.datastore.ShineHistory
import dev.sprout.core.datastore.temporaryShineHistory
import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.Habit
import dev.sprout.core.model.HabitType
import dev.sprout.core.model.Reminder
import dev.sprout.core.model.ScheduleRule
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** 2026-01-05 is a Monday — schedule tests depend on knowing which day of the week "today" is. */
internal val TEST_TODAY: LocalDate = LocalDate.of(2026, 1, 5)
private val TEST_TIME: LocalTime = LocalTime.of(9, 0)
private val TEST_INSTANT: Instant = TEST_TODAY.atTime(TEST_TIME).toInstant(ZoneOffset.UTC)

/**
 * A clock that stands still until the test moves it.
 *
 * Moved only to write something on another day, such as a tick logged on time last night.
 */
internal class TestClock(var instant: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant, zone)
    override fun instant(): Instant = instant
}

/**
 * A real database and real repositories on a frozen clock. No mocks: the seams are the point.
 *
 * [time] is the time of day on [TEST_TODAY]. Yesterday's habits are offered only before noon.
 */
internal class TestStack(time: LocalTime = TEST_TIME) {
    val clock: TestClock = TestClock(TEST_TODAY.atTime(time).toInstant(ZoneOffset.UTC))

    private val repositories =
        inMemoryRepositories(ApplicationProvider.getApplicationContext(), clock)

    val habits = repositories.habits
    val entries = repositories.entries
    val reminders = repositories.reminders

    /** A fresh file per stack, so one test's praise is not remembered in the next one. */
    val shine: ShineHistory = temporaryShineHistory(
        File.createTempFile("shine", ".preferences_pb").also { it.delete() },
    )

    fun close() = repositories.close()

    fun addHabit(
        name: String = "Morning run",
        type: HabitType = HabitType.DO_BOOL,
        schedule: ScheduleRule = ScheduleRule.Daily,
        position: Int = 0,
        createdDaysAgo: Long = 0,
    ): Habit = runBlocking {
        val created = TEST_INSTANT.minus(Duration.ofDays(createdDaysAgo))
        habits.save(
            Habit(
                name = name,
                type = type,
                schedule = schedule,
                minimumVersion = "Put the shoes on",
                position = position,
                createdAt = created,
                updatedAt = created,
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

    fun archive(habitId: String) = runBlocking { habits.archive(habitId) }

    /** Marks the habit done [daysAgo] days back, at 21:00 that day, the way a tick on time is. */
    fun logOnTheDay(habitId: String, daysAgo: Long) = runBlocking {
        val now = clock.instant
        val day = TEST_TODAY.minusDays(daysAgo)
        clock.instant = day.atTime(21, 0).toInstant(ZoneOffset.UTC)
        entries.log(habitId, day, EntryStatus.DONE)
        clock.instant = now
    }

    /** Marks the habit done on each of the given offsets back from today. */
    fun logDaysAgo(habitId: String, vararg daysAgo: Int) = runBlocking {
        daysAgo.forEach {
            entries.log(habitId, TEST_TODAY.minusDays(it.toLong()), EntryStatus.DONE)
        }
    }
}
