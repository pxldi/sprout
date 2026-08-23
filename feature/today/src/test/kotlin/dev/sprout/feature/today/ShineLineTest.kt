/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import dev.sprout.core.datastore.ShineHistory
import dev.sprout.core.model.Entry
import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.ScheduleRule
import dev.sprout.core.scoring.DayLog
import dev.sprout.core.scoring.HabitProgress
import dev.sprout.core.scoring.HabitScorer
import dev.sprout.core.scoring.PlantStage
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val HABIT = "habit-1"

/** A Monday, so "this week" is unambiguous whatever the default locale calls its first day. */
private val MONDAY: LocalDate = LocalDate.of(2026, 3, 2)
private val AT: Instant = Instant.parse("2026-03-02T09:00:00Z")

/**
 * The praise the app gives has to be true, and these are the assertions that it is.
 *
 * Pure JUnit against the real scorer: no mock progress, because a line derived from invented
 * numbers proves nothing about the line the user will actually read.
 */
class ShineLineTest {

    @Test
    fun `the first completion ever is what gets said`() {
        val line = shine(days = listOf(MONDAY), today = MONDAY)
        assertEquals(ShineLine.FirstEver, line)
    }

    @Test
    fun `the first one is only the first one`() {
        // Day two is not "the first". The commonest way to get praise wrong is to keep giving
        // the newcomer's line to somebody who is no longer new.
        val line = shine(days = listOf(MONDAY, MONDAY.plusDays(1)), today = MONDAY.plusDays(1))
        assertTrue(line !is ShineLine.FirstEver, "got $line")
    }

    @Test
    fun `a stage is announced once, in order, and never downwards`() {
        val run = (0L until 80L).map { MONDAY.plusDays(it) }
        val announced = mutableListOf<PlantStage>()
        run.indices.forEach { i ->
            val today = run[i]
            val upTo = run.take(i + 1)
            // A fresh history each day: this is about what is *true*, not what has been said.
            val line = shine(days = upTo, today = today)
            if (line is ShineLine.StageUp) announced += line.stage
        }

        assertEquals(announced.distinct(), announced, "a stage was announced twice")
        assertEquals(announced.sorted(), announced, "a stage was announced out of order")
        assertTrue(PlantStage.SEED !in announced, "every habit starts as a seed; none grows into one")
        assertEquals(
            listOf(PlantStage.SPROUT, PlantStage.SAPLING, PlantStage.TREE, PlantStage.INGRAINED),
            announced,
            "eighty perfect days should pass every stage exactly once",
        )
    }

    @Test
    fun `a stage-up outranks the run it also earned`() {
        // Both are true on the day the plant sprouts. The rarer thing is the one worth saying.
        val run = (0L until 80L).map { MONDAY.plusDays(it) }
        val sproutedOn = run.indices.first { i ->
            shine(days = run.take(i + 1), today = run[i]) is ShineLine.StageUp
        }
        val line = shine(days = run.take(sproutedOn + 1), today = run[sproutedOn])
        assertTrue(line is ShineLine.StageUp, "got $line")
    }

    @Test
    fun `the longest run is not claimed while an older run was longer`() {
        // Five, a fortnight off, then three. Three is not a record and must not be sold as one.
        val first = (0L until 5L).map { MONDAY.plusDays(it) }
        val second = (19L until 22L).map { MONDAY.plusDays(it) }
        val today = second.last()

        val line = shine(days = first + second, today = today)

        val progress = progress(first + second, today)
        assertTrue(progress.currentRun < progress.bestRun, "test premise: the old run was longer")
        assertTrue(line !is ShineLine.LongestRun, "got $line")
    }

    @Test
    fun `the longest run is claimed when nothing was ever longer`() {
        val days = (0L until 3L).map { MONDAY.plusDays(it) }
        val today = days.last()
        // Everything rarer already said, so the run is what is left.
        val line = shine(days = days, today = today, shown = saidYesterday(today, "first", "stage_SPROUT"))

        assertEquals(ShineLine.LongestRun(3), line)
        assertEquals(3, progress(days, today).bestRun, "the claim has to match the arithmetic")
    }

