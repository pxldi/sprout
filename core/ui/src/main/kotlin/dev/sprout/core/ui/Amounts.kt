/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sprout.core.model.Habit
import dev.sprout.core.model.HabitType
import java.text.NumberFormat

private const val MAX_FRACTION_DIGITS = 2

/**
 * "12", "2.5" or "2,5", in the phone's own format. A whole number shows no decimals.
 *
 * No grouping: the amount dialog starts from this text, and [parseAmount] would read the "," in
 * "8,000" as a decimal point.
 */
public fun formatAmount(amount: Double): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = MAX_FRACTION_DIGITS
    isGroupingUsed = false
}.format(amount)

/**
 * The number in a typed amount, or null when there is none. A comma is read as the decimal
 * point, because a German keyboard offers no other.
 */
public fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 && it.isFinite() }

/**
 * Where a counted habit stands on a day: "12 of 20 pages", "1, limit 2 coffees", or "Limit 2
 * coffees" before anything is logged. Null for a habit that is not counted.
 */
@Composable
public fun amountLine(habit: Habit, amount: Double?): String? {
    val goal = when (habit.type) {
        HabitType.DO_NUMERIC -> habit.target
        HabitType.REDUCE -> habit.ceiling
        else -> null
    } ?: return null
    val goalText = habit.unit?.let { stringResource(R.string.amount_with_unit, formatAmount(goal), it) }
        ?: formatAmount(goal)
    return when {
        habit.type == HabitType.DO_NUMERIC ->
            stringResource(R.string.amount_of_target, formatAmount(amount ?: 0.0), goalText)
        amount == null -> stringResource(R.string.amount_limit_unlogged, goalText)
        else -> stringResource(R.string.amount_of_limit, formatAmount(amount), goalText)
    }
}

/**
 * Typing the amount for one day of a counted habit.
 *
 * [day] says which day, because a dialog does not show it. Saving an empty field saves zero,
 * which clears a count day and logs a clean cut-down day.
 */
@Composable
public fun AmountDialog(
    habit: Habit,
    day: String,
    initial: Double?,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(habit.id) { mutableStateOf(initial?.let(::formatAmount).orEmpty()) }
    val amount = if (text.isBlank()) 0.0 else parseAmount(text)
    val focus = remember { FocusRequester() }

    // The field is the only control, so it starts focused with the keyboard up.
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(habit.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = day,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.amount_label)) },
                    suffix = habit.unit?.let { unit -> { Text(unit) } },
                    supportingText = amountLine(habit, null)
                        ?.takeIf { habit.type == HabitType.REDUCE }
                        ?.let { line -> { Text(line) } },
                    isError = amount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { amount?.let(onSave) }, enabled = amount != null) {
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
