package com.example.hdtrailbuilder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            !hasLocationPermission -> SectionCard(
                title = "Ride log",
                subtitle = "Records GPS speed and altitude while you ride"
            ) {
                Text(
                    "Location permission is required to record a run.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("Grant permission")
                }
            }

            !locationRepository.isGpsProviderAvailable -> NoticeCard(
                "No GPS provider available on this device.",
                isError = true
            )

            else -> {
                SectionCard(
                    title = "Ride log",
                    subtitle = "Records GPS speed and altitude while you ride"
                ) {
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
                                            val elapsed =
                                                (System.currentTimeMillis() - startTimeMs) / 1000f
                                            samples.add(RunSample(elapsed, speed, altitude))
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (isRecording) "Stop recording" else "Start recording") }

                    Text(
                        text = when {
                            isRecording -> "Recording · ${samples.size} points"
                            samples.isEmpty() -> "Waiting to start. Keep the phone on you and ride the run-in."
                            else -> "Stopped · ${samples.size} points"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (samples.isNotEmpty()) {
                    RunStats(samples)

                    SectionCard(title = "Altitude", subtitle = "meters over time") {
                        TrailChart(
                            values = samples.map { it.elapsedSec to it.altitudeM },
                            lineColor = MaterialTheme.colorScheme.primary,
                            gridColor = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )
                        AxisLabels(
                            minValue = samples.minOf { it.altitudeM },
                            maxValue = samples.maxOf { it.altitudeM },
                            unit = "m"
                        )
                    }

                    SectionCard(title = "Speed", subtitle = "km/h over time") {
                        TrailChart(
                            values = samples.map { it.elapsedSec to it.speedKmh },
                            lineColor = MaterialTheme.colorScheme.secondary,
                            gridColor = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )
                        AxisLabels(
                            minValue = samples.minOf { it.speedKmh },
                            maxValue = samples.maxOf { it.speedKmh },
                            unit = "km/h"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunStats(samples: List<RunSample>) {
    val maxSpeed = samples.maxOf { it.speedKmh }
    val duration = samples.last().elapsedSec
    val gain = samples.maxOf { it.altitudeM } - samples.minOf { it.altitudeM }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ReadoutTile(label = "TOP SPEED", value = formatValue(maxSpeed), unit = "km/h")
        ReadoutTile(label = "ELEVATION RANGE", value = formatValue(gain), unit = "m")
        ReadoutTile(label = "DURATION", value = formatValue(duration, 0), unit = "s")
    }
}

@Composable
private fun AxisLabels(minValue: Float, maxValue: Float, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "min ${formatValue(minValue)} $unit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "max ${formatValue(maxValue)} $unit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrailChart(
    values: List<Pair<Float, Float>>,
    lineColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        // Horizontal grid lines, drawn regardless of how much data there is.
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = size.height * i / gridLines
            drawLine(
                color = gridColor.copy(alpha = 0.25f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5f
            )
        }

        if (values.size < 2) return@Canvas

        val minX = values.first().first
        val maxX = values.last().first
        val minY = values.minOf { it.second }
        val maxY = values.maxOf { it.second }
        val xRange = (maxX - minX).coerceAtLeast(0.001f)
        val yRange = (maxY - minY).coerceAtLeast(0.001f)
        val inset = size.height * 0.08f

        val points = values.map { (x, y) ->
            Offset(
                x = (x - minX) / xRange * size.width,
                y = size.height - inset - (y - minY) / yRange * (size.height - 2 * inset)
            )
        }

        // Filled area under the trace.
        val area = Path().apply {
            moveTo(points.first().x, size.height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height)
            close()
        }
        drawPath(path = area, color = lineColor.copy(alpha = 0.18f))

        for (i in 0 until points.size - 1) {
            drawLine(
                color = lineColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 5f
            )
        }
    }
}
