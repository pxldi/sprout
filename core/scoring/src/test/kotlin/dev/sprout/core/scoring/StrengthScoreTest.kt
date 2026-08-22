/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.scoring

import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.ScheduleRule
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two calibration points come straight from docs/02-app-design.md:
 * "Daily habit: ~80% after a month of perfect days, 99% after three months."
 */
class StrengthScoreTest {

    @Test
    fun `daily habit reaches about 80 after 30 perfect days`() {
        val result = HabitScorer.evaluate(ScheduleRule.Daily, completions(0, 30), today = day(30))
        assertEquals(80.0, result.strength, 0.5)
    }

    @Test
    fun `daily habit reaches about 99 after 90 perfect days`() {
        val result = HabitScorer.evaluate(ScheduleRule.Daily, completions(0, 90), today = day(90))
        assertEquals(99.0, result.strength, 0.5)
    }

    @Test
    fun `one miss dents strength by a few points and never resets it`() {
        val perfect = HabitScorer.evaluate(ScheduleRule.Daily, completions(0, 30), today = day(30))
        // 30 perfect days, then a single missed day 31 with no rest days left to absorb it.
        val withMiss = HabitScorer.evaluate(
            ScheduleRule.Daily,
            completions(0, 30),
            today = day(31), // day 30 is closed and missed; day 31 is still open
            restDayPolicy = RestDayPolicy.DISABLED,
        )
        val drop = perfect.strength - withMiss.strength
        assertTrue(drop in 1.0..12.0, "a single miss should dent, not demolish; dropped $drop")
        assertTrue(withMiss.strength > 60.0, "strength must never reset: was ${withMiss.strength}")
    }

    @Test
    fun `a skip holds strength constant`() {
        val before = HabitScorer.evaluate(ScheduleRule.Daily, completions(0, 20), today = day(20))
        val withSkip = HabitScorer.evaluate(
            ScheduleRule.Daily,
            completions(0, 20) + log(20 to EntryStatus.SKIP),
            today = day(21),
        )
        assertEquals(before.strength, withSkip.strength, 0.001)
    }

    @Test
    fun `strength decays toward zero but never reaches it`() {
        val result = HabitScorer.evaluate(
            ScheduleRule.Daily,
            completions(0, 30),
            today = day(200),
            restDayPolicy = RestDayPolicy.DISABLED,
        )
        assertTrue(result.strength > 0.0, "EMA must not reach 0")
        assertTrue(result.strength < 5.0, "170 missed days should leave it near 0")
    }

    @Test
    fun `three per week gains strength at the same rate however it is scheduled`() {
        // Mon/Wed/Fri, scored per day.
        val fixedDays = ScheduleRule.SpecificDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val fixedLog = (0 until 28).map { day(it) }
            .filter { it.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY) }
            .map { DayLog(it, EntryStatus.DONE) }

        // "3x a week", scored per week, same real behaviour.
        val flexible = ScheduleRule.TimesPerWeek(times = 3)

        val a = HabitScorer.evaluate(fixedDays, fixedLog, today = day(28))
        val b = HabitScorer.evaluate(flexible, fixedLog, today = day(28))

        assertEquals(a.strength, b.strength, 1.0)
    }

    @Test
    fun `an open occasion is never scored as a miss`() {
        // Today is mid-week; the weekly occasion has not closed yet.
        val rule = ScheduleRule.TimesPerWeek(times = 3)
        val result = HabitScorer.evaluate(rule, emptyList(), today = day(2))
        assertEquals(0.0, result.strength, 0.001)
        assertEquals(StreakState.ACTIVE, result.streakState)
    }
}
