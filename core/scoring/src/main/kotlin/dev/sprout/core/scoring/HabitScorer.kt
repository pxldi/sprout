/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.scoring

import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.ScheduleRule
import dev.sprout.core.scheduling.Occasion
import dev.sprout.core.scheduling.OccasionCalendar
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

private const val DAYS_PER_WEEK = 7.0
private const val MAX_STRENGTH = 100.0

/** Strength after 30 days of perfect adherence, on a 0..1 scale. Calibration point. */
private const val STRENGTH_AT_30_DAYS = 0.80
private const val CALIBRATION_DAYS = 30.0

/**
 * Bounds on the per-occasion retention factor.
 *
 * The upper bound stops a very frequent habit from becoming unmovable; the lower bound stops an
 * infrequent one (once a week) from losing a third of its strength to a single miss.
 */
private const val MIN_ALPHA = 0.85
private const val MAX_ALPHA = 0.99

/** Completions that buy one rest day. Seven — so a daily habit banks two per fortnight. */
private const val COMPLETIONS_PER_REST_DAY = 7

/** Duolingo found two freezes beat one, and three no better than two. */
private const val MAX_REST_DAYS = 2

/** Days after a missed occasion in which showing up still earns the run back. */
private const val REPAIR_WINDOW_DAYS = 2L

private const val RATE_WINDOW_DAYS = 30L

/**
 * The whole scoring model, as one pure function over a habit's log.
 *
 * Deliberate properties, each traceable to docs/01-research.md:
 * - strength is an EMA, so a miss dents it and nothing ever resets to zero (Lally 2010);
 * - slack is banked and spent silently, and a broken run is repairable (Sharif & Shu 2017;
 *   Duolingo's freezes and earn-back);
 * - the day after a miss is singled out, because rewarding the return was the single best
 *   intervention of 53 in the StepUp megastudy (Milkman 2021).
 */
public object HabitScorer {

    public fun evaluate(
        rule: ScheduleRule,
        entries: List<DayLog>,
        today: LocalDate,
        restDayPolicy: RestDayPolicy = RestDayPolicy.EARNED,
    ): HabitProgress {
        val from = entries.minOfOrNull { it.date } ?: today
        val walk = Walk(
            alpha = retentionFactor(rule),
            restDayPolicy = restDayPolicy,
            today = today,
            entriesByDate = entries.groupBy { it.date },
            completionDates = entries.filter { it.status.isCompletion }.mapTo(HashSet()) { it.date },
        )
        OccasionCalendar.occasions(rule, from, today).forEach(walk::step)
        return walk.toProgress()
    }

    /**
     * Per-occasion retention factor, chosen so that perfect adherence reaches
     * [STRENGTH_AT_30_DAYS] after [CALIBRATION_DAYS] days *regardless of how often the habit is
     * scheduled* — a daily habit and a 3x/week habit both feel like they are getting somewhere.
     */
    internal fun retentionFactor(rule: ScheduleRule): Double {
        val occasionsIn30Days = CALIBRATION_DAYS * rule.expectedCompletionsPerWeek / DAYS_PER_WEEK
        return exp(ln(1.0 - STRENGTH_AT_30_DAYS) / occasionsIn30Days).coerceIn(MIN_ALPHA, MAX_ALPHA)
    }
}

/**
 * Mutable accumulator for a single pass over a habit's occasions, oldest first.
 *
 * Kept separate from [HabitScorer] so the ordering rules — a rest day is spent before a repair is
 * attempted, a repair only rescues a run that was still alive — live in one readable place.
 */
