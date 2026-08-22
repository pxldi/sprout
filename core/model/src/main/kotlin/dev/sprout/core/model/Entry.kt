/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

import java.time.Instant
import java.time.LocalDate

/** One logged day for one habit. Absence of a row on a scheduled day is what a miss means. */
public data class Entry(
    val id: String = newId(),
    val habitId: String,
    val date: LocalDate,
    val status: EntryStatus,

    /** Amount for measurable / reduce habits: litres, pages, cigarettes. */
    val value: Double? = null,

    val note: String? = null,
    val source: EntrySource = EntrySource.MANUAL,

    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)
