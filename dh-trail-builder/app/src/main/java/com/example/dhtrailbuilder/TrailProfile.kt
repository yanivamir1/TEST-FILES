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

/** Which measurement of the jump the rider is taking right now. */
enum class TrailStep(val title: String, val instruction: String) {
    RollIn("Drop to the lip", "Capture at your start point, then at the lip"),
    Ramp("Ramp angle", "Phone on the ramp face, length pointing down the slope"),
    Landing("Drop to the landing", "Lip is already set - capture where you touch down. Negative = step-up.")
}

// Side profile in 0..1 of the drawing area: roll-in, kicker, lip, landing.
private val Start = Offset(0.05f, 0.18f)
private val RampBase = Offset(0.33f, 0.66f)
private val KickStart = Offset(0.39f, 0.645f)
private val Lip = Offset(0.46f, 0.42f)
private const val LandingX = 0.76f
private const val LandingDownY = 0.60f
private const val LandingUpY = 0.30f
private val SlopeEnd = Offset(0.97f, 0.80f)

/**
 * The jump, and only the jump: descent, ramp, landing - with the distance between lip and
 * landing marked as the thing being worked out. The berm lives on its own screen.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun TrailProfile(
    activeStep: TrailStep,
    modifier: Modifier = Modifier,
    dropToLipM: Float? = null,
    rampAngleDeg: Float? = null,
    landingDropM: Float? = null,
    jumpDistanceM: Float? = null
) {
    val measurer = rememberTextMeasurer()
    val accent = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val caption = MaterialTheme.colorScheme.onSurfaceVariant

    val spec = tween<Float>(durationMillis = 300)
    val rollInGlow by animateFloatAsState(
        if (activeStep == TrailStep.RollIn) 1f else 0f, spec, label = "rollIn"
    )
    val rampGlow by animateFloatAsState(
        if (activeStep == TrailStep.Ramp) 1f else 0f, spec, label = "ramp"
    )
    val landingGlow by animateFloatAsState(
        if (activeStep == TrailStep.Landing) 1f else 0f, spec, label = "landing"
    )
    val landingY by animateFloatAsState(
        if ((landingDropM ?: 0f) < 0f) LandingUpY else LandingDownY, spec, label = "landingY"
    )

    Canvas(modifier = modifier) {
        val start = point(Start)
        val lip = point(Lip)
        val landing = Offset(LandingX * size.width, landingY * size.height)
        val takeoffGlow = maxOf(rollInGlow, rampGlow)

        drawTakeoff(lerp(dim, accent, takeoffGlow), takeoffGlow)
        drawLandingSlope(landing, lerp(dim, accent, landingGlow), landingGlow)
        drawFlightArc(lip, landing, dim)

        if (rampAngleDeg != null || rampGlow > 0.01f) {
            drawLine(lerp(dim, accent, maxOf(rampGlow, 0.6f)), point(KickStart), lip, 6f)
        }

        if (rollInGlow > 0.01f) {
            drawDrop(
                measurer, start, Offset(start.x, lip.y), lip.x,
                dropToLipM?.let { "${formatValue(it)} m" }, accent.copy(alpha = rollInGlow)
            )
        }
        if (landingGlow > 0.01f) {
            drawDrop(
                measurer, lip, Offset(lip.x, landing.y), landing.x,
                landingDropM?.let {
                    if (it < 0f) "${formatValue(-it)} m up" else "${formatValue(it)} m"
                },
                accent.copy(alpha = landingGlow)
            )
        }
        if (rampGlow > 0.01f && rampAngleDeg != null) {
            drawLabel(
                measurer, "${formatValue(rampAngleDeg)}°",
                Offset(lip.x + 14f, point(KickStart).y - 34f),
                accent.copy(alpha = rampGlow), 14.sp
            )
        }

        drawPointMarker(measurer, start, "A", dropToLipM != null, rollInGlow, accent, dim)
        drawPointMarker(
            measurer, lip, "B", dropToLipM != null || landingDropM != null,
            maxOf(rollInGlow, rampGlow, landingGlow), accent, dim
        )
        drawPointMarker(measurer, landing, "C", landingDropM != null, landingGlow, accent, dim)

        // The unknown the whole screen is for: how far out the landing is.
        drawDistanceSpan(
            measurer = measurer,
            fromX = lip.x,
            toX = landing.x,
            y = size.height * 0.94f,
            label = jumpDistanceM?.let { "${formatValue(it)} m" } ?: "?",
            color = if (jumpDistanceM != null) accent else caption
        )
    }
}

private fun DrawScope.point(p: Offset) = Offset(p.x * size.width, p.y * size.height)

private fun DrawScope.drawTakeoff(color: Color, glow: Float) {
    val path = Path().apply {
        moveTo(point(Start).x, point(Start).y)
        cubicTo(
            point(Offset(0.16f, 0.18f)).x, point(Offset(0.16f, 0.18f)).y,
            point(Offset(0.23f, 0.44f)).x, point(Offset(0.23f, 0.44f)).y,
            point(RampBase).x, point(RampBase).y
        )
        lineTo(point(KickStart).x, point(KickStart).y)
        quadraticBezierTo(
            point(Offset(0.44f, 0.62f)).x, point(Offset(0.44f, 0.62f)).y,
            point(Lip).x, point(Lip).y
        )
    }
    fillUnder(path, point(Lip).x, color, glow)
    drawPath(path, color, style = Stroke(width = 4f + 3f * glow))
}

private fun DrawScope.drawLandingSlope(landing: Offset, color: Color, glow: Float) {
    val path = Path().apply {
        moveTo(landing.x, landing.y)
        lineTo(point(SlopeEnd).x, point(SlopeEnd).y)
    }
    val filled = Path().apply {
        addPath(path)
        lineTo(point(SlopeEnd).x, size.height)
        lineTo(landing.x, size.height)
        close()
    }
    drawPath(filled, color, alpha = 0.08f + 0.10f * glow)
    drawPath(path, color, style = Stroke(width = 4f + 3f * glow))
}

private fun DrawScope.drawFlightArc(lip: Offset, landing: Offset, color: Color) {
    val arc = Path().apply {
        moveTo(lip.x, lip.y)
        quadraticBezierTo(
            (lip.x + landing.x) / 2f,
            minOf(lip.y, landing.y) - size.height * 0.20f,
            landing.x, landing.y
        )
    }
    drawPath(
        arc, color,
        style = Stroke(width = 3.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(11f, 9f)))
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

/**
 * A lettered, measured point: a small dot sits right on the trail line (that's the actual
 * point being measured), and its letter floats above the dot rather than inside it - a
 * letter drawn on top of the ground/arc lines passing through the point is unreadable.
 */
