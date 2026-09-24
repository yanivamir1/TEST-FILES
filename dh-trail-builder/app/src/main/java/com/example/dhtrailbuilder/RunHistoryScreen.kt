package com.example.dhtrailbuilder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** List of saved runs, each with a delete button - tapping a row opens its detail. */
@Composable
fun RunHistoryScreen(
    runStorage: RunStorage,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var runs by remember { mutableStateOf<List<RunSummary>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SavedRun?>(null) }

    fun refresh() {
        scope.launch {
            runs = runStorage.listRuns()
            loaded = true
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val current = selected
    if (current != null) {
        RunDetail(
            run = current,
            onBack = { selected = null },
            onRunChange = { updated ->
                selected = updated
                scope.launch { runStorage.saveRun(updated) }
            },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionCard(title = "Saved runs", subtitle = "Recordings from Ride Log") {
            Text(
                text = when {
                    !loaded -> "Loading…"
                    runs.isEmpty() -> "No runs recorded yet - record one from Ride Log and it will show up here."
                    else -> "${runs.size} saved"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        runs.forEach { summary ->
            RunRow(
                summary = summary,
                onClick = { scope.launch { selected = runStorage.loadRun(summary.id) } },
                onDelete = {
                    scope.launch {
                        runStorage.deleteRun(summary.id)
                        refresh()
                    }
                }
            )
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.US)

@Composable
private fun RunRow(
    summary: RunSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateFormat.format(Date(summary.startedAtMs)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = summaryLine(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

private fun summaryLine(summary: RunSummary): String {
    val parts = mutableListOf(summary.mode.label, "${formatValue(summary.durationSec, 0)} s")
    summary.distanceM?.let { parts.add("${formatValue(it, 0)} m") }
    if (summary.hasJump) parts.add("jump detected")
    return parts.joinToString(" · ")
}

@Composable
private fun RunDetail(
    run: SavedRun,
    onBack: () -> Unit,
    onRunChange: (SavedRun) -> Unit,
    modifier: Modifier = Modifier
) {
    var dropText by remember(run.id) { mutableStateOf(run.landingDropM?.let { formatValue(it) } ?: "") }
    var angleText by remember(run.id) { mutableStateOf(run.rampAngleDeg?.let { formatValue(it, 0) } ?: "") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text(
                text = dateFormat.format(Date(run.startedAtMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (run.mode == RecordMode.Gps) {
            SectionCard(title = "Jump", subtitle = "Landing drop B→C and ramp angle") {
                SteppedValueRow(
                    value = dropText,
                    onValueChange = {
                        dropText = it
                        onRunChange(run.copy(landingDropM = it.toFloatOrNull()))
                    },
                    step = 0.5f,
                    unit = "m"
                )
                SteppedValueRow(
                    value = angleText,
                    onValueChange = {
                        angleText = it
                        onRunChange(run.copy(rampAngleDeg = it.toFloatOrNull()))
                    },
                    step = 1f,
                    unit = "°",
                    decimals = 0
                )
            }
        }

        RunResultsSection(
            samples = run.samples,
            mode = run.mode,
            detectedJump = run.detectedJump,
            predictedDistanceM = run.predictedDistanceM,
            rampAngleDeg = run.rampAngleDeg,
            landingDropM = run.landingDropM
        )
    }
}
