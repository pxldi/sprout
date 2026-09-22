/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.settings

import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sprout.core.datastore.BackupSettings
import dev.sprout.core.datastore.BackupState
import dev.sprout.core.model.BackupFormatException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

/** Something that just happened, said once in a snackbar. */
public sealed interface SettingsMessage {
    public data class Exported(val habits: Int, val days: Int) : SettingsMessage
    public data object ExportFailed : SettingsMessage
    public data class Imported(val habits: Int, val days: Int) : SettingsMessage
    public data object NothingNew : SettingsMessage
    public data class ImportRefused(val reason: BackupFormatException.Reason) : SettingsMessage
    public data object CopyOn : SettingsMessage
    public data object CopyFailed : SettingsMessage
}

public data class SettingsUiState(
    /** The picked folder's name, or null when the daily copy is off. */
    val folderName: String? = null,
    val lastCopyAt: LocalDateTime? = null,
    val lastCopyFailed: Boolean = false,
    /** True while a file is being read or written, so a second tap cannot start another. */
    val isWorking: Boolean = false,
    val message: SettingsMessage? = null,
)

@HiltViewModel
public class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backups: Backups,
    private val settings: BackupSettings,
    private val clock: Clock,
) : ViewModel() {

    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<SettingsMessage?>(null)

    public val uiState: StateFlow<SettingsUiState> = combine(
        settings.state.map { it.withFolderName() }.flowOn(Dispatchers.IO),
        working,
        message,
    ) { (state, folderName), isWorking, message ->
        SettingsUiState(
            folderName = folderName,
            lastCopyAt = state.lastCopyAt?.atZone(clock.zone)?.toLocalDateTime(),
            lastCopyFailed = state.lastCopyFailed,
            isWorking = isWorking,
            message = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    public fun exportName(): String = backups.exportName()

    /** [uri] is null when the user backed out of the picker. */
    public fun exportBackup(uri: Uri?) {
        if (uri == null) return
        perform {
            try {
                val saved = backups.exportTo(uri)
                SettingsMessage.Exported(saved.liveHabitCount, saved.liveEntryCount)
            } catch (e: IOException) {
                failed(e, SettingsMessage.ExportFailed)
            } catch (e: SecurityException) {
                failed(e, SettingsMessage.ExportFailed)
            }
        }
    }

    public fun importBackup(uri: Uri?) {
        if (uri == null) return
        perform {
            try {
                val counts = backups.importFrom(uri)
                if (counts.isEmpty) {
                    SettingsMessage.NothingNew
                } else {
                    SettingsMessage.Imported(counts.habits, counts.entries)
                }
            } catch (e: BackupFormatException) {
                failed(e, SettingsMessage.ImportRefused(e.reason))
            } catch (e: IOException) {
                failed(e, SettingsMessage.ImportRefused(BackupFormatException.Reason.DAMAGED))
            } catch (e: SecurityException) {
                failed(e, SettingsMessage.ImportRefused(BackupFormatException.Reason.DAMAGED))
            } catch (e: SQLException) {
                // A row pointing at a habit the file does not hold. The transaction rolled back.
                failed(e, SettingsMessage.ImportRefused(BackupFormatException.Reason.DAMAGED))
            }
        }
    }

    /** Takes a lasting grant on the folder, then writes today's copy so the user sees it work. */
    public fun chooseFolder(uri: Uri?) {
        if (uri == null) return
        perform {
            val previous = settings.current().folder
            try {
                context.contentResolver.takePersistableUriPermission(uri, READ_WRITE)
            } catch (e: SecurityException) {
                return@perform failed(e, SettingsMessage.CopyFailed)
            }
            if (previous != null && previous != uri.toString()) release(previous)
            settings.setFolder(uri.toString())
            when (backups.copyNow()) {
                CopyResult.SAVED -> SettingsMessage.CopyOn
                CopyResult.FAILED, CopyResult.NO_FOLDER -> SettingsMessage.CopyFailed
            }
        }
    }

    public fun turnOffDailyCopy() {
        viewModelScope.launch {
            settings.current().folder?.let(::release)
            settings.setFolder(null)
        }
    }

    public fun messageShown() {
        message.value = null
    }

    private fun perform(block: suspend () -> SettingsMessage) {
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            try {
                message.value = block()
            } finally {
                working.value = false
            }
        }
    }

    private fun failed(e: Exception, message: SettingsMessage): SettingsMessage {
        Log.w(LOG_TAG, "Backup step failed", e)
        return message
    }

    private fun release(folder: String) {
        try {
            context.contentResolver.releasePersistableUriPermission(folder.toUri(), READ_WRITE)
        } catch (expected: SecurityException) {
            // Already gone, which is the state this call wants.
        }
    }

    /** The folder's display name, read from its provider; the raw uri path if that fails. */
    private fun BackupState.withFolderName(): Pair<BackupState, String?> {
        val uri = folder?.toUri() ?: return this to null
        // A revoked grant or a stale uri shows the raw path; the next copy reports the failure.
        val name = try {
            DocumentFile.fromTreeUri(context, uri)?.name
        } catch (expected: SecurityException) {
            null
        } catch (expected: IllegalArgumentException) {
            null
        }
        return this to (name ?: uri.lastPathSegment ?: folder)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
