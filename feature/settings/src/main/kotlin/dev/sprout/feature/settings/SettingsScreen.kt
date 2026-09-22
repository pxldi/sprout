/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.settings

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sprout.core.model.BackupFormatException
import dev.sprout.core.ui.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@Composable
public fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME),
        viewModel::exportBackup,
    )
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::importBackup)
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), viewModel::chooseFolder)
    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onBack = onBack,
            onExport = { export.launch(viewModel.exportName()) },
            // Some file managers label a .json file as a plain binary, so both are offered.
            onImport = { import.launch(arrayOf(BACKUP_MIME, "application/octet-stream")) },
            onChooseFolder = { folder.launch(null) },
            onTurnOffDailyCopy = viewModel::turnOffDailyCopy,
            onMessageShown = viewModel::messageShown,
        ),
        modifier = modifier,
    )
}

public data class SettingsActions(
    val onBack: () -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onChooseFolder: () -> Unit,
    val onTurnOffDailyCopy: () -> Unit,
    val onMessageShown: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        // Shown from the screen's scope: clearing the message changes this effect's key, which
        // would cancel a snackbar shown from inside it before anybody could read it.
        scope.launch { snackbar.showSnackbar(resources.textFor(message)) }
        actions.onMessageShown()
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.create_back),
                        )
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeading(stringResource(R.string.settings_backup))
            SettingRow(
                title = stringResource(R.string.settings_export),
                body = stringResource(R.string.settings_export_body),
                enabled = !state.isWorking,
                onClick = actions.onExport,
            )
            SettingRow(
                title = stringResource(R.string.settings_import),
                body = stringResource(R.string.settings_import_body),
                enabled = !state.isWorking,
                onClick = actions.onImport,
            )
            DailyCopyRow(state = state, actions = actions)
        }
    }
}

@Composable
private fun DailyCopyRow(state: SettingsUiState, actions: SettingsActions) {
    val folderName = state.folderName
    val body = if (folderName == null) {
        stringResource(R.string.settings_daily_copy_off)
    } else {
        val last = when {
            state.lastCopyFailed -> stringResource(R.string.settings_daily_copy_failed)
            state.lastCopyAt == null -> stringResource(R.string.settings_daily_copy_none)
            else -> stringResource(
                R.string.settings_daily_copy_last,
                state.lastCopyAt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)),
            )
        }
        stringResource(R.string.settings_daily_copy_folder, folderName) + "\n" + last
    }
    ListItem(
        modifier = Modifier.clickable(enabled = !state.isWorking, onClick = actions.onChooseFolder),
        headlineContent = { Text(stringResource(R.string.settings_daily_copy)) },
        supportingContent = { Text(body) },
        trailingContent = if (folderName == null) {
            null
        } else {
            {
                TextButton(onClick = actions.onTurnOffDailyCopy, enabled = !state.isWorking) {
                    Text(stringResource(R.string.settings_daily_copy_turn_off))
                }
            }
        },
    )
}

@Composable
private fun SettingRow(title: String, body: String, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
    )
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
    )
}

private fun Resources.textFor(message: SettingsMessage): String = when (message) {
    is SettingsMessage.Exported ->
        getString(R.string.settings_exported, habits(message.habits), days(message.days))
    SettingsMessage.ExportFailed -> getString(R.string.settings_export_failed)
    is SettingsMessage.Imported ->
        getString(R.string.settings_imported, habits(message.habits), days(message.days))
    SettingsMessage.ImportedChanges -> getString(R.string.settings_imported_changes)
    SettingsMessage.NothingNew -> getString(R.string.settings_import_nothing_new)
    is SettingsMessage.ImportRefused -> getString(
        when (message.reason) {
            BackupFormatException.Reason.NOT_A_BACKUP -> R.string.settings_import_not_backup
            BackupFormatException.Reason.NEWER_VERSION -> R.string.settings_import_newer
            BackupFormatException.Reason.DAMAGED -> R.string.settings_import_failed
        },
    )
    SettingsMessage.CopyOn -> getString(R.string.settings_copy_on)
    SettingsMessage.CopyFailed -> getString(R.string.settings_copy_failed)
}

private fun Resources.habits(n: Int): String = getQuantityString(R.plurals.settings_count_habits, n, n)

private fun Resources.days(n: Int): String = getQuantityString(R.plurals.settings_count_days, n, n)