private class Walk(
    private val alpha: Double,
    private val restDayPolicy: RestDayPolicy,
    private val today: LocalDate,
    private val entriesByDate: Map<LocalDate, List<DayLog>>,
    private val completionDates: Set<LocalDate>,
) {
    private val resolved = mutableListOf<ResolvedOccasion>()
    private var strength = 0.0
    private var restDays = 0
    private var towardNextRestDay = 0
    private var run = 0
    private var best = 0
    private var bounceBack: LocalDate? = null
    private var runWasAlive = true

    fun step(occasion: Occasion) {
        val logged = datesIn(occasion).flatMap { entriesByDate[it].orEmpty() }
        val done = logged.count { it.status.isCompletion }
        val credit = (done.toDouble() / occasion.requiredCompletions).coerceIn(0.0, 1.0)
        val firstCompletion = logged.firstOrNull { it.status.isCompletion }?.date

        // An occasion resolves the moment its target is met — waiting for midnight would mean
        // strength rose a day late and, worse, that coming back after a miss went uncelebrated
        // on the very day it happened.
        val met = done >= occasion.requiredCompletions
        if (!met && !occasion.isClosedOn(today)) {
            resolved += ResolvedOccasion(occasion, OccasionOutcome.OPEN, done, credit, firstCompletion)
            return
        }

        val outcome = classify(occasion, logged, done)
        applyStrength(occasion, outcome, credit)
        applyRun(occasion, outcome, firstCompletion)

        runWasAlive = outcome == OccasionOutcome.COMPLETED || outcome.isNeutral
        resolved += ResolvedOccasion(occasion, outcome, done, credit, firstCompletion)
    }

    private fun classify(occasion: Occasion, logged: List<DayLog>, done: Int): OccasionOutcome = when {
        done >= occasion.requiredCompletions -> OccasionOutcome.COMPLETED
        logged.any { it.status == EntryStatus.SKIP } -> OccasionOutcome.SKIPPED
        restDayPolicy == RestDayPolicy.EARNED && restDays > 0 -> {
            restDays--
            OccasionOutcome.RESTED
        }
        // A repair rescues a run that was alive. It does not retroactively erase a whole gap.
        runWasAlive && repairedWithin(occasion) -> OccasionOutcome.REPAIRED
        else -> OccasionOutcome.MISSED
    }

    /** Steps once per required completion, so `3x/week` and `Mon/Wed/Fri` grow at one rate. */
    private fun applyStrength(occasion: Occasion, outcome: OccasionOutcome, credit: Double) {
        if (outcome.isNeutral) return
        val retained = alpha.pow(occasion.requiredCompletions)
        strength = strength * retained + credit * MAX_STRENGTH * (1.0 - retained)
    }

    private fun applyRun(occasion: Occasion, outcome: OccasionOutcome, firstCompletion: LocalDate?) {
        when (outcome) {
            OccasionOutcome.COMPLETED -> {
                if (cameBackFromAMiss()) bounceBack = firstCompletion ?: occasion.start
                run++
                best = max(best, run)
                earnRestDays(occasion.requiredCompletions)
            }
            // While a miss is still repairable the run is held, not lost.
            OccasionOutcome.MISSED -> if (!stillRepairable(occasion)) run = 0
            else -> Unit // SKIPPED / RESTED / REPAIRED are neutral by design.
        }
    }

    private fun earnRestDays(completions: Int) {
        if (restDayPolicy != RestDayPolicy.EARNED) return
        towardNextRestDay += completions
        while (towardNextRestDay >= COMPLETIONS_PER_REST_DAY) {
            towardNextRestDay -= COMPLETIONS_PER_REST_DAY
            restDays = (restDays + 1).coerceAtMost(MAX_REST_DAYS)
        }
    }

    fun toProgress(): HabitProgress {
        val lastClosed = resolved.lastOrNull { it.outcome != OccasionOutcome.OPEN }
        val recent = recentlyJudged()
        val state = when {
            lastClosed == null || lastClosed.outcome != OccasionOutcome.MISSED -> StreakState.ACTIVE
            stillRepairable(lastClosed.occasion) -> StreakState.PAUSED
            else -> StreakState.BROKEN
        }
        return HabitProgress(
            strength = strength,
            currentRun = run,
            bestRun = max(best, run),
            recentCompletions = recent.count { it.outcome == OccasionOutcome.COMPLETED },
            recentChances = recent.size,
            streakState = state,
            restDaysAvailable = restDays,
            repairDeadline = lastClosed?.occasion
                ?.endInclusive
                ?.plusDays(REPAIR_WINDOW_DAYS)
                ?.takeIf { state == StreakState.PAUSED },
            bounceBackOn = bounceBack,
            occasions = resolved.toList(),
        )
    }

    private fun repairedWithin(occasion: Occasion): Boolean =
        (1..REPAIR_WINDOW_DAYS).any { occasion.endInclusive.plusDays(it) in completionDates }

    private fun stillRepairable(occasion: Occasion): Boolean =
        !today.isAfter(occasion.endInclusive.plusDays(REPAIR_WINDOW_DAYS))

    /** True when the occasion just before this one was a miss the user is coming back from. */
    private fun cameBackFromAMiss(): Boolean {
        val previous = resolved.lastOrNull { it.outcome != OccasionOutcome.OPEN } ?: return false
        return previous.outcome == OccasionOutcome.MISSED || previous.outcome == OccasionOutcome.REPAIRED
    }

    /**
     * Occasions in the window that were actually judged — completed or missed.
     *
     * Skips, rest days and repairs are deliberately excluded. Counting them would drag the
     * fraction down and so make banked slack visible, which is the one thing it must not be.
     */
    private fun recentlyJudged(): List<ResolvedOccasion> {
        val windowStart = today.minusDays(RATE_WINDOW_DAYS)
        return resolved.filter {
            !it.occasion.start.isBefore(windowStart) &&
                (it.outcome == OccasionOutcome.COMPLETED || it.outcome == OccasionOutcome.MISSED)
        }
    }
}

private fun datesIn(occasion: Occasion): List<LocalDate> =
    generateSequence(occasion.start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(occasion.endInclusive) }
        .toList()
