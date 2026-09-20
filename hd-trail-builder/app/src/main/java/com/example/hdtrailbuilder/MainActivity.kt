package com.example.hdtrailbuilder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private enum class Screen(val label: String) {
    Jump("Jump"),
    Berm("Berm"),
    RideLog("Ride Log")
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
            HdTrailBuilderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(sensorRepository, locationRepository, angleRepository)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(
    sensorRepository: SensorRepository,
    locationRepository: LocationRepository,
    angleRepository: AngleRepository
) {
    val context = LocalContext.current
    val liveSensors = rememberLiveSensors(sensorRepository, angleRepository)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HD Trail Builder") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            LiveSensorBar(liveSensors)

            TabRow(selectedTabIndex = currentScreen.ordinal) {
                Screen.entries.forEach { screen ->
                    Tab(
                        selected = screen == currentScreen,
                        onClick = { currentScreen = screen },
                        text = { Text(screen.label) }
                    )
                }
            }

            when (currentScreen) {
                Screen.Jump -> JumpCalculatorScreen(
                    liveSensors = liveSensors,
                    onResult = { lastJumpResult = it }
                )
                Screen.Berm -> BermDistanceScreen(
                    liveSensors = liveSensors,
                    prefillLandingSpeedMs = lastJumpResult?.landingSpeedMs
                )
                Screen.RideLog -> TrailRunScreen(
                    locationRepository = locationRepository,
                    hasLocationPermission = hasLocationPermission,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                )
            }
        }
    }
}
