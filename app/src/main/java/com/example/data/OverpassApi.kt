package com.example.data

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OverpassElement(
    val type: String = "",
    val id: Long = 0L,
    val tags: Map<String, String> = emptyMap(),
    val geometry: List<OverpassLatLon> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OverpassLatLon(
    val lat: Double,
    val lon: Double
)

interface OverpassService {
    @FormUrlEncoded
    @POST("api/interpreter")
    suspend fun queryRoads(@Field("data") query: String): OverpassResponse
}

object OverpassClient {
    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "SpeedSignAndroid/1.0")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(logging)
            .build()
    }

    val primary: OverpassService by lazy {
        Retrofit.Builder()
            .baseUrl("https://overpass-api.de/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(OverpassService::class.java)
    }

    val fallback: OverpassService by lazy {
        Retrofit.Builder()
            .baseUrl("https://overpass.kumi.systems/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(OverpassService::class.java)
    }

    fun buildRoadQuery(lat: Double, lon: Double, radiusMeters: Int = 75): String {
        return """
            [out:json][timeout:8];
            way(around:$radiusMeters,$lat,$lon)["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link|service)$"];
            out tags geom;
        """.trimIndent()
    }
}
