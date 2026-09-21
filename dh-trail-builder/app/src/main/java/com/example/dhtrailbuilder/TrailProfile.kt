package com.example.dhtrailbuilder

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/** Which measurement of the run the rider is taking right now. */
enum class TrailStep(val stepLabel: String, val title: String, val instruction: String) {
    RollIn(
        stepLabel = "Step 1 of 3",
        title = "Drop to the lip",
        instruction = "A where you start rolling in, B at the edge of the ramp"
    ),
    Ramp(
        stepLabel = "Step 2 of 3",
        title = "Ramp angle",
        instruction = "Lay the phone on the ramp face with its length pointing down the slope"
    ),
    Landing(
        stepLabel = "Step 3 of 3",
        title = "Drop to the landing",
        instruction = "A back at the lip, B where you touch down"
    ),
    RunOut(
        stepLabel = "Run-out",
        title = "Landing to berm",
        instruction = "Lay the phone on the ground with its length pointing down the run-out"
    )
}

// Key points of the side profile, in 0..1 of the drawing area.
private val Start = Offset(0.04f, 0.16f)
private val RampBase = Offset(0.34f, 0.63f)
private val KickStart = Offset(0.39f, 0.615f)
private val Lip = Offset(0.46f, 0.40f)
private val LandingPoint = Offset(0.645f, 0.55f)
private val RunOutStart = Offset(0.845f, 0.78f)
private val RunOutEnd = Offset(0.90f, 0.81f)
private val BermTop = Offset(0.985f, 0.56f)

/**
 * Side view of the whole run, with the segment being measured highlighted and the A / B markers
 * sitting on the exact spots on the trail they refer to.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun TrailProfile(
    activeStep: TrailStep,
    modifier: Modifier = Modifier,
    dropToLipM: Float? = null,
    rampAngleDeg: Float? = null,
    landingDropM: Float? = null,
    jumpDistanceM: Float? = null,
    gradientDeg: Float? = null
) {
    val measurer = rememberTextMeasurer()
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val dim = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val takeoffActive = activeStep == TrailStep.RollIn || activeStep == TrailStep.Ramp
        val landingActive = activeStep == TrailStep.Landing
        val runOutActive = activeStep == TrailStep.RunOut

        drawTerrain(
            takeoffColor = if (takeoffActive) accent else dim,
            takeoffActive = takeoffActive,
            landingColor = if (landingActive || runOutActive) accent else dim,
            landingActive = landingActive || runOutActive,
            trajectoryColor = dim
        )

        when (activeStep) {
            TrailStep.RollIn -> {
                drawDimension(
                    measurer = measurer,
                    from = point(Start),
                    to = point(Offset(Start.x, Lip.y)),
                    across = point(Lip),
                    label = dropToLipM?.let { "${formatValue(it)} m" },
                    color = accent
                )
                drawMarker(measurer, point(Start), "A", accent, onAccent)
                drawMarker(measurer, point(Lip), "B", accent, onAccent)
            }

            TrailStep.Ramp -> {
                drawAngleArc(
                    measurer = measurer,
                    label = rampAngleDeg?.let { "${formatValue(it)}°" },
                    color = accent
                )
                drawMarker(measurer, point(Lip), "", accent, onAccent)
            }

            TrailStep.Landing -> {
                drawDimension(
                    measurer = measurer,
                    from = point(Lip),
                    to = point(Offset(Lip.x, LandingPoint.y)),
                    across = point(LandingPoint),
                    label = landingDropM?.let { "${formatValue(it)} m" },
                    color = accent
                )
                drawMarker(measurer, point(Lip), "A", accent, onAccent)
                drawMarker(measurer, point(LandingPoint), "B", accent, onAccent)
            }

            TrailStep.RunOut -> {
                drawHorizontalSpan(
                    measurer = measurer,
                    from = point(LandingPoint),
                    to = point(RunOutEnd),
                    label = gradientDeg?.let { "${formatValue(it)}° gradient" },
                    color = accent
                )
            }
        }

        jumpDistanceM?.let {
            drawHorizontalSpan(
                measurer = measurer,
                from = point(Lip),
                to = point(LandingPoint),
                label = "${formatValue(it)} m",
                color = accent,
                above = true
            )
        }

        drawCaption(measurer, point(Offset(0.70f, 0.90f)), "landing", labelColor)
        drawCaption(measurer, point(Offset(0.92f, 0.90f)), "berm", labelColor)
    }
}

private fun DrawScope.point(p: Offset) = Offset(p.x * size.width, p.y * size.height)

private fun DrawScope.drawTerrain(
    takeoffColor: Color,
    takeoffActive: Boolean,
    landingColor: Color,
    landingActive: Boolean,
    trajectoryColor: Color
) {
    val takeoff = Path().apply {
        moveTo(point(Start).x, point(Start).y)
        cubicTo(
            point(Offset(0.16f, 0.16f)).x, point(Offset(0.16f, 0.16f)).y,
            point(Offset(0.24f, 0.42f)).x, point(Offset(0.24f, 0.42f)).y,
            point(RampBase).x, point(RampBase).y
        )
        lineTo(point(KickStart).x, point(KickStart).y)
        quadraticBezierTo(
            point(Offset(0.445f, 0.60f)).x, point(Offset(0.445f, 0.60f)).y,
            point(Lip).x, point(Lip).y
        )
    }
    val landing = Path().apply {
        moveTo(point(LandingPoint).x, point(LandingPoint).y)
        lineTo(point(RunOutStart).x, point(RunOutStart).y)
        lineTo(point(RunOutEnd).x, point(RunOutEnd).y)
        quadraticBezierTo(
            point(Offset(0.965f, 0.81f)).x, point(Offset(0.965f, 0.81f)).y,
            point(BermTop).x, point(BermTop).y
        )
    }

    fillUnder(takeoff, point(Lip).x, takeoffColor, takeoffActive)
    fillUnder(landing, point(BermTop).x, landingColor, landingActive)

    drawPath(takeoff, takeoffColor, style = Stroke(width = if (takeoffActive) 7f else 4f))
    drawPath(landing, landingColor, style = Stroke(width = if (landingActive) 7f else 4f))

    val trajectory = Path().apply {
        moveTo(point(Lip).x, point(Lip).y)
        quadraticBezierTo(
            point(Offset(0.55f, 0.18f)).x, point(Offset(0.55f, 0.18f)).y,
            point(LandingPoint).x, point(LandingPoint).y
        )
    }
    drawPath(
        trajectory,
        trajectoryColor,
        style = Stroke(
            width = 4f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
        )
    )
}

private fun DrawScope.fillUnder(edge: Path, endX: Float, color: Color, active: Boolean) {
    val filled = Path().apply {
        addPath(edge)
        lineTo(endX, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(filled, color, alpha = if (active) 0.18f else 0.08f)
}

@OptIn(ExperimentalTextApi::class)
internal fun DrawScope.drawMarker(
    measurer: TextMeasurer,
    center: Offset,
    label: String,
    color: Color,
    onColor: Color
) {
    drawCircle(color = color, radius = 15f, center = center)
    if (label.isEmpty()) return
    val layout = measurer.measure(
        AnnotatedString(label),
        TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = onColor)
    )
    drawText(
        layout,
        topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f)
    )
}

/** Vertical height dimension: a dashed bracket between two altitudes, labelled. */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawDimension(
    measurer: TextMeasurer,
    from: Offset,
    to: Offset,
    across: Offset,
    label: String?,
    color: Color
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(7f, 7f))
    val x = from.x + size.width * 0.06f

    drawLine(color, Offset(from.x, from.y), Offset(across.x, from.y), 2f, pathEffect = dash)
    drawLine(color, Offset(from.x, to.y), Offset(across.x, to.y), 2f, pathEffect = dash)
    drawLine(color, Offset(x, from.y), Offset(x, to.y), 4f)

    drawLine(color, Offset(x - 7f, from.y + 10f), Offset(x, from.y), 4f)
    drawLine(color, Offset(x + 7f, from.y + 10f), Offset(x, from.y), 4f)
    drawLine(color, Offset(x - 7f, to.y - 10f), Offset(x, to.y), 4f)
    drawLine(color, Offset(x + 7f, to.y - 10f), Offset(x, to.y), 4f)

    label ?: return
    val layout = measurer.measure(
        AnnotatedString(label),
        TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    )
    drawText(layout, topLeft = Offset(x + 12f, (from.y + to.y) / 2f - layout.size.height / 2f))
}

