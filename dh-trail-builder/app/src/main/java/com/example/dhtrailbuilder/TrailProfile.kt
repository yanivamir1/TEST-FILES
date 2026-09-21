package com.example.dhtrailbuilder

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
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
        instruction = "A back at the lip, B where you touch down. Negative means a step-up."
    ),
    RunOut(
        stepLabel = "Run-out",
        title = "Landing to berm",
        instruction = "A at the landing, B at the berm - how much further you drop before the turn"
    )
}

// Key points of the side profile, in 0..1 of the drawing area.
private val Start = Offset(0.04f, 0.16f)
private val RampBase = Offset(0.34f, 0.63f)
private val KickStart = Offset(0.39f, 0.615f)
private val Lip = Offset(0.46f, 0.40f)
private val LandingDown = Offset(0.645f, 0.55f)
private val LandingUp = Offset(0.645f, 0.30f)
private val RunOutEnd = Offset(0.90f, 0.81f)
private val BermTop = Offset(0.985f, 0.56f)

/**
 * Side view of the whole run. Every measurement point is drawn from the start: points that
 * already have a value are filled in the accent colour with the value beside them, points
 * still missing one are hollow and dim. The segment being measured is highlighted, and the
 * highlight animates as the rider moves from one input to the next.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun TrailProfile(
    activeStep: TrailStep,
    modifier: Modifier = Modifier,
    dropToLipM: Float? = null,
    rampAngleDeg: Float? = null,
    landingDropM: Float? = null,
    dropToBermM: Float? = null,
    jumpDistanceM: Float? = null
) {
    val measurer = rememberTextMeasurer()
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val dim = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val spring = tween<Float>(durationMillis = 350)
    val rollInGlow by animateFloatAsState(
        targetValue = if (activeStep == TrailStep.RollIn) 1f else 0f,
        animationSpec = spring,
        label = "rollInGlow"
    )
    val rampGlow by animateFloatAsState(
        targetValue = if (activeStep == TrailStep.Ramp) 1f else 0f,
        animationSpec = spring,
        label = "rampGlow"
    )
    val landingGlow by animateFloatAsState(
        targetValue = if (activeStep == TrailStep.Landing) 1f else 0f,
        animationSpec = spring,
        label = "landingGlow"
    )
    val runOutGlow by animateFloatAsState(
        targetValue = if (activeStep == TrailStep.RunOut) 1f else 0f,
        animationSpec = spring,
        label = "runOutGlow"
    )

    // A step-up puts the landing above the lip; show the terrain the rider actually described.
    val stepUp = (landingDropM ?: 0f) < 0f
    val landingPoint = if (stepUp) LandingUp else LandingDown
    val landingTarget by animateFloatAsState(
        targetValue = if (stepUp) LandingUp.y else LandingDown.y,
        animationSpec = spring,
        label = "landingY"
    )

    Canvas(modifier = modifier) {
        val landing = Offset(landingPoint.x, landingTarget)
        val takeoffGlow = maxOf(rollInGlow, rampGlow)
        val landingSideGlow = maxOf(landingGlow, runOutGlow)

        drawTerrain(
            landing = landing,
            takeoffColor = lerp(dim, accent, takeoffGlow),
            takeoffGlow = takeoffGlow,
            landingColor = lerp(dim, accent, landingSideGlow),
            landingGlow = landingSideGlow,
            trajectoryColor = dim,
            kickerColor = lerp(dim, accent, maxOf(rampGlow, if (rampAngleDeg != null) 0.55f else 0f))
        )

        // The dimension bracket for whichever measurement is active, faded by its own glow.
        if (rollInGlow > 0.01f) {
            drawDimension(
                measurer = measurer,
                from = point(Start),
                to = Offset(point(Start).x, point(Lip).y),
                across = point(Lip),
                label = dropToLipM?.let { "${formatValue(it)} m" },
                color = accent.copy(alpha = rollInGlow)
            )
        }
        if (rampGlow > 0.01f) {
            drawAngleArc(
                measurer = measurer,
                label = rampAngleDeg?.let { "${formatValue(it)}°" },
                color = accent.copy(alpha = rampGlow)
            )
        }
        if (landingGlow > 0.01f) {
            drawDimension(
                measurer = measurer,
                from = point(Lip),
                to = Offset(point(Lip).x, landing.y),
                across = landing,
                label = landingDropM?.let {
                    if (it < 0f) "${formatValue(-it)} m up" else "${formatValue(it)} m"
                },
                color = accent.copy(alpha = landingGlow)
            )
        }
        if (runOutGlow > 0.01f) {
            drawDimension(
                measurer = measurer,
                from = landing,
                to = Offset(landing.x, point(BermTop).y),
                across = point(BermTop),
                label = dropToBermM?.let { "${formatValue(it)} m" },
                color = accent.copy(alpha = runOutGlow)
            )
        }

        jumpDistanceM?.let {
            drawHorizontalSpan(
                measurer = measurer,
                from = point(Lip),
                to = landing,
                label = "${formatValue(it)} m",
                color = accent,
                above = true
            )
        }

        // Every point, always - filled once it has a value, hollow until then.
        drawPoint(measurer, point(Start), "A", dropToLipM != null, rollInGlow, accent, onAccent, dim)
        drawPoint(
            measurer, point(Lip), "B",
            dropToLipM != null || landingDropM != null,
            maxOf(rollInGlow, rampGlow, landingGlow), accent, onAccent, dim
        )
        drawPoint(
            measurer, landing, "C", landingDropM != null,
            maxOf(landingGlow, runOutGlow), accent, onAccent, dim
        )
        drawPoint(measurer, point(BermTop), "D", dropToBermM != null, runOutGlow, accent, onAccent, dim)

        drawCaption(measurer, point(Offset(0.04f, 0.95f)), "start", labelColor)
        drawCaption(measurer, point(Offset(0.46f, 0.95f)), "lip", labelColor)
        drawCaption(measurer, Offset(landing.x, size.height * 0.95f), "landing", labelColor)
        drawCaption(measurer, point(Offset(0.93f, 0.95f)), "berm", labelColor)
    }
}

private fun DrawScope.point(p: Offset) = Offset(p.x * size.width, p.y * size.height)

private fun DrawScope.drawTerrain(
    landing: Offset,
    takeoffColor: Color,
    takeoffGlow: Float,
    landingColor: Color,
    landingGlow: Float,
    trajectoryColor: Color,
    kickerColor: Color
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
    val landingSide = Path().apply {
        moveTo(landing.x, landing.y)
        lineTo(point(RunOutEnd).x, point(RunOutEnd).y)
        quadraticBezierTo(
            point(Offset(0.965f, 0.81f)).x, point(Offset(0.965f, 0.81f)).y,
            point(BermTop).x, point(BermTop).y
        )
    }

    fillUnder(takeoff, point(Lip).x, takeoffColor, takeoffGlow)
    fillUnder(landingSide, point(BermTop).x, landingColor, landingGlow)

    drawPath(takeoff, takeoffColor, style = Stroke(width = 4f + 3f * takeoffGlow))
    drawPath(landingSide, landingColor, style = Stroke(width = 4f + 3f * landingGlow))

    // The kicker face, drawn over the takeoff so the ramp angle reads once it is known.
    drawLine(kickerColor, point(KickStart), point(Lip), 6f)

    val trajectory = Path().apply {
        moveTo(point(Lip).x, point(Lip).y)
        quadraticBezierTo(
            (point(Lip).x + landing.x) / 2f,
            minOf(point(Lip).y, landing.y) - size.height * 0.22f,
            landing.x, landing.y
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

private fun DrawScope.fillUnder(edge: Path, endX: Float, color: Color, glow: Float) {
    val filled = Path().apply {
        addPath(edge)
        lineTo(endX, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(filled, color, alpha = 0.08f + 0.10f * glow)
}

/** A measurement point: filled with its letter once measured, hollow while it is still missing. */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawPoint(
    measurer: TextMeasurer,
    center: Offset,
    label: String,
    hasValue: Boolean,
    glow: Float,
    accent: Color,
    onAccent: Color,
    dim: Color
) {
    val radius = 13f + 4f * glow
    if (hasValue) {
        if (glow > 0.01f) {
            drawCircle(color = accent.copy(alpha = 0.25f * glow), radius = radius + 7f, center = center)
        }
        drawMarker(measurer, center, label, accent, onAccent, radius)
    } else {
        drawCircle(color = dim, radius = radius, center = center, style = Stroke(width = 3f))
        val layout = measurer.measure(
            AnnotatedString(label),
            TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dim)
        )
        drawText(
            layout,
            topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f)
        )
    }
}

@OptIn(ExperimentalTextApi::class)
internal fun DrawScope.drawMarker(
    measurer: TextMeasurer,
    center: Offset,
    label: String,
    color: Color,
    onColor: Color,
    radius: Float = 15f
) {
    drawCircle(color = color, radius = radius, center = center)
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

    label ?: return
    val layout = measurer.measure(
        AnnotatedString(label),
        TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    )
    drawText(layout, topLeft = Offset(x + 12f, (from.y + to.y) / 2f - layout.size.height / 2f))
}

/** Horizontal span with end ticks, labelled - used for the jump distance. */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawHorizontalSpan(
    measurer: TextMeasurer,
    from: Offset,
    to: Offset,
    label: String?,
    color: Color,
    above: Boolean = false
) {
    val y = if (above) minOf(from.y, to.y) - size.height * 0.12f
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
        TextStyle(fontSize = 10.sp, color = color)
    )
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y))
}
