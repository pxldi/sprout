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
import dev.sprout.feature.habit.HabitListRoute
import dev.sprout.feature.today.TodayRoute

private const val TODAY_ROUTE = "today"
private const val CREATE_HABIT_ROUTE = "habit/new"
private const val HABIT_LIST_ROUTE = "habits"
private const val EDIT_HABIT_ROUTE = "habit/{$HABIT_ID_ARG}/edit"

private fun editHabitRoute(habitId: String) = "habit/$habitId/edit"

@Composable
internal fun SproutNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = TODAY_ROUTE) {
        composable(TODAY_ROUTE) {
            TodayRoute(
                onAddHabit = { navController.navigate(CREATE_HABIT_ROUTE) },
                onEditHabit = { id -> navController.navigate(editHabitRoute(id)) },
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
                onOpenHabit = { id -> navController.navigate(editHabitRoute(id)) },
                onBack = { navController.popBackStack(HABIT_LIST_ROUTE, inclusive = true) },
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
