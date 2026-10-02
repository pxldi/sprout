/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.scoring

import dev.sprout.core.model.Entry
import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.Habit
import dev.sprout.core.model.HabitType
import dev.sprout.core.model.ScheduleRule
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Count habits score by how much of the target was reached, and only a full day is a completion. */
class CountedScoreTest {

    private val pages = habit(HabitType.DO_NUMERIC, target = 20.0)
    private val coffees = habit(HabitType.REDUCE, ceiling = 2.0)

    @Test
    fun `half the target every day scores like half the days, not like all of them`() {
        val half = score(pages, (0 until 90).map { it to 10.0 }, today = day(90))
        val full = score(pages, (0 until 90).map { it to 20.0 }, today = day(90))

        assertEquals(99.0, full.strength, 0.5)
        assertEquals(49.5, half.strength, 1.0)
    }

    @Test
    fun `a partial day moves strength by its share`() {
        val base = (0 until 20).map { it to 20.0 }
        val full = score(pages, base + (20 to 20.0), today = day(21), rest = RestDayPolicy.DISABLED)
        val partial = score(pages, base + (20 to 10.0), today = day(21), rest = RestDayPolicy.DISABLED)
        val missed = score(pages, base, today = day(21), rest = RestDayPolicy.DISABLED)

        assertTrue(missed.strength < partial.strength && partial.strength < full.strength)
        assertEquals((full.strength + missed.strength) / 2, partial.strength, 1e-9)
    }

    @Test
    fun `partial days do not grow the run and count as undone in the fraction`() {
        val result = score(pages, (0 until 10).map { it to 19.0 }, today = day(10))

        assertEquals(0, result.currentRun)
        assertEquals(0, result.recentCompletions)
        assertEquals(10, result.recentChances)
    }

    @Test
    fun `a partial day does not open an occasion that is still today`() {
        val result = score(pages, listOf(0 to 5.0), today = day(0))

        assertEquals(OccasionOutcome.OPEN, result.outcomeOn(day(0)))
        assertEquals(0.0, result.strength, 1e-9)
    }

    @Test
    fun `a reduce habit over its ceiling every other day scores like every other day`() {
        val alternating = score(
            coffees,
            (0 until 90).map { it to if (it % 2 == 0) 1.0 else 4.0 },
            today = day(90),
            rest = RestDayPolicy.DISABLED,
        )
        val always = score(coffees, (0 until 90).map { it to 1.0 }, today = day(90))

        assertEquals(99.0, always.strength, 0.5)
        assertEquals(50.0, alternating.strength, 2.0)
        assertEquals(15, alternating.recentCompletions)
    }

    private fun score(
        habit: Habit,
        amounts: List<Pair<Int, Double>>,
        today: java.time.LocalDate,
        rest: RestDayPolicy = RestDayPolicy.EARNED,
    ): HabitProgress = HabitScorer.evaluate(
        rule = habit.schedule,
        entries = amounts.map { (offset, amount) ->
            habit.dayLogOf(
                Entry(
                    habitId = habit.id,
                    date = day(offset),
                    status = habit.statusFor(amount) ?: error("$amount logs nothing"),
                    value = amount,
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                ),
            )
        },
        today = today,
        restDayPolicy = rest,
    )

    private fun habit(type: HabitType, target: Double? = null, ceiling: Double? = null) = Habit(
        name = "Read",
        type = type,
        schedule = ScheduleRule.Daily,
        target = target,
        ceiling = ceiling,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
