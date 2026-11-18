package com.example.model

enum class SpeedUnit(val label: String) {
    MPH("MPH"),
    KMH("KM/H")
}

enum class SignStyle(val title: String) {
    US_MUTCD("US Standard"),
    INTERNATIONAL("International")
}

enum class OverlaySize(val label: String, val scaleFactor: Float) {
    COMPACT("Compact", 0.85f),
    STANDARD("Standard", 1.0f),
    LARGE("Large", 1.18f)
}

enum class SpeedAlertThreshold(
    val label: String,
    val extraMph: Int,
    val extraKmh: Int
) {
    OFF("Off", Int.MAX_VALUE, Int.MAX_VALUE),
    AT_LIMIT("At Limit", 0, 0),
    PLUS_5("+5", 5, 8),
    PLUS_10("+10", 10, 15);

    fun thresholdFor(unit: SpeedUnit): Int = when (unit) {
        SpeedUnit.MPH -> extraMph
        SpeedUnit.KMH -> extraKmh
    }
}

data class SpeedLimitInfo(
    val mphLimit: Int? = null,
    val kmhLimit: Int? = null,
    val streetName: String = "Locating street…",
    val roadRef: String? = null,
    val roadClass: String? = null,
    val rawMaxSpeedTag: String? = null,
    val updatedAtMillis: Long = 0L
) {
    fun limitIn(unit: SpeedUnit): Int? = when (unit) {
        SpeedUnit.MPH -> mphLimit
        SpeedUnit.KMH -> kmhLimit
    }

    val displayStreetLabel: String
        get() = when {
            streetName.isNotBlank() && !roadRef.isNullOrBlank() && !streetName.contains(roadRef) ->
                "$streetName ($roadRef)"
            streetName.isNotBlank() -> streetName
            !roadRef.isNullOrBlank() -> roadRef
            else -> "Unnamed Road"
        }
}

data class DrivingTelemetry(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val currentSpeedMps: Float = 0f,
    val bearingDegrees: Float? = null,
    val accuracyMeters: Float? = null,
    val hasLocationFix: Boolean = false,
    val speedLimitInfo: SpeedLimitInfo = SpeedLimitInfo(),
    val isFetchingStreet: Boolean = false,
    val statusNote: String? = null
) {
    fun currentSpeedIn(unit: SpeedUnit): Int = when (unit) {
        SpeedUnit.MPH -> kotlin.math.round(currentSpeedMps * 2.236936f).toInt().coerceAtLeast(0)
        SpeedUnit.KMH -> kotlin.math.round(currentSpeedMps * 3.6f).toInt().coerceAtLeast(0)
    }

    fun isSpeeding(unit: SpeedUnit, threshold: SpeedAlertThreshold): Boolean {
        if (threshold == SpeedAlertThreshold.OFF) return false
        val limit = speedLimitInfo.limitIn(unit) ?: return false
        val current = currentSpeedIn(unit)
        return current > (limit + threshold.thresholdFor(unit))
    }
}

data class OverlayPreferences(
    val unit: SpeedUnit = SpeedUnit.MPH,
    val signStyle: SignStyle = SignStyle.US_MUTCD,
    val overlaySize: OverlaySize = OverlaySize.STANDARD,
    val showCurrentSpeed: Boolean = true,
    val alertThreshold: SpeedAlertThreshold = SpeedAlertThreshold.PLUS_5,
    val overlayX: Int = 32,
    val overlayY: Int = 220
)

data class PermissionStatus(
    val hasLocation: Boolean = false,
    val hasOverlay: Boolean = false,
    val hasNotifications: Boolean = true
) {
    val allGranted: Boolean
        get() = hasLocation && hasOverlay && hasNotifications
}
