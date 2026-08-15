package com.trainsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationsTest {

    @Test fun `matchGroup expands Rajasthan in priority order`() {
        assertEquals(listOf("AII", "KSG", "JP", "MTD", "JU"), Stations.matchGroup("Rajasthan")?.codes)
    }

    @Test fun `matchGroup expands Pune and Mumbai`() {
        assertEquals(listOf("PUNE", "KK"), Stations.matchGroup("Pune")?.codes)
        assertEquals(
            listOf("BDTS", "MMCT", "DR", "LTT", "CSMT", "PNVL"),
            Stations.matchGroup("Mumbai")?.codes
        )
    }

    @Test fun `matchGroup ignores case and surrounding space`() {
        assertEquals("Rajasthan", Stations.matchGroup("  rajasthan ")?.name)
    }

    @Test fun `matchGroup returns null for an unknown place`() {
        assertNull(Stations.matchGroup("Bangalore"))
    }

    @Test fun `resolve handles multi-origin comma separated codes`() = kotlinx.coroutines.runBlocking {
        val dummyApi = ConfirmTkt()
        val codes = Stations.resolve("AII, JP, KSG, JU", dummyApi)
        assertEquals(listOf("AII", "JP", "KSG", "JU"), codes)
    }
}
