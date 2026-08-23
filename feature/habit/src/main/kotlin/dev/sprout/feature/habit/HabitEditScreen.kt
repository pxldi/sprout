/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sprout.core.ui.R
import dev.sprout.core.ui.ReminderPermissions
import dev.sprout.core.ui.rememberReminderPermissions

@Composable
public fun EditHabitRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HabitEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Leave once the write has landed, not when the button was tapped — the same rule creation
    // follows, and for the same reason: the list behind this screen should already be right.
    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    EditHabitScreen(
        state = state,
        onEdit = viewModel::edit,
        onSave = viewModel::save,
        onLeave = onFinished,
        actions = HabitEditActions(
            onArchive = viewModel::archive,
            onRestore = viewModel::unarchive,
            onDelete = viewModel::delete,
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun EditHabitScreen(
    state: HabitEditUiState,
    onEdit: ((HabitDraft) -> HabitDraft) -> Unit,
    onSave: () -> Unit,
    onLeave: () -> Unit,
    actions: HabitEditActions,
    modifier: Modifier = Modifier,
    permissions: ReminderPermissions = rememberReminderPermissions(),
) {
    var confirming by remember { mutableStateOf<Confirm?>(null) }
    // Nothing here is written until Save, so leaving with changes throws them away. Asking only
    // when there is something to lose — a discard prompt for someone who changed nothing is the
    // kind of dialog people learn to dismiss without reading.
    val leave = { if (state.isDirty) confirming = Confirm.DISCARD else onLeave() }
    BackHandler(onBack = leave)

    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        topBar = {
            EditTopBar(
                state = state,
                onLeave = leave,
                onArchive = actions.onArchive,
                onDelete = { confirming = Confirm.DELETE },
            )
        },
        bottomBar = {
            Button(
                onClick = onSave,
                enabled = state.canSave && state.isDirty,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) { Text(stringResource(R.string.edit_save)) }
        },
    ) { inner ->
        if (!state.isLoading) {
            EditForm(
                state = state,
                permissions = permissions,
                onEdit = onEdit,
                onRestore = actions.onRestore,
                modifier = Modifier.padding(inner),
            )
        }
    }

    when (confirming) {
        null -> Unit
        Confirm.DELETE -> DeleteDialog(
            name = state.storedName,
            onConfirm = { confirming = null; actions.onDelete() },
            onDismiss = { confirming = null },
        )
        Confirm.DISCARD -> DiscardDialog(
            onConfirm = { confirming = null; onLeave() },
            onDismiss = { confirming = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTopBar(
    state: HabitEditUiState,
    onLeave: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.edit_title)) },
        navigationIcon = {
            IconButton(onClick = onLeave) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.create_back),
                )
            }
        },
        actions = {
            // Nothing to archive or delete until it is known what the habit is.
            if (!state.isLoading) {
                EditOverflow(
                    isArchived = state.isArchived,
                    onArchive = onArchive,
                    onDelete = onDelete,
                )
            }
        },
    )
}

/** The two questions this screen is allowed to interrupt with. */
private enum class Confirm { DELETE, DISCARD }

@Composable
private fun EditForm(
    state: HabitEditUiState,
    permissions: ReminderPermissions,
    onEdit: ((HabitDraft) -> HabitDraft) -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (state.isArchived) {
            ArchivedNotice(onRestore = onRestore)
        }
        Section(stringResource(R.string.edit_section_habit)) {
            NameAndTypeFields(draft, onEdit)
        }
        Section(stringResource(R.string.edit_section_smallest)) {
            SmallestField(draft, onEdit)
        }
        Section(stringResource(R.string.edit_section_cue)) { CueField(draft, onEdit) }
        Section(stringResource(R.string.edit_section_coping)) { CopingField(draft, onEdit) }
        Section(stringResource(R.string.edit_section_schedule)) {
            SchedulePicker(draft, onEdit)
        }
        Section(stringResource(R.string.edit_section_reminder)) {
            ReminderFields(draft, permissions, onEdit)
        }
    }
}

/**
 * A quiet label, not a question.
 *
 * The wizard asks "And when it goes wrong?" because it is talking somebody through a decision.
 * Here the decision is made and the user is looking for the field they came to change, so the
 * heading's whole job is to be findable.
 */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider()
        content()
    }
}

/**
 * Restore sits here rather than in the overflow menu.
 *
 * This is the only screen that can bring a habit back, and hiding the way out inside a menu
 * behind a three-dot icon would make archiving feel a good deal more final than it is.
 */
@Composable
private fun ArchivedNotice(onRestore: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.edit_archived_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRestore) { Text(stringResource(R.string.edit_restore)) }
        }
    }
}

@Composable
private fun EditOverflow(isArchived: Boolean, onArchive: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.action_more),
        )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        // Archiving an archived habit is a no-op with a label on it. Restore lives on the card.
        if (!isArchived) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_archive)) },
                onClick = { open = false; onArchive() },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.edit_delete)) },
            onClick = { open = false; onDelete() },
        )
    }
}

/** Names what is lost, and names the gentler option. "Are you sure?" is not information. */
@Composable
private fun DeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_delete_title, name)) },
        text = { Text(stringResource(R.string.edit_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.edit_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_delete_keep)) }
        },
    )
}

@Composable
private fun DiscardDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_discard_title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.edit_discard)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_keep_editing)) }
        },
    )
}
