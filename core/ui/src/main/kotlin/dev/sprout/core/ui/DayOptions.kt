/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sprout.core.model.EntryStatus

/**
 * What can be set on one day: done, the smallest version, skipped, or nothing.
 *
 * Shared by the calendar on a habit's screen and the Yesterday rows on Today, so a past day
 * offers the same choices wherever it is set. The choice the day already has is left out,
 * because picking it would change nothing. [onSet] gets null for Clear.
 */
@Composable
public fun DayOptions(
    title: String,
    status: EntryStatus?,
    minimumVersion: String?,
    onSet: (EntryStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
        )
        status?.let { logged ->
            Text(
                text = stringResource(logged.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        if (status != EntryStatus.DONE) {
            Option(stringResource(R.string.action_complete)) { onSet(EntryStatus.DONE) }
        }
        if (minimumVersion != null && status != EntryStatus.DONE_MIN) {
            Option(stringResource(R.string.action_minimum), minimumVersion) { onSet(EntryStatus.DONE_MIN) }
        }
        if (status != EntryStatus.SKIP) {
            Option(stringResource(R.string.day_mark_skipped)) { onSet(EntryStatus.SKIP) }
        }
        if (status != null) {
            Option(stringResource(R.string.day_clear)) { onSet(null) }
        }
    }
}

@Composable
private fun Option(label: String, detail: String? = null, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(label) },
        supportingContent = detail?.let { { Text(it) } },
    )
}

private fun EntryStatus.labelRes(): Int = when (this) {
    EntryStatus.DONE -> R.string.day_status_done
    EntryStatus.DONE_MIN -> R.string.day_status_minimum
    EntryStatus.SKIP -> R.string.day_status_skipped
    EntryStatus.LAPSE -> R.string.day_status_lapse
}
