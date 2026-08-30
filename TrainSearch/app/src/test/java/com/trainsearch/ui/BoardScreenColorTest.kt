package com.trainsearch.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BoardScreenColorTest {

    @Test fun `confirmChanceColor is green at and above 70`() {
        assertEquals(AvlGreen, confirmChanceColor(70))
        assertEquals(AvlGreen, confirmChanceColor(100))
    }

    @Test fun `confirmChanceColor is amber between 40 and 69`() {
        assertEquals(RacAmber, confirmChanceColor(40))
        assertEquals(RacAmber, confirmChanceColor(55))
        assertEquals(RacAmber, confirmChanceColor(69))
    }

    @Test fun `confirmChanceColor is red below 40`() {
        assertEquals(WlRed, confirmChanceColor(39))
        assertEquals(WlRed, confirmChanceColor(0))
    }
}
