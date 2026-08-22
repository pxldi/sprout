/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Encodes a [ScheduleRule] as one short, human-readable string for storage and export.
 *
 * Deliberately hand-rolled rather than reflective serialization: this string ends up in the
 * database *and* in the JSON backup users own, so it has to stay readable and stable across
 * refactors of the sealed hierarchy. A renamed Kotlin class must not silently invalidate
 * everyone's backups.
 *
 * ```
 * daily
 * days:1,3,5            (ISO day numbers, Monday = 1)
 * everyN:3:2026-01-05   (interval, anchor date)
 * perWeek:3:1           (times, week start)
 * ```
 */
public object ScheduleRuleCodec {

    public fun encode(rule: ScheduleRule): String = when (rule) {
        is ScheduleRule.Daily -> "daily"
        is ScheduleRule.SpecificDays ->
            "days:" + rule.days.map { it.value }.sorted().joinToString(",")
        is ScheduleRule.EveryNDays -> "everyN:${rule.n}:${rule.anchor}"
        is ScheduleRule.TimesPerWeek -> "perWeek:${rule.times}:${rule.weekStart.value}"
    }

    /** @throws IllegalArgumentException if [encoded] is not a schedule this version understands. */
    public fun decode(encoded: String): ScheduleRule {
        val parts = encoded.split(':')
        return when (parts.first()) {
            "daily" -> ScheduleRule.Daily
            "days" -> ScheduleRule.SpecificDays(
                parts.requiredAt(1).split(',').map { DayOfWeek.of(it.trim().toInt()) }.toSet(),
            )
            "everyN" -> ScheduleRule.EveryNDays(
                n = parts.requiredAt(1).toInt(),
                anchor = LocalDate.parse(parts.requiredAt(2)),
            )
            "perWeek" -> ScheduleRule.TimesPerWeek(
                times = parts.requiredAt(1).toInt(),
                weekStart = DayOfWeek.of(parts.requiredAt(2).toInt()),
            )
            else -> throw IllegalArgumentException("Unknown schedule rule: '$encoded'")
        }
    }

    private fun List<String>.requiredAt(index: Int): String =
        getOrNull(index) ?: throw IllegalArgumentException("Malformed schedule rule: '${joinToString(":")}'")
}
