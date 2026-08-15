package com.trainsearch.agent

import com.trainsearch.data.ClassAvailability
import com.trainsearch.data.StatusKind
import com.trainsearch.data.Train
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {

    private fun train(no: String, from: String, cls: String, kind: StatusKind, n: Int) = Train(
        trainNumber = no, trainName = "Test Exp", fromStnCode = from, fromStnName = from,
        toStnCode = "PUNE", toStnName = "Pune Jn", departureTime = "18:30", arrivalTime = "15:15",
        durationMinutes = 1200, durationFormatted = "20h 0m",
        availability = listOf(
            ClassAvailability(cls, "$kind $n", kind, if (kind == StatusKind.AVL) n else null, n, 665, "GN")
        )
    )

    @Test fun `pairsFor builds the full origin by destination product`() {
        assertEquals(
            listOf("AII" to "PUNE", "AII" to "KK", "JP" to "PUNE", "JP" to "KK"),
            pairsFor(listOf("AII", "JP"), listOf("PUNE", "KK"))
        )
    }

    @Test fun `assemble flattens dedups ranks and truncates`() {
        val rows = assemble(
            collected = listOf(
                Triple(train("A", "JU", "SL", StatusKind.WL, 9), "01-09-2026", 4 to 0),
                Triple(train("B", "AII", "SL", StatusKind.AVL, 12), "01-09-2026", 0 to 0),
                Triple(train("B", "AII", "SL", StatusKind.AVL, 12), "01-09-2026", 0 to 0)
            ),
            classes = emptyList(),
            limit = 10
        )
        assertEquals(listOf("B", "A"), rows.map { it.trainNumber })
        assertEquals(2, rows.size)
    }

    @Test fun `assemble filters to a requested class before ranking`() {
        val rows = assemble(
            collected = listOf(
                Triple(train("A", "AII", "2A", StatusKind.AVL, 20), "01-09-2026", 0 to 0),
                Triple(train("B", "AII", "SL", StatusKind.WL, 3), "01-09-2026", 0 to 0)
            ),
            classes = listOf("SL"),
            limit = 10
        )
        assertEquals(listOf("B"), rows.map { it.trainNumber })
    }

    @Test fun `assemble truncates to the limit`() {
        val many = (1..15).map {
            Triple(train("T$it", "AII", "SL", StatusKind.AVL, it), "01-09-2026", 0 to 0)
        }
        assertEquals(10, assemble(many, emptyList(), 10).size)
    }
}
