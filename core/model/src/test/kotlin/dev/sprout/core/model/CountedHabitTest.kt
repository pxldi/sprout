/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountedHabitTest {

    private val pages = habit(HabitType.DO_NUMERIC, target = 20.0)
    private val coffees = habit(HabitType.REDUCE, ceiling = 2.0)

    @Test
    fun `only count and reduce habits are counted`() {
        assertTrue(pages.isCounted)
        assertTrue(coffees.isCounted)
        assertFalse(habit(HabitType.DO_BOOL).isCounted)
        assertFalse(habit(HabitType.AVOID).isCounted)
    }

    @Test
    fun `a count habit is done at its target and partial below it`() {
        assertEquals(EntryStatus.PARTIAL, pages.statusFor(1.0))
        assertEquals(EntryStatus.PARTIAL, pages.statusFor(19.5))
        assertEquals(EntryStatus.DONE, pages.statusFor(20.0))
        assertEquals(EntryStatus.DONE, pages.statusFor(35.0))
    }

    @Test
    fun `zero of a count habit logs nothing`() {
        assertNull(pages.statusFor(0.0))
    }

    @Test
    fun `a reduce habit is done at or under its ceiling, zero included`() {
        assertEquals(EntryStatus.DONE, coffees.statusFor(0.0))
        assertEquals(EntryStatus.DONE, coffees.statusFor(2.0))
        assertEquals(EntryStatus.LAPSE, coffees.statusFor(3.0))
    }

    @Test
    fun `a partial day is worth its share of the target`() {
        assertEquals(0.5, pages.creditFor(entry(EntryStatus.PARTIAL, 10.0)), 1e-9)
        assertEquals(0.05, pages.creditFor(entry(EntryStatus.PARTIAL, 1.0)), 1e-9)
    }

    @Test
    fun `a partial day never counts for more than a whole one`() {
        // Logged as partial under a higher target, which was lowered afterwards.
        assertEquals(1.0, pages.creditFor(entry(EntryStatus.PARTIAL, 30.0)), 1e-9)
    }

    @Test
    fun `completions are worth a whole day and everything else nothing`() {
        assertEquals(1.0, pages.creditFor(entry(EntryStatus.DONE, 20.0)), 1e-9)
        assertEquals(1.0, pages.creditFor(entry(EntryStatus.DONE, null)), 1e-9)
        assertEquals(0.0, coffees.creditFor(entry(EntryStatus.LAPSE, 3.0)), 1e-9)
        assertEquals(0.0, pages.creditFor(entry(EntryStatus.SKIP, null)), 1e-9)
    }

    @Test
    fun `a count day done without a number stands for the target`() {
        assertEquals(20.0, pages.amountOf(entry(EntryStatus.DONE, null)))
        assertEquals(12.0, pages.amountOf(entry(EntryStatus.PARTIAL, 12.0)))
        assertNull(pages.amountOf(entry(EntryStatus.SKIP, null)))
        assertNull(coffees.amountOf(entry(EntryStatus.DONE, null)))
    }

    private fun entry(status: EntryStatus, value: Double?) = Entry(
        habitId = "h",
        date = LocalDate.of(2026, 1, 5),
        status = status,
        value = value,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
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
