/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import dev.sprout.core.model.Entry
import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.Habit
import dev.sprout.core.scoring.DayLog
import dev.sprout.core.scoring.HabitProgress
import dev.sprout.core.scoring.HabitScorer
import dev.sprout.core.scoring.OccasionOutcome
import dev.sprout.core.scoring.ResolvedOccasion
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.ceil

/** Thirteen weeks: a season, and as many columns as fit a phone without shrinking to dots. */
internal const val WINDOW_WEEKS = 13
internal const val DAYS_PER_WEEK = 7
private const val WINDOW_DAYS = WINDOW_WEEKS * DAYS_PER_WEEK

/** Enough to show that notes are being kept, without turning the screen into a journal. */
private const val NOTE_LIMIT = 12

/**
 * What one day looked like.
 *
 * There is no member for "failed". [MISSED] is a day that was scheduled and went unlogged, and
 * it is drawn in the neutral grey the palette reserves for exactly this — never in the error
 * colour. See the visual identity rules in docs/02-app-design.md.
 */
public enum class DayMark {
    /** Logged as done, at full size or at the smallest version. The two count the same. */
    DONE,

    /** Deliberately skipped. Neutral, and it looks it. */
    SKIPPED,

    /** Scheduled, over, nothing logged. */
    MISSED,

    /** Scheduled and not yet judged: today, or a week still running. */
    OPEN,

    /** Nothing was owed. Empty space, which is what "not scheduled" should look like. */
    OFF,
}

public data class HeatmapDay(val date: LocalDate, val mark: DayMark)

public data class StrengthPoint(val date: LocalDate, val strength: Double)

/** Completions per weekday. A count, never a rate — see [weekdayTallies]. */
public data class WeekdayTally(val day: DayOfWeek, val completions: Int)

public data class DatedNote(val date: LocalDate, val note: String)

/** Everything the detail screen draws, worked out once. */
public data class HabitDetail(
    val habit: Habit,
    val progress: HabitProgress,
    val curve: List<StrengthPoint>,
    val days: List<HeatmapDay>,
    val weekdays: List<WeekdayTally>,
    val notes: List<DatedNote>,
    /**
     * Whether anything has ever been logged.
     *
     * A habit created ten minutes ago has a curve of one point and a grid of blanks. Drawing
     * those would be three empty charts saying nothing; this is what lets the screen say the one
     * true thing instead.
     */
    val hasLog: Boolean,
    /**
     * The day this was worked out for.
     *
     * Carried rather than asked for again by the screen: the ViewModel has the injected clock,
     * and a chart that read the system date for itself would be a second source of "now" — one
     * that no test can move and that disagrees with the data beside it the moment midnight lands.
     */
    val today: LocalDate,
) {
    /**
     * How many weeks the grid and the tallies actually cover.
     *
     * Not [WINDOW_WEEKS]: a habit three days old gets a two-column grid, and captioning that
     * "last 13 weeks" would claim eleven weeks it never lived through. The same argument as the
     * 30-day fraction's denominator, and the same answer.
     */
    val weeks: Int get() = ceil(days.size / DAYS_PER_WEEK.toFloat()).toInt().coerceAtLeast(1)

    /**
     * Whether there is enough behind this habit for a chart to say something.
     *
     * A curve through two points is a line, and a strength of 4 drawn on a 0..100 axis is a rule
     * along the bottom of the screen that looks like a divider. A week in, both of them mean
     * what they look like. Until then the three numbers and the plan are the whole story, and
     * they are not a lesser one.
     */
    val hasEnoughToDraw: Boolean get() = days.count { it.mark != DayMark.OFF } >= DAYS_PER_WEEK
}

/**
 * Turns a habit and its log into the six things the screen shows.
 *
 * Pure, and deliberately outside the ViewModel: every judgement in here — which days count as
 * missed, where the curve starts, what a week-scored habit does to a single Tuesday — is worth
 * testing without a Robolectric runtime around it.
 */
internal fun detailOf(
    habit: Habit,
    entries: List<Entry>,
    today: LocalDate,
    startedOn: LocalDate,
): HabitDetail {
    val progress = HabitScorer.evaluate(
        rule = habit.schedule,
        entries = entries.map { DayLog(it.date, it.status) },
        today = today,
    )
    val days = heatmap(progress, entries.associateBy { it.date }, today, startedOn)
    return HabitDetail(
        habit = habit,
        progress = progress,
        curve = curve(progress.occasions, today),
        days = days,
        weekdays = weekdayTallies(days),
        notes = notes(entries),
        hasLog = entries.isNotEmpty(),
        today = today,
    )
}

