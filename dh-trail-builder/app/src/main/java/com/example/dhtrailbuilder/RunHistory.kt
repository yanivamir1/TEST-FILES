package com.example.dhtrailbuilder

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class RecordMode(val label: String) {
    Gps("Ride (GPS)"),
    NoGps("Test (no GPS)")
}

/** A full recorded run, as saved to disk. */
data class SavedRun(
    val id: String,
    val startedAtMs: Long,
    val mode: RecordMode,
    val samples: List<RunSample>,
    val detectedJump: JumpEvent?,
    val predictedDistanceM: Float?,
    // The jump the run was ridden against, so the "could have jumped" estimate can be
    // recomputed from the recorded speed later on.
    val rampAngleDeg: Float? = null,
    val landingDropM: Float? = null
)

/** The lightweight header shown in the run list, without holding every sample in memory. */
data class RunSummary(
    val id: String,
    val startedAtMs: Long,
    val mode: RecordMode,
    val hasJump: Boolean,
    val distanceM: Float?,
    val durationSec: Float
)

/** Saves and loads recorded runs as small JSON files under the app's private storage. */
class RunStorage(context: Context) {
    private val dir = File(context.filesDir, "runs").apply { mkdirs() }

    // A separate folder (not *.json directly under dir) so the in-progress draft never shows
    // up as a phantom entry in listRuns().
    private val draftDir = File(context.filesDir, "draft").apply { mkdirs() }
    private val draftFile = File(draftDir, "current.json")

    suspend fun saveRun(run: SavedRun) = withContext(Dispatchers.IO) {
        File(dir, "${run.id}.json").writeText(run.toJson().toString())
    }

    suspend fun listRuns(): List<RunSummary> = withContext(Dispatchers.IO) {
        dir.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file -> runCatching { file.readText().toSavedRun() }.getOrNull() }
            ?.map { it.toSummary() }
            ?.sortedByDescending { it.startedAtMs }
            ?: emptyList()
    }

    suspend fun loadRun(id: String): SavedRun? = withContext(Dispatchers.IO) {
        runCatching { File(dir, "$id.json").readText().toSavedRun() }.getOrNull()
    }

    suspend fun deleteRun(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
        Unit
    }

    /**
     * Called every few seconds while a run is being recorded, so that if the phone (or an
     * over-eager battery manager) kills the app outright, at most a few seconds of the ride
     * are lost rather than all of it - see [recoverDraft].
     */
    suspend fun saveDraft(run: SavedRun) = withContext(Dispatchers.IO) {
        runCatching { draftFile.writeText(run.toJson().toString()) }
        Unit
    }

    suspend fun clearDraft() = withContext(Dispatchers.IO) {
        draftFile.delete()
        Unit
    }

    /**
     * A leftover draft means the app never got to a clean stop last time (killed, crashed,
     * battery pulled). Called once on launch: if one exists, it is filed into History as-is
     * and the draft slot is cleared, so nothing is silently lost.
     */
    suspend fun recoverDraft(): SavedRun? = withContext(Dispatchers.IO) {
        if (!draftFile.exists()) return@withContext null
        val recovered = runCatching { draftFile.readText().toSavedRun() }.getOrNull()
        draftFile.delete()
        recovered?.takeIf { it.samples.size >= 2 }
    }
}

private fun SavedRun.toSummary(): RunSummary = RunSummary(
    id = id,
    startedAtMs = startedAtMs,
    mode = mode,
    hasJump = detectedJump != null,
    distanceM = if (mode == RecordMode.Gps) samples.lastOrNull()?.cumulativeDistanceM else null,
    durationSec = samples.lastOrNull()?.elapsedSec ?: 0f
)

private fun SavedRun.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("startedAtMs", startedAtMs)
    put("mode", mode.name)
    put("predictedDistanceM", predictedDistanceM?.toDouble() ?: JSONObject.NULL)
    put("rampAngleDeg", rampAngleDeg?.toDouble() ?: JSONObject.NULL)
    put("landingDropM", landingDropM?.toDouble() ?: JSONObject.NULL)
    put("takeoffAtMs", detectedJump?.takeoffAtMs ?: JSONObject.NULL)
    put("landingAtMs", detectedJump?.landingAtMs ?: JSONObject.NULL)
    put(
        "samples",
        JSONArray().apply {
            samples.forEach { s ->
                put(
                    JSONObject().apply {
                        put("t", s.timestampMs)
                        put("e", s.elapsedSec.toDouble())
                        put("a", s.altitudeM.toDouble())
                        put("s", s.speedKmh?.toDouble() ?: JSONObject.NULL)
                        put("d", s.cumulativeDistanceM?.toDouble() ?: JSONObject.NULL)
                    }
                )
            }
        }
    )
}

private fun String.toSavedRun(): SavedRun {
    val json = JSONObject(this)
    val samplesJson = json.getJSONArray("samples")
    val samples = (0 until samplesJson.length()).map { i ->
        val s = samplesJson.getJSONObject(i)
        RunSample(
            timestampMs = s.getLong("t"),
            elapsedSec = s.getDouble("e").toFloat(),
            altitudeM = s.getDouble("a").toFloat(),
            speedKmh = if (s.isNull("s")) null else s.getDouble("s").toFloat(),
            cumulativeDistanceM = if (s.isNull("d")) null else s.getDouble("d").toFloat()
        )
    }
    val takeoff = if (json.isNull("takeoffAtMs")) null else json.getLong("takeoffAtMs")
    val landing = if (json.isNull("landingAtMs")) null else json.getLong("landingAtMs")
    return SavedRun(
        id = json.getString("id"),
        startedAtMs = json.getLong("startedAtMs"),
        mode = RecordMode.valueOf(json.getString("mode")),
        samples = samples,
        detectedJump = if (takeoff != null && landing != null) JumpEvent(takeoff, landing) else null,
        predictedDistanceM = if (json.isNull("predictedDistanceM")) {
            null
        } else {
            json.getDouble("predictedDistanceM").toFloat()
        },
        rampAngleDeg = json.optionalFloat("rampAngleDeg"),
        landingDropM = json.optionalFloat("landingDropM")
    )
}

private fun JSONObject.optionalFloat(key: String): Float? =
    if (!has(key) || isNull(key)) null else getDouble(key).toFloat()
