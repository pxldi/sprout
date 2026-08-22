/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.scheduling

import dev.sprout.core.model.ScheduleRule
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OccasionCalendarTest {

    private val monday = LocalDate.of(2026, 1, 5)

    @Test
    fun `daily produces one single-day occasion per day, inclusive of both ends`() {
        val occasions = OccasionCalendar.occasions(ScheduleRule.Daily, monday, monday.plusDays(6))
        assertEquals(7, occasions.size)
        assertTrue(occasions.all { it.isSingleDay && it.requiredCompletions == 1 })
        assertEquals(monday, occasions.first().start)
        assertEquals(monday.plusDays(6), occasions.last().start)
    }

    @Test
    fun `specific days only produces the chosen weekdays`() {
        val rule = ScheduleRule.SpecificDays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val occasions = OccasionCalendar.occasions(rule, monday, monday.plusDays(13))
        assertEquals(4, occasions.size)
        assertTrue(occasions.all { it.start.dayOfWeek in rule.days })
    }

    @Test
    fun `every n days stays aligned to the anchor when the window starts after it`() {
        val anchor = LocalDate.of(2026, 1, 1)
        val rule = ScheduleRule.EveryNDays(n = 3, anchor = anchor)
        val occasions = OccasionCalendar.occasions(rule, monday, monday.plusDays(9))

        // Anchor Jan 1 + 3n lands on Jan 7, 10, 13 within Jan 5..Jan 14.
        assertEquals(
            listOf(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 13)),
            occasions.map { it.start },
        )
    }

    @Test
    fun `every n days is also aligned when the window starts before the anchor`() {
        val anchor = LocalDate.of(2026, 1, 20)
        val rule = ScheduleRule.EveryNDays(n = 5, anchor = anchor)
        val occasions = OccasionCalendar.occasions(rule, LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 21))
        assertEquals(
            listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 20)),
            occasions.map { it.start },
        )
    }

    @Test
    fun `times per week produces whole weeks that require the full target`() {
        val rule = ScheduleRule.TimesPerWeek(times = 3)
        val occasions = OccasionCalendar.occasions(rule, monday, monday.plusDays(13))
        assertEquals(2, occasions.size)
        assertTrue(occasions.all { it.requiredCompletions == 3 })
        assertEquals(monday, occasions[0].start)
        assertEquals(monday.plusDays(6), occasions[0].endInclusive)
        assertEquals(monday.plusDays(7), occasions[1].start)
    }

    @Test
    fun `a week is never clipped to the query window`() {
        val rule = ScheduleRule.TimesPerWeek(times = 2)
        val midWeek = monday.plusDays(3)
        val occasions = OccasionCalendar.occasions(rule, midWeek, midWeek)
        assertEquals(1, occasions.size)
        assertEquals(monday, occasions.single().start, "the week starts on Monday, not on the query date")
        assertEquals(monday.plusDays(6), occasions.single().endInclusive)
    }

    @Test
    fun `week start is configurable`() {
        val rule = ScheduleRule.TimesPerWeek(times = 2, weekStart = DayOfWeek.SUNDAY)
        val occasions = OccasionCalendar.occasions(rule, monday, monday)
        assertEquals(monday.minusDays(1), occasions.single().start)
    }

    @Test
    fun `an occasion is only closed once it is over`() {
        val occasion = Occasion(monday, monday.plusDays(6), 3)
        assertTrue(!occasion.isClosedOn(monday.plusDays(6)), "still today, still open")
        assertTrue(occasion.isClosedOn(monday.plusDays(7)))
    }
}
