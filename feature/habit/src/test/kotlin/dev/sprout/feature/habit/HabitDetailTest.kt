/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import dev.sprout.core.model.Entry
import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.Habit
import dev.sprout.core.model.HabitType
import dev.sprout.core.model.ScheduleRule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val TODAY: LocalDate = LocalDate.of(2026, 3, 10) // a Tuesday
private val NOW: Instant = TODAY.atTime(9, 0).toInstant(ZoneOffset.UTC)

/**
 * What the detail screen draws, tested without a screen.
 *
 * The judgements worth pinning are the ones a reader of the chart would take as fact: which days
 * count as missed, where the curve starts, and what a week-scored habit does to a single Tuesday.
 */
class HabitDetailTest {

    @Test
    fun `a week-scored habit never paints an individual day as missed`() {
        // Three times a week, done three times, all on the same three days. The other four days
        // owed nothing — and a grid showing four misses in a completed week would be a lie.
        val habit = habit(schedule = ScheduleRule.TimesPerWeek(times = 3, weekStart = DayOfWeek.MONDAY))
        val detail = detailFor(
            habit,
            entries = listOf(2, 4, 6).map { done(TODAY.minusDays(it.toLong())) },
        )

        assertTrue(
            detail.days.none { it.mark == DayMark.MISSED },
            "week-scored habits owe the week: ${detail.days.filter { it.mark == DayMark.MISSED }}",
        )
        assertEquals(3, detail.days.count { it.mark == DayMark.DONE })
    }

    @Test
    fun `a scheduled day that went unlogged is missed, and today is not`() {
        val detail = detailFor(
            habit(),
            entries = (2..6).map { done(TODAY.minusDays(it.toLong())) },
        )

        assertEquals(DayMark.MISSED, markOn(detail, TODAY.minusDays(1)))
        // An open occasion is never a miss — the day is not over.
        assertEquals(DayMark.OPEN, markOn(detail, TODAY))
    }

    @Test
    fun `a skipped day reads as skipped, not as a gap`() {
        val detail = detailFor(
            habit(),
            entries = listOf(
                done(TODAY.minusDays(2)),
                entry(TODAY.minusDays(1), EntryStatus.SKIP),
            ),
        )

        assertEquals(DayMark.SKIPPED, markOn(detail, TODAY.minusDays(1)))
    }

    @Test
    fun `days a habit was never scheduled on are left blank`() {
        val habit = habit(schedule = ScheduleRule.SpecificDays(setOf(DayOfWeek.MONDAY)))
        val detail = detailFor(habit, entries = listOf(done(TODAY.minusDays(1)))) // the Monday

        assertEquals(DayMark.DONE, markOn(detail, TODAY.minusDays(1)))
        assertEquals(DayMark.OFF, markOn(detail, TODAY), "a Tuesday owes a Monday habit nothing")
    }

    @Test
    fun `the curve ends on the same number the screen prints`() {
        val detail = detailFor(habit(), entries = (0..29).map { done(TODAY.minusDays(it.toLong())) })

        assertEquals(detail.progress.strength, detail.curve.last().strength, 0.000_1)
    }

    @Test
    fun `a long history enters the window at the height it had reached`() {
        val detail = detailFor(habit(), entries = (0..300).map { done(TODAY.minusDays(it.toLong())) })
        val first = detail.curve.first()

        assertEquals(TODAY.minusDays(WINDOW_WEEKS * DAYS_PER_WEEK - 1L), first.date)
        // The curve's own cutoff is the raw window; only the grid rounds to whole weeks.
        assertTrue(
            first.strength > 90.0,
            "ten months of perfect days must not re-enter the chart from zero: ${first.strength}",
        )
    }

    @Test
    fun `weekday tallies count completions and cover the whole week`() {
        val monday = TODAY.minusDays(8)
        val detail = detailFor(
            habit(),
            entries = listOf(done(monday), done(monday.minusDays(7)), done(monday.plusDays(1))),
        )

        assertEquals(DAYS_PER_WEEK, detail.weekdays.size, "every weekday shows, even the empty ones")
        assertEquals(2, detail.weekdays.single { it.day == DayOfWeek.MONDAY }.completions)
        assertEquals(1, detail.weekdays.single { it.day == DayOfWeek.TUESDAY }.completions)
        assertEquals(0, detail.weekdays.single { it.day == DayOfWeek.SATURDAY }.completions)
    }

    @Test
    fun `notes come back most recent first, and blank ones are not notes`() {
        val detail = detailFor(
            habit(),
            entries = listOf(
                done(TODAY.minusDays(5)).copy(note = "shins hurt"),
                done(TODAY.minusDays(1)).copy(note = "easiest one yet"),
                done(TODAY.minusDays(3)).copy(note = "   "),
            ),
        )

        assertEquals(listOf("easiest one yet", "shins hurt"), detail.notes.map { it.note })
    }

    @Test
    fun `a habit with nothing logged says so rather than drawing empty charts`() {
        val detail = detailFor(habit(), entries = emptyList())

        assertFalse(detail.hasLog)
        assertFalse(detail.hasEnoughToDraw)
    }

    @Test
    fun `charts wait for a week, because two points are not a curve`() {
        val threeDays = detailFor(habit(), entries = (1..3).map { done(TODAY.minusDays(it.toLong())) })
        val aWeek = detailFor(habit(), entries = (1..7).map { done(TODAY.minusDays(it.toLong())) })

        assertTrue(threeDays.hasLog, "there is a log — just not enough of one to draw")
        assertFalse(threeDays.hasEnoughToDraw)
        assertTrue(aWeek.hasEnoughToDraw)
    }

    @Test
    fun `a young habit gets a short grid, not three months of blanks`() {
        val started = TODAY.minusDays(3)
        val detail = detailOf(
            habit = habit().copy(createdAt = started.atStartOfDay().toInstant(ZoneOffset.UTC)),
            entries = listOf(done(TODAY.minusDays(1))),
            today = TODAY,
            startedOn = started,
        )

        assertTrue(detail.days.size <= DAYS_PER_WEEK * 2, "grew to ${detail.days.size} cells")
        assertTrue(detail.days.first().date <= started)
        // And the caption counts the weeks that are drawn. "Last 13 weeks" over two columns
        // would claim eleven weeks the habit was never alive for.
        assertTrue(detail.weeks <= 2, "captioned ${detail.weeks} weeks over ${detail.days.size} days")
    }

    @Test
    fun `a habit older than the window is captioned with the whole window`() {
        val detail = detailFor(habit(), entries = (0..200).map { done(TODAY.minusDays(it.toLong())) })

        assertEquals(WINDOW_WEEKS, detail.weeks)
    }

    private fun detailFor(habit: Habit, entries: List<Entry>) = detailOf(
        habit = habit,
        entries = entries,
        today = TODAY,
        // Old enough that the window, not the habit's age, decides where the grid starts.
        startedOn = TODAY.minusYears(1),
    )

    private fun markOn(detail: HabitDetail, date: LocalDate) =
        detail.days.single { it.date == date }.mark

    private fun habit(schedule: ScheduleRule = ScheduleRule.Daily) = Habit(
        name = "Morning run",
        type = HabitType.DO_BOOL,
        schedule = schedule,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun done(date: LocalDate) = entry(date, EntryStatus.DONE)

    private fun entry(date: LocalDate, status: EntryStatus) = Entry(
        habitId = "habit",
        date = date,
        status = status,
        createdAt = NOW,
        updatedAt = NOW,
    )
}
