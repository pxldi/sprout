/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.EditNote
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
import androidx.compose.material3.SwipeToDismissBoxValue
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
import dev.sprout.core.ui.NotificationAccess
import dev.sprout.core.ui.R
import dev.sprout.core.ui.ReminderPermissions
import dev.sprout.core.ui.RemindersSilencedBanner
import dev.sprout.core.ui.rememberReminderPermissions
import dev.sprout.core.ui.StrengthRing
import kotlin.math.roundToInt

@Composable
public fun TodayRoute(
    onAddHabit: () -> Unit,
    onOpenHabit: (String) -> Unit,
    onManageHabits: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(
        state = state,
        actions = TodayActions(
            onAddHabit = onAddHabit,
            onManageHabits = onManageHabits,
            onToggle = viewModel::toggle,
            onSkip = viewModel::skip,
            onMinimum = viewModel::completeMinimum,
            onClear = viewModel::clear,
            onOpen = onOpenHabit,
            onNote = viewModel::note,
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
    permissions: ReminderPermissions = rememberReminderPermissions(),
) {
    var sheetFor by remember { mutableStateOf<TodayItem?>(null) }
    var noteFor by remember { mutableStateOf<TodayItem?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TodayTopBar(state, onManageHabits = actions.onManageHabits) },
        floatingActionButton = {
            // Hidden on first run, which has its own, more explanatory button.
            if (!state.isFirstRun) {
                FloatingActionButton(onClick = actions.onAddHabit) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.today_add_habit),
                    )
                }
            }
        },
    ) { inner ->
        TodayContent(
            state = state,
            actions = actions,
            permissions = permissions,
            onNote = { noteFor = it },
            onMore = { sheetFor = it },
            modifier = Modifier.padding(inner),
        )
    }

    sheetFor?.let { item ->
        ModalBottomSheet(onDismissRequest = { sheetFor = null }) {
            HabitOptions(
                item = item,
                actions = SheetActions(
                    onSkip = { actions.onSkip(item.habit.id); sheetFor = null },
                    onMinimum = { actions.onMinimum(item.habit.id); sheetFor = null },
                    onClear = { actions.onClear(item.habit.id); sheetFor = null },
                    onOpen = { actions.onOpen(item.habit.id); sheetFor = null },
                    // Closes the sheet on the way: two layers of scrim over one text field is a
                    // lot of chrome for a sentence.
                    onNote = { noteFor = item; sheetFor = null },
                ),
            )
        }
    }

    noteFor?.let { item ->
        NoteDialog(
            item = item,
            onSave = { text -> actions.onNote(item.habit.id, text); noteFor = null },
            onDismiss = { noteFor = null },
        )
    }
}

/**
 * Everything under the app bar.
 *
 * Split out from [TodayScreen] so that screen reads as what it is: a Scaffold, a sheet and a
 * dialog. The two `on…` callbacks hand a whole item back rather than an id, because both of them
 * open something *about* that item and the caller would only have to look it up again.
 */
