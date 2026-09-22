/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.scoring

import dev.sprout.core.model.ScheduleRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A repair saves the run and nothing else, and only once in any seven occasions.
 *
 * Before this rule every miss followed by a completion was repaired and held strength still, so a
 * daily habit done every other day for four months scored 91, INGRAINED, "15 of 16".
 */
class RepairLimitTest {

    private val daily = ScheduleRule.Daily

    /** Rest days off, so a miss has to be repaired or missed and cannot be rested. */
    private fun scoreWithoutRest(entries: List<DayLog>, today: Int) =
        HabitScorer.evaluate(daily, entries, today = day(today), restDayPolicy = RestDayPolicy.DISABLED)

    private fun everyNthDay(n: Int, days: Int): List<DayLog> =
        (0 until days step n).flatMap { completions(it, 1) }

    @Test
    fun `a repaired miss still lowers strength`() {
        val result = scoreWithoutRest(completions(0, 10) + completions(11, 2), today = 13)
        val repaired = result.occasions.single { day(10) in it.occasion }
        val before = result.occasions.single { day(9) in it.occasion }

        assertEquals(OccasionOutcome.REPAIRED, repaired.outcome)
        assertTrue(repaired.strength < before.strength, "the miss is still a miss for strength")
    }

    @Test
    fun `a repaired miss keeps the run`() {
        val result = scoreWithoutRest(completions(0, 10) + completions(11, 2), today = 13)
        assertEquals(12, result.currentRun)
    }

    @Test
    fun `a repaired day counts as a missed chance in the recent fraction`() {
        val result = scoreWithoutRest(completions(0, 10) + completions(11, 2), today = 13)
        assertEquals(12, result.recentCompletions)
        assertEquals(13, result.recentChances)
    }

    @Test
    fun `a second miss within seven occasions of a repair ends the run`() {
        // Day 5 missed and repaired, day 8 missed three occasions later.
        val entries = completions(0, 5) + completions(6, 2) + completions(9, 2)
        val result = scoreWithoutRest(entries, today = 11)

        assertEquals(OccasionOutcome.REPAIRED, result.outcomeOn(day(5)))
        assertEquals(OccasionOutcome.MISSED, result.outcomeOn(day(8)))
        assertEquals(2, result.currentRun)
        assertEquals(7, result.bestRun)
    }

    @Test
    fun `a repair is available again seven occasions after the last one`() {
        // Day 5 repaired, day 12 missed exactly seven occasions later.
        val entries = completions(0, 5) + completions(6, 6) + completions(13, 1)
        val result = scoreWithoutRest(entries, today = 14)

        assertEquals(OccasionOutcome.REPAIRED, result.outcomeOn(day(5)))
        assertEquals(OccasionOutcome.REPAIRED, result.outcomeOn(day(12)))
        assertEquals(12, result.currentRun)
    }

    @Test
    fun `a miss with the repair already used is not called paused`() {
        val result = scoreWithoutRest(completions(0, 5) + completions(6, 2), today = 9)

        assertEquals(OccasionOutcome.MISSED, result.outcomeOn(day(8)))
        assertEquals(StreakState.BROKEN, result.streakState)
        assertNull(result.repairDeadline)
        assertEquals(0, result.currentRun)
    }

    @Test
    fun `a daily habit done every other day scores well below one done every day`() {
        val perfect = HabitScorer.evaluate(daily, completions(0, 120), today = day(120))
        val half = HabitScorer.evaluate(daily, everyNthDay(2, 120), today = day(120))

        assertTrue(half.strength < 0.6 * perfect.strength, "strength ${half.strength} vs ${perfect.strength}")
        assertTrue(half.plantStage() < PlantStage.TREE, "stage ${half.plantStage()}")
    }

    @Test
    fun `a daily habit done every other day shows about half in the recent fraction`() {
        val half = HabitScorer.evaluate(daily, everyNthDay(2, 120), today = day(120))
        val rate = half.recentCompletions.toDouble() / half.recentChances

        assertTrue(rate < 0.6, "recent ${half.recentCompletions}/${half.recentChances}")
    }

    @Test
    fun `one missed day a week stays nearly free`() {
        val sixOfSeven = (0 until 120).filter { it % 7 != 6 }.flatMap { completions(it, 1) }
        val result = HabitScorer.evaluate(daily, sixOfSeven, today = day(120))

        assertTrue(result.strength > 90, "strength ${result.strength}")
        assertTrue(result.currentRun > 90, "run ${result.currentRun}")
    }

    @Test
    fun `a weekly habit that drops below its target loses strength`() {
        val weekly = ScheduleRule.TimesPerWeek(times = 3)
        val tenFullWeeks = (0 until 10).flatMap { week -> completions(week * 7, 3) }
        val thenOnceAWeek = (10 until 20).flatMap { week -> completions(week * 7, 1) }

        val before = HabitScorer.evaluate(weekly, tenFullWeeks, today = day(70))
        val after = HabitScorer.evaluate(weekly, tenFullWeeks + thenOnceAWeek, today = day(140))

        assertTrue(after.strength < before.strength - 40, "strength ${before.strength} -> ${after.strength}")
    }
}
