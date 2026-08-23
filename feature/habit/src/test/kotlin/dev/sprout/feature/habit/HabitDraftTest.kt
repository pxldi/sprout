/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import dev.sprout.core.model.Habit
import dev.sprout.core.model.HabitType
import dev.sprout.core.model.Reminder
import dev.sprout.core.model.ScheduleRule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the creation flow refuses to let through.
 *
 * The cue and the coping plan being *required* is the deliberate friction in this app — see
 * docs/02-app-design.md, "the plan is the product". If these tests ever get relaxed, that is a
 * product decision, not a cleanup.
 */
private fun reminderWith(mask: Int) = Reminder(
    habitId = "habit",
    time = LocalTime.NOON,
    daysMask = mask,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

class HabitDraftTest {

    private val anchor = LocalDate.of(2026, 1, 5)

    @Test
    fun `a habit needs a name`() {
        assertFalse(HabitDraft().canLeave(CreationStep.WHAT))
        assertFalse(HabitDraft(name = "   ").canLeave(CreationStep.WHAT))
        assertTrue(HabitDraft(name = "Morning run").canLeave(CreationStep.WHAT))
    }

    @Test
    fun `the plan is required, and it is the only thing that is`() {
        val named = HabitDraft(name = "Morning run")

        assertFalse(named.canLeave(CreationStep.CUE))
        assertFalse(named.canLeave(CreationStep.COPING))

        // The genuinely optional steps never block, even completely untouched.
        assertTrue(named.canLeave(CreationStep.SMALLEST))
        assertTrue(named.canLeave(CreationStep.REMINDER))

        val planned = named.copy(cue = "it's 7am", copingPlan = "after dinner")
        assertTrue(planned.canLeave(CreationStep.CUE))
        assertTrue(planned.canLeave(CreationStep.COPING))
    }

    @Test
    fun `a measurable habit needs a target above zero`() {
        val counted = HabitDraft(name = "Read", type = HabitType.DO_NUMERIC)
        assertFalse(counted.canLeave(CreationStep.WHAT))
        assertFalse(counted.copy(target = "nonsense").canLeave(CreationStep.WHAT))
        assertFalse(counted.copy(target = "0").canLeave(CreationStep.WHAT))
        assertTrue(counted.copy(target = "20").canLeave(CreationStep.WHAT))
    }

    @Test
    fun `picking specific days without picking any is not a schedule`() {
        val draft = HabitDraft(name = "Gym", scheduleKind = ScheduleKind.SPECIFIC_DAYS)
        assertFalse(draft.canLeave(CreationStep.SCHEDULE))

        val withDays = draft.copy(specificDays = setOf(DayOfWeek.MONDAY))
        assertTrue(withDays.canLeave(CreationStep.SCHEDULE))
    }

    @Test
    fun `each schedule kind builds its rule`() {
        val base = HabitDraft(name = "Gym")

        assertEquals(ScheduleRule.Daily, base.scheduleRule(anchor))
        assertEquals(
            ScheduleRule.SpecificDays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            base.copy(
                scheduleKind = ScheduleKind.SPECIFIC_DAYS,
                specificDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            ).scheduleRule(anchor),
        )
        assertEquals(
            ScheduleRule.TimesPerWeek(4),
            base.copy(scheduleKind = ScheduleKind.TIMES_PER_WEEK, timesPerWeek = 4)
                .scheduleRule(anchor),
        )
        assertEquals(
            ScheduleRule.EveryNDays(3, anchor),
            base.copy(scheduleKind = ScheduleKind.EVERY_N_DAYS, everyNDays = 3)
                .scheduleRule(anchor),
        )
    }

    @Test
    fun `a reminder is confined to the days the habit is actually scheduled`() {
        val everyDay = HabitDraft(name = "Meditate")
        assertEquals(Reminder.ALL_DAYS, everyDay.reminderDaysMask())

        val mwf = everyDay.copy(
            scheduleKind = ScheduleKind.SPECIFIC_DAYS,
            specificDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )
        val reminder = reminderWith(mwf.reminderDaysMask())
        assertTrue(reminder.firesOn(DayOfWeek.MONDAY))
        assertFalse(reminder.firesOn(DayOfWeek.TUESDAY))
    }

    @Test
    fun `a stored habit reads back as the answers that made it`() {
        val stored = Habit(
            name = "Read",
            type = HabitType.DO_NUMERIC,
            schedule = ScheduleRule.SpecificDays(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)),
            identityPhrase = "reads every evening",
            minimumVersion = "one page",
            cue = "I'm in bed",
            copingPlan = "I'll read at lunch",
            unit = "pages",
            target = 20.0,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

        val draft = HabitDraft.of(stored, reminder = null)

        assertEquals("Read", draft.name)
        assertEquals(HabitType.DO_NUMERIC, draft.type)
        assertEquals(ScheduleKind.SPECIFIC_DAYS, draft.scheduleKind)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), draft.specificDays)
        assertEquals("reads every evening", draft.identityPhrase)
        assertEquals("one page", draft.minimumVersion)
        assertEquals("pages", draft.unit)
        assertTrue(draft.isComplete)
        assertFalse(draft.reminderEnabled)
    }

    @Test
    fun `a target of twenty goes back in the box as twenty`() {
        // Stored as a Double, but "20.0" is not what anybody typed, and it is what they would
        // have to delete before typing 25.
        val draft = HabitDraft.of(measurable(target = 20.0), reminder = null)
        assertEquals("20", draft.target)

        // A genuinely fractional target keeps its fraction.
        assertEquals("2.5", HabitDraft.of(measurable(target = 2.5), reminder = null).target)
    }

    @Test
    fun `a switched-off reminder still offers the time it was set to`() {
        val stored = Habit(
            name = "Read",
            type = HabitType.DO_BOOL,
            schedule = ScheduleRule.Daily,
            cue = "I'm in bed",
            copingPlan = "I'll read at lunch",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val off = Reminder(
            habitId = stored.id,
            time = LocalTime.of(21, 30),
            enabled = false,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

        val draft = HabitDraft.of(stored, off)

        assertFalse(draft.reminderEnabled)
        assertEquals(LocalTime.of(21, 30), draft.reminderTime)
    }

    private fun measurable(target: Double) = Habit(
        name = "Read",
        type = HabitType.DO_NUMERIC,
        schedule = ScheduleRule.Daily,
        cue = "I'm in bed",
        copingPlan = "I'll read at lunch",
        unit = "pages",
        target = target,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
