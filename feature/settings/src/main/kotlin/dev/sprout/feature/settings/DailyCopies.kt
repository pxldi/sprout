/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.settings

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException
import java.time.LocalDate

/**
 * Names and rotation for the daily copy.
 *
 * The daily copies have their own prefix, apart from a manual export's, so that rotation only
 * ever deletes files this code wrote. Anything else in the folder, a manual export included, is
 * never touched.
 */
internal object DailyCopies {

    const val KEEP: Int = 7

    private val NAME = Regex("""sprout-daily-\d{4}-\d{2}-\d{2}\.json""")

    fun nameFor(day: LocalDate): String = "sprout-daily-$day.json"

    /** Daily copies beyond the newest [KEEP]. ISO dates sort the same as text and as days. */
    fun surplus(names: List<String>): List<String> =
        names.filter { NAME.matches(it) }.sortedDescending().drop(KEEP)

    /** Writes [bytes] as the copy for [day], replacing an earlier one from the same day. */
    fun write(folder: CopyFolder, day: LocalDate, bytes: ByteArray) {
        folder.write(nameFor(day), bytes)
        surplus(folder.names()).forEach(folder::delete)
    }
}

/** The folder the daily copy goes into. A seam so the rotation can be tested on plain files. */
internal interface CopyFolder {
    fun names(): List<String>
    fun write(name: String, bytes: ByteArray)
    fun delete(name: String)
}

/**
 * A folder the user picked through the system picker.
 *
 * Throws [FileNotFoundException] or [SecurityException] when the folder is gone or the grant was
 * revoked; the caller records that as a failed copy.
 */
internal class DocumentTreeFolder(private val context: Context, uri: Uri) : CopyFolder {

    private val tree: DocumentFile = DocumentFile.fromTreeUri(context, uri)
        ?.takeIf { it.isDirectory && it.canWrite() }
        ?: throw FileNotFoundException("Backup folder not writable: $uri")

    override fun names(): List<String> = tree.listFiles().mapNotNull { it.name }

    override fun write(name: String, bytes: ByteArray) {
        // Found first, so a second copy on the same day overwrites instead of becoming "(1)".
        val file = tree.findFile(name)
            ?: tree.createFile(BACKUP_MIME, name)
            ?: throw FileNotFoundException("Could not create $name")
        // "wt" truncates. Plain "w" leaves the tail of a longer old file on some providers.
        val out = context.contentResolver.openOutputStream(file.uri, "wt")
            ?: throw FileNotFoundException("Could not open $name")
        out.use { it.write(bytes) }
    }

    override fun delete(name: String) {
        tree.findFile(name)?.delete()
    }
}

internal const val BACKUP_MIME = "application/json"

/** For `adb logcat`. The user sees a one-line message; the log says which call failed and why. */
internal const val LOG_TAG = "SproutBackup"