@OptIn(ExperimentalTextApi::class)
internal fun DrawScope.drawPointMarker(
    measurer: TextMeasurer,
    center: Offset,
    label: String,
    hasValue: Boolean,
    glow: Float,
    accent: Color,
    dim: Color
) {
    val radius = 6f + 2f * glow
    val color = if (hasValue) accent else dim
    if (hasValue && glow > 0.01f) {
        drawCircle(accent.copy(alpha = 0.3f * glow), radius + 6f, center)
    }
    if (hasValue) {
        drawCircle(color, radius, center)
    } else {
        drawCircle(color, radius, center, style = Stroke(width = 2.5f))
    }

    val layout = measurer.measure(
        AnnotatedString(label),
        TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    )
    drawText(
        layout,
        topLeft = Offset(center.x - layout.size.width / 2f, center.y - radius - layout.size.height - 4f)
    )
}

/** Vertical height difference between two altitudes, with its value. */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawDrop(
    measurer: TextMeasurer,
    from: Offset,
    to: Offset,
    acrossX: Float,
    label: String?,
    color: Color
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
    val x = from.x + size.width * 0.05f

    drawLine(color, Offset(from.x, from.y), Offset(acrossX, from.y), 1.8f, pathEffect = dash)
    drawLine(color, Offset(from.x, to.y), Offset(acrossX, to.y), 1.8f, pathEffect = dash)
    drawLine(color, Offset(x, from.y), Offset(x, to.y), 3.5f)

    label ?: return
    drawLabel(measurer, label, Offset(x + 8f, (from.y + to.y) / 2f - 9f), color, 13.sp)
}

/** The horizontal distance being solved for, under the picture. */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawDistanceSpan(
    measurer: TextMeasurer,
    fromX: Float,
    toX: Float,
    y: Float,
    label: String,
    color: Color
) {
    drawLine(color, Offset(fromX, y), Offset(toX, y), 2.5f)
    drawLine(color, Offset(fromX, y - 6f), Offset(fromX, y + 6f), 2.5f)
    drawLine(color, Offset(toX, y - 6f), Offset(toX, y + 6f), 2.5f)

    val layout = measurer.measure(
        AnnotatedString(label),
        TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    )
    drawText(
        layout,
        topLeft = Offset((fromX + toX) / 2f - layout.size.width / 2f, y - layout.size.height - 3f)
    )
}

@OptIn(ExperimentalTextApi::class)
internal fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    topLeft: Offset,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp
) {
    val layout = measurer.measure(
        AnnotatedString(text),
        TextStyle(fontSize = fontSize, fontWeight = FontWeight.Bold, color = color)
    )
    drawText(layout, topLeft = topLeft)
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
