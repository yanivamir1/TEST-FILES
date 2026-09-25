package com.example.dhtrailbuilder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    RideLog("Run-up"),
    History("History")
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
            var isDark by remember { mutableStateOf(loadDarkModePreference(applicationContext)) }
            val themeState = AppThemeState(
                isDark = isDark,
                toggle = {
                    isDark = !isDark
                    saveDarkModePreference(applicationContext, isDark)
                }
            )
            CompositionLocalProvider(LocalAppTheme provides themeState) {
                DhTrailBuilderTheme(darkTheme = isDark) {
                    AppBackground {
                        AppRoot(sensorRepository, locationRepository, angleRepository)
                    }
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
    val liveSensors = rememberLiveSensors(sensorRepository, angleRepository)
    val runStorage = remember { RunStorage(context.applicationContext) }
    var currentScreen by remember { mutableStateOf(Screen.Jump) }
    // While a run is recording, tab switching is locked - a stray touch through fabric in a
    // pocket must not be able to navigate away from Run-up and tear down the recording.
    var isRideRecording by remember { mutableStateOf(false) }
    var lastJumpResult by remember { mutableStateOf<JumpScreenResult?>(null) }
    var landingAltitudeM by remember { mutableStateOf<Float?>(null) }
    var rampAngleDeg by remember { mutableStateOf<Float?>(null) }

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

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        LiveSensorBar(
            liveSensors = liveSensors,
            locationRepository = locationRepository,
            hasLocationPermission = hasLocationPermission
        )

        TabRow(
            selectedTabIndex = currentScreen.ordinal,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            divider = {
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        ) {
            Screen.entries.forEach { screen ->
                Tab(
                    selected = screen == currentScreen,
                    onClick = { if (!isRideRecording) currentScreen = screen },
                    enabled = !isRideRecording || screen == currentScreen,
                    text = { Text(screen.label, style = MaterialTheme.typography.labelLarge) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (currentScreen) {
            Screen.Jump -> JumpCalculatorScreen(
                liveSensors = liveSensors,
                onResult = { lastJumpResult = it },
                onLandingAltitudeCaptured = { landingAltitudeM = it },
                onRampAngleCaptured = { rampAngleDeg = it }
            )
            Screen.Berm -> BermDistanceScreen(
                liveSensors = liveSensors,
                prefillLandingSpeedMs = lastJumpResult?.landingSpeedMs,
                landingAltitudeM = landingAltitudeM
            )
            Screen.RideLog -> TrailRunScreen(
                sensorRepository = sensorRepository,
                locationRepository = locationRepository,
                liveSensors = liveSensors,
                runStorage = runStorage,
                hasLocationPermission = hasLocationPermission,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                predictedJump = lastJumpResult,
                rampAngleDeg = rampAngleDeg,
                onRecordingChanged = { isRideRecording = it }
            )
            Screen.History -> RunHistoryScreen(runStorage = runStorage)
        }
    }
}
