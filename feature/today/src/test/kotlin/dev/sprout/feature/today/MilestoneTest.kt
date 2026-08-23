/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import dev.sprout.core.model.Entry
import dev.sprout.core.model.EntryStatus
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val HABIT = "habit-1"
private val START: LocalDate = LocalDate.of(2026, 3, 2)
private val AT: Instant = Instant.parse("2026-03-02T09:00:00Z")

/**
 * A card that says "sixty-six times" has to have counted sixty-six of them.
 *
 * The whole feature is one arithmetic decision — what counts as a repetition — so this is where
 * that decision is pinned down. Nothing here touches Compose: the picture is a rendering of the
 * answer, and the answer is what has to be right.
 */
class MilestoneTest {

    @Test
    fun `the seventh completion is the one that gets a card`() {
        assertNull(milestoneFor(done(6), START.plusDays(5)), "six is not seven")
        assertEquals(Milestone.SEVEN, milestoneFor(done(7), START.plusDays(6)))
        assertNull(milestoneFor(done(8), START.plusDays(7)), "the card is for the day, not for after it")
    }

    @Test
    fun `the sixty-sixth completion gets the other one`() {
        assertEquals(Milestone.SIXTY_SIX, milestoneFor(done(66), START.plusDays(65)))
        assertNull(milestoneFor(done(65), START.plusDays(64)))
        assertNull(milestoneFor(done(67), START.plusDays(66)))
    }

    @Test
    fun `misses in between do not count towards it`() {
        // Seven completions with a month of nothing scattered through them is still seven
        // completions. Elapsed days are exactly what this must not be counting.
        val days = listOf(0L, 1L, 9L, 10L, 25L, 26L, 40L).map { START.plusDays(it) }
        val today = days.last()

        assertEquals(Milestone.SEVEN, milestoneFor(days.map(::completion), today))
    }

    @Test
    fun `the smallest version counts as a repetition`() {
        // "Ten minutes counts" is the app's promise everywhere else; a milestone that quietly
        // refused to count the ten-minute days would be taking it back.
        val entries = done(6) + entry(START.plusDays(6), EntryStatus.DONE_MIN)

        assertEquals(Milestone.SEVEN, milestoneFor(entries, START.plusDays(6)))
    }

    @Test
    fun `a skip is not a repetition`() {
        val entries = done(6) + entry(START.plusDays(6), EntryStatus.SKIP)

        assertNull(milestoneFor(entries, START.plusDays(6)), "a skip did not do the thing")
    }

    @Test
    fun `a card only ever lands on a day that went well`() {
        // Seven completions already banked, and today was skipped. The count says seven; the day
        // says otherwise, and the day wins. No card ever arrives on a bad one.
        val entries = done(7) + entry(START.plusDays(10), EntryStatus.SKIP)

        assertNull(milestoneFor(entries, START.plusDays(10)))
    }

    @Test
    fun `an unlogged day gets nothing`() {
        assertNull(milestoneFor(done(7), START.plusDays(10)))
    }

    @Test
    fun `days logged ahead of the calendar do not tip it over`() {
        val entries = done(6) + completion(START.plusDays(30))

        assertNull(milestoneFor(entries, START.plusDays(5)), "the thirty-first day has not happened")
    }

    @Test
    fun `clearing an earlier day takes the card back, and re-logging it returns the same one`() {
        // Derived state, not an event — the same rule the shine line follows. Nothing is written
        // down when the card appears, so nothing has to be unwritten when the count changes
        // underneath it.
        val today = START.plusDays(6)
        val seven = done(7)
        val withoutThursday = seven.filterNot { it.date == START.plusDays(3) }

        assertEquals(Milestone.SEVEN, milestoneFor(seven, today))
        assertNull(milestoneFor(withoutThursday, today), "six completions is not the seventh")
        assertEquals(Milestone.SEVEN, milestoneFor(seven, today))
    }

    private fun done(count: Int): List<Entry> =
        (0 until count).map { completion(START.plusDays(it.toLong())) }

    private fun completion(date: LocalDate) = entry(date, EntryStatus.DONE)

    private fun entry(date: LocalDate, status: EntryStatus) = Entry(
        habitId = HABIT,
        date = date,
        status = status,
        createdAt = AT,
        updatedAt = AT,
    )
}
