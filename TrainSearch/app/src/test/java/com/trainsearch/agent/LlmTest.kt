package com.trainsearch.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmTest {

    private fun envelope(content: String) =
        """{"choices":[{"message":{"content":${org.json.JSONObject.quote(content)}}}]}"""

    private val llm = Llm("sk-test")

    @Test fun `parseTripJson reads a well-formed trip`() {
        val q = llm.parseTripJson(
            envelope("""{"origin":"Rajasthan","destination":"Pune","dates":["2026-09-01"],"classes":["SL"]}""")
        )
        assertEquals("Rajasthan", q.origin)
        assertEquals("Pune", q.destination)
        assertEquals(listOf("2026-09-01"), q.dates)
        assertEquals(listOf("SL"), q.classes)
    }

    @Test fun `parseTripJson accepts an empty class list`() {
        val q = llm.parseTripJson(
            envelope("""{"origin":"Jaipur","destination":"Pune","dates":["2026-09-02"],"classes":[]}""")
        )
        assertTrue(q.classes.isEmpty())
    }

    @Test fun `parseTripJson truncates to thirty-one dates`() {
        val many = (1..35).joinToString(",") { """"2026-09-${"%02d".format(it)}"""" }
        val q = llm.parseTripJson(
            envelope("""{"origin":"Rajasthan","destination":"Pune","dates":[$many],"classes":[]}""")
        )
        assertEquals(31, q.dates.size)
    }

    @Test fun `parseTripJson rejects a blank origin`() {
        val e = runCatching {
            llm.parseTripJson(envelope("""{"origin":"","destination":"Pune","dates":["2026-09-01"],"classes":[]}"""))
        }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

    @Test fun `parseTripJson rejects an empty date list`() {
        val e = runCatching {
            llm.parseTripJson(envelope("""{"origin":"Ajmer","destination":"Pune","dates":[],"classes":[]}"""))
        }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

    @Test fun `parseTripJson rejects an unparseable date`() {
        val e = runCatching {
            llm.parseTripJson(envelope("""{"origin":"Ajmer","destination":"Pune","dates":["someday"],"classes":[]}"""))
        }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

    @Test fun `parseTripJson rejects a non-JSON model reply`() {
        val e = runCatching { llm.parseTripJson(envelope("I'm not sure what you mean")) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }
}
