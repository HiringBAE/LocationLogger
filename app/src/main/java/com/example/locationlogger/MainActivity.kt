package com.example.locationlogger

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.locationlogger.ui.theme.LocationLoggerTheme
import com.localsdk.LocalSDK
import com.localsdk.config.Config
import com.localsdk.modules.locationtracking.enums.TrackingMode

class MainActivity : ComponentActivity() {
    private var uiState by mutableStateOf(LocalSdkUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initializeLocalSdk()
        setContent {
            LocationLoggerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LocalSdkScreen(
                        state = uiState,
                        shouldShowBackgroundPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                        onRequestLocationPermission = ::requestLocationPermission,
                        onRequestBackgroundPermission = ::requestBackgroundPermission,
                        onStartTracking = ::startTracking,
                        onStopTracking = ::stopTracking,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    override fun onDestroy() {
        if (uiState.isTracking) {
            runCatching { LocalSDK.stopTracking() }
        }
        super.onDestroy()
    }

    private fun initializeLocalSdk() {
        val sdkKey = BuildConfig.LOCALSDK_SDK_KEY.trim()
        if (sdkKey.isBlank()) {
            uiState = uiState.copy(statusMessage = getString(R.string.sdk_key_missing))
            return
        }

        runCatching {
            LocalSDK.initialize(this, sdkKey)

            val config = Config.Builder()
                .setTrackingMode(TrackingMode.STANDARD)
                .setForegroundServiceEnabled(true)
                .build()

            LocalSDK.setConfig(config)
            LocalSDK.onLocation { location ->
                runOnUiThread {
                    uiState = uiState.copy(
                        lastLatitude = location.latitude,
                        lastLongitude = location.longitude,
                        lastError = null
                    )
                }
            }
            LocalSDK.onError { error ->
                runOnUiThread {
                    uiState = uiState.copy(
                        isTracking = false,
                        lastError = error.toString()
                    )
                }
            }
        }.onSuccess {
            uiState = uiState.copy(
                isInitialized = true,
                statusMessage = getString(R.string.sdk_initialized)
            )
            refreshPermissionState()
        }.onFailure { throwable ->
            uiState = uiState.copy(
                statusMessage = getString(R.string.sdk_not_initialized),
                lastError = throwable.message ?: throwable.toString()
            )
        }
    }

    private fun refreshPermissionState() {
        if (!uiState.isInitialized) {
            return
        }

        val hasForegroundPermission = runCatching {
            LocalSDK.checkLocationPermission()
        }.getOrDefault(false)
        val hasBackgroundPermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            true
        } else {
            runCatching {
                LocalSDK.checkBackgroundLocationPermission()
            }.getOrDefault(false)
        }

        uiState = uiState.copy(
            hasForegroundPermission = hasForegroundPermission,
            hasBackgroundPermission = hasBackgroundPermission
        )
    }

    private fun requestLocationPermission() {
        if (!uiState.isInitialized) {
            return
        }

        runCatching {
            LocalSDK.requestLocationPermission(this)
        }.onFailure { throwable ->
            uiState = uiState.copy(lastError = throwable.message ?: throwable.toString())
        }
    }

    private fun requestBackgroundPermission() {
        if (!uiState.isInitialized || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        runCatching {
            LocalSDK.requestBackgroundLocationPermission(this)
        }.onFailure { throwable ->
            uiState = uiState.copy(lastError = throwable.message ?: throwable.toString())
        }
    }

    private fun startTracking() {
        if (!uiState.isInitialized) {
            uiState = uiState.copy(statusMessage = getString(R.string.sdk_key_missing))
            return
        }
        if (!uiState.hasForegroundPermission) {
            uiState = uiState.copy(lastError = getString(R.string.foreground_permission_missing))
            return
        }

        runCatching {
            LocalSDK.startTracking()
        }.onSuccess {
            uiState = uiState.copy(isTracking = true, lastError = null)
        }.onFailure { throwable ->
            uiState = uiState.copy(lastError = throwable.message ?: throwable.toString())
        }
    }

    private fun stopTracking() {
        if (!uiState.isInitialized) {
            return
        }

        runCatching {
            LocalSDK.stopTracking()
        }.onSuccess {
            uiState = uiState.copy(isTracking = false)
        }.onFailure { throwable ->
            uiState = uiState.copy(lastError = throwable.message ?: throwable.toString())
        }
    }
}

@Composable
private fun LocalSdkScreen(
    state: LocalSdkUiState,
    shouldShowBackgroundPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRequestBackgroundPermission: () -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "LocalSDK Location Tracking",
            style = MaterialTheme.typography.headlineSmall
        )

        StatusCard(title = "SDK", value = state.statusMessage ?: "No SDK status")
        StatusCard(
            title = "Foreground Permission",
            value = if (state.hasForegroundPermission) "Granted" else "Missing"
        )

        if (shouldShowBackgroundPermission) {
            StatusCard(
                title = "Background Permission",
                value = if (state.hasBackgroundPermission) "Granted" else "Missing"
            )
        }

        StatusCard(
            title = "Tracking",
            value = if (state.isTracking) "Running" else "Stopped"
        )
        StatusCard(title = "Last Location", value = state.locationText())

        state.lastError?.let { error ->
            StatusCard(title = "Last Error", value = error)
        }

        Button(
            onClick = onRequestLocationPermission,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.isInitialized
        ) {
            Text(text = "Request location permission")
        }

        if (shouldShowBackgroundPermission) {
            OutlinedButton(
                onClick = onRequestBackgroundPermission,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isInitialized && state.hasForegroundPermission
            ) {
                Text(text = "Request background permission")
            }
        }

        Button(
            onClick = onStartTracking,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.isInitialized && state.hasForegroundPermission && !state.isTracking
        ) {
            Text(text = "Start tracking")
        }

        OutlinedButton(
            onClick = onStopTracking,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.isInitialized && state.isTracking
        ) {
            Text(text = "Stop tracking")
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private data class LocalSdkUiState(
    val isInitialized: Boolean = false,
    val hasForegroundPermission: Boolean = false,
    val hasBackgroundPermission: Boolean = false,
    val isTracking: Boolean = false,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastError: String? = null,
    val statusMessage: String? = null
) {
    fun locationText(): String {
        if (lastLatitude == null || lastLongitude == null) {
            return "No location received yet"
        }

        return "Lat: %.5f, Lng: %.5f".format(lastLatitude, lastLongitude)
    }
}

@Preview(showBackground = true)
@Composable
private fun LocalSdkScreenPreview() {
    LocationLoggerTheme {
        LocalSdkScreen(
            state = LocalSdkUiState(
                isInitialized = true,
                hasForegroundPermission = true,
                hasBackgroundPermission = false,
                isTracking = true,
                lastLatitude = 37.7749,
                lastLongitude = -122.4194
            ),
            shouldShowBackgroundPermission = true,
            onRequestLocationPermission = {},
            onRequestBackgroundPermission = {},
            onStartTracking = {},
            onStopTracking = {},
            modifier = Modifier
        )
    }
}
