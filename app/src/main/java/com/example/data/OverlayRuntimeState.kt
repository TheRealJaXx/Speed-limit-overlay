package com.example.data

import com.example.model.DrivingTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OverlayRuntimeState {
    private val _isOverlayRunning = MutableStateFlow(false)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

    private val _telemetry = MutableStateFlow(DrivingTelemetry())
    val telemetry: StateFlow<DrivingTelemetry> = _telemetry.asStateFlow()

    fun setOverlayRunning(running: Boolean) {
        _isOverlayRunning.value = running
    }

    fun updateTelemetry(newTelemetry: DrivingTelemetry) {
        _telemetry.value = newTelemetry
    }
}
