package com.example

import com.example.data.OverpassElement
import com.example.data.OverpassLatLon
import com.example.data.SpeedLimitParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedLimitParserTest {

    @Test
    fun parseMaxSpeed_handlesMphAndKmhFormats() {
        val mph = SpeedLimitParser.parseMaxSpeed("35 mph")
        assertNotNull(mph)
        assertEquals(35, mph?.mph)
        assertEquals(56, mph?.kmh)

        val bareKmh = SpeedLimitParser.parseMaxSpeed("50")
        assertNotNull(bareKmh)
        assertEquals(50, bareKmh?.kmh)
        assertEquals(31, bareKmh?.mph)

        val explicitKmh = SpeedLimitParser.parseMaxSpeed("100 km/h")
        assertNotNull(explicitKmh)
        assertEquals(100, explicitKmh?.kmh)
        assertEquals(62, explicitKmh?.mph)

        assertNull(SpeedLimitParser.parseMaxSpeed("signals"))
        assertNull(SpeedLimitParser.parseMaxSpeed(null))
    }

    @Test
    fun matchClosestRoad_prefersAlignedDrivingStreetAtIntersection() {
        // User at (37.7749, -122.4194) heading East (bearing 90 deg) at 12 m/s
        val eastWestStreet = OverpassElement(
            type = "way",
            id = 1L,
            tags = mapOf(
                "name" to "Market Street",
                "highway" to "primary",
                "maxspeed" to "25 mph"
            ),
            geometry = listOf(
                OverpassLatLon(37.7749, -122.4200),
                OverpassLatLon(37.7749, -122.4188)
            )
        )

        val crossStreet = OverpassElement(
            type = "way",
            id = 2L,
            tags = mapOf(
                "name" to "Van Ness Avenue",
                "highway" to "primary",
                "maxspeed" to "35 mph"
            ),
            geometry = listOf(
                OverpassLatLon(37.7743, -122.4194),
                OverpassLatLon(37.7755, -122.4194)
            )
        )

        val matched = SpeedLimitParser.matchClosestRoad(
            userLat = 37.7749,
            userLon = -122.4194,
            bearingDegrees = 90f,
            speedMps = 12f,
            elements = listOf(crossStreet, eastWestStreet)
        )

        assertNotNull(matched)
        assertEquals("Market Street", matched?.streetName)
        assertEquals(25, matched?.mphLimit)
    }
}
