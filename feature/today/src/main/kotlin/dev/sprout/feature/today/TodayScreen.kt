/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import dev.sprout.core.scoring.plantStage
import dev.sprout.core.ui.R
import dev.sprout.core.ui.StrengthRing
import kotlin.math.roundToInt

@Composable
public fun TodayRoute(
    onAddHabit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(
        state = state,
        actions = TodayActions(
            onAddHabit = onAddHabit,
            onToggle = viewModel::toggle,
            onSkip = viewModel::skip,
            onMinimum = viewModel::completeMinimum,
            onClear = viewModel::clear,
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun TodayScreen(
    state: TodayUiState,
    actions: TodayActions,
    modifier: Modifier = Modifier,
) {
    var sheetFor by remember { mutableStateOf<TodayItem?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TodayTopBar(state) },
        floatingActionButton = {
            // Hidden on the empty state, which has its own, more explanatory button.
            if (!state.isEmpty) {
                FloatingActionButton(onClick = actions.onAddHabit) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.today_add_habit),
                    )
                }
            }
        },
    ) { inner ->
        if (state.isEmpty) {
            EmptyToday(onAddHabit = actions.onAddHabit, modifier = Modifier.padding(inner))
        } else {
            LazyColumn(modifier = Modifier.padding(inner).fillMaxSize()) {
                items(state.items, key = { it.habit.id }) { item ->
                    SwipeableHabitRow(
                        item = item,
                        onToggle = { actions.onToggle(item.habit.id) },
                        onSkip = { actions.onSkip(item.habit.id) },
                        onMore = { sheetFor = item },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    sheetFor?.let { item ->
        ModalBottomSheet(onDismissRequest = { sheetFor = null }) {
            HabitOptions(
                item = item,
                onSkip = { actions.onSkip(item.habit.id); sheetFor = null },
                onMinimum = { actions.onMinimum(item.habit.id); sheetFor = null },
                onClear = { actions.onClear(item.habit.id); sheetFor = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayTopBar(state: TodayUiState) {
    TopAppBar(
        title = { Text(stringResource(R.string.today_title)) },
        actions = {
            if (state.items.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.today_progress,
                        state.doneCount,
                        state.items.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        },
    )
}

/**
 * Swipe left to skip today.
 *
 * The row is put back afterwards rather than removed: a skip is a neutral log, not a deletion,
 * and the habit belongs on today's list either way. The feedback is the "Skipped today" line
 * appearing underneath, not the row disappearing. Skipping stays reversible from the overflow
 * sheet, which also remains the path for anyone who cannot make the gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableHabitRow(
    item: TodayItem,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onMore: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val swipeState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = false,
        onDismiss = {
            onSkip()
            scope.launch { swipeState.reset() }
        },
        backgroundContent = { SkipBackground() },
    ) {
        HabitRow(item = item, onToggle = onToggle, onMore = onMore)
    }
}

@Composable
private fun SkipBackground() {
    Row(
        modifier = Modifier
            // Deliberately not the error colour. Skipping on purpose is a decision, not a failure.
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.EventBusy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.action_skip),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun HabitRow(item: TodayItem, onToggle: () -> Unit, onMore: () -> Unit) {
    val context = LocalContext.current
    val ringLabel = context.getString(
        R.string.habit_strength_description,
        item.habit.name,
        item.progress.strength.roundToInt(),
    )

    ListItem(
        modifier = Modifier.toggleable(
            value = item.isDone,
            role = Role.Checkbox,
            onValueChange = { onToggle() },
        ),
        leadingContent = {
            StrengthRing(strength = item.progress.strength, label = ringLabel)
        },
        headlineContent = {
            Text(
                text = item.habit.name,
                // Struck through when done: the satisfying part, and the only state change needed.
                textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
            )
        },
        supportingContent = { RowSupport(item) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.isDone, onCheckedChange = { onToggle() })
                IconButton(onClick = onMore) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
                    )
                }
            }
        },
    )
}

@Composable
private fun RowSupport(item: TodayItem) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val progress = item.progress
        Text(
            text = buildString {
                append(stringResource(R.string.habit_run, progress.currentRun))
                // Omitted on day one: "done 0 of 0" is noise, not honesty.
                if (progress.recentChances > 0) {
                    append(" · ")
                    append(
                        stringResource(
                            R.string.habit_recent,
                            progress.recentCompletions,
                            progress.recentChances,
                        ),
                    )
                }
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (item.isSkipped) {
            Text(
                text = stringResource(R.string.status_skipped),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.gentleNote?.let { note ->
            Text(
                text = stringResource(note.stringRes()),
                style = MaterialTheme.typography.bodySmall,
                // Never the error colour. A miss is not an error.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun GentleNote.stringRes(): Int = when (this) {
    GentleNote.MISSED_YESTERDAY -> R.string.note_missed_yesterday
    GentleNote.REPAIRABLE -> R.string.note_repairable
    GentleNote.BOUNCED_BACK -> R.string.note_bounced_back
}

@Composable
private fun HabitOptions(
    item: TodayItem,
    onSkip: () -> Unit,
    onMinimum: () -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            text = item.habit.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        item.habit.minimumVersion?.let { smallest ->
            ListItem(
                modifier = Modifier.toggleable(value = false, onValueChange = { onMinimum() }),
                headlineContent = { Text(stringResource(R.string.action_minimum)) },
                supportingContent = { Text(smallest) },
            )
        }
        ListItem(
            modifier = Modifier.toggleable(value = false, onValueChange = { onSkip() }),
            headlineContent = { Text(stringResource(R.string.action_skip)) },
        )
        if (item.todayStatus != null) {
            ListItem(
                modifier = Modifier.toggleable(value = false, onValueChange = { onClear() }),
                headlineContent = { Text(stringResource(R.string.action_clear)) },
            )
        }
    }
}

@Composable
private fun EmptyToday(onAddHabit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.today_empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.today_empty_body), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onAddHabit, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.today_add_habit))
        }
    }
}
