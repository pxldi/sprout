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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/**
 * When each kind of praise was last said, per habit.
 *
 * The app says one specific true thing when a habit is ticked off, and the same true thing said
 * every morning stops being praise and becomes wallpaper. This is the memory that stops that: a
 * kind of line is not repeated for a habit within [NOVELTY_DAYS] days.
 *
 * Not in the database, deliberately. Nothing here is the user's data — it is the app's memory of
 * what it has already said, worth nothing in a backup and no loss if it is wiped. Losing it costs
 * one repeated sentence.
 */
public class ShineHistory internal constructor(
    private val store: DataStore<Preferences>,
) {
    /** Keys are `habitId|kind`; values are epoch days. */
    public val shown: Flow<Map<String, LocalDate>> = store.data.map { prefs ->
        decode(prefs[SHOWN]).mapValues { LocalDate.ofEpochDay(it.value) }
    }

    /**
     * Records that [kind] was said for [habitId] on [on].
     *
     * Callers are expected to skip this when the stored date already matches: every write emits,
     * and the caller recomputes its line from that emission, so an unconditional write would spin.
     */
    public suspend fun record(habitId: String, kind: String, on: LocalDate) {
        store.edit { prefs ->
            val updated = decode(prefs[SHOWN]) + (keyOf(habitId, kind) to on.toEpochDay())
            prefs[SHOWN] = Json.encodeToString(updated)
        }
    }

    /** Drops every line remembered for one habit. Called when the habit itself is deleted. */
    public suspend fun forget(habitId: String) {
        store.edit { prefs ->
            val kept = decode(prefs[SHOWN]).filterKeys { it.substringBefore(SEPARATOR) != habitId }
            prefs[SHOWN] = Json.encodeToString(kept)
        }
    }

    private fun decode(raw: String?): Map<String, Long> =
        // A corrupt or half-written value is not worth crashing over: the cost of losing it is
        // that one line may be said twice.
        raw?.let { runCatching { Json.decodeFromString<Map<String, Long>>(it) }.getOrNull() }.orEmpty()

    public companion object {
        /** Long enough that a line feels earned again, short enough that it comes back. */
        public const val NOVELTY_DAYS: Long = 15

        private const val SEPARATOR = '|'
        private val SHOWN = stringPreferencesKey("shine_shown")

        public fun keyOf(habitId: String, kind: String): String = "$habitId$SEPARATOR$kind"
    }
}

/**
 * Wiped and started again if the file cannot be read.
 *
 * Without a handler, a truncated write — a process killed mid-save — makes every read from this
 * store throw, and Today derives its whole list from a flow that combines this one. Losing the
 * app's memory of its own compliments costs at most one repeated sentence; taking the screen down
 * over it is not a trade worth making.
 */
private val corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }

private val Context.shineStore: DataStore<Preferences> by preferencesDataStore(
    name = "shine",
    corruptionHandler = corruptionHandler,
)

/** Builds the real, file-backed store. Tests build one over a temporary directory instead. */
public fun shineHistory(context: Context): ShineHistory = ShineHistory(context.shineStore)

/**
 * A store over a throwaway file, for tests in modules that cannot see this one's internals.
 *
 * The same deliberate trade as `inMemoryRepositories` in `:core:database`: a test hook shipped in
 * production code, because the alternative is making the constructor public purely so another
 * module's tests can build one — and because the real factory is a `Context` delegate that hands
 * every test in a JVM the same file, and so the previous test's leftovers.
 */
@VisibleForTesting
public fun temporaryShineHistory(file: File): ShineHistory =
    ShineHistory(PreferenceDataStoreFactory.create(corruptionHandler = corruptionHandler) { file })
