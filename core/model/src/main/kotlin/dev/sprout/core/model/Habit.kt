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

    /** Kept off the lock screen, the widget and share cards. See [displayName] and [outsideName]. */
    val isPrivate: Boolean = false,

    /** What a private habit is called everywhere, the app included. Ignored while not private. */
    val alias: String? = null,

    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
    val deletedAt: Instant? = null,
) {
    public val isArchived: Boolean get() = archivedAt != null

    /** Logged as an amount: [HabitType.DO_NUMERIC] against [target], [HabitType.REDUCE] against [ceiling]. */
    public val isCounted: Boolean get() = type == HabitType.DO_NUMERIC || type == HabitType.REDUCE

    /**
     * What a day with [amount] logged counts as, or null when that amount means nothing logged.
     *
     * Decided when the day is logged and stored with it, so lowering a target later does not
     * rewrite how earlier days went. Zero clears a [HabitType.DO_NUMERIC] day, because nothing
     * done is the absence of an entry. Zero on a [HabitType.REDUCE] day is the best day there is.
     */
    public fun statusFor(amount: Double): EntryStatus? = when (type) {
        HabitType.DO_NUMERIC -> when {
            amount <= 0.0 -> null
            amount >= (target ?: 0.0) -> EntryStatus.DONE
            else -> EntryStatus.PARTIAL
        }
        HabitType.REDUCE -> when {
            amount < 0.0 -> null
            amount <= (ceiling ?: Double.MAX_VALUE) -> EntryStatus.DONE
            else -> EntryStatus.LAPSE
        }
        else -> if (amount > 0.0) EntryStatus.DONE else null
    }

    /**
     * The amount [entry] stands for, or null when it has none.
     *
     * A count day marked done without a number, from a notification or a tick, stands for the
     * target. Adding one to it must not turn a finished day back into a partial one.
     */
    public fun amountOf(entry: Entry): Double? = entry.value
        ?: target.takeIf { type == HabitType.DO_NUMERIC && entry.status.isCompletion }

    /**
     * How much of a day an entry is worth to strength, in `0.0..1.0`.
     *
     * A completion is worth a whole day and a partial count its share of [target]. Everything
     * else, a skip and a lapse included, is worth nothing here; a skip is held by the scorer instead.
     */
    public fun creditFor(entry: Entry): Double = when {
        entry.status.isCompletion -> 1.0
        entry.status == EntryStatus.PARTIAL -> {
            val goal = target?.takeIf { it > 0.0 }
            if (goal == null || entry.value == null) 0.0 else (entry.value / goal).coerceIn(0.0, 1.0)
        }
        else -> 0.0
    }

    /** The name the app shows. Only the edit form shows [name] itself. */
    public val displayName: String get() = alias?.takeIf { isPrivate } ?: name

    /**
     * The name for places other people can see, such as a notification or a widget.
     *
     * Null for a private habit without an alias; the caller shows a neutral label instead.
     * The real name is left out even of the unlocked notification, because Android shows a
     * notification's full content on the lock screen unless the user has changed the default.
     */
    public val outsideName: String? get() = if (isPrivate) alias else name
}
