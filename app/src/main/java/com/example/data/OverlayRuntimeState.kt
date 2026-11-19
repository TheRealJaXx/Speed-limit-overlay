package com.example.data

import android.location.Location
import com.example.model.ActiveTripEta
import com.example.model.DrivingTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OverlayRuntimeState {
    private val _isOverlayRunning = MutableStateFlow(false)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

    private val _telemetry = MutableStateFlow(DrivingTelemetry())
    val telemetry: StateFlow<DrivingTelemetry> = _telemetry.asStateFlow()

    private val _activeTrip = MutableStateFlow(ActiveTripEta())
    val activeTrip: StateFlow<ActiveTripEta> = _activeTrip.asStateFlow()

    private var lastTripLat: Double? = null
    private var lastTripLon: Double? = null

    fun setOverlayRunning(running: Boolean) {
        _isOverlayRunning.value = running
    }

    fun updateTelemetry(newTelemetry: DrivingTelemetry) {
        _telemetry.value = newTelemetry
    }

    fun startTrip(mapsMinutes: Int, distanceKm: Double, nowMillis: Long = System.currentTimeMillis()) {
        val distMeters = (distanceKm * 1000.0).coerceAtLeast(100.0)
        val currentTel = _telemetry.value
        lastTripLat = currentTel.latitude
        lastTripLon = currentTel.longitude
        _activeTrip.value = ActiveTripEta(
            isActive = true,
            initialDistanceMeters = distMeters,
            remainingDistanceMeters = distMeters,
            mapsInitialSeconds = (mapsMinutes.coerceAtLeast(1) * 60L),
            startedAtMillis = nowMillis
        )
    }

    fun clearTrip() {
        lastTripLat = null
        lastTripLon = null
        _activeTrip.value = ActiveTripEta()
    }

    fun advanceTripDistance(lat: Double, lon: Double) {
        val trip = _activeTrip.value
        if (!trip.isActive) return

        val prevLat = lastTripLat
        val prevLon = lastTripLon
        lastTripLat = lat
        lastTripLon = lon

        if (prevLat == null || prevLon == null) return

        val result = FloatArray(1)
        Location.distanceBetween(prevLat, prevLon, lat, lon, result)
        val delta = result[0].toDouble()
        // Filter stationary GPS jitter (<2m) and huge jumps (>500m per 1s tick)
        if (delta in 2.0..500.0) {
            val remaining = (trip.remainingDistanceMeters - delta).coerceAtLeast(0.0)
            _activeTrip.value = trip.copy(remainingDistanceMeters = remaining)
        }
    }
}
