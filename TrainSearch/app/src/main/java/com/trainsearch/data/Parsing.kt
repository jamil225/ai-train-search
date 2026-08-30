package com.trainsearch.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val DDMMYYYY = Regex("""^\d{2}-\d{2}-\d{4}$""")
private val ISO = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
private val SLASHED = Regex("""^(\d{2})/(\d{2})/(\d{4})$""")
private val FIRST_INT = Regex("""(\d+)""")

/** Accepts DD-MM-YYYY, YYYY-MM-DD or DD/MM/YYYY. Emits the API's DD-MM-YYYY. */
fun normalizeDate(input: String): String {
    val t = input.trim()
    if (DDMMYYYY.matches(t)) return t
    ISO.matchEntire(t)?.let { return "${it.groupValues[3]}-${it.groupValues[2]}-${it.groupValues[1]}" }
    SLASHED.matchEntire(t)?.let { return "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }
    throw IllegalArgumentException("Invalid date \"$input\". Use DD-MM-YYYY or YYYY-MM-DD.")
}

fun formatDuration(minutes: String): String {
    val m = minutes.trim().toIntOrNull() ?: return minutes
    return "${m / 60}h ${m % 60}m"
}

private val MONTH_NAMES = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Converts DD-MM-YYYY or YYYY-MM-DD to "15 Aug 2026" or "4 Sep 2026". */
fun formatDateDisplay(rawDate: String): String {
    val normalized = runCatching { normalizeDate(rawDate) }.getOrDefault(rawDate)
    val parts = normalized.split("-")
    if (parts.size != 3) return rawDate
    val day = parts[0].toIntOrNull() ?: return rawDate
    val monthNum = parts[1].toIntOrNull() ?: return rawDate
    val year = parts[2]
    val monthStr = MONTH_NAMES.getOrNull(monthNum) ?: return rawDate
    return "$day $monthStr $year"
}

/** Formats a list of dates into a clean range like "16 Aug – 22 Aug 2026". */
fun formatDateRange(dates: List<String>): String {
    if (dates.isEmpty()) return ""
    if (dates.size == 1) return formatDateDisplay(dates.first())
    val start = formatDateDisplay(dates.first())
    val end = formatDateDisplay(dates.last())
    val startParts = start.split(" ")
    val endParts = end.split(" ")
    return if (startParts.size == 3 && endParts.size == 3 && startParts[2] == endParts[2]) {
        "${startParts[0]} ${startParts[1]} \u2013 ${endParts[0]} ${endParts[1]} ${endParts[2]}"
    } else {
        "$start \u2013 $end"
    }
}

fun classifyStatus(raw: String): StatusKind {
    val s = raw.uppercase()
    return when {
        s.contains("NOT AVAILABLE") || s.contains("NOT_AVAILABLE") || s.contains("NOT") || s.contains("REGRET") -> StatusKind.OTHER
        s.contains("AVAILABLE") || s.startsWith("AVL") -> StatusKind.AVL
        s.startsWith("RAC") -> StatusKind.RAC
        s.contains("WL") -> StatusKind.WL
        else -> StatusKind.OTHER
    }
}

fun parseStatusNumber(raw: String): Int? =
    FIRST_INT.find(raw)?.groupValues?.get(1)?.toIntOrNull()

private val CLASS_ORDER = listOf("1A", "2A", "3A", "3E", "CC", "EC", "SL", "2S")

/** Port of parseAvailability() from the vendored MCP client. */
fun parseAvailability(cache: JsonObject?): List<ClassAvailability> {
    if (cache == null) return emptyList()
    val out = mutableListOf<ClassAvailability>()
    for ((cls, element) in cache) {
        val info = runCatching { element.jsonObject }.getOrNull() ?: continue
        fun str(key: String): String? =
            runCatching { info[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }

        val raw = str("availability") ?: ""
        val display = str("availabilityDisplayName") ?: raw
        val kind = classifyStatus(raw.ifBlank { display })
        val number = parseStatusNumber(raw.ifBlank { display })

        val statusText = when {
            kind == StatusKind.AVL && number != null -> "AVL $number"
            kind == StatusKind.RAC && number != null -> "RAC $number"
            kind == StatusKind.WL && number != null -> "WL $number"
            kind == StatusKind.OTHER && display.contains("NOT AVAILABLE", ignoreCase = true) -> "REGRET"
            display.isNotBlank() -> display
            else -> raw
        }

        out += ClassAvailability(
            travelClass = cls,
            status = statusText,
            kind = kind,
            seats = if (kind == StatusKind.AVL) number else null,
            number = number,
            fare = str("fare")?.toDoubleOrNull()?.toInt(),
            quota = str("quota"),
            confirmChance = str("predictionPercentage")?.toDoubleOrNull()?.toInt()
        )
    }
    return out.sortedBy { CLASS_ORDER.indexOf(it.travelClass).let { i -> if (i < 0) 99 else i } }
}
