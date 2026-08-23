/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import dev.sprout.core.database.inMemoryRepositories
import dev.sprout.core.datastore.ShineHistory
import dev.sprout.core.datastore.temporaryShineHistory
import dev.sprout.core.model.Habit
import dev.sprout.core.model.HabitType
import dev.sprout.core.model.Reminder
import dev.sprout.core.model.ScheduleRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val TODAY: LocalDate = LocalDate.of(2026, 3, 10)
private val NOW: Instant = TODAY.atTime(9, 0).toInstant(ZoneOffset.UTC)
private val LONG_AGO: Instant = Instant.parse("2025-11-01T06:00:00Z")

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HabitEditViewModelTest {

    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val repositories =
        inMemoryRepositories(ApplicationProvider.getApplicationContext(), clock)

    /** A fresh file per test, so one test's remembered praise is not the next one's. */
    private val shine = temporaryShineHistory(
        File.createTempFile("shine", ".preferences_pb").also { it.delete() },
    )

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
    fun `an edit keeps everything the six questions never asked about`() = runTest {
        // The fields most at risk: nothing on the edit screen can set any of them, so a save
        // that rebuilt the habit from the draft would quietly drop all of them on a rename.
        val stored = save(
            habit(name = "Morning run").copy(
                anchorHabitId = "brush-teeth",
                bundleText = "only listen to the podcast while running",
                colorArgb = 0x00FF7043,
                icon = "run",
                position = 4,
                createdAt = LONG_AGO,
            ),
        )
        val viewModel = editing(stored.id)

        viewModel.edit { it.copy(name = "Evening run") }
        viewModel.save()

        val after = assertNotNull(repositories.habits.find(stored.id))
        assertEquals("Evening run", after.name)
        assertEquals(stored.id, after.id)
        assertEquals("brush-teeth", after.anchorHabitId)
        assertEquals("only listen to the podcast while running", after.bundleText)
        assertEquals(0x00FF7043, after.colorArgb)
        assertEquals("run", after.icon)
        assertEquals(4, after.position)
        assertEquals(LONG_AGO, after.createdAt)
    }

    @Test
    fun `renaming an every-N-days habit does not move which days it falls on`() = runTest {
        val anchor = LocalDate.of(2026, 1, 1)
        val stored = save(habit(schedule = ScheduleRule.EveryNDays(3, anchor)))
        val viewModel = editing(stored.id)

        viewModel.edit { it.copy(name = "Renamed") }
        viewModel.save()

        val after = assertNotNull(repositories.habits.find(stored.id))
        assertEquals(ScheduleRule.EveryNDays(3, anchor), after.schedule)
    }

    @Test
    fun `switching to every-N-days is a new decision, and counts from today`() = runTest {
        val stored = save(habit(schedule = ScheduleRule.Daily))
        val viewModel = editing(stored.id)

        viewModel.edit { it.copy(scheduleKind = ScheduleKind.EVERY_N_DAYS, everyNDays = 3) }
        viewModel.save()

        val after = assertNotNull(repositories.habits.find(stored.id))
        assertEquals(ScheduleRule.EveryNDays(3, TODAY), after.schedule)
    }

    @Test
    fun `moving the reminder edits the row it already had`() = runTest {
        val stored = save(habit())
        val before = saveReminder(stored.id, LocalTime.of(7, 0))
        val viewModel = editing(stored.id)

        viewModel.edit { it.copy(reminderTime = LocalTime.of(7, 30)) }
        viewModel.save()

        val after = repositories.reminders.observeForHabit(stored.id).first()
        assertEquals(1, after.size, "a second row would mean the first was abandoned")
        assertEquals(before.id, after.single().id)
        assertEquals(LocalTime.of(7, 30), after.single().time)
        assertEquals(0, after.single().leadMinutes.compareTo(before.leadMinutes))
    }

    @Test
    fun `switching the reminder off keeps the time it was set to`() = runTest {
        val stored = save(habit())
        saveReminder(stored.id, LocalTime.of(7, 0))
        val viewModel = editing(stored.id)

        viewModel.edit { it.copy(reminderEnabled = false) }
        viewModel.save()

        val row = repositories.reminders.observeForHabit(stored.id).first().single()
        assertFalse(row.enabled)
        assertNull(row.deletedAt, "disabled, not tombstoned")
        assertEquals(LocalTime.of(7, 0), row.time)
        assertTrue(repositories.reminders.allEnabled().isEmpty())
    }

    @Test
    fun `switching it back on re-enables the same row, at the time it remembered`() = runTest {
        val stored = save(habit())
        val before = saveReminder(stored.id, LocalTime.of(7, 0), enabled = false)
        val viewModel = editing(stored.id)

        assertFalse(viewModel.uiState.value.draft.reminderEnabled)
        assertEquals(LocalTime.of(7, 0), viewModel.uiState.value.draft.reminderTime)

        viewModel.edit { it.copy(reminderEnabled = true) }
        viewModel.save()

        val row = repositories.reminders.observeForHabit(stored.id).first().single()
        assertEquals(before.id, row.id)
        assertTrue(row.enabled)
    }

    @Test
    fun `a habit that never had a reminder gets one`() = runTest {
        val stored = save(habit(schedule = ScheduleRule.SpecificDays(setOf(DayOfWeek.MONDAY))))
        val viewModel = editing(stored.id)

        viewModel.edit { it.copy(reminderEnabled = true, reminderTime = LocalTime.of(18, 0)) }
        viewModel.save()

        val row = repositories.reminders.observeForHabit(stored.id).first().single()
        assertEquals(LocalTime.of(18, 0), row.time)
        // Follows the schedule, so it cannot nag about a day the habit is not due.
        assertEquals(Reminder.maskOf(setOf(DayOfWeek.MONDAY)), row.daysMask)
    }

    @Test
    fun `emptying a required answer blocks the save rather than storing half a plan`() = runTest {
        val stored = save(habit())
        val viewModel = editing(stored.id)

        viewModel.edit { it.copy(copingPlan = "  ") }
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.save()
        assertFalse(viewModel.uiState.value.finished)
        assertEquals("I'll do it after dinner", repositories.habits.find(stored.id)?.copingPlan)
    }

    @Test
    fun `nothing counts as a change until something changes`() = runTest {
        val viewModel = editing(save(habit()).id)
        assertFalse(viewModel.uiState.value.isDirty)

        viewModel.edit { it.copy(name = "Something else") }
        assertTrue(viewModel.uiState.value.isDirty)

        // Undoing it by hand is not a change either — otherwise leaving would ask about nothing.
        viewModel.edit { it.copy(name = "Morning run") }
        assertFalse(viewModel.uiState.value.isDirty)
    }

    @Test
    fun `archiving takes it off Today and silences its reminder, without losing either`() =
        runTest {
            val stored = save(habit())
            saveReminder(stored.id, LocalTime.of(7, 0))
            val viewModel = editing(stored.id)

            viewModel.archive()

            assertTrue(viewModel.uiState.value.finished)
            assertTrue(repositories.habits.observeActive().first().isEmpty())
            assertTrue(assertNotNull(repositories.habits.find(stored.id)).isArchived)
            // The row survives untouched: unarchiving has to bring the reminder back as it was.
            assertTrue(repositories.reminders.observeForHabit(stored.id).first().single().enabled)
        }

    @Test
    fun `an archived habit opens with the way back on it`() = runTest {
        val stored = save(habit())
        repositories.habits.archive(stored.id)
        val viewModel = editing(stored.id)

        assertTrue(viewModel.uiState.value.isArchived)

        viewModel.unarchive()
        assertFalse(assertNotNull(repositories.habits.find(stored.id)).isArchived)
    }

    @Test
    fun `deleting tombstones it`() = runTest {
        val stored = save(habit())
        val viewModel = editing(stored.id)

        viewModel.delete()

        // Deleting now also clears the app's memory of what it said about this habit, and that
        // write lands on DataStore's own dispatcher — so wait for the screen to say it is done
        // rather than for the tap to return.
        viewModel.uiState.first { it.finished }
        assertNull(repositories.habits.find(stored.id))
    }

    @Test
    fun `deleting forgets what the app said about it, but archiving does not`() = runTest {
        val stored = save(habit())
        val key = ShineHistory.keyOf(stored.id, "first")
        shine.record(stored.id, "first", TODAY)

        editing(stored.id).archive()
        assertEquals(
            TODAY,
            shine.shown.first()[key],
            "an unarchived habit congratulated again on its first completion has not earned it",
        )

        editing(stored.id).delete()
        assertNull(shine.shown.first { it[key] == null }[key])
    }

    @Test
    fun `a habit that is already gone leaves instead of showing an empty form`() = runTest {
        val viewModel = editing("no-such-habit")

        assertTrue(viewModel.uiState.value.finished)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private fun editing(habitId: String) = HabitEditViewModel(
        habits = repositories.habits,
        reminders = repositories.reminders,
        shine = shine,
        clock = clock,
        savedState = SavedStateHandle(mapOf(HABIT_ID_ARG to habitId)),
    )

    private fun habit(
        name: String = "Morning run",
        schedule: ScheduleRule = ScheduleRule.Daily,
    ) = Habit(
        name = name,
        type = HabitType.DO_BOOL,
        schedule = schedule,
        cue = "it's 7am",
        copingPlan = "I'll do it after dinner",
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun save(habit: Habit) = runBlocking { repositories.habits.save(habit) }

    private fun saveReminder(
        habitId: String,
        time: LocalTime,
        enabled: Boolean = true,
    ) = runBlocking {
        repositories.reminders.save(
            Reminder(
                habitId = habitId,
                time = time,
                enabled = enabled,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
    }
}
