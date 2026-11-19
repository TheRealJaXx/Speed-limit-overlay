package com.example.data

import com.example.model.ActiveTripEta
import com.example.model.DrivingTelemetry
import com.example.model.EtaSummary
import com.example.model.LearnedPaceProfile
import com.example.model.OverlayPreferences
import com.example.model.RoadTier
import kotlin.math.min
import kotlin.math.roundToInt

object EtaCalculator {

    private const val MIN_LEARNING_SPEED_KMH = 22f
    private const val MAX_LEARNING_SPEED_KMH = 180f

    fun recordDrivingSample(
        current: LearnedPaceProfile,
        tier: RoadTier,
        speedMps: Float,
        accuracyMeters: Float?
    ): LearnedPaceProfile? {
        if (accuracyMeters != null && accuracyMeters > 40f) return null
        val speedKmh = speedMps * 3.6f
        if (speedKmh < MIN_LEARNING_SPEED_KMH || speedKmh > MAX_LEARNING_SPEED_KMH) return null

        val oldAvg = current.averageKmhFor(tier)
        val oldCount = current.samplesFor(tier)
        val newCount = oldCount + 1
        val alpha = if (oldCount < 12) 1f / newCount else 0.08f
        val newAvg = if (oldAvg == null) {
            speedKmh
        } else {
            oldAvg + alpha * (speedKmh - oldAvg)
        }

        return when (tier) {
            RoadTier.HIGHWAY -> current.copy(highwayAvgKmh = newAvg, highwaySamples = newCount)
            RoadTier.ARTERIAL -> current.copy(arterialAvgKmh = newAvg, arterialSamples = newCount)
            RoadTier.LOCAL -> current.copy(localAvgKmh = newAvg, localSamples = newCount)
        }
    }

    fun calculateEtaSummary(
        telemetry: DrivingTelemetry,
        prefs: OverlayPreferences,
        learnedProfile: LearnedPaceProfile,
        activeTrip: ActiveTripEta,
        nowMillis: Long = System.currentTimeMillis()
    ): EtaSummary {
        val tier = telemetry.speedLimitInfo.roadTier
        val postedKmh = telemetry.speedLimitInfo.kmhLimit ?: tier.defaultLimitKmh
        val capKmh = if (prefs.personalMaxSpeedEnabled) prefs.personalMaxSpeedKmh else Int.MAX_VALUE

        val learnedTierAvg = if (prefs.useLearnedPaceForEta && learnedProfile.samplesFor(tier) >= 3) {
            learnedProfile.averageKmhFor(tier)
        } else {
            null
        }

        val liveSpeedKmh = telemetry.currentSpeedMps * 3.6f
        val isMovingSteadily = telemetry.hasLocationFix && liveSpeedKmh >= 20f

        val baselinePaceKmh: Float = when {
            learnedTierAvg != null -> min(learnedTierAvg, min(postedKmh.toFloat(), capKmh.toFloat()))
            else -> min(postedKmh.toFloat(), capKmh.toFloat())
        }

        val effectivePaceFloat = if (isMovingSteadily) {
            val blended = (0.55f * liveSpeedKmh) + (0.45f * baselinePaceKmh)
            blended.coerceIn(15f, min(postedKmh.toFloat(), capKmh.toFloat()).coerceAtLeast(15f))
        } else {
            baselinePaceKmh.coerceAtLeast(15f)
        }

        val effectivePaceKmh = effectivePaceFloat.roundToInt()
        val effectivePaceMph = (effectivePaceFloat * 0.621371f).roundToInt()

        val sourceLabel = when {
            isMovingSteadily && learnedTierAvg != null -> "Live + Learned Pace"
            isMovingSteadily -> "Live Speed + Cap"
            learnedTierAvg != null -> "Learned ${tier.title} Pace"
            prefs.personalMaxSpeedEnabled && capKmh < postedKmh -> "Personal Cap ($capKmh km/h)"
            else -> "Posted Limit"
        }

        val extraMinutesPerHour = if (effectivePaceFloat < postedKmh.toFloat()) {
            val ratio = postedKmh.toFloat() / effectivePaceFloat
            ((ratio - 1f) * 60f).roundToInt().coerceAtLeast(0)
        } else {
            0
        }

        val isExceedingPersonalCap = prefs.personalMaxSpeedEnabled &&
            telemetry.hasLocationFix &&
            liveSpeedKmh > prefs.personalMaxSpeedKmh.toFloat()

        if (!activeTrip.isActive || activeTrip.remainingDistanceMeters <= 50.0) {
            return EtaSummary(
                effectivePaceKmh = effectivePaceKmh,
                effectivePaceMph = effectivePaceMph,
                paceSourceLabel = sourceLabel,
                extraMinutesPerHour = extraMinutesPerHour,
                isExceedingPersonalCap = isExceedingPersonalCap
            )
        }

        val remainingKm = activeTrip.remainingDistanceMeters / 1000.0
        val recalculatedSeconds = ((remainingKm / effectivePaceFloat.toDouble()) * 3600.0).toLong()
        val recalculatedMinutes = ((recalculatedSeconds + 30L) / 60L).toInt().coerceAtLeast(1)
        val arrivalMillis = nowMillis + (recalculatedSeconds * 1000L)

        val remainingFraction = if (activeTrip.initialDistanceMeters > 1.0) {
            (activeTrip.remainingDistanceMeters / activeTrip.initialDistanceMeters).coerceIn(0.0, 1.0)
        } else {
            1.0
        }
        val mapsRemainingSeconds = (activeTrip.mapsInitialSeconds * remainingFraction).toLong()
        val deltaMinutes = ((recalculatedSeconds - mapsRemainingSeconds) / 60.0).roundToInt()

        return EtaSummary(
            effectivePaceKmh = effectivePaceKmh,
            effectivePaceMph = effectivePaceMph,
            paceSourceLabel = sourceLabel,
            extraMinutesPerHour = extraMinutesPerHour,
            isExceedingPersonalCap = isExceedingPersonalCap,
            recalculatedRemainingMinutes = recalculatedMinutes,
            recalculatedArrivalMillis = arrivalMillis,
            deltaMinutesVsMaps = deltaMinutes,
            remainingDistanceKm = remainingKm
        )
    }
}