/**
 * The strength curve over the window, in dates rather than occasion numbers.
 *
 * Plotted against the calendar because an every-three-days habit would otherwise draw a dent
 * right beside a recovery that was a fortnight away from it. Between occasions strength does not
 * move, so the honest render of these points is a step, not a smooth slope.
 */
private fun curve(occasions: List<ResolvedOccasion>, today: LocalDate): List<StrengthPoint> {
    val cutoff = today.minusDays(WINDOW_DAYS.toLong() - 1)
    val points = occasions.map {
        // The day the occasion resolved. Clamped, because the week a habit is currently inside
        // ends in the future and nothing is drawn there yet.
        StrengthPoint(minOf(it.occasion.endInclusive, today), it.strength)
    }
    // A habit with two years behind it must enter the window at the height it had reached, not
    // climb from zero as though its history began when the chart did.
    val entering = points.lastOrNull { it.date.isBefore(cutoff) }?.copy(date = cutoff)
    return listOfNotNull(entering) + points.filter { !it.date.isBefore(cutoff) }
}

private fun heatmap(
    progress: HabitProgress,
    entriesByDate: Map<LocalDate, Entry>,
    today: LocalDate,
    startedOn: LocalDate,
): List<HeatmapDay> {
    // Whole weeks, so the grid's rows are weekdays and its columns are weeks.
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val cutoff = today.minusDays(WINDOW_DAYS.toLong() - 1)
    val start = if (startedOn.isAfter(cutoff)) {
        // The habit's own start is not negotiable: rounding it forward would drop days it
        // actually lived through. A habit younger than the window gets a shorter grid rather
        // than three months of blank cells implying an absence that predates it.
        startedOn.with(TemporalAdjusters.previousOrSame(firstDay))
    } else {
        // The cutoff is a rough "about three months", so it may as well land on a week
        // boundary. Rounding it outward instead would add a fourteenth column covering two or
        // three days, and then the caption underneath would have to claim a week for them.
        cutoff.with(TemporalAdjusters.nextOrSame(firstDay))
    }
    return generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { HeatmapDay(it, markFor(it, entriesByDate[it], progress)) }
        .toList()
}

private fun markFor(date: LocalDate, entry: Entry?, progress: HabitProgress): DayMark {
    val resolved = progress.occasions.firstOrNull { date in it.occasion }
    return when {
        entry?.status?.isCompletion == true -> DayMark.DONE
        entry?.status == EntryStatus.SKIP -> DayMark.SKIPPED
        // A logged slip is scored as a miss, and it looks like one. Nothing about the drawing
        // says it was worse than forgetting — logging it was the harder thing to do.
        entry?.status == EntryStatus.LAPSE -> DayMark.MISSED
        // Before the first logged day the scorer has no opinion, so neither does the grid.
        resolved == null -> DayMark.OFF
        // A `3x/week` habit owes the week, not the Tuesday. Painting its unlogged days as misses
        // would show four failures in a week the user completed.
        !resolved.occasion.isSingleDay -> DayMark.OFF
        resolved.outcome == OccasionOutcome.OPEN -> DayMark.OPEN
        else -> DayMark.MISSED
    }
}

/**
 * Which weekdays the habit actually happens on.
 *
 * Counts, not percentages. The denominator would have to be "chances that fell on a Tuesday",
 * which differs per schedule kind and is exactly the sort of number that ends up meaning
 * something slightly different from what it says.
 */
private fun weekdayTallies(days: List<HeatmapDay>): List<WeekdayTally> {
    val done = days.filter { it.mark == DayMark.DONE }.groupingBy { it.date.dayOfWeek }.eachCount()
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    return (0 until DAYS_PER_WEEK).map { offset ->
        val day = firstDay.plus(offset.toLong())
        WeekdayTally(day, done[day] ?: 0)
    }
}

/** Most recent first: a note from this week is worth more than one from March. */
private fun notes(entries: List<Entry>): List<DatedNote> = entries
    .mapNotNull { entry -> entry.note?.takeIf { it.isNotBlank() }?.let { DatedNote(entry.date, it) } }
    .sortedByDescending { it.date }
    .take(NOTE_LIMIT)
