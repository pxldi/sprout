/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.scoring

import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.ScheduleRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The per-occasion strength the detail screen draws as a curve.
 *
 * What these pin down is that the curve and the headline number are the *same* calculation: the
 * alternative — asking the scorer for a score at ninety past dates — would be ninety walks that
 * agree today and could silently disagree after any change to the model.
 */
class StrengthHistoryTest {

    @Test
    fun `the last closed occasion carries the strength the habit ends on`() {
        val result = HabitScorer.evaluate(ScheduleRule.Daily, completions(0, 30), today = day(30))
        val lastClosed = result.occasions.last { it.outcome != OccasionOutcome.OPEN }
        assertEquals(result.strength, lastClosed.strength, 0.000_1)
    }

    @Test
    fun `an open occasion carries what it inherited, because nothing has been judged`() {
        // Day 20 is today and unlogged: still open, and an open occasion is never a miss.
        val result = HabitScorer.evaluate(ScheduleRule.Daily, completions(0, 20), today = day(20))
        val open = result.occasions.last()
        val previous = result.occasions[result.occasions.lastIndex - 1]

        assertEquals(OccasionOutcome.OPEN, open.outcome)
        assertEquals(previous.strength, open.strength, 0.000_1)
    }

    @Test
    fun `strength only climbs while the habit is being done`() {
        val result = HabitScorer.evaluate(ScheduleRule.Daily, completions(0, 40), today = day(40))
        val history = result.occasions.map { it.strength }

        assertTrue(
            history.zipWithNext().all { (before, after) -> after >= before },
            "a perfect run must never dip: $history",
        )
        assertTrue(history.first() < history.last(), "and it has to go somewhere")
    }

    @Test
    fun `the dent lands on the occasion it happened, not on the one after it`() {
        val result = HabitScorer.evaluate(
            ScheduleRule.Daily,
            // Twenty perfect days, then a gap wide enough that coming back cannot repair it —
            // a return on day 21 or 22 would make day 20 REPAIRED, and repairs never dent.
            completions(0, 20) + completions(23, 3),
            today = day(26),
            restDayPolicy = RestDayPolicy.DISABLED,
        )
        val missed = result.occasions.single { day(20) in it.occasion }
        val before = result.occasions.single { day(19) in it.occasion }
        // The bottom of the gap, not the first day of it: days 21 and 22 dented it further, so
        // the return climbs back from where the gap left the score, not from where it began.
        val bottom = result.occasions.single { day(22) in it.occasion }
        val back = result.occasions.single { day(23) in it.occasion }

        assertEquals(OccasionOutcome.MISSED, missed.outcome)
        assertTrue(missed.strength < before.strength, "the miss is where the drop belongs")
        assertTrue(back.strength > bottom.strength, "and coming back is where the recovery does")
    }

    @Test
    fun `a skipped occasion records the strength it held, so the curve runs flat through it`() {
        val result = HabitScorer.evaluate(
            ScheduleRule.Daily,
            completions(0, 20) + log(20 to EntryStatus.SKIP) + completions(21, 1),
            today = day(22),
        )
        val skipped = result.occasions.single { day(20) in it.occasion }
        val before = result.occasions.single { day(19) in it.occasion }

        assertEquals(OccasionOutcome.SKIPPED, skipped.outcome)
        assertEquals(before.strength, skipped.strength, 0.000_1)
    }
}
