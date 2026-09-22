/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.compose.ui.geometry.Offset
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 13 cells of 10 and 12 gaps of 2: every cell starts on a multiple of 12. */
private const val WIDTH = 154f
private const val GAP = 2f
private const val PITCH = 12f
private const val FULL_WINDOW = WINDOW_WEEKS * DAYS_PER_WEEK

class HeatmapGeometryTest {

    @Test
    fun `a tap lands on the day drawn under it, for every day in the window`() {
        val geometry = HeatmapGeometry(WIDTH, GAP, WINDOW_WEEKS)
        repeat(FULL_WINDOW) { index ->
            assertEquals(index, geometry.indexAt(geometry.topLeft(index) + Offset(1f, 1f), FULL_WINDOW))
        }
    }

    @Test
    fun `the bottom right cell is the last day`() {
        val geometry = HeatmapGeometry(WIDTH, GAP, WINDOW_WEEKS)
        assertEquals(FULL_WINDOW - 1, geometry.indexAt(Offset(WIDTH - 1f, 7 * PITCH - 3f), FULL_WINDOW))
    }

    @Test
    fun `a tap on the gap after a cell counts as that cell`() {
        val geometry = HeatmapGeometry(WIDTH, GAP, WINDOW_WEEKS)
        assertEquals(0, geometry.indexAt(Offset(PITCH - 1f, 5f), FULL_WINDOW))
    }

    @Test
    fun `a young habit's grid starts at the right, and the space left of it is no day`() {
        val geometry = HeatmapGeometry(WIDTH, GAP, weeks = 2)
        val firstColumn = (WINDOW_WEEKS - 2) * PITCH

        assertNull(geometry.indexAt(Offset(5f, 5f), dayCount = 10))
        assertEquals(0, geometry.indexAt(Offset(firstColumn + 5f, 5f), dayCount = 10))
    }

    @Test
    fun `a cell after today is no day`() {
        // Ten days: the second week stops at its fourth day.
        val geometry = HeatmapGeometry(WIDTH, GAP, weeks = 2)
        val secondColumn = (WINDOW_WEEKS - 1) * PITCH

        assertEquals(9, geometry.indexAt(Offset(secondColumn + 5f, 2 * PITCH + 5f), dayCount = 10))
        assertNull(geometry.indexAt(Offset(secondColumn + 5f, 3 * PITCH + 5f), dayCount = 10))
    }

    @Test
    fun `a tap below the seventh row is no day`() {
        val geometry = HeatmapGeometry(WIDTH, GAP, WINDOW_WEEKS)
        assertNull(geometry.indexAt(Offset(5f, 7 * PITCH + 1f), FULL_WINDOW))
    }
}
