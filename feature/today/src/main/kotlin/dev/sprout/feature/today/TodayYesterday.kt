/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.sprout.core.model.EntryStatus
import dev.sprout.core.ui.DayOptions
import dev.sprout.core.ui.R
import dev.sprout.core.ui.rememberCompletionHaptic
import java.time.LocalDate

/**
 * Yesterday's habits, under today's. Nothing when the list is empty, which it is from noon on.
 *
 * [onSet] gets null to clear the day. Ticking a done row clears it, so a wrong tap is undone
 * with the same tap that made it.
 */
internal fun LazyListScope.yesterdaySection(
    items: List<YesterdayItem>,
    onSet: (YesterdayItem, EntryStatus?) -> Unit,
    onMore: (YesterdayItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "yesterday-heading") { YesterdayHeading() }
    // Prefixed, because a daily habit is in both lists and a lazy list crashes on a repeated key.
    items(items, key = { "yesterday:${it.habit.id}" }) { item ->
        YesterdayRow(
            item = item,
            onToggle = { onSet(item, if (item.isDone) null else EntryStatus.DONE) },
            onMore = { onMore(item) },
        )
        HorizontalDivider()
    }
}

/** The smallest version, Skip and Clear for one of yesterday's habits. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YesterdaySheet(
    item: YesterdayItem,
    onSetDay: (String, LocalDate, EntryStatus?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        DayOptions(
            title = stringResource(R.string.today_yesterday_options, item.habit.displayName),
            status = item.status,
            minimumVersion = item.habit.minimumVersion,
            onSet = { status -> onSetDay(item.habit.id, item.date, status); onDismiss() },
        )
    }
}

@Composable
private fun YesterdayHeading() {
    Text(
        text = stringResource(R.string.today_yesterday),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/**
 * A name and a tick, with the other choices behind More.
 *
 * No ring and no run. Today's row for the same habit already counts yesterday, and printing the
 * figures twice would say nothing new.
 */
@Composable
private fun YesterdayRow(item: YesterdayItem, onToggle: () -> Unit, onMore: () -> Unit) {
    val haptic = rememberCompletionHaptic()
    val toggle = {
        if (!item.isDone) haptic()
        onToggle()
    }
    ListItem(
        modifier = Modifier.toggleable(value = item.isDone, role = Role.Checkbox, onValueChange = { toggle() }),
        headlineContent = {
            Text(
                text = item.habit.displayName,
                textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
            )
        },
        supportingContent = if (item.status == EntryStatus.SKIP) {
            { Text(stringResource(R.string.day_status_skipped)) }
        } else {
            null
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.isDone, onCheckedChange = { toggle() })
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
