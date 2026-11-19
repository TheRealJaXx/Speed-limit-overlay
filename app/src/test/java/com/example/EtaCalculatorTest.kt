package com.example

import com.example.data.EtaCalculator
import com.example.model.ActiveTripEta
import com.example.model.DrivingTelemetry
import com.example.model.LearnedPaceProfile
import com.example.model.OverlayPreferences
import com.example.model.RoadTier
import com.example.model.SpeedLimitInfo
import com.example.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaCalculatorTest {

    @Test
    fun personalCap100On120Highway_recalculatesEtaAccurately() {
        val telemetry = DrivingTelemetry(
            hasLocationFix = true,
            currentSpeedMps = 100f / 3.6f, // cruising at 100 km/h
            speedLimitInfo = SpeedLimitInfo(
                kmhLimit = 120,
                mphLimit = 75,
                streetName = "Autobahn A1",
                roadTier = RoadTier.HIGHWAY
            )
        )
        val prefs = OverlayPreferences(
            unit = SpeedUnit.KMH,
            personalMaxSpeedEnabled = true,
            personalMaxSpeedKmh = 100
        )
        // 120 km trip, Google Maps estimates 60 min (at 120 km/h)
        val trip = ActiveTripEta(
            isActive = true,
            initialDistanceMeters = 120_000.0,
            remainingDistanceMeters = 120_000.0,
            mapsInitialSeconds = 3600L
        )

        val summary = EtaCalculator.calculateEtaSummary(
            telemetry = telemetry,
            prefs = prefs,
            learnedProfile = LearnedPaceProfile(),
            activeTrip = trip,
            nowMillis = 1_000_000L
        )

        assertEquals(100, summary.effectivePaceKmh)
        assertEquals(72, summary.recalculatedRemainingMinutes)
        assertEquals(12, summary.deltaMinutesVsMaps)
        assertEquals(12, summary.extraMinutesPerHour)
    }

    @Test
    fun drivingPaceLearner_learnsSteadySpeedAndAppliesToEtaWhenStopped() {
        var profile = LearnedPaceProfile()
        // Simulate 4 highway cruising samples around 96 km/h
        repeat(4) {
            val updated = EtaCalculator.recordDrivingSample(
                current = profile,
                tier = RoadTier.HIGHWAY,
                speedMps = 96f / 3.6f,
                accuracyMeters = 8f
            )
            assertNotNull(updated)
            profile = updated!!
        }

        assertEquals(4, profile.highwaySamples)
        assertTrue((profile.highwayAvgKmh ?: 0f) in 95f..97f)

        // When stationary at a rest stop, ETA still uses the learned 96 km/h highway pace
        val stoppedTelemetry = DrivingTelemetry(
            hasLocationFix = true,
            currentSpeedMps = 0f,
            speedLimitInfo = SpeedLimitInfo(
                kmhLimit = 120,
                roadTier = RoadTier.HIGHWAY
            )
        )
        val prefs = OverlayPreferences(
            unit = SpeedUnit.KMH,
            personalMaxSpeedEnabled = false,
            useLearnedPaceForEta = true
        )
        val trip = ActiveTripEta(
            isActive = true,
            initialDistanceMeters = 96_000.0,
            remainingDistanceMeters = 96_000.0,
            mapsInitialSeconds = 48 * 60L // Maps @ 120 km/h = 48m
        )

        val summary = EtaCalculator.calculateEtaSummary(
            telemetry = stoppedTelemetry,
            prefs = prefs,
            learnedProfile = profile,
            activeTrip = trip,
            nowMillis = 2_000_000L
        )

        assertEquals(96, summary.effectivePaceKmh)
        assertEquals(60, summary.recalculatedRemainingMinutes)
        assertEquals(12, summary.deltaMinutesVsMaps)
    }
}
