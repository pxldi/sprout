/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sprout.core.database.repository.EntryRepository
import dev.sprout.core.database.repository.HabitRepository
import dev.sprout.core.model.Habit
import dev.sprout.core.model.HabitType
import dev.sprout.core.model.ScheduleRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal val TEST_NOW: Instant = Instant.parse("2026-01-05T08:00:00Z")
internal val TEST_START: LocalDate = LocalDate.of(2026, 1, 5)

internal fun fixedClock(at: Instant = TEST_NOW): Clock = Clock.fixed(at, ZoneOffset.UTC)

internal fun inMemoryDatabase(): SproutDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        SproutDatabase::class.java,
    ).allowMainThreadQueries().build()

internal fun habit(
    name: String = "Morning run",
    type: HabitType = HabitType.DO_BOOL,
    schedule: ScheduleRule = ScheduleRule.Daily,
    at: Instant = TEST_NOW,
): Habit = Habit(
    name = name,
    type = type,
    schedule = schedule,
    identityPhrase = "I'm someone who runs in the morning",
    minimumVersion = "Put the shoes on and step outside",
    cue = "If it's 7 am and I've brushed my teeth, then I put on running shoes",
    copingPlan = "If I miss the morning, then I run after dinner",
    createdAt = at,
    updatedAt = at,
)

internal fun repositories(db: SproutDatabase, clock: Clock = fixedClock()) =
    HabitRepository(db.habitDao(), clock) to EntryRepository(db.entryDao(), clock)
