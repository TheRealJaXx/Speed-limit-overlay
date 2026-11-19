package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DrivingLocationTracker
import com.example.data.EtaCalculator
import com.example.data.OverlayRuntimeState
import com.example.data.PreferencesRepository
import com.example.data.SpeedLimitRepository
import com.example.model.ActiveTripEta
import com.example.model.DrivingTelemetry
import com.example.model.EtaSummary
import com.example.model.LearnedPaceProfile
import com.example.model.OverlayPreferences
import com.example.model.OverlaySize
import com.example.model.PermissionStatus
import com.example.model.SignStyle
import com.example.model.SpeedAlertThreshold
import com.example.model.SpeedUnit
import com.example.service.SpeedOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CockpitUiState(
    val telemetry: DrivingTelemetry = DrivingTelemetry(),
    val preferences: OverlayPreferences = OverlayPreferences(),
    val learnedPace: LearnedPaceProfile = LearnedPaceProfile(),
    val activeTrip: ActiveTripEta = ActiveTripEta(),
    val etaSummary: EtaSummary = EtaSummary(),
    val permissions: PermissionStatus = PermissionStatus(),
    val isOverlayActive: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application)
    private val locationTracker = DrivingLocationTracker(application)
    private val speedLimitRepository = SpeedLimitRepository()

    private val _permissionStatus = MutableStateFlow(readPermissions())
    private var previewLocationJob: Job? = null
    private var lastPaceSampleMillis: Long = 0L

    private val baseFlow = combine(
        OverlayRuntimeState.telemetry,
        preferencesRepository.preferencesFlow,
        preferencesRepository.learnedPaceFlow,
        OverlayRuntimeState.activeTrip
    ) { telemetry, prefs, learned, trip ->
        val eta = EtaCalculator.calculateEtaSummary(
            telemetry = telemetry,
            prefs = prefs,
            learnedProfile = learned,
            activeTrip = trip
        )
        CockpitUiState(
            telemetry = telemetry,
            preferences = prefs,
            learnedPace = learned,
            activeTrip = trip,
            etaSummary = eta
        )
    }

    val uiState: StateFlow<CockpitUiState> = combine(
        baseFlow,
        _permissionStatus,
        OverlayRuntimeState.isOverlayRunning
    ) { state, perms, active ->
        state.copy(
            permissions = perms,
            isOverlayActive = active
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CockpitUiState(permissions = readPermissions())
    )

    init {
        viewModelScope.launch {
            combine(_permissionStatus, OverlayRuntimeState.isOverlayRunning) { perms, running ->
                perms.hasLocation && !running
            }.collectLatest { shouldRunPreview ->
                if (shouldRunPreview) {
                    startInAppPreviewUpdates()
                } else {
                    previewLocationJob?.cancel()
                    previewLocationJob = null
                }
            }
        }
    }

    fun refreshPermissions() {
        _permissionStatus.value = readPermissions()
    }

    private fun readPermissions(): PermissionStatus {
        val ctx = getApplication<Application>()
        val hasFine = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasOverlay = Settings.canDrawOverlays(ctx)
        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return PermissionStatus(
            hasLocation = hasFine || hasCoarse,
            hasOverlay = hasOverlay,
            hasNotifications = hasNotifications
        )
    }

    private fun startInAppPreviewUpdates() {
        if (previewLocationJob?.isActive == true) return
        previewLocationJob = viewModelScope.launch(Dispatchers.IO) {
            locationTracker.locationUpdates()
                .catch { error ->
                    val current = OverlayRuntimeState.telemetry.value
                    OverlayRuntimeState.updateTelemetry(
                        current.copy(statusNote = error.localizedMessage ?: "Location unavailable")
                    )
                }
                .collectLatest { location ->
                    OverlayRuntimeState.advanceTripDistance(location.latitude, location.longitude)

                    val current = OverlayRuntimeState.telemetry.value
                    val base = current.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        currentSpeedMps = if (location.hasSpeed()) location.speed else 0f,
                        bearingDegrees = if (location.hasBearing()) location.bearing else null,
                        accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                        hasLocationFix = true,
                        isFetchingStreet = true,
                        statusNote = null
                    )
                    OverlayRuntimeState.updateTelemetry(base)

                    val lookup = speedLimitRepository.lookupStreetSpeedLimit(
                        lat = location.latitude,
                        lon = location.longitude,
                        bearingDegrees = base.bearingDegrees,
                        speedMps = base.currentSpeedMps
                    )

                    val updatedInfo = lookup.info ?: base.speedLimitInfo
                    val updatedTelemetry = base.copy(
                        speedLimitInfo = updatedInfo,
                        isFetchingStreet = false,
                        statusNote = lookup.errorMessage
                    )
                    OverlayRuntimeState.updateTelemetry(updatedTelemetry)

                    val now = System.currentTimeMillis()
                    if (now - lastPaceSampleMillis >= 5_000L) {
                        val currentProfile = preferencesRepository.learnedPaceFlow.first()
                        val updatedProfile = EtaCalculator.recordDrivingSample(
                            current = currentProfile,
                            tier = updatedInfo.roadTier,
                            speedMps = updatedTelemetry.currentSpeedMps,
                            accuracyMeters = updatedTelemetry.accuracyMeters
                        )
                        if (updatedProfile != null) {
                            lastPaceSampleMillis = now
                            preferencesRepository.saveLearnedPaceProfile(updatedProfile)
                        }
                    }
                }
        }
    }

    fun startOverlay() {
        SpeedOverlayService.start(getApplication())
    }

    fun stopOverlay() {
        SpeedOverlayService.stop(getApplication())
    }

    fun resetOverlayPosition() {
        viewModelScope.launch {
            preferencesRepository.setOverlayPosition(32, 220)
            if (OverlayRuntimeState.isOverlayRunning.value) {
                SpeedOverlayService.resetPosition(getApplication())
            }
        }
    }

    fun setSpeedUnit(unit: SpeedUnit) {
        viewModelScope.launch { preferencesRepository.setSpeedUnit(unit) }
    }

    fun setSignStyle(style: SignStyle) {
        viewModelScope.launch { preferencesRepository.setSignStyle(style) }
    }

    fun setOverlaySize(size: OverlaySize) {
        viewModelScope.launch { preferencesRepository.setOverlaySize(size) }
    }

    fun setShowCurrentSpeed(show: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowCurrentSpeed(show) }
    }

    fun setAlertThreshold(threshold: SpeedAlertThreshold) {
        viewModelScope.launch { preferencesRepository.setAlertThreshold(threshold) }
    }

    fun setPersonalMaxSpeedEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setPersonalMaxSpeedEnabled(enabled) }
    }

    fun setPersonalMaxSpeedKmh(kmh: Int) {
        viewModelScope.launch { preferencesRepository.setPersonalMaxSpeedKmh(kmh) }
    }

    fun setUseLearnedPaceForEta(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setUseLearnedPaceForEta(enabled) }
    }

    fun resetLearnedPace() {
        viewModelScope.launch { preferencesRepository.resetLearnedPaceProfile() }
    }

    fun startTripEta(mapsMinutes: Int, distanceInCurrentUnit: Double) {
        val unit = uiState.value.preferences.unit
        val distanceKm = if (unit == SpeedUnit.MPH) {
            distanceInCurrentUnit * 1.609344
        } else {
            distanceInCurrentUnit
        }
        OverlayRuntimeState.startTrip(mapsMinutes = mapsMinutes, distanceKm = distanceKm)
    }

    fun clearTripEta() {
        OverlayRuntimeState.clearTrip()
    }
}
