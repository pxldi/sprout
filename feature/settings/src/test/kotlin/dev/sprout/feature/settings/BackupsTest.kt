/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.settings

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.sprout.core.database.SproutRepositories
import dev.sprout.core.database.inMemoryRepositories
import dev.sprout.core.datastore.temporaryBackupSettings
import dev.sprout.core.model.BackupFormatException
import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.Habit
import dev.sprout.core.model.HabitType
import dev.sprout.core.model.ScheduleRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Export, wipe, import, through real files: the check the roadmap signs backup off against,
 * with a fresh in-memory database standing in for a wiped app.
 */
@RunWith(RobolectricTestRunner::class)
class BackupsTest {

    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-09-22T06:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val stacks = mutableListOf<SproutRepositories>()

    @After fun tearDown() = stacks.forEach { it.close() }

    private fun backupsOver(repositories: SproutRepositories) = Backups(
        context = context,
        repository = repositories.backup,
        settings = temporaryBackupSettings(File(tmp.root, "backup-${stacks.size}.preferences_pb")),
        clock = clock,
    )

    private fun stack() = inMemoryRepositories(context, clock).also { stacks += it }

    @Test
    fun `a wiped app gets every row back from an exported file`() = runTest {
        val phone = stack()
        val read = phone.habits.save(
            Habit(
                name = "Read",
                type = HabitType.DO_BOOL,
                schedule = ScheduleRule.Daily,
                createdAt = now,
                updatedAt = now,
            ),
        )
        phone.entries.log(read.id, LocalDate.of(2026, 9, 21), EntryStatus.DONE)
        phone.entries.note(read.id, LocalDate.of(2026, 9, 21), "Twenty pages.")
        val file = Uri.fromFile(tmp.newFile("sprout-backup.json"))

        backupsOver(phone).exportTo(file)
        val wiped = stack()
        backupsOver(wiped).importFrom(file)

        assertEquals(phone.backup.snapshot(), wiped.backup.snapshot())
    }

    @Test
    fun `a file that is not a backup is refused before anything is written`() = runTest {
        val file = tmp.newFile("notes.json").apply { writeText("""{"shopping": ["milk"]}""") }
        val phone = stack()

        val e = assertFailsWith<BackupFormatException> { backupsOver(phone).importFrom(Uri.fromFile(file)) }

        assertEquals(BackupFormatException.Reason.NOT_A_BACKUP, e.reason)
        assertEquals(0, phone.backup.snapshot().habits.size)
    }

    @Test
    fun `the export is offered under today's date`() {
        assertEquals("sprout-backup-2026-09-22.json", backupsOver(stack()).exportName())
    }
}
