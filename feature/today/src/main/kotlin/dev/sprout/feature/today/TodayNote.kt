/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import dev.sprout.core.ui.R

/**
 * Writing down what happened today.
 *
 * Nothing here asks the user to justify anything. The field is empty, the example is a plain
 * observation, and whatever they type is stored exactly as typed and read back to them unchanged
 * on the habit's own screen. The app never interprets a note, counts them, or scores them.
 *
 * Only reachable for a day that has been logged — see [TodayItem.canNote].
 */
@Composable
internal fun NoteDialog(
    item: TodayItem,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Seeded from the stored note, so editing starts from what is there rather than from blank —
    // and survives a rotation with whatever has been typed since.
    var text by rememberSaveable(item.habit.id) { mutableStateOf(item.todayNote.orEmpty()) }
    val focus = remember { FocusRequester() }

    // A dialog whose only control is a text field should not need a tap to start typing.
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        // The habit's name: the swipe opens this directly, without the sheet that would say it.
        title = { Text(item.habit.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Notes hang off one day, and which day is not obvious from a dialog.
                Text(
                    text = stringResource(R.string.note_dialog_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.note_label)) },
                    placeholder = { Text(stringResource(R.string.note_hint)) },
                    minLines = MIN_LINES,
                    // No `focusable()` alongside this: it would add a second focus target and
                    // take the request itself, leaving the editor unfocused and the keyboard down.
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                // Worth saying only when there is something an empty field would remove.
                if (!item.todayNote.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.note_clear_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text(stringResource(R.string.note_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.note_cancel))
            }
        },
    )
}

/** Tall enough that a sentence or two does not scroll inside a three-line box. */
private const val MIN_LINES = 3
