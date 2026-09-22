/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sprout.core.ui.MissNeutral
import dev.sprout.core.ui.R
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil

private const val MAX_STRENGTH = 100f
private const val FILL_ALPHA = 0.14f
private val CURVE_STROKE = 2.5.dp
private val CELL_GAP = 3.dp
private val CELL_CORNER = 2.dp
private val OFF_OUTLINE = 1.dp
private val CURVE_HEIGHT = 96.dp
private val BAR_HEIGHT = 64.dp

/** How present each kind of day looks. A missed day is quiet and grey — never the error colour. */
private const val MISSED_ALPHA = 0.45f
private const val SKIPPED_ALPHA = 0.40f
private const val TRACK_ALPHA = 0.18f

/**
 * The strength curve, drawn as steps.
 *
 * Steps rather than a smooth line, because that is what the number does: strength moves when an
 * occasion resolves and holds still in between. Joining the points with diagonals would draw a
 * gentle daily climb across a fortnight in which nothing happened at all.
 *
 * The x-axis is the calendar, not the occasion number — see [StrengthPoint].
 */
@Composable
internal fun StrengthCurve(
    points: List<StrengthPoint>,
    today: LocalDate,
    label: String,
    modifier: Modifier = Modifier,
) {
    val line = MaterialTheme.colorScheme.primary
    val fill = line.copy(alpha = FILL_ALPHA)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(CURVE_HEIGHT)
            .semantics { contentDescription = label },
    ) {
        val start = points.firstOrNull()?.date ?: return@Canvas
        val stroke = CURVE_STROKE.toPx()
        val span = ChronoUnit.DAYS.between(start, today).coerceAtLeast(1).toFloat()
        val usable = size.height - stroke

        fun x(date: LocalDate) = ChronoUnit.DAYS.between(start, date) / span * size.width
        fun y(strength: Double) =
            stroke / 2 + usable * (1f - (strength.toFloat() / MAX_STRENGTH).coerceIn(0f, 1f))

        val steps = mutableListOf(Offset(0f, y(points.first().strength)))
        points.drop(1).forEach { point ->
            // Along at the old height, then up or down: the step, not the diagonal.
            steps += Offset(x(point.date), steps.last().y)
            steps += Offset(x(point.date), y(point.strength))
        }
        // Runs flat to the right edge so the chart ends at today rather than at the last time
        // anything happened — a habit untouched for a fortnight should look untouched.
        steps += Offset(size.width, steps.last().y)

        drawPath(areaUnder(steps, size.height), color = fill)
        drawPath(polyline(steps), color = line, style = Stroke(width = stroke))
    }
}

private fun polyline(points: List<Offset>): Path = Path().apply {
    moveTo(points.first().x, points.first().y)
    points.drop(1).forEach { lineTo(it.x, it.y) }
}

private fun areaUnder(points: List<Offset>, baseline: Float): Path = polyline(points).apply {
    lineTo(points.last().x, baseline)
    lineTo(points.first().x, baseline)
    close()
}

/**
 * Thirteen weeks as columns of seven days. Tapping a cell hands its day to [onDayClick].
 *
 * Days that owed nothing are drawn as a faint outline rather than shaded. A filled cell would
 * read as a result, and there is none; a blank one gave the finger nothing to aim at, and every
 * day can be tapped to set it.
 */
@Composable
internal fun HabitHeatmap(
    days: List<HeatmapDay>,
    label: String,
    onDayClick: (HeatmapDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = heatmapPalette()
    val weeks = ceil(days.size / DAYS_PER_WEEK.toFloat()).toInt().coerceAtLeast(1)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            // Square cells, whatever the screen is: the grid is thirteen weeks across by seven
            // down. Sized from the whole window rather than from the weeks that happen to be
            // filled, or a habit four days old would draw two cells the size of a thumbnail and
            // a column of empty canvas the length of the screen under them.
            .aspectRatio(WINDOW_WEEKS.toFloat() / DAYS_PER_WEEK)
            // Keyed on the days, or the handler keeps the first list it saw and a tap after a
            // new log opens the sheet with the old status.
            .pointerInput(days) {
                detectTapGestures { point ->
                    val geometry = HeatmapGeometry(size.width.toFloat(), CELL_GAP.toPx(), weeks)
                    geometry.indexAt(point, days.size)?.let { onDayClick(days[it]) }
                }
            }
            .semantics { contentDescription = label },
    ) {
        val geometry = HeatmapGeometry(size.width, CELL_GAP.toPx(), weeks)
        val corner = CornerRadius(CELL_CORNER.toPx())
        val line = OFF_OUTLINE.toPx()
        days.forEachIndexed { index, day ->
            val color = palette.getValue(day.mark)
            if (day.mark == DayMark.OFF) {
                // Inset by half the line, so the outline sits inside the cell and not over the gap.
                drawRoundRect(
                    color = color,
                    topLeft = geometry.topLeft(index) + Offset(line / 2, line / 2),
                    size = Size(geometry.cell - line, geometry.cell - line),
                    cornerRadius = corner,
                    style = Stroke(width = line),
                )
            } else {
                drawRoundRect(
                    color = color,
                    topLeft = geometry.topLeft(index),
                    size = Size(geometry.cell, geometry.cell),
                    cornerRadius = corner,
                )
            }
        }
    }
}

