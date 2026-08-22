/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database

import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.ScheduleRule
import dev.sprout.core.scoring.DayLog
import dev.sprout.core.scoring.HabitScorer
import dev.sprout.core.scoring.OccasionOutcome
import dev.sprout.core.scoring.StreakState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The seam between storage and scoring.
 *
 * `:core:scoring` is pure and knows nothing about Room; `:core:database` knows nothing about
 * scoring. This test is the only place the two meet, and it exists to prove that what comes out
 * of SQLite is exactly what the scorer expects to be fed.
 */
@RunWith(RobolectricTestRunner::class)
class ScoringIntegrationTest {

    private val db = inMemoryDatabase()
    private val habits = repositories(db).first
    private val entries = repositories(db).second

    @After fun tearDown() = db.close()

    private suspend fun logsFor(habitId: String): List<DayLog> =
        entries.observeForHabit(habitId).first().map { DayLog(it.date, it.status) }

    @Test
    fun `thirty stored days of showing up score as a strong habit`() = runTest {
        val h = habits.save(habit(schedule = ScheduleRule.Daily))
        repeat(30) { entries.log(h.id, TEST_START.plusDays(it.toLong()), EntryStatus.DONE) }

        val progress = HabitScorer.evaluate(h.schedule, logsFor(h.id), today = TEST_START.plusDays(30))

        assertEquals(80.0, progress.strength, 0.5)
        assertEquals(30, progress.currentRun)
        assertEquals(StreakState.ACTIVE, progress.streakState)
        assertEquals(2, progress.restDaysAvailable)
    }

    @Test
    fun `a cleared day reads back as a miss, not as a stale completion`() = runTest {
        val h = habits.save(habit())
        repeat(4) { entries.log(h.id, TEST_START.plusDays(it.toLong()), EntryStatus.DONE) }
        entries.log(h.id, TEST_START.plusDays(4), EntryStatus.DONE)

        // The user un-logs day 4 — the row is tombstoned and must vanish from the scorer's input.
        entries.clear(h.id, TEST_START.plusDays(4))

        val progress = HabitScorer.evaluate(h.schedule, logsFor(h.id), today = TEST_START.plusDays(5))
        assertEquals(OccasionOutcome.MISSED, progress.outcomeOn(TEST_START.plusDays(4)))
    }

    @Test
    fun `a stored skip is neutral all the way through the stack`() = runTest {
        val h = habits.save(habit())
        repeat(3) { entries.log(h.id, TEST_START.plusDays(it.toLong()), EntryStatus.DONE) }
        entries.log(h.id, TEST_START.plusDays(3), EntryStatus.SKIP)
        entries.log(h.id, TEST_START.plusDays(4), EntryStatus.DONE)

        val progress = HabitScorer.evaluate(h.schedule, logsFor(h.id), today = TEST_START.plusDays(5))

        assertEquals(OccasionOutcome.SKIPPED, progress.outcomeOn(TEST_START.plusDays(3)))
        assertEquals(4, progress.currentRun)
        assertEquals(StreakState.ACTIVE, progress.streakState)
    }

    @Test
    fun `a weekly schedule stored in the database still scores per week`() = runTest {
        val rule = ScheduleRule.TimesPerWeek(times = 3)
        val h = habits.save(habit(schedule = rule))
        listOf(0L, 2L, 4L).forEach { entries.log(h.id, TEST_START.plusDays(it), EntryStatus.DONE) }

        val progress = HabitScorer.evaluate(h.schedule, logsFor(h.id), today = TEST_START.plusDays(7))

        assertEquals(OccasionOutcome.COMPLETED, progress.outcomeOn(TEST_START))
        assertEquals(1, progress.currentRun, "one completed week, not three completed days")
    }

    @Test
    fun `coming back the day after a miss is flagged from stored data`() = runTest {
        val h = habits.save(habit())
        repeat(3) { entries.log(h.id, TEST_START.plusDays(it.toLong()), EntryStatus.DONE) }
        // Day 3 never logged. Day 4 logged.
        entries.log(h.id, TEST_START.plusDays(4), EntryStatus.DONE)

        val progress = HabitScorer.evaluate(h.schedule, logsFor(h.id), today = TEST_START.plusDays(5))

        assertEquals(TEST_START.plusDays(4), progress.bounceBackOn)
        assertTrue(progress.strength > 0.0)
    }
}