@Composable
private fun TodayContent(
    state: TodayUiState,
    actions: TodayActions,
    permissions: ReminderPermissions,
    onNote: (TodayItem) -> Unit,
    onMore: (TodayItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Above the list rather than in it: a reminder that is not arriving is true all day,
        // and a warning that scrolls away is a warning the user will not see again.
        if (state.hasReminders && permissions.notifications != NotificationAccess.GRANTED) {
            RemindersSilencedBanner(
                onOpenSettings = permissions::openNotificationSettings,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (state.isFirstRun) {
            FirstRunToday(onAddHabit = actions.onAddHabit)
        } else if (state.nothingScheduled) {
            NothingDueToday()
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.items, key = { it.habit.id }) { item ->
                    SwipeableHabitRow(
                        item = item,
                        onToggle = { actions.onToggle(item.habit.id) },
                        onSkip = { actions.onSkip(item.habit.id) },
                        onNote = { onNote(item) },
                        onMore = { onMore(item) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayTopBar(state: TodayUiState, onManageHabits: () -> Unit) {
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
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            // The only way to reach a habit that is not due today, and the only way to reach an
            // archived one. Hidden on first run, where there is nothing to list.
            if (!state.isFirstRun) {
                IconButton(onClick = onManageHabits) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ListAlt,
                        contentDescription = stringResource(R.string.habits_title),
                    )
                }
            }
        },
    )
}

/**
 * Swipe left to skip today, right to write a note about it.
 *
 * The row is put back after either one rather than removed: both are logs, not deletions, and the
 * habit belongs on today's list whichever way it went. The feedback is the line that appears
 * underneath — "Skipped today", or the note itself — not the row disappearing.
 *
 * The note swipe is only enabled once the day has been logged, because a note hangs off a logged
 * day; on an untouched row the gesture does nothing and reveals nothing. Both actions stay in the
 * overflow sheet, which remains the path for anyone who cannot make the gesture at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableHabitRow(
    item: TodayItem,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onNote: () -> Unit,
    onMore: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val swipeState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = item.canNote,
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> onNote()
                SwipeToDismissBoxValue.EndToStart -> onSkip()
                // Never delivered on a dismiss; listed so a new value cannot silently become a
                // skip, which is the one of the two that writes to the log unprompted.
                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { swipeState.reset() }
        },
        backgroundContent = {
            SwipeBackground(direction = swipeState.dismissDirection, hasNote = item.todayNote != null)
        },
    ) {
        HabitRow(item = item, onToggle = onToggle, onMore = onMore)
    }
}

/** What sits behind the row mid-swipe: which one depends on which way it is going. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, hasNote: Boolean) {
    if (direction == SwipeToDismissBoxValue.Settled) return
    val noting = direction == SwipeToDismissBoxValue.StartToEnd
    Row(
        modifier = Modifier
            // Deliberately not the error colour, either way. Skipping on purpose is a decision,
            // and writing something down is just writing something down.
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = if (noting) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (noting) Icons.Outlined.EditNote else Icons.Outlined.EventBusy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                when {
                    noting && hasNote -> R.string.action_note_edit
                    noting -> R.string.action_note_add
                    else -> R.string.action_skip
                },
            ),
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
        // Read back verbatim, and never truncated with an ellipsis: it is one line the user
        // wrote themselves, and hiding half of it would mean opening a dialog to reread it.
        item.todayNote?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

/**
 * What the overflow sheet can do to one habit's today.
 *
 * Grouped rather than passed one by one: they all close the sheet as well as acting, so every
 * one of them is a two-part lambda the sheet itself must not be trusted to remember.
 */
private data class SheetActions(
    val onSkip: () -> Unit,
    val onMinimum: () -> Unit,
    val onClear: () -> Unit,
    val onOpen: () -> Unit,
    val onNote: () -> Unit,
)

@Composable
private fun HabitOptions(item: TodayItem, actions: SheetActions) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            text = item.habit.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        item.habit.minimumVersion?.let { smallest ->
            ListItem(
                modifier = Modifier.toggleable(value = false, onValueChange = { actions.onMinimum() }),
                headlineContent = { Text(stringResource(R.string.action_minimum)) },
                supportingContent = { Text(smallest) },
            )
        }
        ListItem(
            modifier = Modifier.toggleable(value = false, onValueChange = { actions.onSkip() }),
            headlineContent = { Text(stringResource(R.string.action_skip)) },
        )
        // Gated exactly like Clear, and for the same reason: both act on today's entry, and
        // there is no entry to act on until the day is logged.
        if (item.canNote) {
            ListItem(
                modifier = Modifier.clickable(onClick = actions.onNote),
                headlineContent = {
                    Text(
                        stringResource(
                            if (item.todayNote.isNullOrBlank()) {
                                R.string.action_note_add
                            } else {
                                R.string.action_note_edit
                            },
                        ),
                    )
                },
                supportingContent = item.todayNote?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
            )
        }
        if (item.todayStatus != null) {
            ListItem(
                modifier = Modifier.toggleable(value = false, onValueChange = { actions.onClear() }),
                headlineContent = { Text(stringResource(R.string.action_clear)) },
            )
        }
        // Last, and separated: everything above logs today, this one leaves Today entirely.
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ListItem(
            modifier = Modifier.clickable(onClick = actions.onOpen),
            headlineContent = { Text(stringResource(R.string.action_open)) },
        )
    }
}
