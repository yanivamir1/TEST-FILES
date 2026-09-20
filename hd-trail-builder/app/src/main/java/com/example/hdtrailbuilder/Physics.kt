package com.example.hdtrailbuilder

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure math, no Android dependency - see PHYSICS.md for the derivations and sign conventions. */
object Physics {
    const val G = 9.81f

    /** Speed at the ramp lip from energy conservation. dropToLip = start height - lip height. */
    fun lipSpeed(startSpeedMs: Float, dropToLipM: Float): Float {
        val v2 = startSpeedMs * startSpeedMs + 2 * G * dropToLipM
        return sqrt(v2.coerceAtLeast(0f))
    }

    data class JumpResult(
        val distanceM: Float,
        val landingSpeedMs: Float,
        val airTimeSec: Float
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
            airTimeSec = t
        )
    }

    /**
     * Distance needed after landing to coast/brake from landingSpeedMs down to targetEntrySpeedMs,
     * given an elevation drop (dropToBermM, can be negative if the berm is higher/uphill) and an
     * effective friction/rolling-resistance coefficient mu (see FrictionPreset).
     * Returns null when no braking distance is needed (already at or below target speed at zero distance).
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

enum class FrictionPreset(val label: String, val mu: Float) {
    PAVED("אספלט / מסלול מהודק", 0.03f),
    PACKED_TRAIL("שטח חבוט/כבוש (trail רגיל)", 0.06f),
    LOOSE_DIRT("אדמה רגילה, קצת רופפת", 0.10f),
    SAND_GRAVEL("חול/חצץ רופף", 0.18f),
    SCREE("דרדרת / אבנים משוחררות", 0.30f)
}
