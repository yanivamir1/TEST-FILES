package com.example.hdtrailbuilder

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

    /**
     * Distance needed after landing to coast/brake from landingSpeedMs down to targetEntrySpeedMs,
     * given an elevation drop (dropToBermM, can be negative if the berm is higher/uphill) and an
     * effective friction/rolling-resistance coefficient mu (see FrictionPreset).
     * Returns null when no braking distance is needed (already at or below target speed).
     */
    fun bermApproachDistance(
        landingSpeedMs: Float,
        dropToBermM: Float,
        targetEntrySpeedMs: Float,
        mu: Float
    ): Float? {
        val numerator = 0.5f * landingSpeedMs * landingSpeedMs + G * dropToBermM -
            0.5f * targetEntrySpeedMs * targetEntrySpeedMs
        if (numerator <= 0f) return null
        return numerator / (mu * G)
    }
}

enum class FrictionPreset(val label: String, val description: String, val mu: Float) {
    PAVED("Paved / hardpack", "Asphalt or compacted surface", 0.03f),
    PACKED_TRAIL("Packed trail", "Typical ridden-in singletrack", 0.06f),
    LOOSE_DIRT("Loose dirt", "Dry soil, slightly loose on top", 0.10f),
    SAND_GRAVEL("Sand or gravel", "Soft, noticeably draggy", 0.18f),
    SCREE("Loose scree", "Dry rocks, worst case", 0.30f)
}
