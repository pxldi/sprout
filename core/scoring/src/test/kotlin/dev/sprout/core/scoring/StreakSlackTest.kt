/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.scoring

import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.ScheduleRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Streaks with slack — docs/02-app-design.md, Layer 0.
 * Rest days are *earned* (never bought), spent silently, and capped. When they run out the
 * streak pauses rather than breaking, and completing within 48 h repairs it.
 */
class StreakSlackTest {

    private val daily = ScheduleRule.Daily

    @Test
    fun `a rest day is earned every seven completions and absorbs the next miss silently`() {
        // 7 done, day 7 missed, day 8 done again.
        val entries = completions(0, 7) + completions(8, 1)
        val result = HabitScorer.evaluate(daily, entries, today = day(9))

        assertEquals(OccasionOutcome.RESTED, result.outcomeOn(day(7)))
        assertEquals(StreakState.ACTIVE, result.streakState)
        assertEquals(8, result.currentRun, "the absorbed miss must not end the run")
    }

    @Test
    fun `rest days cap at two`() {
        val result = HabitScorer.evaluate(daily, completions(0, 28), today = day(28))
        assertEquals(2, result.restDaysAvailable, "balance is capped, unused days do not stockpile")
    }

    @Test
    fun `a miss with no rest days banked pauses the streak`() {
        // Miss on day 3 — only 3 completions so far, nothing earned yet.
        val entries = completions(0, 3)
        val result = HabitScorer.evaluate(daily, entries, today = day(4))

        assertEquals(OccasionOutcome.MISSED, result.outcomeOn(day(3)))
        assertEquals(StreakState.PAUSED, result.streakState)
        assertEquals(day(5), result.repairDeadline, "48 h from the end of the missed day")
    }

    @Test
    fun `completing within 48 hours repairs the missed occasion and the run continues`() {
        // Miss day 3, come back on day 4.
        val entries = completions(0, 3) + completions(4, 1)
        val result = HabitScorer.evaluate(daily, entries, today = day(5))

        assertEquals(OccasionOutcome.REPAIRED, result.outcomeOn(day(3)))
        assertEquals(StreakState.ACTIVE, result.streakState)
        assertEquals(4, result.currentRun)
        assertNull(result.repairDeadline)
    }

    @Test
    fun `coming back after the repair window starts a new run but keeps the best run`() {
        // Miss days 3, 4, 5; return on day 6.
        val entries = completions(0, 3) + completions(6, 2)
        val result = HabitScorer.evaluate(daily, entries, today = day(8))

        assertEquals(2, result.currentRun)
        assertEquals(3, result.bestRun, "the best run is history, it is never taken away")
        assertEquals(StreakState.ACTIVE, result.streakState)
    }

    @Test
    fun `a skip is neutral - it neither extends nor breaks the run`() {
        val entries = completions(0, 3) + log(3 to EntryStatus.SKIP) + completions(4, 2)
        val result = HabitScorer.evaluate(daily, entries, today = day(6))

        assertEquals(OccasionOutcome.SKIPPED, result.outcomeOn(day(3)))
        assertEquals(StreakState.ACTIVE, result.streakState)
        assertEquals(5, result.currentRun, "3 before + 2 after, the skip itself does not count")
    }

    @Test
    fun `a logged lapse scores like a miss but is still recorded as a lapse`() {
        val entries = completions(0, 3) + log(3 to EntryStatus.LAPSE)
        val result = HabitScorer.evaluate(daily, entries, today = day(4))

        assertEquals(OccasionOutcome.MISSED, result.outcomeOn(day(3)))
        assertEquals(StreakState.PAUSED, result.streakState)
    }

    @Test
    fun `weekly habit needs the whole week's target to keep the run`() {
        val rule = ScheduleRule.TimesPerWeek(times = 3)
        // Week 1: 3 done. Week 2: only 2 done.
        val entries = completions(0, 3) + completions(7, 2)
        val result = HabitScorer.evaluate(rule, entries, today = day(15))

        assertEquals(OccasionOutcome.COMPLETED, result.outcomeOn(day(0)))
        assertTrue(result.outcomeOn(day(7)) != OccasionOutcome.COMPLETED)
    }

    @Test
    fun `bounce back day is flagged for the reward the research says matters most`() {
        // Miss day 3 (nothing banked), return day 4.
        val entries = completions(0, 3) + completions(4, 1)
        val result = HabitScorer.evaluate(daily, entries, today = day(5))
        assertNotNull(result.bounceBackOn)
        assertEquals(day(4), result.bounceBackOn)
    }

    @Test
    fun `recent fraction is reported alongside the run`() {
        // 24 done, then six days off. Two of those six are absorbed by the rest days those 24
        // completions earned, so the honest denominator is 28 — not 30, and not 24.
        val result = HabitScorer.evaluate(daily, completions(0, 24), today = day(30))
        assertEquals(24, result.recentCompletions)
        assertEquals(28, result.recentChances)
    }

    @Test
    fun `a habit younger than the window is measured against its own age`() {
        // Ten days old, never missed. "100% of the last 30 days" would claim twenty days it
        // has not lived through; the honest reading is ten of ten.
        val result = HabitScorer.evaluate(daily, completions(0, 10), today = day(10))
        assertEquals(10, result.recentCompletions)
        assertEquals(10, result.recentChances)
    }

    @Test
    fun `skips and spent rest days are in neither half of the fraction`() {
        // Seven completions bank one rest day; day 7 is skipped and day 8 missed, which the
        // rest day absorbs. Neither may show up as a chance the user failed to take.
        val entries = completions(0, 7) + DayLog(day(7), EntryStatus.SKIP)
        val result = HabitScorer.evaluate(daily, entries, today = day(9))

        assertEquals(OccasionOutcome.SKIPPED, result.outcomeOn(day(7)))
        assertEquals(OccasionOutcome.RESTED, result.outcomeOn(day(8)))
        assertEquals(7, result.recentCompletions)
        assertEquals(7, result.recentChances)
    }
}