    @Test
    fun `a best week is not claimed while an earlier week did better`() {
        val busyWeek = (0L until 5L).map { MONDAY.plusDays(it) }
        val thisWeek = listOf(MONDAY.plusDays(14), MONDAY.plusDays(15))
        val today = thisWeek.last()

        val line = shine(days = busyWeek + thisWeek, today = today, shown = allSaidYesterday(today))

        assertNull(line, "two is not a best week when an earlier week had five")
    }

    @Test
    fun `a best week is claimed when no earlier week did better`() {
        val quietWeek = listOf(MONDAY, MONDAY.plusDays(1))
        val thisWeek = (14L until 17L).map { MONDAY.plusDays(it) }
        val today = thisWeek.last()

        val line = shine(
            days = quietWeek + thisWeek,
            today = today,
            shown = saidYesterday(today, "first", "run", "stage_SPROUT", "stage_SAPLING"),
        )

        assertEquals(ShineLine.BestWeek(3), line)
    }

    @Test
    fun `the week count counts this week and no other`() {
        val lastWeek = (0L until 4L).map { MONDAY.plusDays(it) }
        val thisWeek = listOf(MONDAY.plusDays(7), MONDAY.plusDays(8))
        val today = thisWeek.last()

        val line = shine(
            days = lastWeek + thisWeek,
            today = today,
            shown = saidYesterday(today, "first", "run", "week_best", "stage_SPROUT", "stage_SAPLING"),
        )

        assertEquals(ShineLine.TimesThisWeek(2), line, "four of those days were last week")
    }

    @Test
    fun `nothing is said rather than something generic`() {
        val days = (0L until 3L).map { MONDAY.plusDays(it) }
        val today = days.last()

        assertNull(shine(days = days, today = today, shown = allSaidYesterday(today)))
    }

    @Test
    fun `a line already said today keeps being said today`() {
        // Otherwise reopening the app at lunchtime silently swaps the sentence out from under
        // somebody who saw it at breakfast.
        val days = listOf(MONDAY)
        val shown = mapOf(ShineHistory.keyOf(HABIT, "first") to MONDAY)

        assertEquals(ShineLine.FirstEver, shine(days = days, today = MONDAY, shown = shown))
    }

    @Test
    fun `a line comes back once the fortnight is up`() {
        val days = (0L until 3L).map { MONDAY.plusDays(it) }
        val today = days.last()
        val stale = today.minusDays(ShineHistory.NOVELTY_DAYS + 1)
        val shown = mapOf(
            ShineHistory.keyOf(HABIT, "first") to today.minusDays(1),
            ShineHistory.keyOf(HABIT, "stage_SPROUT") to today.minusDays(1),
            ShineHistory.keyOf(HABIT, "run") to stale,
        )

        assertEquals(ShineLine.LongestRun(3), shine(days = days, today = today, shown = shown))
    }

    @Test
    fun `one habit's memory is not another's`() {
        val shown = mapOf(ShineHistory.keyOf("some-other-habit", "first") to MONDAY)
        assertEquals(ShineLine.FirstEver, shine(days = listOf(MONDAY), today = MONDAY, shown = shown))
    }

    private fun shine(
        days: List<LocalDate>,
        today: LocalDate,
        shown: Map<String, LocalDate> = emptyMap(),
    ): ShineLine? {
        val entries = days.map(::entry)
        return shineFor(HABIT, progress(days, today), entries, today, shown)
    }

    private fun progress(days: List<LocalDate>, today: LocalDate): HabitProgress =
        HabitScorer.evaluate(
            rule = ScheduleRule.Daily,
            entries = days.map { DayLog(it, EntryStatus.DONE) },
            today = today,
        )

    private fun entry(date: LocalDate) = Entry(
        habitId = HABIT,
        date = date,
        status = EntryStatus.DONE,
        createdAt = AT,
        updatedAt = AT,
    )

    /** Everything but the named kinds is free to fire. */
    private fun saidYesterday(today: LocalDate, vararg kinds: String) =
        kinds.associate { ShineHistory.keyOf(HABIT, it) to today.minusDays(1) }

    private fun allSaidYesterday(today: LocalDate) = saidYesterday(
        today,
        "first", "run", "week_best", "week_count",
        *PlantStage.entries.map { "stage_${it.name}" }.toTypedArray(),
    )
}
