/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sprout.core.database.repository.BackupRepository
import dev.sprout.core.database.repository.RestoreCounts
import dev.sprout.core.datastore.BackupSettings
import dev.sprout.core.model.Backup
import dev.sprout.core.model.BackupCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** How a daily copy went. */
public enum class CopyResult { NO_FOLDER, SAVED, FAILED }

/**
 * Export, import and the daily copy, over the files the user picked.
 *
 * One instance per process, because the Settings screen and the daily worker can both write
 * the same day's copy and [copying] keeps them from racing into two files.
 */
@Singleton
public class Backups @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BackupRepository,
    private val settings: BackupSettings,
    private val clock: Clock,
) {
    private val copying = Mutex()

    /** The name the system picker offers for a manual export. */
    public fun exportName(): String = "sprout-backup-${LocalDate.now(clock)}.json"

    /** Writes every row to [uri], a document the user just created. Returns what was saved. */
    public suspend fun exportTo(uri: Uri): Backup = withContext(Dispatchers.IO) {
        val backup = repository.snapshot()
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw FileNotFoundException("Could not open $uri")
        out.use { it.write(encode(backup)) }
        backup
    }

    /**
     * Merges the backup at [uri] into the database.
     *
     * @throws dev.sprout.core.model.BackupFormatException if the file is not a backup this
     *   version can read.
     */
    public suspend fun importFrom(uri: Uri): RestoreCounts = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Could not open $uri")
        val text = input.use { it.readBytes().decodeToString() }
        repository.restore(BackupCodec.decode(text))
    }

    /** Writes today's copy into the chosen folder, if there is one, and records the result. */
    public suspend fun copyNow(): CopyResult = copying.withLock {
        val folder = settings.current().folder ?: return@withLock CopyResult.NO_FOLDER
        val saved = try {
            withContext(Dispatchers.IO) {
                val bytes = encode(repository.snapshot())
                DailyCopies.write(DocumentTreeFolder(context, folder.toUri()), LocalDate.now(clock), bytes)
            }
            true
        } catch (e: IOException) {
            logFailure("Daily copy not written", e)
        } catch (e: SecurityException) {
            // The grant was revoked, or the folder's app was uninstalled.
            logFailure("Daily copy not written", e)
        } catch (e: IllegalArgumentException) {
            // The stored uri is no longer a tree this device knows.
            logFailure("Daily copy not written", e)
        }
        settings.recordCopy(clock.instant(), failed = !saved)
        if (saved) CopyResult.SAVED else CopyResult.FAILED
    }

    private fun logFailure(what: String, e: Exception): Boolean {
        Log.w(LOG_TAG, what, e)
        return false
    }

    private fun encode(backup: Backup): ByteArray = BackupCodec.encode(backup, clock.instant()).encodeToByteArray()
}