/** Horizontal span with end ticks, labelled - used for jump distance and run-out. */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawHorizontalSpan(
    measurer: TextMeasurer,
    from: Offset,
    to: Offset,
    label: String?,
    color: Color,
    above: Boolean = false
) {
    val y = if (above) minOf(from.y, to.y) - size.height * 0.10f
    else maxOf(from.y, to.y) + size.height * 0.08f

    drawLine(color, Offset(from.x, y), Offset(to.x, y), 3f)
    drawLine(color, Offset(from.x, y - 8f), Offset(from.x, y + 8f), 3f)
    drawLine(color, Offset(to.x, y - 8f), Offset(to.x, y + 8f), 3f)

    label ?: return
    val layout = measurer.measure(
        AnnotatedString(label),
        TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    )
    drawText(
        layout,
        topLeft = Offset((from.x + to.x) / 2f - layout.size.width / 2f, y - layout.size.height - 6f)
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawAngleArc(measurer: TextMeasurer, label: String?, color: Color) {
    val lip = point(Lip)
    val base = point(KickStart)
    drawLine(
        color,
        Offset(base.x - size.width * 0.06f, base.y),
        Offset(lip.x + size.width * 0.05f, base.y),
        2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f))
    )
    drawLine(color, base, lip, 6f)

    label ?: return
    val layout = measurer.measure(
        AnnotatedString(label),
        TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
    )
    drawText(layout, topLeft = Offset(lip.x + 20f, base.y - layout.size.height - 4f))
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawCaption(
    measurer: TextMeasurer,
    at: Offset,
    text: String,
    color: Color
) {
    val layout = measurer.measure(
        AnnotatedString(text),
        TextStyle(fontSize = 11.sp, color = color)
    )
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y))
}
