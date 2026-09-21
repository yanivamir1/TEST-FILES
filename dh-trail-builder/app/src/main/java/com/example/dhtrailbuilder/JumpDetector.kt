package com.example.dhtrailbuilder

/** A detected airborne window, by wall-clock timestamp. */
data class JumpEvent(val takeoffAtMs: Long, val landingAtMs: Long)

/**
 * Free-fall based jump detector: while the phone (on the bike) is airborne, the raw
 * accelerometer's proper-acceleration magnitude drops to roughly zero, versus ~9.8 m/s² at
 * rest or while riding. A sustained dip below [freeFallThresholdMs2] that lasts between
 * [minAirTimeMs] and [maxAirTimeMs] is reported as a jump.
 *
 * This is a heuristic, not a certainty - phone mount position, trail chatter and the exact
 * thresholds all affect it. [minAirTimeMs] exists to filter out bumps and potholes;
 * [maxAirTimeMs] is a sanity cap against a spurious multi-second dip (e.g. the phone coming
 * loose) being reported as a jump.
 */
class JumpDetector(
    private val freeFallThresholdMs2: Float = 3f,
    private val minAirTimeMs: Long = 200L,
    private val maxAirTimeMs: Long = 4000L
) {
    private var fallStartMs: Long? = null

    /** Feed one accelerometer magnitude sample; returns a [JumpEvent] exactly when a landing closes a valid window. */
    fun onSample(magnitude: Float, atMs: Long): JumpEvent? {
        if (magnitude < freeFallThresholdMs2) {
            if (fallStartMs == null) fallStartMs = atMs
            return null
        }

        val start = fallStartMs ?: return null
        fallStartMs = null
        val duration = atMs - start
        return if (duration in minAirTimeMs..maxAirTimeMs) {
            JumpEvent(takeoffAtMs = start, landingAtMs = atMs)
        } else {
            null
        }
    }

    fun reset() {
        fallStartMs = null
    }
}
