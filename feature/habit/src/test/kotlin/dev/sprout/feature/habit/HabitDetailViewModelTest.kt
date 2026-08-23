/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.sprout.core.database.inMemoryRepositories
import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.Habit
import dev.sprout.core.model.HabitType
import dev.sprout.core.model.ScheduleRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val TODAY: LocalDate = LocalDate.of(2026, 3, 10)
private val NOW: Instant = TODAY.atTime(9, 0).toInstant(ZoneOffset.UTC)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HabitDetailViewModelTest {

    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val repositories =
        inMemoryRepositories(ApplicationProvider.getApplicationContext(), clock)

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
    fun `a habit arrives with its history worked out`() = runTest {
        val habit = save(habit())
        log(habit.id, daysAgo = 1)
        log(habit.id, daysAgo = 2)

        viewModel(habit.id).uiState.test {
            val detail = assertNotNull(awaitLoaded().detail)
            assertEquals("Morning run", detail.habit.name)
            assertEquals(2, detail.progress.currentRun)
            assertEquals(2, detail.days.count { it.mark == DayMark.DONE })
            assertTrue(detail.hasLog)
        }
    }

    @Test
    fun `a day logged while the screen is open shows up on it`() = runTest {
        val habit = save(habit())

        viewModel(habit.id).uiState.test {
            assertEquals(0, assertNotNull(awaitLoaded().detail).progress.currentRun)

            // A notification action, or the widget, or Today behind this screen. The screen is
            // observing, so this arrives rather than waiting for the user to back out and return.
            log(habit.id, daysAgo = 0)

            val after = assertNotNull(awaitItem().detail)
            assertEquals(1, after.progress.currentRun)
            assertEquals(DayMark.DONE, after.days.single { it.date == TODAY }.mark)
        }
    }

    @Test
    fun `a habit that is not there finishes instead of showing an empty screen`() = runTest {
        viewModel("no-such-habit").uiState.test {
            val state = awaitLoaded()
            assertTrue(state.finished)
            assertNull(state.detail)
        }
    }

    @Test
    fun `deleting the habit takes the screen with it`() = runTest {
        val habit = save(habit())

        viewModel(habit.id).uiState.test {
            assertNotNull(awaitLoaded().detail)

            repositories.habits.delete(habit.id)

            val gone = awaitItem()
            assertTrue(gone.finished, "the habit is gone; there is nothing left to show")
            assertNull(gone.detail)
        }
    }

    @Test
    fun `an archived habit still opens - its history is the reason it was kept`() = runTest {
        val habit = save(habit())
        log(habit.id, daysAgo = 1)
        repositories.habits.archive(habit.id)

        viewModel(habit.id).uiState.test {
            val detail = assertNotNull(awaitLoaded().detail)
            assertTrue(detail.habit.isArchived)
            assertEquals(1, detail.days.count { it.mark == DayMark.DONE })
        }
    }

    private suspend fun ReceiveTurbine<HabitDetailUiState>.awaitLoaded(): HabitDetailUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    private fun viewModel(habitId: String) = HabitDetailViewModel(
        habits = repositories.habits,
        entries = repositories.entries,
        clock = clock,
        savedState = SavedStateHandle(mapOf(HABIT_ID_ARG to habitId)),
    )

    private fun habit() = Habit(
        name = "Morning run",
        type = HabitType.DO_BOOL,
        schedule = ScheduleRule.Daily,
        cue = "it's 7am",
        copingPlan = "I'll do it after dinner",
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun save(habit: Habit) = runBlocking { repositories.habits.save(habit) }

    private fun log(habitId: String, daysAgo: Int) = runBlocking {
        repositories.entries.log(habitId, TODAY.minusDays(daysAgo.toLong()), EntryStatus.DONE)
    }
}
