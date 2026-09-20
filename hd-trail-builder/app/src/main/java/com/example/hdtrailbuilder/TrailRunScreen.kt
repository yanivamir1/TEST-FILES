package com.example.hdtrailbuilder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class RunSample(val elapsedSec: Float, val speedKmh: Float, val altitudeM: Float)

@Composable
fun TrailRunScreen(
    locationRepository: LocationRepository,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val samples = remember { mutableStateListOf<RunSample>() }
    var isRecording by remember { mutableStateOf(false) }
    var recordingJob by remember { mutableStateOf<Job?>(null) }
    var startTimeMs by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose { recordingJob?.cancel() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("בדיקת רמפה - מהירות וגובה (GPS)", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        if (!hasLocationPermission) {
            Text("נדרשת הרשאת מיקום כדי להקליט")
            Button(onClick = onRequestPermission) { Text("בקש הרשאה") }
        } else if (!locationRepository.isGpsProviderAvailable) {
            Text("אין GPS זמין במכשיר זה")
        } else {
            Button(
                onClick = {
                    if (isRecording) {
                        recordingJob?.cancel()
                        isRecording = false
                    } else {
                        samples.clear()
                        startTimeMs = System.currentTimeMillis()
                        isRecording = true
                        recordingJob = scope.launch {
                            locationRepository.locationFlow().collect { sample ->
                                val speed = sample.speedKmh
                                val altitude = sample.altitudeMeters
                                if (speed != null && altitude != null) {
                                    val elapsed = (System.currentTimeMillis() - startTimeMs) / 1000f
                                    samples.add(RunSample(elapsed, speed, altitude))
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isRecording) "עצור הקלטה" else "התחל הקלטה") }

            if (isRecording) {
                Text("מקליט... ${samples.size} נקודות")
            }

            if (samples.isNotEmpty()) {
                Text("גובה (מ')", fontSize = 14.sp)
                TrailChart(
                    values = samples.map { it.elapsedSec to it.altitudeM },
                    lineColor = Color(0xFF4A7DFF),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )

                Spacer(Modifier.height(4.dp))

                Text("מהירות (קמ\"ש)", fontSize = 14.sp)
                TrailChart(
                    values = samples.map { it.elapsedSec to it.speedKmh },
                    lineColor = Color(0xFF3ECF5F),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
        }
    }
}

@Composable
private fun TrailChart(values: List<Pair<Float, Float>>, lineColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas

        val minX = values.first().first
        val maxX = values.last().first
        val minY = values.minOf { it.second }
        val maxY = values.maxOf { it.second }
        val xRange = (maxX - minX).coerceAtLeast(0.001f)
        val yRange = (maxY - minY).coerceAtLeast(0.001f)

        fun toOffset(point: Pair<Float, Float>): Offset {
            val px = (point.first - minX) / xRange * size.width
            val py = size.height - (point.second - minY) / yRange * size.height
            return Offset(px, py)
        }

        val path = values.map { toOffset(it) }
        for (i in 0 until path.size - 1) {
            drawLine(
                color = lineColor,
                start = path[i],
                end = path[i + 1],
                strokeWidth = 4f
            )
        }
    }
}
