/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sprout.core.model.ScheduleRule
import dev.sprout.core.ui.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
public fun HabitListRoute(
    onOpenHabit: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HabitListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HabitListScreen(
        state = state,
        onOpenHabit = onOpenHabit,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun HabitListScreen(
    state: HabitListUiState,
    onOpenHabit: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.habits_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.create_back),
                        )
                    }
                },
            )
        },
    ) { inner ->
        if (state.isEmpty) {
            EmptyHabits(modifier = Modifier.padding(inner))
        } else {
            LazyColumn(modifier = Modifier.padding(inner).fillMaxSize()) {
                items(state.active, key = { it.habit.id }) { summary ->
                    HabitListRow(summary = summary, onOpen = { onOpenHabit(summary.habit.id) })
                    HorizontalDivider()
                }
                // Headed rather than merely appended: without a heading the archived habits read
                // as live ones that have somehow stopped appearing on Today.
                if (state.archived.isNotEmpty()) {
                    item { SectionHeading(stringResource(R.string.habits_archived)) }
                    items(state.archived, key = { it.habit.id }) { summary ->
                        HabitListRow(summary = summary, onOpen = { onOpenHabit(summary.habit.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
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

@Composable
private fun HabitListRow(summary: HabitSummary, onOpen: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = { Text(summary.habit.name) },
        supportingContent = {
            Text(
                text = buildString {
                    append(scheduleSummary(summary.habit.schedule))
                    summary.reminderAt?.let {
                        append(" · ")
                        append(it.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}

/** The schedule in as few words as it takes. The edit screen is where the detail lives. */
@Composable
private fun scheduleSummary(rule: ScheduleRule): String = when (rule) {
    ScheduleRule.Daily -> stringResource(R.string.create_schedule_daily)
    is ScheduleRule.SpecificDays -> rule.days.sorted().joinToString(" ") { day ->
        day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
    is ScheduleRule.TimesPerWeek -> stringResource(R.string.create_schedule_times, rule.times)
    is ScheduleRule.EveryNDays -> stringResource(R.string.create_schedule_every, rule.n)
}

@Composable
private fun EmptyHabits(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.habits_empty),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
