package com.trainsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmTktTest {

    private val sample = """
    {"data":{"trainList":[
      {"trainNumber":"11090","trainName":"BGKT PUNE EXP","fromStnCode":"AII","fromStnName":"AJMER JN",
       "toStnCode":"PUNE","toStnName":"PUNE JN","departureTime":"18:30","arrivalTime":"15:15",
       "duration":"1245","distance":1180,
       "availabilityCache":{"SL":{"availability":"AVAILABLE-0024","availabilityDisplayName":"AVL 24","fare":"665","quota":"GN"}}}
    ]}}
    """.trimIndent()

    @Test fun `parseSearchResponse maps a train`() {
        val trains = ConfirmTkt().parseSearchResponse(sample)
        assertEquals(1, trains.size)
        val t = trains[0]
        assertEquals("11090", t.trainNumber)
        assertEquals("AII", t.fromStnCode)
        assertEquals(1245, t.durationMinutes)
        assertEquals("20h 45m", t.durationFormatted)
        assertEquals(1, t.availability.size)
        assertEquals(24, t.availability[0].seats)
    }

    @Test fun `parseSearchResponse returns empty for no trains`() {
        assertEquals(emptyList<Train>(), ConfirmTkt().parseSearchResponse("""{"data":{"trainList":[]}}"""))
    }

    @Test fun `parseSearchResponse raises the API error message`() {
        val e = runCatching {
            ConfirmTkt().parseSearchResponse("""{"error":{"code":"E1","message":"bad station"}}""")
        }.exceptionOrNull()
        assertTrue(e is IllegalStateException)
        assertTrue(e!!.message!!.contains("bad station"))
    }

    @Test fun `parseSearchResponse raises on non-JSON`() {
        val e = runCatching { ConfirmTkt().parseSearchResponse("<html>503</html>") }.exceptionOrNull()
        assertTrue(e is IllegalStateException)
    }
}
