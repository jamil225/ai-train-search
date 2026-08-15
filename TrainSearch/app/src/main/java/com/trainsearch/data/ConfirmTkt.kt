package com.trainsearch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val HOST = "https://cttrainsapi.confirmtkt.com"

// Public constants from ConfirmTkt's own web bundle. Not secrets, not per-user.
private val HEADERS = mapOf(
    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept" to "application/json",
    "clientid" to "ct-web",
    "apikey" to "ct-web!2\$",
    "deviceid" to "ct-mcp-0000-0000-0000-000000000000"
)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

class ConfirmTkt(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$HOST$path").apply {
            HEADERS.forEach { (k, v) -> addHeader(k, v) }
        }.build()
        client.newCall(req).execute().use { it.body?.string().orEmpty() }
    }

    /** Shared response envelope handling, mirroring apiGet() in the reference client. */
    private fun envelope(body: String): JsonObject {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw IllegalStateException(
                "ConfirmTkt returned a non-JSON response: ${body.take(200)}"
            )
        root["error"]?.let { err ->
            val obj = runCatching { err.jsonObject }.getOrNull()
            val message = obj?.get("message")?.jsonPrimitive?.content ?: err.toString()
            val code = obj?.get("code")?.jsonPrimitive?.content.orEmpty()
            throw IllegalStateException("ConfirmTkt API error $code: $message")
        }
        return root
    }

    internal fun parseSearchResponse(body: String): List<Train> {
        val list = envelope(body)["data"]?.jsonObject?.get("trainList")?.jsonArray ?: return emptyList()
        return list.mapNotNull { element ->
            val t = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            fun str(k: String) = runCatching { t[k]?.jsonPrimitive?.content }.getOrNull().orEmpty()
            val durationRaw = str("duration")
            Train(
                trainNumber = str("trainNumber"),
                trainName = str("trainName"),
                fromStnCode = str("fromStnCode"),
                fromStnName = str("fromStnName"),
                toStnCode = str("toStnCode"),
                toStnName = str("toStnName"),
                departureTime = str("departureTime"),
                arrivalTime = str("arrivalTime"),
                durationMinutes = durationRaw.toIntOrNull(),
                durationFormatted = formatDuration(durationRaw),
                availability = parseAvailability(
                    runCatching { t["availabilityCache"]?.jsonObject }.getOrNull()
                )
            )
        }
    }

    internal fun parseStationResponse(body: String): List<Station> {
        val list = envelope(body)["data"]?.jsonObject?.get("stationList")?.jsonArray ?: return emptyList()
        return list.mapNotNull { element ->
            val s = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            fun str(k: String) = runCatching { s[k]?.jsonPrimitive?.content }.getOrNull()
            Station(
                stationCode = str("stationCode") ?: return@mapNotNull null,
                stationName = str("stationName").orEmpty(),
                city = str("city"),
                isMajor = str("majorStn")?.toBoolean() ?: false
            )
        }
    }

    /** date accepts DD-MM-YYYY or YYYY-MM-DD. */
    suspend fun searchTrains(src: String, dst: String, date: String): List<Train> =
        parseSearchResponse(
            get(
                "/api/v1/trains/search?sourceStationCode=${enc(src)}" +
                    "&destinationStationCode=${enc(dst)}" +
                    "&dateOfJourney=${enc(normalizeDate(date))}"
            )
        )

    suspend fun lookupStations(query: String): List<Station> =
        parseStationResponse(
            get(
                "/api/v2/trains/stations/auto-suggestion?searchString=${enc(query.trim())}" +
                    "&sourceStnCode=&popularStnListLimit=15&preferredStnListLimit=6" +
                    "&channel=mwebd&language=EN"
            )
        )
}
