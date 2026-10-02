/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import dev.sprout.core.model.HabitType
import dev.sprout.core.ui.AmountDialog
import dev.sprout.core.ui.R
import dev.sprout.core.ui.StrengthRing
import dev.sprout.core.ui.amountLine
import dev.sprout.core.ui.rememberCompletionHaptic
import java.time.LocalDate
import kotlin.math.roundToInt
/**
 * A counted habit: a tap on the row types the amount, and the plus adds one.
 *
 * The plus is for the glass or the page just finished, one thumb, no keyboard. The buzz comes
 * only on the tap that reaches the target, so it still means done.
 */
@Composable
internal fun CountedHabitRow(item: TodayItem, onAddOne: () -> Unit, onAmount: () -> Unit, onMore: () -> Unit) {
    val haptic = rememberCompletionHaptic()
    val addOne = {
        val next = item.habit.statusFor((item.todayAmount ?: 0.0) + 1.0)
        if (!item.isDone && next?.isCompletion == true) haptic()
        onAddOne()
    }
    ListItem(
        modifier = Modifier.clickable(
            onClickLabel = stringResource(R.string.action_enter_amount),
            onClick = onAmount,
        ),
        leadingContent = {
            StrengthRing(
                strength = item.progress.strength,
                label = stringResource(
                    R.string.habit_strength_description,
                    item.habit.displayName,
                    item.progress.strength.roundToInt(),
                ),
            )
        },
        headlineContent = {
            Text(
                text = item.habit.displayName,
                textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
            )
        },
        supportingContent = { RowSupport(item) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = addOne) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add_one),
                    )
                }
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
internal fun CountedOptions(item: TodayItem, actions: SheetActions) {
    ListItem(
        modifier = Modifier.clickable(onClick = actions.onAmount),
        headlineContent = { Text(stringResource(R.string.action_enter_amount)) },
        supportingContent = amountLine(item.habit, item.todayAmount)?.let { { Text(it) } },
    )
    // Zero is a done day for a cut-down habit, and typing a 0 is a lot to ask for one.
    if (item.habit.type == HabitType.REDUCE && item.todayAmount != 0.0) {
        ListItem(
            modifier = Modifier.clickable(onClick = actions.onNoneToday),
            headlineContent = { Text(stringResource(R.string.action_none_today)) },
        )
    }
}

/** Saving closes the dialog through [onDone] as well as cancelling does. */
@Composable
internal fun TodayAmountDialog(
    request: AmountRequest,
    today: LocalDate,
    onSetAmount: (String, LocalDate, Double) -> Unit,
    onDone: () -> Unit,
) {
    AmountDialog(
        habit = request.habit,
        day = stringResource(if (request.date == today) R.string.amount_today else R.string.today_yesterday),
        initial = request.initial,
        onSave = { amount -> onSetAmount(request.habit.id, request.date, amount); onDone() },
        onDismiss = onDone,
    )
}
