package com.watsup.azimute_find.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMathTest {

    @Test
    fun destinationPoint_oneDegreeNorth() {
        val p = destinationPoint(0.0, 0.0, 0.0, 111_195.0)
        assertEquals(1.0, p.lat, 1e-4)
        assertEquals(0.0, p.lon, 1e-9)
    }

    @Test
    fun destinationPoint_tenKmEastFromKrakow() {
        val p = destinationPoint(50.0614, 19.9366, 90.0, 10_000.0)
        // 10 km east at 50°N is ~0.1400° of longitude; latitude drifts slightly south on a great circle.
        assertEquals(50.0611, p.lat, 1e-3)
        assertEquals(20.0766, p.lon, 1e-3)
    }

    @Test
    fun destinationPoint_wrapsAcrossAntimeridian() {
        val p = destinationPoint(0.0, 179.95, 90.0, 11_119.5)
        assertEquals(-179.95, p.lon, 1e-3)
    }

    @Test
    fun normalizeDeg_wrapsIntoRange() {
        assertEquals(350f, normalizeDeg(-10f), 1e-4f)
        assertEquals(10f, normalizeDeg(370f), 1e-4f)
        assertEquals(0f, normalizeDeg(360f), 1e-4f)
    }

    @Test
    fun shortestAngleDelta_takesShortWay() {
        assertEquals(20f, shortestAngleDelta(350f, 10f), 1e-4f)
        assertEquals(-20f, shortestAngleDelta(10f, 350f), 1e-4f)
        assertEquals(90f, shortestAngleDelta(0f, 90f), 1e-4f)
    }

    @Test
    fun lowPassAngle_doesNotSwingThrough180AtWrap() {
        val filtered = lowPassAngle(359f, 1f, 0.5f)
        assertEquals(0f, shortestAngleDelta(0f, filtered), 1e-3f)
    }

    @Test
    fun lowPassAngle_firstSampleIsPassedThrough() {
        assertEquals(42f, lowPassAngle(null, 42f, 0.1f), 1e-4f)
    }
}
