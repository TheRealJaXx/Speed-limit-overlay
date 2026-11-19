package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.screens.CockpitScreen
import com.example.ui.theme.SpeedSignTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SpeedSignTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val lifecycleOwner = LocalLifecycleOwner.current

                val locationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    viewModel.refreshPermissions()
                }

                val notificationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {
                    viewModel.refreshPermissions()
                }

                val overlaySettingsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    viewModel.refreshPermissions()
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.refreshPermissions()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val launchLocationPermission = {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }

                val launchOverlayPermission = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    overlaySettingsLauncher.launch(intent)
                }

                val launchNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                CockpitScreen(
                    uiState = uiState,
                    onRequestLocation = launchLocationPermission,
                    onRequestOverlay = launchOverlayPermission,
                    onRequestNotifications = launchNotificationPermission,
                    onToggleOverlay = {
                        when {
                            uiState.isOverlayActive -> viewModel.stopOverlay()
                            !uiState.permissions.hasLocation -> launchLocationPermission()
                            !uiState.permissions.hasOverlay -> launchOverlayPermission()
                            else -> {
                                if (!uiState.permissions.hasNotifications &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ) {
                                    launchNotificationPermission()
                                }
                                viewModel.startOverlay()
                            }
                        }
                    },
                    onOpenGoogleMaps = { openGoogleMaps() },
                    onSelectUnit = viewModel::setSpeedUnit,
                    onSelectSignStyle = viewModel::setSignStyle,
                    onSelectOverlaySize = viewModel::setOverlaySize,
                    onToggleShowCurrentSpeed = viewModel::setShowCurrentSpeed,
                    onSelectAlertThreshold = viewModel::setAlertThreshold,
                    onResetPosition = viewModel::resetOverlayPosition,
                    onTogglePersonalCap = viewModel::setPersonalMaxSpeedEnabled,
                    onChangePersonalCapKmh = viewModel::setPersonalMaxSpeedKmh,
                    onToggleUseLearnedPace = viewModel::setUseLearnedPaceForEta,
                    onResetLearnedPace = viewModel::resetLearnedPace,
                    onStartTripEta = viewModel::startTripEta,
                    onClearTripEta = viewModel::clearTripEta
                )
            }
        }
    }

    private fun openGoogleMaps() {
        val mapsPackage = "com.google.android.apps.maps"
        val launchIntent = packageManager.getLaunchIntentForPackage(mapsPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            return
        }

        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).apply {
            setPackage(mapsPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (geoIntent.resolveActivity(packageManager) != null) {
            startActivity(geoIntent)
            return
        }

        val webMapsIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://maps.google.com/")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { startActivity(webMapsIntent) }.onFailure {
            Toast.makeText(this, "Google Maps is not installed on this device.", Toast.LENGTH_SHORT).show()
        }
    }
}
