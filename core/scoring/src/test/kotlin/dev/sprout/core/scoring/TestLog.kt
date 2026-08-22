/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.scoring

import dev.sprout.core.model.EntryStatus
import java.time.LocalDate

internal val START: LocalDate = LocalDate.of(2026, 1, 5) // a Monday

/** Builds a day-by-day log starting at [START]; '.' = no entry (a miss). */
internal fun log(vararg entries: Pair<Int, EntryStatus>): List<DayLog> =
    entries.map { (dayOffset, status) -> DayLog(START.plusDays(dayOffset.toLong()), status) }

/** [days] consecutive completions from day [from]. */
internal fun completions(from: Int, days: Int): List<DayLog> =
    (from until from + days).map { DayLog(START.plusDays(it.toLong()), EntryStatus.DONE) }

internal fun day(offset: Int): LocalDate = START.plusDays(offset.toLong())
