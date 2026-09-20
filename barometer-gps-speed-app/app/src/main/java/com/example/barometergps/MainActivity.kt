package com.example.barometergps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

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
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen(sensorRepository, locationRepository, angleRepository)
                }
            }
        }
    }
}

@Composable
private fun AppScreen(
    sensorRepository: SensorRepository,
    locationRepository: LocationRepository,
    angleRepository: AngleRepository
) {
    val context = LocalContext.current

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

    var pressureReading by remember { mutableStateOf<PressureReading?>(null) }
    var speedReading by remember { mutableStateOf<SpeedReading?>(null) }
    var angleReading by remember { mutableStateOf<AngleReading?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, hasLocationPermission) {
        var pressureJob: Job? = null
        var speedJob: Job? = null
        var angleJob: Job? = null

        val observer = LifecycleEventObserver { owner, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    pressureJob = owner.lifecycleScope.launch {
                        sensorRepository.pressureFlow().collect { pressureReading = it }
                    }
                    angleJob = owner.lifecycleScope.launch {
                        angleRepository.angleFlow().collect { angleReading = it }
                    }
                    if (hasLocationPermission) {
                        speedJob = owner.lifecycleScope.launch {
                            locationRepository.speedFlow().collect { speedReading = it }
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    pressureJob?.cancel()
                    speedJob?.cancel()
                    angleJob?.cancel()
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pressureJob?.cancel()
            speedJob?.cancel()
            angleJob?.cancel()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ברומטר, מהירות GPS וזווית", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(40.dp))

        Text("לחץ אטמוספרי", fontSize = 14.sp)
        Text(
            text = when (val p = pressureReading) {
                null -> "טוען..."
                is PressureReading.Unavailable -> "אין חיישן ברומטר במכשיר זה"
                is PressureReading.Value -> String.format(Locale.US, "%.1f hPa", p.hPa)
            },
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(24.dp))

        Text("גובה משוער (מהברומטר)", fontSize = 14.sp)
        Text(
            text = when (val p = pressureReading) {
                null -> "טוען..."
                is PressureReading.Unavailable -> "אין חיישן ברומטר במכשיר זה"
                is PressureReading.Value -> String.format(
                    Locale.US, "%.0f מ'", sensorRepository.altitudeMeters(p.hPa)
                )
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(40.dp))

        Text("מהירות תנועה", fontSize = 14.sp)
        Text(
            text = when {
                !hasLocationPermission -> "נדרשת הרשאת מיקום"
                !locationRepository.isGpsProviderAvailable -> "אין GPS זמין במכשיר זה"
                speedReading?.speedKmh == null -> "ממתין לאיתות GPS..."
                else -> String.format(Locale.US, "%.1f קמ\"ש", speedReading!!.speedKmh)
            },
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(40.dp))

        Text("זווית הטלפון (הטיה)", fontSize = 14.sp)
        Text(
            text = when (val a = angleReading) {
                null -> "טוען..."
                is AngleReading.Unavailable -> "אין חיישן תאוצה במכשיר זה"
                is AngleReading.Value -> String.format(
                    Locale.US, "הטיה: %.0f°   נטייה: %.0f°", a.pitchDeg, a.rollDeg
                )
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
