package com.trainsearch.data

enum class StatusKind { AVL, RAC, WL, OTHER }

data class ClassAvailability(
    val travelClass: String,
    val status: String,
    val kind: StatusKind,
    val seats: Int?,
    val number: Int?,
    val fare: Int?,
    val quota: String?,
    /** ConfirmTkt's own confirmation-chance prediction, 0-100. Only meaningful for WL. */
    val confirmChance: Int? = null
)

data class Train(
    val trainNumber: String,
    val trainName: String,
    val fromStnCode: String,
    val fromStnName: String,
    val toStnCode: String,
    val toStnName: String,
    val departureTime: String,
    val arrivalTime: String,
    val durationMinutes: Int?,
    val durationFormatted: String,
    val availability: List<ClassAvailability>
)

/** One train, in one class, on one date. The unit the board renders and the ranker sorts. */
data class ResultRow(
    val trainNumber: String,
    val trainName: String,
    val fromStnCode: String,
    val toStnCode: String,
    val departureTime: String,
    val arrivalTime: String,
    val durationMinutes: Int?,
    val durationFormatted: String,
    val date: String,
    val travelClass: String,
    val status: String,
    val kind: StatusKind,
    val seats: Int?,
    val number: Int?,
    val fare: Int?,
    val originGroupIndex: Int,
    val destGroupIndex: Int,
    /** ConfirmTkt's own confirmation-chance prediction, 0-100. Only meaningful for WL. */
    val confirmChance: Int? = null
)

data class TripQuery(
    val origin: String,
    val destination: String,
    val dates: List<String>,
    val classes: List<String>
)

/**
 * Explicit, typed conversation state — the source of truth for slot values.
 * Singular comma-joined strings mirror the TripQuery shape; Stations.resolve already splits them.
 */
@kotlinx.serialization.Serializable
data class TripState(
    val origin: String? = null,
    val destination: String? = null,
    val dateExpression: String? = null,  // "today till 4 Sep" — as the user phrased it
    val dates: List<String> = emptyList(), // expanded ISO, e.g. ["2026-09-03", "2026-09-04"]
    val classes: List<String> = emptyList() // empty means "any"
) {
    fun isComplete() = !origin.isNullOrBlank() && !destination.isNullOrBlank() && dates.isNotEmpty()
    fun toTripQuery() = TripQuery(origin!!, destination!!, dates, classes)
}

/** Raw response from LLM reduceTrip call. */
@kotlinx.serialization.Serializable
data class TripStateDelta(
    val reset: Boolean = false,
    val cleared: List<String> = emptyList(),
    val state: TripState = TripState(),
    val needsClarification: Boolean = false,
    val question: String? = null
)

/**
 * Merge the given delta into the current state using the merge rule:
 * 1. Non-empty wins — model return non-empty → take it.
 * 2. Empty never clears — model omits field → keep old value.
 * 3. Explicit clears — only fields named in cleared[] are blanked.
 *
 * This is the crux of the TripState design — guarantees defensive behavior.
 */
fun mergeTripState(current: TripState, delta: TripStateDelta): TripState {
    if (delta.reset) return delta.state
    return TripState(
        origin = delta.state.origin?.takeIf { it.isNotBlank() } ?: (if ("origin" in delta.cleared) null else current.origin),
        destination = delta.state.destination?.takeIf { it.isNotBlank() } ?: (if ("destination" in delta.cleared) null else current.destination),
        dateExpression = delta.state.dateExpression?.takeIf { it.isNotBlank() } ?: (if ("dateExpression" in delta.cleared) null else current.dateExpression),
        dates = delta.state.dates.takeIf { it.isNotEmpty() } ?: (if ("dates" in delta.cleared) emptyList() else current.dates),
        classes = delta.state.classes.takeIf { it.isNotEmpty() } ?: (if ("classes" in delta.cleared) emptyList() else current.classes)
    )
}

/** A lightweight conversation turn passed into the LLM prompt — role + text only, no id/timestamp. */
data class ConvTurn(val role: MessageRole, val content: String)

/** What the model returned for one parse attempt: either a complete trip, or a follow-up question. */
sealed interface ParseOutcome {
    data class Parsed(val trip: TripQuery) : ParseOutcome
    data class NeedsClarification(val question: String) : ParseOutcome
}

data class StationGroup(val name: String, val codes: List<String>)

data class Station(
    val stationCode: String,
    val stationName: String,
    val city: String?,
    val isMajor: Boolean
)
