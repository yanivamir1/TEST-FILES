package com.example.hdtrailbuilder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private enum class Screen(val label: String) {
    Jump("קפיצה"),
    Berm("ברם"),
    TrailRun("בדיקת רמפה")
}

class MainActivity : ComponentActivity() {

    private lateinit var sensorRepository: SensorRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var angleRepository: AngleRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorRepository = SensorRepository(applicationContext)
        locationRepository = LocationRepository(applicationContext)
        angleRepository = AngleRepository(applicationContext)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier) {
                    AppRoot(sensorRepository, locationRepository, angleRepository)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(
    sensorRepository: SensorRepository,
    locationRepository: LocationRepository,
    angleRepository: AngleRepository
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.Jump) }
    var lastJumpResult by remember { mutableStateOf<JumpScreenResult?>(null) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocationPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Column(modifier = Modifier.padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Screen.entries.forEach { screen ->
                if (screen == currentScreen) {
                    Button(onClick = { currentScreen = screen }) { Text(screen.label) }
                } else {
                    OutlinedButton(onClick = { currentScreen = screen }) { Text(screen.label) }
                }
            }
        }

        when (currentScreen) {
            Screen.Jump -> JumpCalculatorScreen(
                sensorRepository = sensorRepository,
                angleRepository = angleRepository,
                onResult = { lastJumpResult = it }
            )
            Screen.Berm -> BermDistanceScreen(
                sensorRepository = sensorRepository,
                prefillLandingSpeedMs = lastJumpResult?.landingSpeedMs
            )
            Screen.TrailRun -> TrailRunScreen(
                locationRepository = locationRepository,
                hasLocationPermission = hasLocationPermission,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
            )
        }
    }
}
