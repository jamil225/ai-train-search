package com.trainsearch.data

/**
 * Groups are a table, not code. A general user's "Delhi" is simply a group of one,
 * resolved through the live endpoint. The preferences screen planned for later
 * writes rows here and nothing else in the app changes.
 */
object Stations {

    val seeded: List<StationGroup> = listOf(
        StationGroup("Rajasthan", listOf("AII", "KSG", "JP", "MTD", "JU")),
        StationGroup("Pune", listOf("PUNE", "KK")),
        StationGroup("Mumbai", listOf("BDTS", "MMCT", "DR", "LTT", "CSMT", "PNVL"))
    )

    fun matchGroup(name: String): StationGroup? {
        val n = name.trim().lowercase()
        return seeded.firstOrNull { it.name.lowercase() == n }
    }

    /**
     * Ordered station codes for a place name or comma-separated list of places.
     * A group expands to its list; individual places resolve via group match or live endpoint.
     * Supports multi-origin/multi-destination strings like "Ajmer, Jaipur, Kishangarh, Jodhpur".
     */
    suspend fun resolve(name: String, api: ConfirmTkt): List<String> {
        val tokens = name.split(Regex("[,/|]|\\band\\b|\\bor\\b", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (tokens.size > 1) {
            val result = mutableListOf<String>()
            for (token in tokens) {
                result.addAll(resolveSingle(token, api))
            }
            return result.distinct()
        }
        return resolveSingle(name, api)
    }

    private suspend fun resolveSingle(name: String, api: ConfirmTkt): List<String> {
        matchGroup(name)?.let { return it.codes }

        val trimmed = name.trim()
        if (Regex("""^[A-Za-z]{2,5}$""").matches(trimmed)) return listOf(trimmed.uppercase())

        val matches = api.lookupStations(trimmed)
        val best = matches.firstOrNull { it.isMajor } ?: matches.firstOrNull() ?: return emptyList()
        return listOf(best.stationCode)
    }
}
