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

/** One point of a detected jump, mapped onto the ride profile's axes. */
data class JumpMarker(val distanceM: Float, val altitudeM: Float)

/**
 * The real-data counterpart to [TrailProfile]: altitude plotted against ground distance covered,
 * in the same visual language (filled trace, accent color, dashed flight-path overlay, circular
 * markers) - but traced from what actually happened on a recorded ride instead of an idealized
 * geometry. When a jump was detected, [takeoff] and [landing] draw the same dashed-arc-plus-marker
 * idiom the calculator diagrams use for the predicted trajectory.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun RideProfileChart(
    samples: List<RunSample>,
    modifier: Modifier = Modifier,
    takeoff: JumpMarker? = null,
    landing: JumpMarker? = null
) {
    val measurer = rememberTextMeasurer()
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
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

        if (samples.size < 2) return@Canvas

        val minX = samples.first().cumulativeDistanceM
        val maxX = samples.last().cumulativeDistanceM
        val minY = samples.minOf { it.altitudeM }
        val maxY = samples.maxOf { it.altitudeM }
        val xRange = (maxX - minX).coerceAtLeast(0.001f)
        val yRange = (maxY - minY).coerceAtLeast(0.001f)
        val inset = size.height * 0.10f

        fun toOffset(distanceM: Float, altitudeM: Float): Offset = Offset(
            x = (distanceM - minX) / xRange * size.width,
            y = size.height - inset - (altitudeM - minY) / yRange * (size.height - 2 * inset)
        )

        val points = samples.map { toOffset(it.cumulativeDistanceM, it.altitudeM) }

        val area = Path().apply {
            moveTo(points.first().x, size.height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height)
            close()
        }
        drawPath(path = area, color = accent.copy(alpha = 0.14f))

        for (i in 0 until points.size - 1) {
            drawLine(color = accent, start = points[i], end = points[i + 1], strokeWidth = 5f)
        }

        if (takeoff != null && landing != null) {
            val takeoffPoint = toOffset(takeoff.distanceM, takeoff.altitudeM)
            val landingPoint = toOffset(landing.distanceM, landing.altitudeM)

            val flight = Path().apply {
                moveTo(takeoffPoint.x, takeoffPoint.y)
                val liftHeight = size.height * 0.12f
                quadraticBezierTo(
                    (takeoffPoint.x + landingPoint.x) / 2f,
                    minOf(takeoffPoint.y, landingPoint.y) - liftHeight,
                    landingPoint.x, landingPoint.y
                )
            }
            drawPath(
                flight,
                accent,
                style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            )

            drawMarker(measurer, takeoffPoint, "T", accent, onAccent)
            drawMarker(measurer, landingPoint, "L", accent, onAccent)
        }
    }
}
