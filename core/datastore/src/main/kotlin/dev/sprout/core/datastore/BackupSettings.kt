/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.datastore

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Instant

/** Where the daily copy goes, and how the last one went. */
public data class BackupState(
    /** A document-tree uri the user picked, or null when the daily copy is off. */
    val folder: String? = null,
    val lastCopyAt: Instant? = null,
    val lastCopyFailed: Boolean = false,
)

/**
 * The daily copy's settings.
 *
 * Its own file, apart from [ShineHistory], so that a corrupt file here costs only this: the
 * folder is forgotten and Settings shows the daily copy as off, which the user can see and fix.
 */
public class BackupSettings internal constructor(
    private val store: DataStore<Preferences>,
) {
    public val state: Flow<BackupState> = store.data.map { prefs ->
        BackupState(
            folder = prefs[FOLDER],
            lastCopyAt = prefs[LAST_COPY_AT]?.let(Instant::ofEpochMilli),
            lastCopyFailed = prefs[LAST_COPY_FAILED] ?: false,
        )
    }

    public suspend fun current(): BackupState = state.first()

    /** A new folder starts with no history: the last result was about the old one. */
    public suspend fun setFolder(uri: String?) {
        store.edit { prefs ->
            if (uri == null) prefs.remove(FOLDER) else prefs[FOLDER] = uri
            prefs.remove(LAST_COPY_AT)
            prefs.remove(LAST_COPY_FAILED)
        }
    }

    public suspend fun recordCopy(at: Instant, failed: Boolean) {
        store.edit { prefs ->
            prefs[LAST_COPY_AT] = at.toEpochMilli()
            prefs[LAST_COPY_FAILED] = failed
        }
    }

    private companion object {
        val FOLDER = stringPreferencesKey("folder")
        val LAST_COPY_AT = longPreferencesKey("last_copy_at")
        val LAST_COPY_FAILED = booleanPreferencesKey("last_copy_failed")
    }
}

private val backupCorruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }

private val Context.backupStore: DataStore<Preferences> by preferencesDataStore(
    name = "backup",
    corruptionHandler = backupCorruptionHandler,
)

public fun backupSettings(context: Context): BackupSettings = BackupSettings(context.backupStore)

/** A store over a throwaway file, for tests. The same trade as `temporaryShineHistory`. */
@VisibleForTesting
public fun temporaryBackupSettings(file: File): BackupSettings =
    BackupSettings(PreferenceDataStoreFactory.create(corruptionHandler = backupCorruptionHandler) { file })
