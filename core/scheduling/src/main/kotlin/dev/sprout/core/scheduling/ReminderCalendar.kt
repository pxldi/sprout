/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.scheduling

import dev.sprout.core.model.Reminder
import dev.sprout.core.model.ScheduleRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** A reminder together with the schedule of the habit it belongs to. */
public data class ReminderTarget(
    val reminder: Reminder,
    val schedule: ScheduleRule,
)

/**
 * One moment at which the app must wake up, and everything due at it.
 *
 * Several habits can share a minute, so this is a list. Only one alarm is ever registered with
 * the system — see [ReminderCalendar].
 */
public data class ReminderFire(
    val at: Instant,
    val due: List<Due>,
) {
    public data class Due(val reminderId: String, val habitId: String)
}

/**
 * Works out when the next reminder is due. Pure, so the awkward parts are testable without a device.
 *
 * Deliberately knows nothing about what has already been logged. Suppressing a reminder for a day
 * that is already done needs entry data and a decision about *when* "already done" is read — at
 * schedule time it would be wrong the moment the user logs the habit early. That check belongs at
 * delivery, not here.
 */
public object ReminderCalendar {

    /**
     * How far ahead to look before giving up.
     *
     * A `SpecificDays` habit is at most a week out and `EveryNDays` at most n days, so this only
     * bites on absurd configurations. Returning null there is better than looping forever.
     */
    private const val SEARCH_DAYS = 400

    /** The next fire across all [targets], or null if nothing is scheduled. */
    public fun nextFire(targets: List<ReminderTarget>, now: Instant, zone: ZoneId): ReminderFire? {
        val upcoming = targets.mapNotNull { target ->
            nextFireFor(target, now, zone)?.let { at -> at to target }
        }
        val earliest = upcoming.minOfOrNull { (at, _) -> at } ?: return null
        return ReminderFire(
            at = earliest,
            due = upcoming
                .filter { (at, _) -> at == earliest }
                .map { (_, target) -> ReminderFire.Due(target.reminder.id, target.reminder.habitId) },
        )
    }

    /** The next fire for one reminder, strictly after [now]. */
    public fun nextFireFor(target: ReminderTarget, now: Instant, zone: ZoneId): Instant? {
        val reminder = target.reminder
        if (!reminder.enabled || reminder.deletedAt != null) return null

        val today = now.atZone(zone).toLocalDate()
        return generateSequence(today) { it.plusDays(1) }
            .take(SEARCH_DAYS)
            .filter { target.firesOn(it) }
            .map { fireInstant(it, reminder, zone) }
            // Not `>= now`: an alarm for the instant we are already at would fire immediately and
            // then reschedule to the same instant, forever.
            .firstOrNull { it.isAfter(now) }
    }

    /**
     * The wall-clock moment for an occasion on [date], resolved in [zone].
     *
     * Lead time is subtracted as *local* time and can cross midnight backwards, so the fire may
     * land on the day before the occasion. That is the intended reading of "30 minutes before".
     *
     * [java.time.ZonedDateTime] absorbs the DST edges: a time inside a spring-forward gap moves
     * to just after it rather than throwing, and an ambiguous autumn time takes the earlier of
     * the two offsets. A reminder is a nudge, so shifting by an hour once a year beats not firing.
     */
    private fun fireInstant(date: LocalDate, reminder: Reminder, zone: ZoneId): Instant =
        LocalDateTime.of(date, reminder.time)
            .minusMinutes(reminder.leadMinutes.toLong())
            .atZone(zone)
            .toInstant()
}

/**
 * Whether this reminder belongs on [date] at all.
 *
 * Both gates have to pass. The mask alone is not enough: an `EveryNDays` habit is saved with an
 * all-days mask because no weekday pattern describes it, and would otherwise nag daily.
 *
 * `TimesPerWeek` is the deliberate exception — every day of the week is a valid chance to do a
 * 3x/week habit, so the mask is the only constraint and the reminder does repeat daily.
 */
private fun ReminderTarget.firesOn(date: LocalDate): Boolean =
    reminder.firesOn(date.dayOfWeek) && OccasionCalendar.occasionOn(schedule, date) != null
