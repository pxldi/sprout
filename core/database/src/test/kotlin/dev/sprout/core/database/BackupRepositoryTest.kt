/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import dev.sprout.core.database.repository.RestoreCounts
import dev.sprout.core.model.Backup
import dev.sprout.core.model.BackupCodec
import dev.sprout.core.model.Entry
import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.LapseTrigger
import dev.sprout.core.model.Reminder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private val stacks = mutableListOf<SproutRepositories>()

    @After fun tearDown() = stacks.forEach { it.close() }

    private fun stack(clock: Clock = fixedClock()): SproutRepositories =
        inMemoryRepositories(ApplicationProvider.getApplicationContext(), clock).also { stacks += it }

    /** Every table, with an archived habit, a deleted one, a note and a cleared day. */
    private suspend fun SproutRepositories.fill(): Backup {
        val read = habits.save(habit(name = "Read"))
        val archived = habits.save(habit(name = "No alcohol"))
        val gone = habits.save(habit(name = "Stretch"))
        habits.archive(archived.id)
        habits.delete(gone.id)
        entries.log(read.id, TEST_START, EntryStatus.DONE)
        entries.note(read.id, TEST_START, "Finished the chapter.")
        entries.log(read.id, TEST_START.plusDays(1), EntryStatus.DONE_MIN)
        entries.log(read.id, TEST_START.plusDays(2), EntryStatus.SKIP)
        entries.clear(read.id, TEST_START.plusDays(2))
        reminders.save(
            Reminder(
                habitId = read.id,
                time = LocalTime.of(7, 30),
                leadMinutes = 30,
                createdAt = TEST_NOW,
                updatedAt = TEST_NOW,
            ),
        )
        lapses.record(archived.id, triggers = setOf(LapseTrigger.STRESS), note = "Work dinner.")
        return backup.snapshot()
    }

    @Test
    fun `a backup restored into an empty app gives back every row`() = runTest {
        val before = stack().fill()
        val file = BackupCodec.encode(before, TEST_NOW)

        val fresh = stack()
        fresh.backup.restore(BackupCodec.decode(file))

        assertEquals(before, fresh.backup.snapshot())
    }

    @Test
    fun `the snapshot includes tombstones and archived habits`() = runTest {
        val snapshot = stack().fill()
        assertEquals(3, snapshot.habits.size)
        assertEquals(3, snapshot.entries.size)
    }

    @Test
    fun `restore counts only habits and days the user can see`() = runTest {
        val counts = stack().backup.restore(stack().fill())
        assertEquals(2 to 2, counts.habits to counts.entries)
    }

    @Test
    fun `a file that only deletes a habit is not reported as nothing new`() = runTest {
        val phone = stack()
        val habit = phone.habits.save(habit(name = "Read"))
        val deleted = habit.copy(updatedAt = TEST_NOW.plusSeconds(60), deletedAt = TEST_NOW.plusSeconds(60))

        val counts = phone.backup.restore(Backup(listOf(deleted), emptyList(), emptyList(), emptyList()))

        assertEquals(RestoreCounts(habits = 0, entries = 0, rows = 1), counts)
    }

    @Test
    fun `importing the same file twice writes nothing the second time`() = runTest {
        val file = stack().fill()
        val target = stack()
        target.backup.restore(file)

        assertTrue(target.backup.restore(file).isEmpty)
    }

    @Test
    fun `an older copy does not undo a rename made since`() = runTest {
        val phone = stack(fixedClock(TEST_NOW.plusSeconds(3_600)))
        val old = stack().fill()
        phone.backup.restore(old)
        val read = old.habits.first { it.name == "Read" }
        phone.habits.save(read.copy(name = "Read fiction"))

        phone.backup.restore(old)

        assertEquals("Read fiction", phone.habits.find(read.id)?.name)
    }

    @Test
    fun `a newer copy updates the habit on the phone`() = runTest {
        val phone = stack()
        val habit = phone.habits.save(habit(name = "Read"))
        val renamed = habit.copy(name = "Read fiction", updatedAt = TEST_NOW.plusSeconds(60))

        phone.backup.restore(Backup(listOf(renamed), emptyList(), emptyList(), emptyList()))

        assertEquals("Read fiction", phone.habits.find(habit.id)?.name)
    }

    @Test
    fun `a deletion in the file wins over an older live habit`() = runTest {
        val phone = stack()
        val habit = phone.habits.save(habit(name = "Read"))
        val deleted = habit.copy(updatedAt = TEST_NOW.plusSeconds(60), deletedAt = TEST_NOW.plusSeconds(60))

        phone.backup.restore(Backup(listOf(deleted), emptyList(), emptyList(), emptyList()))

        assertNull(phone.habits.find(habit.id))
    }

    @Test
    fun `a day logged on the phone and in a newer file stays one row with the file's status`() = runTest {
        val phone = stack()
        val habit = phone.habits.save(habit())
        val here = phone.entries.log(habit.id, TEST_START, EntryStatus.DONE_MIN)
        val fromFile = entryOn(habit.id, EntryStatus.DONE, updatedAt = TEST_NOW.plusSeconds(60))

        phone.backup.restore(Backup(emptyList(), listOf(fromFile), emptyList(), emptyList()))

        val rows = phone.backup.snapshot().entries.filter { it.date == TEST_START }
        assertEquals(1, rows.size)
        assertEquals(EntryStatus.DONE, rows.single().status)
        assertEquals(here.id, rows.single().id)
    }

    @Test
    fun `a day logged on the phone after the file was written keeps the phone's status`() = runTest {
        val phone = stack(fixedClock(TEST_NOW.plusSeconds(3_600)))
        val habit = phone.habits.save(habit())
        phone.entries.log(habit.id, TEST_START, EntryStatus.DONE_MIN)
        val fromFile = entryOn(habit.id, EntryStatus.DONE, updatedAt = TEST_NOW)

        phone.backup.restore(Backup(emptyList(), listOf(fromFile), emptyList(), emptyList()))

        assertEquals(EntryStatus.DONE_MIN, phone.entries.find(habit.id, TEST_START)?.status)
    }

    @Test
    fun `a row whose habit is nowhere rolls back the whole import`() = runTest {
        val phone = stack()
        val habit = habit(name = "Read")
        val orphan = entryOn("no-such-habit", EntryStatus.DONE, updatedAt = TEST_NOW)

        assertFailsWith<SQLiteConstraintException> {
            phone.backup.restore(Backup(listOf(habit), listOf(orphan), emptyList(), emptyList()))
        }

        assertNull(phone.habits.find(habit.id))
    }

    private fun entryOn(habitId: String, status: EntryStatus, updatedAt: Instant) = Entry(
        habitId = habitId,
        date = TEST_START,
        status = status,
        createdAt = TEST_NOW,
        updatedAt = updatedAt,
    )
}
