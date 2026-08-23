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

class PlantStageTest {

    @Test
    fun `a new habit is a seed and grows one stage at a time`() {
        val new = HabitScorer.evaluate(ScheduleRule.Daily, emptyList(), today = day(0))
        assertEquals(PlantStage.SEED, new.plantStage())

        // Strength after n perfect days: 3 -> 14.9, 4 -> 19.3, 14 -> 52.8, 24 -> 72.4.
        assertEquals(PlantStage.SEED, stageAfter(perfectDays = 3), "just under the sprout threshold")
        assertEquals(PlantStage.SPROUT, stageAfter(perfectDays = 4))
        assertEquals(PlantStage.SAPLING, stageAfter(perfectDays = 14))
        assertEquals(PlantStage.TREE, stageAfter(perfectDays = 24))
    }

    private fun stageAfter(perfectDays: Int): PlantStage =
        HabitScorer.evaluate(ScheduleRule.Daily, completions(0, perfectDays), today = day(perfectDays))
            .plantStage()

    @Test
    fun `strength alone cannot buy ingrained - it takes the 66 days the research asks for`() {
        val strongButYoung = HabitScorer.evaluate(ScheduleRule.Daily, completions(0, 40), today = day(40))
        assertTrue(strongButYoung.strength >= 85.0, "strength is there")
        assertEquals(PlantStage.TREE, strongButYoung.plantStage(), "but the time is not")

        val ingrained = HabitScorer.evaluate(ScheduleRule.Daily, completions(0, 70), today = day(70))
        assertEquals(PlantStage.INGRAINED, ingrained.plantStage())
    }

    @Test
    fun `one miss never knocks a habit back to seed`() {
        val afterMiss = HabitScorer.evaluate(
            ScheduleRule.Daily,
            completions(0, 30),
            today = day(31),
            restDayPolicy = RestDayPolicy.DISABLED,
        )
        assertTrue(afterMiss.plantStage() >= PlantStage.SAPLING)
    }

    @Test
    fun `before the first occasion there is no plant`() {
        val firstDay = HabitScorer.evaluate(ScheduleRule.Daily, completions(0, 1), today = day(0))
        assertEquals(PlantStage.SEED, firstDay.stageBefore(day(0)))
    }

    @Test
    fun `the stage before a day is the one the day before it produced`() {
        // The whole point of `stageBefore`: read as of yesterday it must agree with what
        // yesterday's own evaluation said, or a screen announces growth that never happened.
        (2..70).forEach { days ->
            assertEquals(
                stageAfter(perfectDays = days - 1),
                HabitScorer.evaluate(ScheduleRule.Daily, completions(0, days), today = day(days - 1))
                    .stageBefore(day(days - 1)),
                "day $days disagrees with day ${days - 1}",
            )
        }
    }

    @Test
    fun `a perfect run passes every stage exactly once and never goes backwards`() {
        val stages = (1..80).map { stageAfter(perfectDays = it) }
        assertEquals(stages.sorted(), stages, "a stage was lost on a day nothing went wrong")
        assertEquals(PlantStage.INGRAINED, stages.last())
    }
}
