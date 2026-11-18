package com.example.data

import android.location.Location
import com.example.model.SpeedLimitInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SpeedLimitRepository(
    private val primaryService: OverpassService = OverpassClient.primary,
    private val fallbackService: OverpassService = OverpassClient.fallback
) {
    private val mutex = Mutex()

    private var cachedLat: Double = 0.0
    private var cachedLon: Double = 0.0
    private var cachedAtMillis: Long = 0L
    private var cachedElements: List<OverpassElement> = emptyList()
    private var lastAttemptMillis: Long = 0L

    data class LookupResult(
        val info: SpeedLimitInfo?,
        val fetchedFromNetwork: Boolean,
        val errorMessage: String? = null
    )

    suspend fun lookupStreetSpeedLimit(
        lat: Double,
        lon: Double,
        bearingDegrees: Float?,
        speedMps: Float,
        forceRefresh: Boolean = false
    ): LookupResult = mutex.withLock {
        val now = System.currentTimeMillis()
        val distFromCache = if (cachedElements.isNotEmpty()) {
            distanceMeters(cachedLat, cachedLon, lat, lon)
        } else {
            Float.MAX_VALUE
        }

        val cacheFresh = cachedElements.isNotEmpty() &&
            distFromCache <= 85f &&
            (now - cachedAtMillis) < 90_000L

        if (cacheFresh && !forceRefresh) {
            val matched = SpeedLimitParser.matchClosestRoad(
                userLat = lat,
                userLon = lon,
                bearingDegrees = bearingDegrees,
                speedMps = speedMps,
                elements = cachedElements,
                nowMillis = now
            )
            if (matched != null) {
                return@withLock LookupResult(info = matched, fetchedFromNetwork = false)
            }
        }

        // Throttle network calls to avoid hammering Overpass if GPS drifts or offline
        if (!forceRefresh && (now - lastAttemptMillis) < 4_500L && distFromCache < 30f) {
            val matched = SpeedLimitParser.matchClosestRoad(
                userLat = lat,
                userLon = lon,
                bearingDegrees = bearingDegrees,
                speedMps = speedMps,
                elements = cachedElements,
                nowMillis = now
            )
            return@withLock LookupResult(info = matched, fetchedFromNetwork = false)
        }

        lastAttemptMillis = now
        val query = OverpassClient.buildRoadQuery(lat, lon, radiusMeters = 110)

        val response = runCatching { primaryService.queryRoads(query) }
            .recoverCatching { fallbackService.queryRoads(query) }

        return@withLock response.fold(
            onSuccess = { body ->
                cachedLat = lat
                cachedLon = lon
                cachedAtMillis = now
                cachedElements = body.elements

                val matched = SpeedLimitParser.matchClosestRoad(
                    userLat = lat,
                    userLon = lon,
                    bearingDegrees = bearingDegrees,
                    speedMps = speedMps,
                    elements = body.elements,
                    nowMillis = now
                )
                LookupResult(
                    info = matched ?: SpeedLimitInfo(
                        streetName = "No mapped road nearby",
                        updatedAtMillis = now
                    ),
                    fetchedFromNetwork = true
                )
            },
            onFailure = { error ->
                val fallbackCached = SpeedLimitParser.matchClosestRoad(
                    userLat = lat,
                    userLon = lon,
                    bearingDegrees = bearingDegrees,
                    speedMps = speedMps,
                    elements = cachedElements,
                    nowMillis = now
                )
                LookupResult(
                    info = fallbackCached,
                    fetchedFromNetwork = false,
                    errorMessage = error.localizedMessage ?: "Road data temporarily unavailable"
                )
            }
        )
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, result)
        return result[0]
    }
}
