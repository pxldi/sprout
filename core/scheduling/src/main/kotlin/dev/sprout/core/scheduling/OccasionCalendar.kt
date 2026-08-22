/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.scheduling

import dev.sprout.core.model.ScheduleRule
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val DAYS_PER_WEEK = 7

/** Expands a [ScheduleRule] into the concrete [Occasion]s it produces. Pure and deterministic. */
public object OccasionCalendar {

    /**
     * All occasions overlapping `[from, to]`, in ascending order.
     *
     * Week-based occasions are emitted whole (never clipped), so the same week always scores
     * identically regardless of the query window.
     */
    public fun occasions(rule: ScheduleRule, from: LocalDate, to: LocalDate): List<Occasion> {
        require(!to.isBefore(from)) { "to $to precedes from $from" }
        return when (rule) {
            ScheduleRule.Daily ->
                datesBetween(from, to).map { Occasion(it, it, 1) }

            is ScheduleRule.SpecificDays ->
                datesBetween(from, to).filter { it.dayOfWeek in rule.days }.map { Occasion(it, it, 1) }

            is ScheduleRule.EveryNDays -> {
                val step = rule.n.toLong()
                // First on-or-after `from` that is congruent to the anchor, without walking day by day.
                val delta = ChronoUnit.DAYS.between(rule.anchor, from)
                val offset = ((delta % step) + step) % step
                var cursor = if (offset == 0L) from else from.plusDays(step - offset)
                buildList {
                    while (!cursor.isAfter(to)) {
                        add(Occasion(cursor, cursor, 1))
                        cursor = cursor.plusDays(step)
                    }
                }
            }

            is ScheduleRule.TimesPerWeek -> {
                var weekStart = startOfWeek(from, rule)
                buildList {
                    while (!weekStart.isAfter(to)) {
                        add(Occasion(weekStart, weekStart.plusDays(DAYS_PER_WEEK - 1L), rule.times))
                        weekStart = weekStart.plusWeeks(1)
                    }
                }
            }
        }
    }

    /** The occasion containing [date], or null if the habit is not scheduled then. */
    public fun occasionOn(rule: ScheduleRule, date: LocalDate): Occasion? =
        occasions(rule, date, date).firstOrNull { date in it }

    private fun startOfWeek(date: LocalDate, rule: ScheduleRule.TimesPerWeek): LocalDate {
        val shift = ((date.dayOfWeek.value - rule.weekStart.value) + DAYS_PER_WEEK) % DAYS_PER_WEEK
        return date.minusDays(shift.toLong())
    }

    private fun datesBetween(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
}
