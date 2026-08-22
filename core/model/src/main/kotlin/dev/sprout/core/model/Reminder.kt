/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * A reminder for a habit.
 *
 * [leadMinutes] fires the notification *before* [time] — the gym research asks for a prompt about
 * 30 minutes ahead, not at the moment itself.
 */
public data class Reminder(
    val id: String = newId(),
    val habitId: String,
    val time: LocalTime,
    /** Bit per [DayOfWeek], Monday = bit 0. */
    val daysMask: Int = ALL_DAYS,
    val leadMinutes: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    public fun firesOn(day: DayOfWeek): Boolean = daysMask and (1 shl (day.value - 1)) != 0

    public companion object {
        public const val ALL_DAYS: Int = 0b111_1111

        public fun maskOf(days: Set<DayOfWeek>): Int =
            days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
    }
}
