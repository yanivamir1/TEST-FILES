package com.example.dhtrailbuilder

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

    sealed interface JumpResult {
        data class Landed(
            val distanceM: Float,
            val landingSpeedMs: Float,
            val airTimeSec: Float,
            val lipSpeedMs: Float,
            val peakAboveLipM: Float
        ) : JumpResult

        /** A step-up the arc never reaches: it peaks below the landing height. */
        data class ShortOfLanding(
            val peakAboveLipM: Float,
            val neededAboveLipM: Float
        ) : JumpResult
    }

    /**
     * Projectile motion from the ramp lip. landingDropM = lip height - landing height:
     * positive when the landing sits below the lip (a normal jump), negative for a
     * step-up where the landing is above the lip.
     */
    fun computeJump(vLipMs: Float, rampAngleDeg: Float, landingDropM: Float): JumpResult {
        val angleRad = Math.toRadians(rampAngleDeg.toDouble())
        val vx = (vLipMs * cos(angleRad)).toFloat()
        val vy0 = (vLipMs * sin(angleRad)).toFloat()
        val peakAboveLip = vy0 * vy0 / (2f * G)

        // 0.5*g*t^2 - vy0*t - landingDropM = 0. On a step-up the discriminant can go
        // negative, which is exactly "the arc never climbs to the landing".
        val discriminant = vy0 * vy0 + 2 * G * landingDropM
        if (discriminant < 0f) {
            return JumpResult.ShortOfLanding(
                peakAboveLipM = peakAboveLip,
                neededAboveLipM = -landingDropM
            )
        }

        val t = (vy0 + sqrt(discriminant)) / G
        val vyLand = vy0 - G * t

        return JumpResult.Landed(
            distanceM = vx * t,
            landingSpeedMs = sqrt(vx * vx + vyLand * vyLand),
            airTimeSec = t,
            lipSpeedMs = vLipMs,
            peakAboveLipM = peakAboveLip
        )
    }

    sealed interface BermResult {
        data class Distance(
            val horizontalM: Float,
            val alongGroundM: Float,
            val dropM: Float
        ) : BermResult

        /** Already at or below the target speed without braking at all. */
        object NoRunOutNeeded : BermResult
    }

    /**
     * Run-out needed between the landing and the berm, to go from landingSpeedMs down to
     * targetEntrySpeedMs given the measured drop between the two (positive = berm lower)
     * and the surface.
     *
     * No gradient is needed. Friction work along a path of length d at gradient theta is
     * mu*g*cos(theta)*d, and d*cos(theta) is exactly the horizontal distance x, so the
     * slope's shape cancels out of the energy balance:
     *
     *     0.5*v1^2 + g*drop - mu*g*x = 0.5*v2^2     ->     x = (0.5*v1^2 + g*drop - 0.5*v2^2) / (mu*g)
     *
     * The along-the-ground distance you would pace out follows from the same two numbers.
     */
    fun bermRunOut(
        landingSpeedMs: Float,
        targetEntrySpeedMs: Float,
        dropToBermM: Float,
        mu: Float
    ): BermResult {
        val energy = 0.5f * landingSpeedMs * landingSpeedMs + G * dropToBermM -
            0.5f * targetEntrySpeedMs * targetEntrySpeedMs
        if (energy <= 0f) return BermResult.NoRunOutNeeded

        val horizontal = energy / (mu * G)
        return BermResult.Distance(
            horizontalM = horizontal,
            alongGroundM = sqrt(horizontal * horizontal + dropToBermM * dropToBermM),
            dropM = dropToBermM
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
