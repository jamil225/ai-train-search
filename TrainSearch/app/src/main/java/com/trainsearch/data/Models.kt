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

data class StationGroup(val name: String, val codes: List<String>)

data class Station(
    val stationCode: String,
    val stationName: String,
    val city: String?,
    val isMajor: Boolean
)
