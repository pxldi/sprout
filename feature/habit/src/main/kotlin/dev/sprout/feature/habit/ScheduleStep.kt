/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.sprout.core.ui.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun ScheduleStep(draft: HabitDraft, onEdit: Edit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StepPrompt(stringResource(R.string.create_schedule_title))
        ScheduleKind.entries.forEach { kind ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = draft.scheduleKind == kind,
                        role = Role.RadioButton,
                        onClick = { onEdit { it.copy(scheduleKind = kind) } },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Null: the row above owns the click, so the button must not be a second target.
                RadioButton(selected = draft.scheduleKind == kind, onClick = null)
                Text(
                    text = stringResource(kind.labelRes()),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            if (draft.scheduleKind == kind) {
                ScheduleDetail(kind = kind, draft = draft, onEdit = onEdit)
            }
        }
    }
}

@Composable
private fun ScheduleDetail(kind: ScheduleKind, draft: HabitDraft, onEdit: Edit) {
    Column(
        modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (kind) {
            ScheduleKind.DAILY -> Unit
            ScheduleKind.SPECIFIC_DAYS -> DayPicker(draft, onEdit)
            ScheduleKind.TIMES_PER_WEEK -> TimesPerWeekPicker(draft, onEdit)
            ScheduleKind.EVERY_N_DAYS -> EveryNPicker(draft, onEdit)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayPicker(draft: HabitDraft, onEdit: Edit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in draft.specificDays,
                onClick = {
                    onEdit { d ->
                        val days = if (day in d.specificDays) {
                            d.specificDays - day
                        } else {
                            d.specificDays + day
                        }
                        d.copy(specificDays = days)
                    }
                },
                label = {
                    Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                },
            )
        }
    }
    if (draft.specificDays.isEmpty()) {
        Text(
            text = stringResource(R.string.create_schedule_days_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimesPerWeekPicker(draft: HabitDraft, onEdit: Edit) {
    Text(stringResource(R.string.create_schedule_times, draft.timesPerWeek))
    Slider(
        value = draft.timesPerWeek.toFloat(),
        onValueChange = { value -> onEdit { it.copy(timesPerWeek = value.toInt()) } },
        valueRange = 1f..DAYS_IN_WEEK.toFloat(),
        steps = DAYS_IN_WEEK - 2,
    )
    Text(
        text = stringResource(R.string.create_schedule_times_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EveryNPicker(draft: HabitDraft, onEdit: Edit) {
    Text(stringResource(R.string.create_schedule_every, draft.everyNDays))
    Slider(
        value = draft.everyNDays.toFloat(),
        onValueChange = { value -> onEdit { it.copy(everyNDays = value.toInt()) } },
        valueRange = 2f..MAX_EVERY_N_DAYS.toFloat(),
    )
}

private fun ScheduleKind.labelRes(): Int = when (this) {
    ScheduleKind.DAILY -> R.string.create_schedule_daily
    ScheduleKind.SPECIFIC_DAYS -> R.string.create_schedule_days
    ScheduleKind.TIMES_PER_WEEK -> R.string.create_schedule_weekly
    ScheduleKind.EVERY_N_DAYS -> R.string.create_schedule_interval
}
