package com.example.dhtrailbuilder

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure math, no Android dependency - see PHYSICS.md for the derivations and sign conventions. */
object Physics {
    const val G = 9.81f
    const val STANDARD_SEA_LEVEL_HPA = 1013.25f

    private const val BARO_SCALE = 44330.0
    private const val BARO_EXPONENT = 5.255

    /** Barometric altitude in meters: 44330 * (1 - (p / p0)^(1/5.255)). */
    fun altitudeFrom(seaLevelHpa: Float, currentHpa: Float): Float {
        val ratio = (currentHpa / seaLevelHpa).toDouble()
        return (BARO_SCALE * (1.0 - ratio.pow(1.0 / BARO_EXPONENT))).toFloat()
    }

    /**
     * Inverse of [altitudeFrom]: given the pressure measured at a known altitude,
     * what is the local sea-level pressure? Used to calibrate the absolute readout.
     */
    fun seaLevelPressureFor(currentHpa: Float, knownAltitudeM: Float): Float {
        val factor = (1.0 - knownAltitudeM / BARO_SCALE).pow(BARO_EXPONENT)
        return (currentHpa / factor).toFloat()
    }

    /** Speed at the ramp lip from energy conservation. dropToLip = start height - lip height. */
    fun lipSpeed(startSpeedMs: Float, dropToLipM: Float): Float {
        val v2 = startSpeedMs * startSpeedMs + 2 * G * dropToLipM
        return sqrt(v2.coerceAtLeast(0f))
    }

    data class JumpResult(
        val distanceM: Float,
        val landingSpeedMs: Float,
        val airTimeSec: Float,
        val lipSpeedMs: Float
    )

    /**
     * Projectile motion from the ramp lip. landingDropM = lip height - landing height,
     * must be >= 0 (landing below the lip, the normal case for a jump).
     */
    fun computeJump(vLipMs: Float, rampAngleDeg: Float, landingDropM: Float): JumpResult? {
        if (landingDropM < 0f) return null

        val angleRad = Math.toRadians(rampAngleDeg.toDouble())
        val vx = (vLipMs * cos(angleRad)).toFloat()
        val vy0 = (vLipMs * sin(angleRad)).toFloat()

        // 0.5*g*t^2 - vy0*t - landingDropM = 0, positive root:
        val discriminant = vy0 * vy0 + 2 * G * landingDropM
        val t = (vy0 + sqrt(discriminant)) / G
        val vyLand = vy0 - G * t

        return JumpResult(
            distanceM = vx * t,
            landingSpeedMs = sqrt(vx * vx + vyLand * vyLand),
            airTimeSec = t,
            lipSpeedMs = vLipMs
        )
    }

    sealed interface BermResult {
        /** Distance needed, plus the deceleration achieved and the height lost over it. */
        data class Distance(
            val distanceM: Float,
            val decelerationG: Float,
            val elevationDropM: Float
        ) : BermResult

        /** Already at or below the target speed on touchdown. */
        object NoBrakingNeeded : BermResult

        /** The run-out descends faster than this surface can scrub speed - you keep accelerating. */
        data class CannotSlow(val gradientLimitDeg: Float) : BermResult
    }

    /**
     * Distance needed after landing to get from landingSpeedMs down to targetEntrySpeedMs on a
     * run-out of the given gradient (positive = descending) and surface.
     *
     * Friction and gravity are resolved along the slope:
     *     a = mu*g*cos(theta) - g*sin(theta)
     * so a run-out steeper than the friction angle (atan(mu)) can never slow the rider down,
     * which is reported rather than turned into a meaningless number.
     */
    fun bermApproachDistance(
        landingSpeedMs: Float,
        targetEntrySpeedMs: Float,
        gradientDeg: Float,
        mu: Float
    ): BermResult {
        if (targetEntrySpeedMs >= landingSpeedMs) return BermResult.NoBrakingNeeded

        val theta = Math.toRadians(gradientDeg.toDouble())
        val deceleration = (mu * G * cos(theta) - G * sin(theta)).toFloat()
        if (deceleration <= 0f) {
            val limit = Math.toDegrees(atan(mu.toDouble())).toFloat()
            return BermResult.CannotSlow(gradientLimitDeg = limit)
        }

        val distance = (landingSpeedMs * landingSpeedMs - targetEntrySpeedMs * targetEntrySpeedMs) /
            (2f * deceleration)
        return BermResult.Distance(
            distanceM = distance,
            decelerationG = deceleration / G,
            elevationDropM = (distance * sin(theta)).toFloat()
        )
    }
}

/**
 * Effective friction coefficients per surface.
 *
 * [muBraking] is traction-limited braking - what a rider actually does approaching a berm. It is
 * roughly ten times [muRolling], and is capped near 0.6 because a bike pitches over the bars
 * before it can use more grip than that.
 *
 * [muRolling] is rolling resistance only: coasting with the brakes off.
 */
enum class FrictionPreset(
    val label: String,
    val description: String,
    val muBraking: Float,
    val muRolling: Float
) {
    HARDPACK("Hardpack / dry loam", "Grippiest - fresh loam or firm dirt", 0.60f, 0.015f),
    PACKED_TRAIL("Packed trail", "Typical ridden-in singletrack", 0.50f, 0.030f),
    LOOSE_OVER_HARD("Loose over hardpack", "Dust and pebbles on a firm base", 0.38f, 0.050f),
    SAND_GRAVEL("Sand or deep gravel", "Soft and draggy, brakes wash out", 0.28f, 0.110f),
    SCREE("Loose scree / dust", "Dry rocks rolling underneath", 0.22f, 0.130f),
    WET_ROOTS("Wet roots or mud", "Slick - almost no braking grip", 0.18f, 0.060f);

    fun mu(braking: Boolean): Float = if (braking) muBraking else muRolling
}
