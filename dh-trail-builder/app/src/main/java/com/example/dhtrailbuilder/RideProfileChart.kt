package com.example.dhtrailbuilder

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.rememberTextMeasurer

/** A point of a detected jump, in the same x-space as the chart's points. */
data class JumpMarker(val x: Float, val altitudeM: Float)

/**
 * The real-data counterpart to [TrailProfile]: altitude plotted against whatever the screen
 * chose for the x axis - ground distance when recording with GPS, elapsed time without - in
 * the same visual language, with the detected jump drawn as a dashed flight path between
 * takeoff and landing markers.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun RideProfileChart(
    points: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
    takeoff: JumpMarker? = null,
    landing: JumpMarker? = null,
    cursor: Pair<Float, Float>? = null
) {
    val measurer = rememberTextMeasurer()
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val cursorColor = MaterialTheme.colorScheme.secondary
    val onCursorColor = MaterialTheme.colorScheme.onSecondary
    val gridColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier) {
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = size.height * i / gridLines
            drawLine(
                color = gridColor.copy(alpha = 0.25f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5f
            )
        }

        if (points.size < 2) return@Canvas

        val minX = points.first().first
        val maxX = points.last().first
        val minY = points.minOf { it.second }
        val maxY = points.maxOf { it.second }
        val xRange = (maxX - minX).coerceAtLeast(0.001f)
        val yRange = (maxY - minY).coerceAtLeast(0.001f)
        val inset = size.height * 0.10f

        fun toOffset(x: Float, altitude: Float): Offset = Offset(
            x = (x - minX) / xRange * size.width,
            y = size.height - inset - (altitude - minY) / yRange * (size.height - 2 * inset)
        )

        val plotted = points.map { toOffset(it.first, it.second) }

        val area = Path().apply {
            moveTo(plotted.first().x, size.height)
            plotted.forEach { lineTo(it.x, it.y) }
            lineTo(plotted.last().x, size.height)
            close()
        }
        drawPath(path = area, color = accent.copy(alpha = 0.14f))

        for (i in 0 until plotted.size - 1) {
            drawLine(color = accent, start = plotted[i], end = plotted[i + 1], strokeWidth = 5f)
        }

        if (takeoff != null && landing != null) {
            val takeoffPoint = toOffset(takeoff.x, takeoff.altitudeM)
            val landingPoint = toOffset(landing.x, landing.altitudeM)

            val flight = Path().apply {
                moveTo(takeoffPoint.x, takeoffPoint.y)
                quadraticBezierTo(
                    (takeoffPoint.x + landingPoint.x) / 2f,
                    minOf(takeoffPoint.y, landingPoint.y) - size.height * 0.12f,
                    landingPoint.x, landingPoint.y
                )
            }
            drawPath(
                flight,
                accent,
                style = Stroke(
                    width = 4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                )
            )

            drawMarker(measurer, takeoffPoint, "T", accent, onAccent)
            drawMarker(measurer, landingPoint, "L", accent, onAccent)
        }

        cursor?.let { (x, altitude) ->
            val cursorPoint = toOffset(x, altitude)
            drawLine(
                color = cursorColor,
                start = Offset(cursorPoint.x, 0f),
                end = Offset(cursorPoint.x, size.height),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
            drawMarker(measurer, cursorPoint, "", cursorColor, onCursorColor, radius = 10f)
        }
    }
}
