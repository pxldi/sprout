/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import app.cash.turbine.test
import dev.sprout.core.datastore.ShineHistory
import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.HabitType
import dev.sprout.core.model.ScheduleRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TodayViewModelTest {

    private lateinit var stack: TestStack

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        stack = TestStack()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        stack.close()
    }

    private fun viewModel() =
        TodayViewModel(stack.habits, stack.entries, stack.reminders, stack.shine, stack.clock)

    @Test
    fun `an account with no habits reports empty rather than loading forever`() = runTest {
        viewModel().uiState.test {
            assertTrue(awaitLoaded().isFirstRun)
        }
    }

    @Test
    fun `only habits scheduled today appear`() = runTest {
        // TEST_TODAY is a Monday.
        stack.addHabit(name = "Run", schedule = ScheduleRule.Daily)
        stack.addHabit(name = "Long ride", schedule = ScheduleRule.SpecificDays(setOf(DayOfWeek.SATURDAY)))

        viewModel().uiState.test {
            val state = awaitLoaded()
            assertEquals(listOf("Run"), state.items.map { it.habit.name })
        }
    }

    @Test
    fun `a weekly habit is available every day of its week`() = runTest {
        stack.addHabit(name = "Gym", schedule = ScheduleRule.TimesPerWeek(times = 3))
        viewModel().uiState.test {
            assertEquals(listOf("Gym"), awaitLoaded().items.map { it.habit.name })
        }
    }

    @Test
    fun `completing a habit marks it done and counts toward the day`() = runTest {
        val habit = stack.addHabit(name = "Meditate")
        val vm = viewModel()

        vm.uiState.test {
            assertTrue(!awaitLoaded().items.single().isDone)
            vm.complete(habit.id)
            val after = awaitItem()
            assertTrue(after.items.single().isDone)
            assertEquals(1, after.doneCount)
        }
    }

    @Test
    fun `toggling twice returns the day to unlogged, not to a failure state`() = runTest {
        val habit = stack.addHabit()
        val vm = viewModel()

        vm.toggle(habit.id)
        vm.toggle(habit.id)
        // The stack runs queries on this thread, so both writes have already landed.
        assertNull(stack.entries.find(habit.id, TEST_TODAY), "an undone day is blank, never MISSED")

        vm.uiState.test {
            val item = awaitLoaded().items.single()
            assertTrue(!item.isDone)
        }
    }

    @Test
    fun `the smallest version counts as done`() = runTest {
        val habit = stack.addHabit()
        val vm = viewModel()
        vm.completeMinimum(habit.id)

        vm.uiState.test {
            val item = awaitLoaded().items.single()
            assertTrue(item.isDone)
            assertEquals(EntryStatus.DONE_MIN, item.todayStatus)
        }
    }

    @Test
    fun `a skipped habit is neither done nor blamed`() = runTest {
        val habit = stack.addHabit()
        val vm = viewModel()
        vm.skip(habit.id)

        vm.uiState.test {
            val item = awaitLoaded().items.single()
            assertTrue(!item.isDone)
            assertTrue(item.isSkipped)
            assertNull(item.gentleNote)
        }
    }

    @Test
    fun `a paused run is offered back rather than mourned`() = runTest {
        val habit = stack.addHabit()
        // Three days logged, then nothing yesterday — the run is held, pending repair.
        stack.logDaysAgo(habit.id, 4, 3, 2)

        viewModel().uiState.test {
            assertEquals(GentleNote.REPAIRABLE, awaitLoaded().items.single().gentleNote)
        }
    }

    @Test
    fun `with no run to repair, the note is the plain one about yesterday`() = runTest {
        val habit = stack.addHabit()
        // One day logged, then a gap wide enough that the earlier run is long gone. Yesterday is
        // still repairable, but there is nothing left to repair.
        stack.logDaysAgo(habit.id, 5)

        viewModel().uiState.test {
            assertEquals(GentleNote.MISSED_YESTERDAY, awaitLoaded().items.single().gentleNote)
        }
    }

    @Test
    fun `coming back today outranks every other thing the screen could say`() = runTest {
        val habit = stack.addHabit()
        stack.logDaysAgo(habit.id, 4, 3, 2) // then a missed yesterday
        val vm = viewModel()
        vm.complete(habit.id)

        vm.uiState.test {
            val item = awaitLoaded().items.single()
            assertTrue(item.isDone)
            assertEquals(GentleNote.BOUNCED_BACK, item.gentleNote)
        }
    }

    @Test
    fun `rows are ordered by reminder time, and unscheduled ones sink to the bottom`() = runTest {
        val evening = stack.addHabit(name = "Read", position = 0)
        val morning = stack.addHabit(name = "Run", position = 1)
        stack.addHabit(name = "Water", position = 2) // no reminder — sinks to the bottom

        stack.addReminder(evening.id, LocalTime.of(21, 0))
        stack.addReminder(morning.id, LocalTime.of(7, 0))

        viewModel().uiState.test {
            assertEquals(listOf("Run", "Read", "Water"), awaitLoaded().items.map { it.habit.name })
        }
    }

    @Test
    fun `an avoid habit appears on today like any other`() = runTest {
        stack.addHabit(name = "Alcohol-free day", type = HabitType.AVOID)
        viewModel().uiState.test {
            assertEquals(1, awaitLoaded().items.size)
        }
    }

    @Test
    fun `a habit that exists but is not due today is not a first run`() = runTest {
        // TEST_TODAY is a Monday; this habit only falls on Tuesdays.
        stack.addHabit(schedule = ScheduleRule.SpecificDays(setOf(DayOfWeek.TUESDAY)))
        viewModel().uiState.test {
            val state = awaitLoaded()
            assertTrue(state.items.isEmpty())
            // The distinction that matters: creating a Mon/Wed/Fri habit on a Saturday must not
            // send the user back to "add the first habit", which reads as a failed save.
            assertFalse(state.isFirstRun)
            assertTrue(state.nothingScheduled)
        }
    }

    @Test
    fun `no habits at all is a first run`() = runTest {
        viewModel().uiState.test {
            val state = awaitLoaded()
            assertTrue(state.isFirstRun)
            assertFalse(state.nothingScheduled)
        }
    }

    /**
     * Whether it is worth telling the user that notifications are off.
     *
     * Only the presence of a reminder makes that worth saying. Someone who never asked to be
     * reminded is not missing anything, and a warning about a feature they declined is noise.
     */
    @Test
    fun `a habit with no reminder is not something to warn about`() = runTest {
        stack.addHabit(name = "Run")

        viewModel().uiState.test {
            assertFalse(awaitLoaded().hasReminders)
        }
    }

    @Test
    fun `a live habit with a reminder is`() = runTest {
        val habit = stack.addHabit(name = "Run")
        stack.addReminder(habit.id, LocalTime.of(7, 0))

        viewModel().uiState.test {
            assertTrue(awaitLoaded().hasReminders)
        }
    }

    @Test
    fun `a note is offered only once the day has been logged`() = runTest {
        val habit = stack.addHabit(name = "Run")
        val model = viewModel()

        model.uiState.test {
            assertFalse(awaitLoaded().items.single().canNote, "nothing to hang a note on yet")

            model.complete(habit.id)
            assertTrue(awaitUntilItem { it.items.single().canNote }.canNote)
        }
    }

    @Test
    fun `writing a note leaves the day exactly as it was logged`() = runTest {
        val habit = stack.addHabit(name = "Run")
        val model = viewModel()
        model.skip(habit.id)

        model.note(habit.id, "away for work")

        model.uiState.test {
            val item = awaitUntilItem { it.items.single().todayNote != null }
            assertEquals("away for work", item.todayNote)
            // The sentence describes the day; it does not decide what the day was.
            assertEquals(EntryStatus.SKIP, item.todayStatus)
            assertTrue(item.isSkipped)
        }
    }

    @Test
    fun `a note on an untouched day changes nothing`() = runTest {
        val habit = stack.addHabit(name = "Run")
        val model = viewModel()

        model.note(habit.id, "meant to, did not")

        model.uiState.test {
            val item = awaitLoaded().items.single()
            assertNull(item.todayStatus, "a note must not log the day")
            assertNull(item.todayNote)
            // And so the one line Today is allowed to say about a miss still gets to say it.
            assertFalse(item.isDone)
        }
    }

    @Test
    fun `ticking a habit off says one specific true thing about it`() = runTest {
        val habit = stack.addHabit(name = "Run")
        val model = viewModel()

        model.complete(habit.id)

        model.uiState.test {
            val item = awaitUntilItem { it.items.single().shine != null }
            assertEquals(ShineLine.FirstEver, item.shine)
        }
    }

    @Test
    fun `coming back after a miss outranks any praise for the streak`() = runTest {
        // The most rewarded state in the app. Two celebratory lines under one row would be two
        // things said where the rule is one, and this is the one that must survive.
        val habit = stack.addHabit(name = "Run")
        stack.logDaysAgo(habit.id, 4, 3, 2)
        val model = viewModel()

        model.complete(habit.id)

        model.uiState.test {
            val item = awaitUntilItem { it.items.single().gentleNote != null }
            assertEquals(GentleNote.BOUNCED_BACK, item.gentleNote)
            assertNull(item.shine, "nothing else gets to speak over coming back")
        }
    }

    @Test
    fun `a day that was not completed gets no praise`() = runTest {
        val habit = stack.addHabit(name = "Run")
        val model = viewModel()

        model.skip(habit.id)

        model.uiState.test {
            val item = awaitUntilItem { it.items.single().isSkipped }
            assertNull(item.shine, "a skip is a decision, not an achievement")
        }
    }

    @Test
    fun `the seventh completion gets a card, and the card does the talking`() = runTest {
        val habit = stack.addHabit(name = "Run")
        stack.logDaysAgo(habit.id, 6, 5, 4, 3, 2, 1)
        val model = viewModel()

        model.complete(habit.id)

        model.uiState.test {
            val item = awaitUntilItem { it.items.single().milestone != null }
            assertEquals(Milestone.SEVEN, item.milestone)
            assertNull(item.shine, "the card is already the whole sentence and then some")
        }
    }

    @Test
    fun `a comeback that is also the seventh completion gets both`() = runTest {
        // The one place two things are said under one row. A miss inside the first week is the
        // common case, not an edge one, and holding the card back would not postpone it — by
        // tomorrow the count is eight and there is no seventh completion left to celebrate.
        val habit = stack.addHabit(name = "Run")
        stack.logDaysAgo(habit.id, 7, 6, 5, 4, 3, 2)
        val model = viewModel()

        model.complete(habit.id)

        model.uiState.test {
            val item = awaitUntilItem { it.items.single().milestone != null }
            assertEquals(GentleNote.BOUNCED_BACK, item.gentleNote, "test premise: yesterday was missed")
            assertEquals(Milestone.SEVEN, item.milestone)
            assertNull(item.shine, "the line still waits its turn")
        }
    }

    @Test
    fun `an ordinary completion gets no card`() = runTest {
        val habit = stack.addHabit(name = "Run")
        val model = viewModel()

        model.complete(habit.id)

        model.uiState.test {
            val item = awaitUntilItem { it.items.single().isDone }
            assertNull(item.milestone, "the first one is not the seventh")
        }
    }

    @Test
    fun `what was said is written down, so it is not said again tomorrow`() = runTest {
        val habit = stack.addHabit(name = "Run")
        val model = viewModel()

        model.complete(habit.id)

        model.uiState.test {
            awaitUntilItem { it.items.single().shine != null }
        }
        // The write runs on DataStore's own dispatcher after the line appears, so wait for it.
        // Reading the first value raced it and failed on CI.
        val key = ShineHistory.keyOf(habit.id, ShineLine.FirstEver.kind)
        assertEquals(TEST_TODAY, stack.shine.shown.first { key in it }[key])
    }

    @Test
    fun `an archived habit's leftover reminder is not`() = runTest {
        // Nothing was going to fire for it, so warning about it would be warning about nothing.
        val habit = stack.addHabit(name = "Run")
        stack.addReminder(habit.id, LocalTime.of(7, 0))
        stack.archive(habit.id)

        viewModel().uiState.test {
            assertFalse(awaitLoaded().hasReminders)
        }
    }

    // Yesterday, until noon. TEST_TODAY is a Monday, so yesterday is a Sunday.

    @Test
    fun `a habit left unlogged yesterday is offered before noon`() = runTest {
        stack.addHabit(name = "Run", createdDaysAgo = 7)

        viewModel().uiState.test {
            val row = awaitLoaded().yesterday.single()
            assertEquals("Run", row.habit.name)
            assertEquals(TEST_TODAY.minusDays(1), row.date)
            assertNull(row.status)
        }
    }

    @Test
    fun `yesterday is not offered from noon on`() = runTest {
        stack.close()
        stack = TestStack(time = LocalTime.NOON)
        stack.addHabit(createdDaysAgo = 7)

        viewModel().uiState.test {
            assertTrue(awaitLoaded().yesterday.isEmpty())
        }
    }

    @Test
    fun `a habit ticked on time yesterday is not offered again`() = runTest {
        val habit = stack.addHabit(createdDaysAgo = 7)
        stack.logOnTheDay(habit.id, daysAgo = 1)

        viewModel().uiState.test {
            assertTrue(awaitLoaded().yesterday.isEmpty())
        }
    }

    @Test
    fun `a habit created today is not offered for yesterday`() = runTest {
        stack.addHabit()

        viewModel().uiState.test {
            assertTrue(awaitLoaded().yesterday.isEmpty())
        }
    }

    @Test
    fun `a habit not due yesterday is not offered`() = runTest {
        stack.addHabit(
            schedule = ScheduleRule.SpecificDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
            createdDaysAgo = 7,
        )

        viewModel().uiState.test {
            assertTrue(awaitLoaded().yesterday.isEmpty())
        }
    }

    /** A 3x/week habit owes the week. An unticked Sunday is not a day it left open. */
    @Test
    fun `a weekly habit is not offered for yesterday`() = runTest {
        stack.addHabit(schedule = ScheduleRule.TimesPerWeek(times = 3), createdDaysAgo = 7)

        viewModel().uiState.test {
            assertTrue(awaitLoaded().yesterday.isEmpty())
        }
    }

    @Test
    fun `ticking yesterday logs yesterday and keeps the row so it can be undone`() = runTest {
        val habit = stack.addHabit(createdDaysAgo = 7)
        val vm = viewModel()

        vm.uiState.test {
            awaitLoaded()
            vm.setDay(habit.id, TEST_TODAY.minusDays(1), EntryStatus.DONE)

            val row = awaitUntil { it.yesterday.single().status != null }.yesterday.single()
            assertEquals(EntryStatus.DONE, row.status)
            assertEquals(EntryStatus.DONE, stack.entries.find(habit.id, TEST_TODAY.minusDays(1))?.status)
            assertNull(stack.entries.find(habit.id, TEST_TODAY), "today is untouched")
        }
    }

    @Test
    fun `unticking yesterday leaves it unlogged`() = runTest {
        val habit = stack.addHabit(createdDaysAgo = 7)
        val vm = viewModel()

        vm.setDay(habit.id, TEST_TODAY.minusDays(1), EntryStatus.DONE)
        vm.setDay(habit.id, TEST_TODAY.minusDays(1), status = null)

        assertNull(stack.entries.find(habit.id, TEST_TODAY.minusDays(1)))
        vm.uiState.test {
            assertNull(awaitLoaded().yesterday.single().status)
        }
    }

    @Test
    fun `ticking yesterday takes back today's note about missing it`() = runTest {
        val habit = stack.addHabit(createdDaysAgo = 7)
        stack.logOnTheDay(habit.id, daysAgo = 2)
        val vm = viewModel()

        vm.uiState.test {
            assertNotNull(awaitLoaded().items.single().gentleNote)
            vm.setDay(habit.id, TEST_TODAY.minusDays(1), EntryStatus.DONE)

            assertNull(awaitUntil { it.yesterday.single().isDone }.items.single().gentleNote)
        }
    }

    @Test
    fun `today cannot be set as a past day`() = runTest {
        val habit = stack.addHabit(createdDaysAgo = 7)

        viewModel().setDay(habit.id, TEST_TODAY, EntryStatus.SKIP)

        assertNull(stack.entries.find(habit.id, TEST_TODAY))
    }
}

private suspend fun app.cash.turbine.TurbineTestContext<TodayUiState>.awaitLoaded(): TodayUiState {
    var state = awaitItem()
    while (state.isLoading) state = awaitItem()
    return state
}

/** Waits for the write that is already in flight to land, rather than guessing at emissions. */
private suspend fun app.cash.turbine.TurbineTestContext<TodayUiState>.awaitUntilItem(
    predicate: (TodayUiState) -> Boolean,
): TodayItem = awaitUntil(predicate).items.single()

private suspend fun app.cash.turbine.TurbineTestContext<TodayUiState>.awaitUntil(
    predicate: (TodayUiState) -> Boolean,
): TodayUiState {
    var state = awaitLoaded()
    while (!predicate(state)) state = awaitItem()
    return state
}

