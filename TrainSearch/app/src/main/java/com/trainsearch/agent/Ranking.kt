package com.trainsearch.agent

import com.trainsearch.data.ResultRow
import com.trainsearch.data.StatusKind
import com.trainsearch.data.Train
import com.trainsearch.data.formatDateDisplay

/**
 * Ranking is availability-only. Station convenience is a final tiebreak and
 * travel class is not an axis at all — a user who wants one class filters for
 * it before ranking rather than having it weighted here.
 */

/** One train becomes one row per class. */
fun flatten(train: Train, date: String, originGroupIndex: Int, destGroupIndex: Int): List<ResultRow> =
    train.availability.map { a ->
        ResultRow(
            trainNumber = train.trainNumber,
            trainName = train.trainName,
            fromStnCode = train.fromStnCode,
            toStnCode = train.toStnCode,
            departureTime = train.departureTime,
            arrivalTime = train.arrivalTime,
            durationMinutes = train.durationMinutes,
            durationFormatted = train.durationFormatted,
            date = formatDateDisplay(date),
            travelClass = a.travelClass,
            status = a.status,
            kind = a.kind,
            seats = a.seats,
            number = a.number,
            fare = a.fare,
            originGroupIndex = originGroupIndex,
            destGroupIndex = destGroupIndex,
            confirmChance = a.confirmChance
        )
    }

fun dedup(rows: List<ResultRow>): List<ResultRow> =
    rows.distinctBy {
        listOf(it.trainNumber, it.fromStnCode, it.date, it.travelClass)
    }

fun isDaytime(departureTime: String): Boolean {
    val hour = departureTime.substringBefore(':').trim().toIntOrNull() ?: return false
    return hour in 6..21
}

private fun kindRank(kind: StatusKind) = when (kind) {
    StatusKind.AVL -> 0
    StatusKind.RAC -> 1
    StatusKind.WL -> 2
    StatusKind.OTHER -> 3
}

/** Within-kind position: more seats is better for AVL; a shorter queue is better otherwise. */
private fun withinKind(row: ResultRow): Int = when (row.kind) {
    StatusKind.AVL -> -(row.seats ?: 0)
    else -> row.number ?: Int.MAX_VALUE
}

fun rank(rows: List<ResultRow>): List<ResultRow> = rows.sortedWith(
    compareBy<ResultRow> { kindRank(it.kind) }
        .thenBy { withinKind(it) }
        .thenBy { if (isDaytime(it.departureTime)) 0 else 1 }
        .thenBy { it.durationMinutes ?: Int.MAX_VALUE }
        .thenBy { it.originGroupIndex }
        .thenBy { it.destGroupIndex }
)
