/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

import java.time.Instant

/**
 * Why a lapse happened, tagged at the moment it is logged.
 *
 * The tags are the categories the relapse literature actually found: more than half of lapses
 * follow negative emotion or conflict, and over a fifth follow social pressure. The point of
 * tagging is attribution — "Friday, stressed, at the bar" promotes learning, where "I'm a
 * failure" drives escalation.
 */
public enum class LapseTrigger { STRESS, CONFLICT, SOCIAL, BOREDOM, CUE, CELEBRATION, OTHER }

/** A logged slip on an AVOID or REDUCE habit. Recorded without judgement; noticing is the skill. */
public data class Lapse(
    val id: String = newId(),
    val habitId: String,
    val at: Instant,
    val triggers: Set<LapseTrigger> = emptySet(),
    val amount: Double? = null,
    val note: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)
