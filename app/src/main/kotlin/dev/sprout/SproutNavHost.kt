/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.sprout.feature.habit.CreateHabitRoute
import dev.sprout.feature.habit.EditHabitRoute
import dev.sprout.feature.habit.HABIT_ID_ARG
import dev.sprout.feature.habit.HabitDetailRoute
import dev.sprout.feature.habit.HabitListRoute
import dev.sprout.feature.today.TodayRoute

private const val TODAY_ROUTE = "today"
private const val CREATE_HABIT_ROUTE = "habit/new"
private const val HABIT_LIST_ROUTE = "habits"
private const val EDIT_HABIT_ROUTE = "habit/{$HABIT_ID_ARG}/edit"

/**
 * Deliberately three segments, matching the edit route rather than the shorter `habit/{id}`.
 *
 * A two-segment pattern would sit alongside the literal `habit/new`, and which of the two wins a
 * navigation to "habit/new" is Navigation's deep-link scoring, not something this file states.
 * Keeping the verb on the end means the create wizard cannot be shadowed by a habit whose id
 * happens to be the word "new".
 */
private const val HABIT_DETAIL_ROUTE = "habit/{$HABIT_ID_ARG}/detail"

private fun editHabitRoute(habitId: String) = "habit/$habitId/edit"

private fun habitDetailRoute(habitId: String) = "habit/$habitId/detail"

@Composable
internal fun SproutNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = TODAY_ROUTE) {
        composable(TODAY_ROUTE) {
            TodayRoute(
                onAddHabit = { navController.navigate(CREATE_HABIT_ROUTE) },
                onOpenHabit = { id -> navController.navigate(habitDetailRoute(id)) },
                onManageHabits = { navController.navigate(HABIT_LIST_ROUTE) },
            )
        }
        composable(CREATE_HABIT_ROUTE) {
            // Popping *this* destination by name, rather than bare popBackStack(), which pops
            // whatever happens to be on top. onFinished can fire more than once — a system back
            // racing the exit transition, or a save landing as the user backs out — and a second
            // bare pop would take Today with it and leave the NavHost with an empty stack, which
            // renders as a blank white screen. Named and inclusive, a repeat call is a no-op.
            CreateHabitRoute(
                onFinished = { navController.popBackStack(CREATE_HABIT_ROUTE, inclusive = true) },
            )
        }
        composable(HABIT_LIST_ROUTE) {
            HabitListRoute(
                onOpenHabit = { id -> navController.navigate(habitDetailRoute(id)) },
                onBack = { navController.popBackStack(HABIT_LIST_ROUTE, inclusive = true) },
            )
        }
        composable(
            route = HABIT_DETAIL_ROUTE,
            arguments = listOf(navArgument(HABIT_ID_ARG) { type = NavType.StringType }),
        ) {
            // The hub for one habit: everything about it is read here, and Edit is the one way
            // out that changes anything. Popping by name for the same reason as the others —
            // onBack also fires when the habit is deleted out from under the screen.
            HabitDetailRoute(
                onBack = { navController.popBackStack(HABIT_DETAIL_ROUTE, inclusive = true) },
                onEdit = { id -> navController.navigate(editHabitRoute(id)) },
            )
        }
        composable(
            route = EDIT_HABIT_ROUTE,
            arguments = listOf(navArgument(HABIT_ID_ARG) { type = NavType.StringType }),
        ) {
            // Named and inclusive for the same reason as above: onFinished fires on save, on
            // archive, on delete and on back, and more than one of those can land.
            EditHabitRoute(
                onFinished = { navController.popBackStack(EDIT_HABIT_ROUTE, inclusive = true) },
            )
        }
    }
}
