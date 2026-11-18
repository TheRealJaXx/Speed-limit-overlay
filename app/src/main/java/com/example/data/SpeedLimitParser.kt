package com.example.data

import com.example.model.SpeedLimitInfo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

data class ParsedSpeed(
    val mph: Int,
    val kmh: Int
)

object SpeedLimitParser {

    private val mphRegex = Regex("""(\d{1,3})\s*mph""", RegexOption.IGNORE_CASE)
    private val kmhRegex = Regex("""(\d{1,3})\s*(km/?h|kph)""", RegexOption.IGNORE_CASE)
    private val bareNumberRegex = Regex("""^(\d{1,3})$""")

    fun parseMaxSpeed(rawTag: String?): ParsedSpeed? {
        if (rawTag.isNullOrBlank()) return null
        val cleaned = rawTag.trim()

        mphRegex.find(cleaned)?.let { match ->
            val mph = match.groupValues[1].toIntOrNull() ?: return null
            if (mph in 5..120) {
                val kmh = (mph * 1.609344).roundToInt()
                return ParsedSpeed(mph = mph, kmh = kmh)
            }
        }

        kmhRegex.find(cleaned)?.let { match ->
            val kmh = match.groupValues[1].toIntOrNull() ?: return null
            if (kmh in 5..200) {
                val mph = (kmh * 0.621371).roundToInt()
                return ParsedSpeed(mph = mph, kmh = kmh)
            }
        }

        bareNumberRegex.find(cleaned)?.let { match ->
            // Standard OpenStreetMap convention: unitless maxspeed values are in km/h
            val kmh = match.groupValues[1].toIntOrNull() ?: return null
            if (kmh in 5..200) {
                val mph = (kmh * 0.621371).roundToInt()
                return ParsedSpeed(mph = mph, kmh = kmh)
            }
        }

        return null
    }

    fun matchClosestRoad(
        userLat: Double,
        userLon: Double,
        bearingDegrees: Float?,
        speedMps: Float,
        elements: List<OverpassElement>,
        nowMillis: Long = System.currentTimeMillis()
    ): SpeedLimitInfo? {
        if (elements.isEmpty()) return null

        val metersPerLat = 111_320.0
        val metersPerLon = 111_320.0 * cos(userLat * PI / 180.0).coerceAtLeast(0.1)
        val useBearing = bearingDegrees != null && speedMps >= 2.5f

        var bestElement: OverpassElement? = null
        var bestScore = Double.MAX_VALUE

        for (element in elements) {
            if (element.geometry.size < 2) continue
            val highwayType = element.tags["highway"] ?: ""

            var minRoadDistance = Double.MAX_VALUE
            var matchedSegmentBearing = 0.0

            for (i in 0 until element.geometry.lastIndex) {
                val a = element.geometry[i]
                val b = element.geometry[i + 1]

                val ax = (a.lon - userLon) * metersPerLon
                val ay = (a.lat - userLat) * metersPerLat
                val bx = (b.lon - userLon) * metersPerLon
                val by = (b.lat - userLat) * metersPerLat

                val dist = pointToSegmentDistanceMeters(ax, ay, bx, by)
                if (dist < minRoadDistance) {
                    minRoadDistance = dist
                    val dx = bx - ax
                    val dy = by - ay
                    matchedSegmentBearing = ((atan2(dx, dy) * 180.0 / PI) + 360.0) % 360.0
                }
            }

            if (minRoadDistance > 65.0) continue

            var score = minRoadDistance

            // Penalize service/parking aisles so main driving street wins
            if (highwayType == "service") {
                score += 18.0
            }

            // Slight preference for named roads and roads with speed tags
            if (element.tags["name"].isNullOrBlank() && element.tags["ref"].isNullOrBlank()) {
                score += 6.0
            }

            // Align with vehicle travel heading when moving
            if (useBearing && bearingDegrees != null) {
                val rawDiff = abs(((bearingDegrees.toDouble() - matchedSegmentBearing + 540.0) % 360.0) - 180.0)
                val bidirectionalDiff = min(rawDiff, 180.0 - rawDiff)
                score += (bidirectionalDiff / 90.0) * 18.0
            }

            if (score < bestScore) {
                bestScore = score
                bestElement = element
            }
        }

        val winner = bestElement ?: return null
        val tags = winner.tags

        val rawMaxSpeed = tags["maxspeed"]
            ?: tags["maxspeed:forward"]
            ?: tags["maxspeed:backward"]
            ?: tags["maxspeed:advisory"]

        val parsed = parseMaxSpeed(rawMaxSpeed)
        val streetName = tags["name"]?.takeIf { it.isNotBlank() }
            ?: tags["ref"]?.takeIf { it.isNotBlank() }
            ?: formatHighwayClass(tags["highway"])

        return SpeedLimitInfo(
            mphLimit = parsed?.mph,
            kmhLimit = parsed?.kmh,
            streetName = streetName,
            roadRef = tags["ref"],
            roadClass = formatHighwayClass(tags["highway"]),
            rawMaxSpeedTag = rawMaxSpeed,
            updatedAtMillis = nowMillis
        )
    }

    private fun pointToSegmentDistanceMeters(
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double
    ): Double {
        val abx = bx - ax
        val aby = by - ay
        val lenSq = abx * abx + aby * aby
        if (lenSq <= 1e-6) return hypot(ax, ay)

        val t = (-(ax * abx + ay * aby) / lenSq).coerceIn(0.0, 1.0)
        val closestX = ax + t * abx
        val closestY = ay + t * aby
        return hypot(closestX, closestY)
    }

    private fun formatHighwayClass(raw: String?): String = when (raw) {
        "motorway", "motorway_link" -> "Highway"
        "trunk", "trunk_link" -> "Expressway"
        "primary", "primary_link" -> "Primary Road"
        "secondary", "secondary_link" -> "Secondary Road"
        "tertiary", "tertiary_link" -> "Collector Street"
        "residential" -> "Residential Street"
        "living_street" -> "Living Street"
        "unclassified" -> "Local Road"
        "service" -> "Service Road"
        else -> "Street"
    }
}
