/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import dev.sprout.core.model.Habit
import dev.sprout.core.model.HabitType
import dev.sprout.core.model.Reminder
import dev.sprout.core.model.ScheduleRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.truncate

/**
 * The six questions asked when creating a habit, in order.
 *
 * This is a wizard rather than a form on purpose. "The plan is the product": the cue and the
 * coping plan are the intervention, not metadata about it, and a single scrolling form invites
 * people to fill in the name and skip the two fields that carry the effect
 * (docs/02-app-design.md, "Habit creation flow").
 */
public enum class CreationStep {
    WHAT,
    SMALLEST,
    CUE,
    COPING,
    SCHEDULE,
    REMINDER,
    ;

    public val isFirst: Boolean get() = ordinal == 0
    public val isLast: Boolean get() = ordinal == entries.lastIndex
}

/** Which shape of schedule the user picked. The rule itself needs more than the choice. */
public enum class ScheduleKind { DAILY, SPECIFIC_DAYS, TIMES_PER_WEEK, EVERY_N_DAYS }

private const val DEFAULT_TIMES_PER_WEEK = 3
private const val DEFAULT_EVERY_N = 2
public const val MAX_EVERY_N_DAYS: Int = 30
public const val DAYS_IN_WEEK: Int = 7

/** Early enough to be a morning habit, late enough not to be an alarm clock. */
private val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(8, 0)

/**
 * A habit being written, with every field still free to be empty.
 *
 * Deliberately not a [dev.sprout.core.model.Habit]: a half-built habit has no id, no timestamps
 * and an invalid schedule, and modelling it as the real thing would mean weakening the real
 * thing's invariants to accommodate a screen.
 */
public data class HabitDraft(
    val name: String = "",
    val type: HabitType = HabitType.DO_BOOL,
    val identityPhrase: String = "",
    val unit: String = "",
    val target: String = "",
    val minimumVersion: String = "",
    val cue: String = "",
    val copingPlan: String = "",
    val scheduleKind: ScheduleKind = ScheduleKind.DAILY,
    val specificDays: Set<DayOfWeek> = emptySet(),
    val timesPerWeek: Int = DEFAULT_TIMES_PER_WEEK,
    val everyNDays: Int = DEFAULT_EVERY_N,
    val reminderEnabled: Boolean = false,
    val reminderTime: LocalTime = DEFAULT_REMINDER_TIME,

    /**
     * The day an every-N-days schedule counts from.
     *
     * Carried so that editing a habit's *name* cannot silently move which days it falls on.
     * Null while the habit is new, and null while some other kind is picked: switching **to**
     * every-N-days is a fresh decision, and it starts from today.
     */
    val everyNAnchor: LocalDate? = null,

    /** Which day an n-times-a-week week begins on. Never asked, only preserved. */
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    /** True once [scheduleRule] can be built. [ScheduleRule.SpecificDays] rejects an empty set. */
    public val hasUsableSchedule: Boolean
        get() = scheduleKind != ScheduleKind.SPECIFIC_DAYS || specificDays.isNotEmpty()

    public fun scheduleRule(anchor: LocalDate): ScheduleRule = when (scheduleKind) {
        ScheduleKind.DAILY -> ScheduleRule.Daily
        ScheduleKind.SPECIFIC_DAYS -> ScheduleRule.SpecificDays(specificDays)
        ScheduleKind.TIMES_PER_WEEK -> ScheduleRule.TimesPerWeek(timesPerWeek, weekStart)
        ScheduleKind.EVERY_N_DAYS -> ScheduleRule.EveryNDays(everyNDays, everyNAnchor ?: anchor)
    }

    /**
     * Which days the reminder may fire on.
     *
     * Tied to the schedule rather than defaulting to every day: a Mon/Wed/Fri habit whose
     * reminder went off on Tuesday would be nagging about something that is not due.
     */
    public fun reminderDaysMask(): Int = when (scheduleKind) {
        ScheduleKind.SPECIFIC_DAYS -> Reminder.maskOf(specificDays)
        else -> Reminder.ALL_DAYS
    }

    /**
     * Whether the user may leave [step].
     *
     * Only three answers are required, and two of them are the plan. Requiring the cue and the
     * coping plan is the one place this app is deliberately more demanding than every other
     * habit tracker: implementation intentions are the strongest single technique in the
     * literature (d = 0.65), and for exercise the effect only holds when a coping plan comes
     * with it. A habit with no plan is a wish, and this app declines to store wishes.
     *
     * The smallest version and the reminder are genuinely optional, so their steps always pass.
     */
    public fun canLeave(step: CreationStep): Boolean = when (step) {
        CreationStep.WHAT -> name.isNotBlank() && typeFieldsValid()
        CreationStep.SMALLEST -> true
        CreationStep.CUE -> cue.isNotBlank()
        CreationStep.COPING -> copingPlan.isNotBlank()
        CreationStep.SCHEDULE -> hasUsableSchedule
        CreationStep.REMINDER -> true
    }

    /**
     * Whether every question has an answer good enough to store.
     *
     * The wizard reaches this by walking the steps; the edit form has no steps to walk, so it
     * asks outright. Both go through [canLeave], which keeps one definition of "answered".
     */
    public val isComplete: Boolean get() = CreationStep.entries.all { canLeave(it) }

    /** A measurable habit without a target has nothing to measure against. */
    private fun typeFieldsValid(): Boolean =
        type != HabitType.DO_NUMERIC || (target.toDoubleOrNull()?.let { it > 0 } == true)

    public val targetValue: Double? get() = target.toDoubleOrNull()

    public companion object {
        /**
         * The answers a stored habit would have given.
         *
         * Optional fields come back as `""` rather than null, because the draft is what the text
         * fields are bound to and a null there is a crash, not an empty box.
         */
        public fun of(habit: Habit, reminder: Reminder?): HabitDraft {
            val everyN = habit.schedule as? ScheduleRule.EveryNDays
            val weekly = habit.schedule as? ScheduleRule.TimesPerWeek
            return HabitDraft(
                name = habit.name,
                type = habit.type,
                identityPhrase = habit.identityPhrase.orEmpty(),
                unit = habit.unit.orEmpty(),
                target = habit.target?.asTargetText().orEmpty(),
                minimumVersion = habit.minimumVersion.orEmpty(),
                cue = habit.cue.orEmpty(),
                copingPlan = habit.copingPlan.orEmpty(),
                scheduleKind = habit.schedule.kind(),
                specificDays = (habit.schedule as? ScheduleRule.SpecificDays)?.days.orEmpty(),
                timesPerWeek = weekly?.times ?: DEFAULT_TIMES_PER_WEEK,
                everyNDays = everyN?.n ?: DEFAULT_EVERY_N,
                reminderEnabled = reminder?.enabled == true,
                reminderTime = reminder?.time ?: DEFAULT_REMINDER_TIME,
                everyNAnchor = everyN?.anchor,
                weekStart = weekly?.weekStart ?: DayOfWeek.MONDAY,
            )
        }
    }
}

private fun ScheduleRule.kind(): ScheduleKind = when (this) {
    ScheduleRule.Daily -> ScheduleKind.DAILY
    is ScheduleRule.SpecificDays -> ScheduleKind.SPECIFIC_DAYS
    is ScheduleRule.TimesPerWeek -> ScheduleKind.TIMES_PER_WEEK
    is ScheduleRule.EveryNDays -> ScheduleKind.EVERY_N_DAYS
}

/** A target of 5.0 was typed into a box as "5", and that is what belongs back in the box. */
private fun Double.asTargetText(): String =
    if (this == truncate(this)) toLong().toString() else toString()
