/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

import java.time.Instant

/**
 * A habit, including its plan.
 *
 * [cue] and [copingPlan] are not decoration: implementation intentions are the strongest single
 * technique in the literature (d = 0.65), and for exercise the effect is only reliable when a
 * coping plan is included. The creation flow requires them — see docs/02-app-design.md.
 *
 * [updatedAt] and [deletedAt] exist on every table so a Syncthing-style folder transport can
 * merge two devices last-writer-wins with tombstones, without a server.
 */
public data class Habit(
    val id: String = newId(),
    val name: String,
    val type: HabitType,
    val schedule: ScheduleRule,

    /** "I'm someone who runs in the morning." Identity correlates with habit at r = 0.55. */
    val identityPhrase: String? = null,

    /** The smallest version that still counts. "Put the shoes on." */
    val minimumVersion: String? = null,

    /**
     * The *if* half of an implementation intention: "it's 7 am and I've brushed my teeth".
     *
     * Only the situation is stored. The then-half is the habit itself, and the sentence around
     * it ("If …, then I'll …") is UI copy, so it stays translatable and survives a rename.
     */
    val cue: String? = null,

    /** The fallback, stored the same way: "I'll do it after dinner" for "If I miss it, then …". */
    val copingPlan: String? = null,

    /** Habit this one is stacked onto, if any. */
    val anchorHabitId: String? = null,

    /** Temptation bundling: the treat paired with the habit. */
    val bundleText: String? = null,

    // Measurable habits.
    val unit: String? = null,
    val target: Double? = null,

    /** Ceiling for REDUCE habits. A day at or under this counts as done. */
    val ceiling: Double? = null,

    val colorArgb: Int? = null,
    val icon: String? = null,
    val position: Int = 0,

    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
    val deletedAt: Instant? = null,
) {
    public val isArchived: Boolean get() = archivedAt != null
}
