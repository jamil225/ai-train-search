package com.trainsearch.agent

import com.trainsearch.data.ResultRow
import com.trainsearch.data.StatusKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingTest {

    private fun row(
        train: String = "11090",
        from: String = "AII",
        to: String = "PUNE",
        cls: String = "SL",
        kind: StatusKind = StatusKind.AVL,
        number: Int? = 10,
        dep: String = "18:30",
        duration: Int? = 1200,
        date: String = "01-09-2026",
        originIdx: Int = 0,
        destIdx: Int = 0
    ) = ResultRow(
        trainNumber = train, trainName = "Test Exp", fromStnCode = from, toStnCode = to,
        departureTime = dep, arrivalTime = "10:00", durationMinutes = duration,
        durationFormatted = "20h 0m", date = date, travelClass = cls,
        status = "$kind $number", kind = kind,
        seats = if (kind == StatusKind.AVL) number else null,
        number = number, fare = 665, originGroupIndex = originIdx, destGroupIndex = destIdx
    )

    @Test fun `availability kind is the primary axis`() {
        val ranked = rank(
            listOf(
                row(train = "D", kind = StatusKind.OTHER, number = null),
                row(train = "C", kind = StatusKind.WL, number = 3),
                row(train = "B", kind = StatusKind.RAC, number = 40),
                row(train = "A", kind = StatusKind.AVL, number = 1)
            )
        )
        assertEquals(listOf("A", "B", "C", "D"), ranked.map { it.trainNumber })
    }

    @Test fun `station convenience never beats availability`() {
        val ranked = rank(
            listOf(
                row(train = "AII_WL", from = "AII", kind = StatusKind.WL, number = 4, originIdx = 0),
                row(train = "JU_AVL", from = "JU", kind = StatusKind.AVL, number = 2, originIdx = 4)
            )
        )
        assertEquals("JU_AVL", ranked.first().trainNumber)
    }

    @Test fun `travel class is not a ranking axis`() {
        val ranked = rank(
            listOf(
                row(train = "SL_WL", cls = "SL", kind = StatusKind.WL, number = 2),
                row(train = "2A_AVL", cls = "2A", kind = StatusKind.AVL, number = 1)
            )
        )
        assertEquals("2A_AVL", ranked.first().trainNumber)
    }

    @Test fun `available seats sort descending`() {
        val ranked = rank(
            listOf(
                row(train = "few", kind = StatusKind.AVL, number = 3),
                row(train = "many", kind = StatusKind.AVL, number = 40)
            )
        )
        assertEquals(listOf("many", "few"), ranked.map { it.trainNumber })
    }

    @Test fun `queue numbers sort ascending for RAC and WL`() {
        assertEquals(
            listOf("near", "far"),
            rank(
                listOf(
                    row(train = "far", kind = StatusKind.RAC, number = 30),
                    row(train = "near", kind = StatusKind.RAC, number = 2)
                )
            ).map { it.trainNumber }
        )
        assertEquals(
            listOf("near", "far"),
            rank(
                listOf(
                    row(train = "far", kind = StatusKind.WL, number = 55),
                    row(train = "near", kind = StatusKind.WL, number = 6)
                )
            ).map { it.trainNumber }
        )
    }

    @Test fun `daytime departures break ties before duration`() {
        val ranked = rank(
            listOf(
                row(train = "night", dep = "03:10"),
                row(train = "day", dep = "09:00")
            )
        )
        assertEquals(listOf("day", "night"), ranked.map { it.trainNumber })
    }

    @Test fun `shorter duration breaks remaining ties`() {
        val ranked = rank(
            listOf(
                row(train = "slow", duration = 1500),
                row(train = "fast", duration = 900)
            )
        )
        assertEquals(listOf("fast", "slow"), ranked.map { it.trainNumber })
    }

    @Test fun `station index is the final tiebreak`() {
        val ranked = rank(
            listOf(
                row(train = "JU", from = "JU", originIdx = 4),
                row(train = "AII", from = "AII", originIdx = 0)
            )
        )
        assertEquals(listOf("AII", "JU"), ranked.map { it.trainNumber })
    }

    @Test fun `isDaytime covers the 6am to 10pm window`() {
        assertTrue(isDaytime("06:00"))
        assertTrue(isDaytime("21:59"))
        assertFalse(isDaytime("22:00"))
        assertFalse(isDaytime("05:59"))
        assertFalse(isDaytime(""))
    }

    @Test fun `dedup removes identical train class date and pair`() {
        val a = row()
        val b = row()
        assertEquals(1, dedup(listOf(a, b)).size)
    }

    @Test fun `dedup keeps the same train boarding at different stations`() {
        val fromAii = row(from = "AII")
        val fromKsg = row(from = "KSG")
        assertEquals(2, dedup(listOf(fromAii, fromKsg)).size)
    }

    @Test fun `dedup keeps the same train on different dates`() {
        assertEquals(2, dedup(listOf(row(date = "01-09-2026"), row(date = "02-09-2026"))).size)
    }
}
