package com.trainsearch.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsingTest {

    @Test fun `normalizeDate passes through DD-MM-YYYY`() {
        assertEquals("28-06-2026", normalizeDate("28-06-2026"))
    }

    @Test fun `normalizeDate converts ISO to DD-MM-YYYY`() {
        assertEquals("01-09-2026", normalizeDate("2026-09-01"))
    }

    @Test fun `normalizeDate converts slashes to DD-MM-YYYY`() {
        assertEquals("01-09-2026", normalizeDate("01/09/2026"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `normalizeDate rejects garbage`() {
        normalizeDate("next tuesday")
    }

    @Test fun `formatDuration renders hours and minutes`() {
        assertEquals("20h 45m", formatDuration("1245"))
        assertEquals("0h 5m", formatDuration("5"))
    }

    @Test fun `formatDuration passes through non-numeric input`() {
        assertEquals("unknown", formatDuration("unknown"))
    }

    @Test fun `classifyStatus recognises each kind`() {
        assertEquals(StatusKind.AVL, classifyStatus("AVL 24"))
        assertEquals(StatusKind.AVL, classifyStatus("AVAILABLE-0024"))
        assertEquals(StatusKind.OTHER, classifyStatus("Not Available"))
        assertEquals(StatusKind.OTHER, classifyStatus("NOT AVAILABLE"))
        assertEquals(StatusKind.RAC, classifyStatus("RAC 5"))
        assertEquals(StatusKind.WL, classifyStatus("WL 12"))
        assertEquals(StatusKind.WL, classifyStatus("GNWL 30"))
        assertEquals(StatusKind.WL, classifyStatus("RLWL 8"))
        assertEquals(StatusKind.OTHER, classifyStatus("Regret"))
        assertEquals(StatusKind.OTHER, classifyStatus(""))
    }

    @Test fun `parseStatusNumber extracts the first integer`() {
        assertEquals(24, parseStatusNumber("AVL 24"))
        assertEquals(24, parseStatusNumber("AVAILABLE-0024"))
        assertEquals(12, parseStatusNumber("GNWL 12"))
        assertNull(parseStatusNumber("Regret"))
    }

    @Test fun `parseAvailability reads seats fare and sorts by class order`() {
        val cache = Json.parseToJsonElement(
            """
            {
              "SL": {"availability":"AVAILABLE-0024","availabilityDisplayName":"AVL 24","fare":"665","quota":"GN"},
              "2A": {"availability":"WL-0012","availabilityDisplayName":"WL 12","fare":"1890","quota":"GN"},
              "3A": {"availability":"RAC-0005","availabilityDisplayName":"RAC 5","fare":"1245","quota":"GN"}
            }
            """.trimIndent()
        ) as JsonObject

        val out = parseAvailability(cache)

        assertEquals(listOf("2A", "3A", "SL"), out.map { it.travelClass })
        val sl = out.first { it.travelClass == "SL" }
        assertEquals(24, sl.seats)
        assertEquals(665, sl.fare)
        assertEquals(StatusKind.AVL, sl.kind)
        assertNull(out.first { it.travelClass == "2A" }.seats)
    }

    @Test fun `parseAvailability tolerates null and junk entries`() {
        assertEquals(emptyList<ClassAvailability>(), parseAvailability(null))
    }
}
