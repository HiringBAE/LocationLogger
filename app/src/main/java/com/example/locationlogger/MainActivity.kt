package com.example.locationlogger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.localsdk.LocalSDK
import com.localsdk.TrackingMode
import com.example.locationlogger.ui.theme.LocationLoggerTheme

class MainActivity : ComponentActivity() {
    private var trackingStatus by mutableStateOf("Idle")
    private var latestLocation by mutableStateOf("No location received yet")
    private var sdkInitialized by mutableStateOf(false)
    private var sdk: LocalSDK? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocationLoggerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val hasPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

                        if (hasPermission) {
                            startTracking()
                        } else {
                            trackingStatus = "Location permission denied"
                        }
                    }

                    LocationLoggerScreen(
                        trackingStatus = trackingStatus,
                        latestLocation = latestLocation,
                        sdkInitialized = sdkInitialized,
                        onStartTracking = {
                            if (hasLocationPermission()) {
                                startTracking()
                            } else {
                                permissionLauncher.launch(locationPermissions)
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun startTracking() {
        val localSdk = ensureSdkInitialized()
        if (localSdk == null) {
            trackingStatus = "Set a LocalSDK API key before starting tracking"
            return
        }

        trackingStatus = "Starting tracking"
        localSdk.startTracking(TrackingMode.ADAPTIVE)
        trackingStatus = "Tracking active"
    }

    private fun ensureSdkInitialized(): LocalSDK? {
        if (sdk != null) {
            return sdk
        }

        if (LOCALSDK_API_KEY == "replace_with_real_api_key") {
            return null
        }

        val localSdk = LocalSDK.init(applicationContext, LOCALSDK_API_KEY)
        localSdk.onLocation { update ->
            latestLocation = "${update.latitude}, ${update.longitude}"
            Log.d("LocationLogger", latestLocation)
        }

        sdk = localSdk
        sdkInitialized = true
        trackingStatus = "SDK initialized"
        return localSdk
    }

    private fun hasLocationPermission(): Boolean {
        return locationPermissions.any { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private const val LOCALSDK_API_KEY = "replace_with_real_api_key"
        private val locationPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}

@Composable
fun LocationLoggerScreen(
    trackingStatus: String,
    latestLocation: String,
    sdkInitialized: Boolean,
    onStartTracking: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "LocalSDK integration")
        Text(text = "SDK status: ${if (sdkInitialized) "Initialized" else "Not initialized"}")
        Text(text = "Tracking status: $trackingStatus")
        Text(text = "Latest location: $latestLocation")
        Button(onClick = onStartTracking) {
            Text(text = "Start tracking")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LocationLoggerPreview() {
    LocationLoggerTheme {
        LocationLoggerScreen(
            trackingStatus = "Idle",
            latestLocation = "37.7749, -122.4194",
            sdkInitialized = false,
            onStartTracking = {}
        )
    }
}
