/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.test.core.app.ApplicationProvider
import dev.sprout.core.database.inMemoryRepositories
import dev.sprout.core.model.HabitType
import dev.sprout.core.model.ScheduleRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val TODAY: LocalDate = LocalDate.of(2026, 1, 5)
private val NOW: Instant = TODAY.atTime(9, 0).toInstant(ZoneOffset.UTC)

/** A real database behind the view model. The write is the thing worth testing. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HabitCreationViewModelTest {

    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val repositories =
        inMemoryRepositories(ApplicationProvider.getApplicationContext(), clock)
    private val viewModel =
        HabitCreationViewModel(repositories.habits, repositories.reminders, clock)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        repositories.close()
    }

    @Test
    fun `next refuses to advance past an unanswered question`() {
        viewModel.next()
        assertEquals(CreationStep.WHAT, viewModel.uiState.value.step)

        viewModel.edit { it.copy(name = "Morning run") }
        viewModel.next()
        assertEquals(CreationStep.SMALLEST, viewModel.uiState.value.step)
    }

    @Test
    fun `back reports when there is nowhere left to go, so the caller can leave`() {
        assertFalse(viewModel.back())

        fillIn()
        viewModel.next()
        assertTrue(viewModel.back())
        assertEquals(CreationStep.WHAT, viewModel.uiState.value.step)
    }

    @Test
    fun `a complete draft is written and shows up as an active habit`() = runTest {
        fillIn()
        viewModel.save()

        val saved = viewModel.uiState.value.savedHabitId
        assertNotNull(saved)

        val habit = repositories.habits.observeActive().first().single()
        assertEquals("Morning run", habit.name)
        assertEquals(HabitType.DO_BOOL, habit.type)
        assertEquals(ScheduleRule.SpecificDays(setOf(java.time.DayOfWeek.MONDAY)), habit.schedule)
        assertEquals("it's 7am and I've had my coffee", habit.cue)
        assertEquals("I'll do it after dinner", habit.copingPlan)
        assertEquals("Put my shoes on", habit.minimumVersion)
        assertEquals(NOW, habit.createdAt)
    }

    @Test
    fun `blank optional answers are stored as absent, not as empty strings`() = runTest {
        fillIn()
        viewModel.edit { it.copy(minimumVersion = "   ", identityPhrase = "") }
        viewModel.save()

        val habit = repositories.habits.observeActive().first().single()
        assertNull(habit.minimumVersion)
        assertNull(habit.identityPhrase)
    }

    @Test
    fun `a reminder is only written when one was asked for`() = runTest {
        fillIn()
        viewModel.save()
        assertTrue(repositories.reminders.allEnabled().isEmpty())
    }

    @Test
    fun `an asked-for reminder is written against the new habit`() = runTest {
        fillIn()
        viewModel.edit { it.copy(reminderEnabled = true, reminderTime = LocalTime.of(6, 30)) }
        viewModel.save()

        val habit = repositories.habits.observeActive().first().single()
        val reminder = repositories.reminders.allEnabled().single()
        assertEquals(habit.id, reminder.habitId)
        assertEquals(LocalTime.of(6, 30), reminder.time)
    }

    @Test
    fun `an incomplete draft is never written, however the save is reached`() = runTest {
        viewModel.edit { it.copy(name = "Morning run") } // no cue, no coping plan
        viewModel.save()

        assertNull(viewModel.uiState.value.savedHabitId)
        assertTrue(repositories.habits.observeActive().first().isEmpty())
    }

    @Test
    fun `a second save is ignored, so a double tap cannot create two habits`() = runTest {
        fillIn()
        viewModel.save()
        viewModel.save()

        assertEquals(1, repositories.habits.observeActive().first().size)
    }

    @Test
    fun `each new habit lands at the end of the list rather than all at zero`() = runTest {
        fillIn()
        viewModel.save()

        val second = HabitCreationViewModel(repositories.habits, repositories.reminders, clock)
        second.edit {
            it.copy(
                name = "Meditate",
                cue = "I sit down",
                copingPlan = "before bed",
            )
        }
        second.save()

        val positions = repositories.habits.observeActive().first().map { it.position }
        assertEquals(listOf(0, 1), positions.sorted())
    }

    /** The shortest draft the flow will accept, plus one optional answer. */
    private fun fillIn() {
        viewModel.edit {
            it.copy(
                name = "Morning run",
                minimumVersion = "Put my shoes on",
                cue = "it's 7am and I've had my coffee",
                copingPlan = "I'll do it after dinner",
                scheduleKind = ScheduleKind.SPECIFIC_DAYS,
                specificDays = setOf(java.time.DayOfWeek.MONDAY),
            )
        }
    }
}
