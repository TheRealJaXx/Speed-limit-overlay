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

enum class RoadTier(val title: String, val defaultLimitKmh: Int) {
    HIGHWAY("Highway", 110),
    ARTERIAL("Main Road", 70),
    LOCAL("City Street", 45);

    companion object {
        fun fromOsmHighway(raw: String?): RoadTier = when (raw) {
            "motorway", "motorway_link", "trunk", "trunk_link" -> HIGHWAY
            "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link" -> ARTERIAL
            else -> LOCAL
        }
    }
}

data class LearnedPaceProfile(
    val highwayAvgKmh: Float? = null,
    val highwaySamples: Int = 0,
    val arterialAvgKmh: Float? = null,
    val arterialSamples: Int = 0,
    val localAvgKmh: Float? = null,
    val localSamples: Int = 0
) {
    fun averageKmhFor(tier: RoadTier): Float? = when (tier) {
        RoadTier.HIGHWAY -> highwayAvgKmh
        RoadTier.ARTERIAL -> arterialAvgKmh
        RoadTier.LOCAL -> localAvgKmh
    }

    fun samplesFor(tier: RoadTier): Int = when (tier) {
        RoadTier.HIGHWAY -> highwaySamples
        RoadTier.ARTERIAL -> arterialSamples
        RoadTier.LOCAL -> localSamples
    }

    val totalSamples: Int
        get() = highwaySamples + arterialSamples + localSamples
}

data class ActiveTripEta(
    val isActive: Boolean = false,
    val initialDistanceMeters: Double = 0.0,
    val remainingDistanceMeters: Double = 0.0,
    val mapsInitialSeconds: Long = 0L,
    val startedAtMillis: Long = 0L
)

data class EtaSummary(
    val effectivePaceKmh: Int = 100,
    val effectivePaceMph: Int = 62,
    val paceSourceLabel: String = "Personal Pace",
    val extraMinutesPerHour: Int = 0,
    val isExceedingPersonalCap: Boolean = false,
    val recalculatedRemainingMinutes: Int? = null,
    val recalculatedArrivalMillis: Long? = null,
    val deltaMinutesVsMaps: Int? = null,
    val remainingDistanceKm: Double? = null
) {
    fun effectivePaceIn(unit: SpeedUnit): Int = when (unit) {
        SpeedUnit.MPH -> effectivePaceMph
        SpeedUnit.KMH -> effectivePaceKmh
    }
}

data class SpeedLimitInfo(
    val mphLimit: Int? = null,
    val kmhLimit: Int? = null,
    val streetName: String = "Locating street…",
    val roadRef: String? = null,
    val roadClass: String? = null,
    val roadTier: RoadTier = RoadTier.LOCAL,
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
    val unit: SpeedUnit = SpeedUnit.KMH,
    val signStyle: SignStyle = SignStyle.US_MUTCD,
    val overlaySize: OverlaySize = OverlaySize.STANDARD,
    val showCurrentSpeed: Boolean = true,
    val alertThreshold: SpeedAlertThreshold = SpeedAlertThreshold.PLUS_5,
    val overlayX: Int = 32,
    val overlayY: Int = 220,
    val personalMaxSpeedEnabled: Boolean = true,
    val personalMaxSpeedKmh: Int = 100,
    val useLearnedPaceForEta: Boolean = true
)

data class PermissionStatus(
    val hasLocation: Boolean = false,
    val hasOverlay: Boolean = false,
    val hasNotifications: Boolean = true
) {
    val allGranted: Boolean
        get() = hasLocation && hasOverlay && hasNotifications
}
