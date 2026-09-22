/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.settings

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyCopiesTest {

    @get:Rule val tmp = TemporaryFolder()

    private val start = LocalDate.of(2026, 9, 1)

    @Test
    fun `the eighth daily copy removes the oldest`() {
        val folder = DirectoryFolder(tmp.root)
        repeat(8) { DailyCopies.write(folder, start.plusDays(it.toLong()), byteArrayOf(1)) }

        val names = folder.names().sorted()
        assertEquals(7, names.size)
        assertEquals("sprout-daily-2026-09-02.json", names.first())
        assertEquals("sprout-daily-2026-09-08.json", names.last())
    }

    @Test
    fun `files that are not daily copies are never deleted`() {
        val folder = DirectoryFolder(tmp.root)
        val others = listOf("sprout-backup-2026-01-01.json", "notes.txt", "sprout-daily-old.json")
        others.forEach { File(tmp.root, it).writeText("keep") }

        repeat(10) { DailyCopies.write(folder, start.plusDays(it.toLong()), byteArrayOf(1)) }

        assertTrue(folder.names().containsAll(others))
    }

    @Test
    fun `a second copy on the same day replaces the first`() {
        val folder = DirectoryFolder(tmp.root)
        DailyCopies.write(folder, start, "first, and longer".encodeToByteArray())
        DailyCopies.write(folder, start, "second".encodeToByteArray())

        assertEquals(listOf("sprout-daily-2026-09-01.json"), folder.names())
        assertEquals("second", File(tmp.root, "sprout-daily-2026-09-01.json").readText())
    }
}

/** Plain files, standing in for the document tree the system picker hands out. */
private class DirectoryFolder(private val dir: File) : CopyFolder {
    override fun names(): List<String> = dir.list().orEmpty().toList()
    override fun write(name: String, bytes: ByteArray) = File(dir, name).writeBytes(bytes)
    override fun delete(name: String) {
        File(dir, name).delete()
    }
}
