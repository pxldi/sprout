/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

import java.time.DayOfWeek
import java.time.LocalDate

private const val DAYS_PER_WEEK = 7

/**
 * How often a habit is scheduled.
 *
 * [expectedCompletionsPerWeek] is the single number the scoring model needs: it makes a
 * `3x/week` habit and a `Mon/Wed/Fri` habit gain strength at the same rate, even though the
 * former is scored per week and the latter per day.
 */
public sealed interface ScheduleRule {

    public val expectedCompletionsPerWeek: Double

    /** Every calendar day. */
    public data object Daily : ScheduleRule {
        override val expectedCompletionsPerWeek: Double get() = DAYS_PER_WEEK.toDouble()
    }

    /** Fixed weekdays, e.g. Mon/Wed/Fri. */
    public data class SpecificDays(val days: Set<DayOfWeek>) : ScheduleRule {
        init { require(days.isNotEmpty()) { "SpecificDays needs at least one day" } }
        override val expectedCompletionsPerWeek: Double get() = days.size.toDouble()
    }

    /** Every [n] days counting from [anchor]. */
    public data class EveryNDays(val n: Int, val anchor: LocalDate) : ScheduleRule {
        init { require(n >= 1) { "EveryNDays needs n >= 1, was $n" } }
        override val expectedCompletionsPerWeek: Double get() = DAYS_PER_WEEK.toDouble() / n
    }

    /**
     * [times] completions anywhere within a week. Scored per *week*, not per day, so a bad
     * Tuesday can still be a good week — the slack the research asks for (Sharif & Shu 2017).
     */
    public data class TimesPerWeek(
        val times: Int,
        val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ) : ScheduleRule {
        init { require(times in 1..DAYS_PER_WEEK) { "TimesPerWeek needs 1..7, was $times" } }
        override val expectedCompletionsPerWeek: Double get() = times.toDouble()
    }
}
