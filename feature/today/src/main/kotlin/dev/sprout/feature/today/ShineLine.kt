/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import dev.sprout.core.datastore.ShineHistory
import dev.sprout.core.model.Entry
import dev.sprout.core.scoring.HabitProgress
import dev.sprout.core.scoring.PlantStage
import dev.sprout.core.scoring.plantStage
import dev.sprout.core.scoring.stageBefore
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The one specific true thing the app says when a habit is ticked off.
 *
 * Values, not strings: every one of these carries the numbers it is claiming, so the claim can be
 * asserted in a test and the wording can live in the one strings file with the rest of the copy.
 * The same discipline as [GentleNote], for the same reason — this is the sentence the user reads
 * at the moment they did the thing, and it has to be true.
 *
 * "Praise is specific and comes from real numbers." There is no generic line, and no fallback: if
 * nothing true is worth saying today, the app says nothing.
 */
public sealed interface ShineLine {

    /** What this line is *about*, for the "not twice in a fortnight" rule. */
    public val kind: String

    /** The first completion this habit has ever had. */
    public data object FirstEver : ShineLine {
        override val kind: String get() = "first"
    }

    /** This completion is what moved the plant on a stage. */
    public data class StageUp(val stage: PlantStage) : ShineLine {
        // Per stage: reaching a tree is not the same news as reaching a sprout, and one must
        // not be silenced because the other was said last week.
        override val kind: String get() = "stage_${stage.name}"
    }

    /** No run this habit has ever had was longer than the one it is on. */
    public data class LongestRun(val run: Int) : ShineLine {
        override val kind: String get() = "run"
    }

    /** More completions this week than in any week before it. */
    public data class BestWeek(val completions: Int) : ShineLine {
        override val kind: String get() = "week_best"
    }

    /** Plainly how many times this week. The commonest line, and the least remarkable. */
    public data class TimesThisWeek(val times: Int) : ShineLine {
        override val kind: String get() = "week_count"
    }
}

/**
 * Picks what to say about today's completion, or nothing.
 *
 * Ordered rarest first: being told the plant just became a tree beats being told it is the third
 * time this week, and only one thing is ever said. A line already said for this habit within
 * [ShineHistory.NOVELTY_DAYS] days is skipped, and the next true one down is offered instead.
 */
internal fun shineFor(
    habitId: String,
    progress: HabitProgress,
    entries: List<Entry>,
    today: LocalDate,
    shown: Map<String, LocalDate>,
): ShineLine? = candidates(progress, entries, today).firstOrNull { isFresh(habitId, it.kind, today, shown) }

private fun candidates(
    progress: HabitProgress,
    entries: List<Entry>,
    today: LocalDate,
): List<ShineLine> {
    val completions = entries.filter { it.status.isCompletion }.map { it.date }
    val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek()))
    val thisWeek = completions.count { !it.isBefore(weekStart) && !it.isAfter(today) }
    val was = progress.stageBefore(today)
    val now = progress.plantStage()

    return listOfNotNull(
        ShineLine.FirstEver.takeIf { completions.size == 1 },
        // Strictly upward. A stage can be re-crossed after a bad fortnight, and being told a
        // habit has fallen back to a sprout is not praise — it is the loss framing this app bans.
        ShineLine.StageUp(now).takeIf { now > was },
        // `bestRun` is the longer of the best and the current, so equality means nothing before
        // it ever ran longer. A tie with an old record is still "no run was ever longer".
        ShineLine.LongestRun(progress.currentRun)
            .takeIf { progress.currentRun >= LEAST_WORTH_SAYING && progress.currentRun == progress.bestRun },
        ShineLine.BestWeek(thisWeek)
            .takeIf { thisWeek >= LEAST_WORTH_SAYING && thisWeek > bestEarlierWeek(completions, weekStart) },
        ShineLine.TimesThisWeek(thisWeek).takeIf { thisWeek >= LEAST_WORTH_SAYING },
    )
}

/**
 * The most completions in any week before this one.
 *
 * Zero when there is no earlier week, which makes a second completion in the very first week a
 * best week — correctly: no week has done better.
 */
private fun bestEarlierWeek(completions: List<LocalDate>, weekStart: LocalDate): Int = completions
    .filter { it.isBefore(weekStart) }
    .groupingBy { it.with(TemporalAdjusters.previousOrSame(firstDayOfWeek())) }
    .eachCount()
    .values
    .maxOrNull()
    ?: 0

private fun isFresh(
    habitId: String,
    kind: String,
    today: LocalDate,
    shown: Map<String, LocalDate>,
): Boolean {
    val last = shown[ShineHistory.keyOf(habitId, kind)] ?: return true
    // Already said today: keep saying it. The line is derived state, not an event, so a habit
    // reopened at lunchtime must show the same sentence it showed at breakfast rather than
    // rotating to the next one down.
    return last == today || last.isBefore(today.minusDays(ShineHistory.NOVELTY_DAYS))
}

private fun firstDayOfWeek() = WeekFields.of(Locale.getDefault()).firstDayOfWeek

/** Once is not a pattern. Two is the smallest number this app will call a week's worth. */
private const val LEAST_WORTH_SAYING = 2