/**
 * Where each cell of the heatmap sits. The drawing and the tap handler both use it, so a tap
 * always lands on the day that is drawn under the finger.
 */
internal class HeatmapGeometry(width: Float, gap: Float, weeks: Int) {
    val cell: Float = ((width - gap * (WINDOW_WEEKS - 1)) / WINDOW_WEEKS).coerceAtLeast(1f)
    private val pitch = cell + gap

    /** Pushed right, so today is the last column whether the habit is a year old or a day. */
    private val indent = (WINDOW_WEEKS - weeks) * pitch

    fun topLeft(index: Int): Offset = Offset(
        x = indent + (index / DAYS_PER_WEEK) * pitch,
        y = (index % DAYS_PER_WEEK) * pitch,
    )

    /**
     * The index of the day under [point], or null left of the first week or after the last day.
     *
     * A tap on the gap after a cell counts as that cell. A cell is about 20dp across, well under
     * a fingertip, and a tap that does nothing reads as a broken grid.
     */
    fun indexAt(point: Offset, dayCount: Int): Int? {
        val x = point.x - indent
        val row = (point.y / pitch).toInt()
        val index = (x / pitch).toInt() * DAYS_PER_WEEK + row
        val onGrid = x >= 0f && point.y >= 0f && row < DAYS_PER_WEEK && index < dayCount
        return index.takeIf { onGrid }
    }
}

@Composable
private fun heatmapPalette(): Map<DayMark, Color> {
    val scheme = MaterialTheme.colorScheme
    return mapOf(
        DayMark.DONE to scheme.primary,
        DayMark.MISSED to MissNeutral.copy(alpha = MISSED_ALPHA),
        DayMark.SKIPPED to scheme.secondary.copy(alpha = SKIPPED_ALPHA),
        DayMark.OPEN to scheme.primary.copy(alpha = TRACK_ALPHA),
        DayMark.OFF to scheme.outlineVariant,
    )
}

/** Names the three colours that mean something. The outlines need no key: they owed nothing. */
@Composable
internal fun HeatmapLegend(modifier: Modifier = Modifier) {
    val palette = heatmapPalette()
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem(palette.getValue(DayMark.DONE), stringResource(R.string.detail_legend_done))
        LegendItem(palette.getValue(DayMark.MISSED), stringResource(R.string.detail_legend_missed))
        LegendItem(
            color = palette.getValue(DayMark.SKIPPED),
            text = stringResource(R.string.detail_legend_skipped),
        )
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(10.dp)) {
            drawRoundRect(color = color, cornerRadius = CornerRadius(CELL_CORNER.toPx()))
        }
        Text(text = text, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * A bar per weekday, scaled to the busiest one.
 *
 * Counts rather than rates, so the bars compare like with like — a rate would need a denominator
 * of "Tuesdays this habit was actually due", which is a different number for every schedule kind.
 */
@Composable
internal fun WeekdayBars(tallies: List<WeekdayTally>, weeks: Int, modifier: Modifier = Modifier) {
    val most = tallies.maxOfOrNull { it.completions } ?: 0
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tallies.forEach { tally ->
            val name = tally.day.getDisplayName(TextStyle.FULL, Locale.getDefault())
            WeekdayBar(
                fraction = if (most == 0) 0f else tally.completions.toFloat() / most,
                initial = tally.day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                // The bar is decorative; this sentence is what TalkBack actually reads out.
                label = pluralStringResource(
                    R.plurals.detail_weekdays_description,
                    weeks,
                    name,
                    tally.completions,
                    weeks,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeekdayBar(
    fraction: Float,
    initial: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(BAR_HEIGHT)) {
            val corner = CornerRadius(CELL_CORNER.toPx())
            // The track is drawn even at zero, so the row reads as seven days rather than as
            // however many of them happened to have a bar.
            drawRoundRect(color = color.copy(alpha = TRACK_ALPHA), cornerRadius = corner)
            if (fraction > 0f) {
                val height = size.height * fraction
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, size.height - height),
                    size = Size(size.width, height),
                    cornerRadius = corner,
                )
            }
        }
        Text(text = initial, style = MaterialTheme.typography.labelSmall)
    }
}
