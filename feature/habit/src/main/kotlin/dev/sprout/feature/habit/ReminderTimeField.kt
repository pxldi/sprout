/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.sprout.core.ui.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderTimeField(draft: HabitDraft, onEdit: Edit) {
    var picking by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = draft.reminderTime.format(
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT),
            ),
            style = MaterialTheme.typography.headlineMedium,
        )
        TextButton(onClick = { picking = true }) {
            Text(stringResource(R.string.create_reminder_change))
        }
    }

    if (picking) {
        val pickerState = rememberTimePickerState(
            initialHour = draft.reminderTime.hour,
            initialMinute = draft.reminderTime.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.create_reminder_dialog_title)) },
            text = { TimeInput(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val time = LocalTime.of(pickerState.hour, pickerState.minute)
                        onEdit { it.copy(reminderTime = time) }
                        picking = false
                    },
                ) { Text(stringResource(R.string.create_reminder_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) {
                    Text(stringResource(R.string.create_reminder_cancel))
                }
            },
        )
    }
}
