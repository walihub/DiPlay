package com.shilapi.xcertplay.vehicle.arcfox

import com.shilapi.xcertplay.transport.VehicleGear
import org.junit.Assert.*
import org.junit.Test

class ArcfoxMotionBufferTest {
    @Test fun androidGearValuesAndSpeedAreNotBydEnumsOrKmh() {
        for ((raw, expected) in mapOf(4 to VehicleGear.PARK, 2 to VehicleGear.REVERSE,
            1 to VehicleGear.NEUTRAL, 8 to VehicleGear.DRIVE, 16 to VehicleGear.DRIVE)) {
            val b = ArcfoxMotionBuffer()
            b.accept(-10.0, raw, 1000, 1000)
            val reading = b.drain(1000)!!
            assertEquals(expected, reading.gear)
            assertEquals(10.0, reading.samples.single().metersPerSecond, 0.0)
        }
    }
    @Test fun transitionDoesNotRelabelPreviousSamples() {
        val b = ArcfoxMotionBuffer()
        b.accept(2.0, 8, 1000, 1000)
        b.accept(-1.0, 2, 1250, 1250)
        val r = b.drain(1250)!!
        assertEquals(VehicleGear.REVERSE, r.gear)
        assertEquals(listOf(1250L), r.samples.map { it.elapsedMillis })
    }
    @Test fun invalidOrMissingPropertyClearsPendingData() {
        for (bad in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, 100.0)) {
            val b = ArcfoxMotionBuffer()
            b.accept(1.0, 8, 1000, 1000)
            b.accept(bad, 8, 1250, 1250)
            assertNull(b.drain(1250))
        }
        val b = ArcfoxMotionBuffer()
        b.accept(1.0, 8, 1000, 1000)
        b.accept(1.0, null, 1250, 1250)
        assertNull(b.drain(1250))
    }
    @Test fun staleAndFutureAndZeroTimestampsAreRejected() {
        for (timestamp in listOf(0L, 1000L, 4000L)) {
            val b = ArcfoxMotionBuffer()
            b.accept(1.0, 8, timestamp, 3500)
            assertNull(b.drain(3500))
        }
        val b = ArcfoxMotionBuffer()
        b.accept(1.0, 8, 1000, 1000)
        assertNull(b.drain(3001))
    }
    @Test fun cachedValuesAreNotResampledAndReconnectStartsClean() {
        val b = ArcfoxMotionBuffer()
        b.accept(1.0, 8, 1000, 1000)
        assertNotNull(b.drain(1000))
        b.accept(1.0, 8, 1000, 1250)
        assertNull(b.drain(1250))
        b.clear()
        b.accept(1.0, 8, 1000, 1250)
        assertNotNull(b.drain(1250))
    }
}
