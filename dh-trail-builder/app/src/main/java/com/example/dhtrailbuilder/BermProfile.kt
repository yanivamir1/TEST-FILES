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
import androidx.compose.ui.unit.sp

/**
 * The run-out, on its own: where you touch down, the ground falling away to the berm, and the
 * distance you have to scrub speed in - which is what this screen works out.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun BermProfile(
    modifier: Modifier = Modifier,
    dropToBermM: Float? = null,
    runOutM: Float? = null
) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val caption = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val landing = Offset(size.width * 0.10f, size.height * 0.30f)
        val bermFoot = Offset(size.width * 0.78f, size.height * 0.66f)
        val bermTop = Offset(size.width * 0.95f, size.height * 0.34f)

        val ground = Path().apply {
            moveTo(landing.x, landing.y)
            lineTo(bermFoot.x, bermFoot.y)
            quadraticBezierTo(
                size.width * 0.90f, size.height * 0.66f,
                bermTop.x, bermTop.y
            )
        }
        val filled = Path().apply {
            addPath(ground)
            lineTo(bermTop.x, size.height)
            lineTo(landing.x, size.height)
            close()
        }
        drawPath(filled, accent, alpha = 0.14f)
        drawPath(ground, accent, style = Stroke(width = 5f))

        // The measured drop from the landing down to the berm.
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        val dropX = landing.x + size.width * 0.05f
        drawLine(accent, Offset(landing.x, landing.y), Offset(bermFoot.x, landing.y), 1.8f, pathEffect = dash)
        drawLine(accent, Offset(landing.x, bermFoot.y), Offset(bermFoot.x, bermFoot.y), 1.8f, pathEffect = dash)
        drawLine(accent, Offset(dropX, landing.y), Offset(dropX, bermFoot.y), 3.5f)
        dropToBermM?.let {
            drawLabel(
                measurer, "${formatValue(it)} m",
                Offset(dropX + 8f, (landing.y + bermFoot.y) / 2f - 9f),
                accent, 13.sp
            )
        }

        drawMarker(measurer, landing, "C", accent, onAccent, 10f)
        drawCircle(dim, 10f, bermTop, style = Stroke(width = 2.5f))
        drawLabel(measurer, "D", Offset(bermTop.x - 4f, bermTop.y - 6f), dim, 11.sp)

        // The unknown: how much run-out it takes to get down to the berm speed.
        val y = size.height * 0.94f
        val color = if (runOutM != null) accent else caption
        drawLine(color, Offset(landing.x, y), Offset(bermFoot.x, y), 2.5f)
        drawLine(color, Offset(landing.x, y - 6f), Offset(landing.x, y + 6f), 2.5f)
        drawLine(color, Offset(bermFoot.x, y - 6f), Offset(bermFoot.x, y + 6f), 2.5f)
        val label = runOutM?.let { "${formatValue(it)} m" } ?: "?"
        drawLabel(
            measurer, label,
            Offset((landing.x + bermFoot.x) / 2f - 14f, y - 20f),
            color, 13.sp
        )
    }
}
