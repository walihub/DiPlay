package com.shilapi.xcertplay.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ArcfoxHudSyncTest {
    @Test fun mapsAppleTurnsToTheNaviServiceHudIcons() {
        assertEquals(7, ArcfoxHudSync.guidance(BydAppleManeuver(150, 1, 0, "Left road")).icon)
        assertEquals(3, ArcfoxHudSync.guidance(BydAppleManeuver(150, 2, 0, "Right road")).icon)
        assertEquals(1, ArcfoxHudSync.guidance(BydAppleManeuver(150, 3, 0, "Straight road")).icon)
        assertEquals(89, ArcfoxHudSync.guidance(BydAppleManeuver(150, 30, 0, "Roundabout")).icon)
        assertEquals(0, ArcfoxHudSync.icon(0, 0))
    }

    @Test fun keepsDistanceAndRoadForHud() {
        val guidance = ArcfoxHudSync.guidance(BydAppleManeuver(325, 1, 0, "测试大道"))
        assertEquals(325, guidance.distance)
        assertEquals("测试大道", guidance.road)
    }

    @Test fun encodesRoadUsingTheNaviServicePacket() {
        val text = "A路".toByteArray(charset("GBK"))
        assertArrayEquals(byteArrayOf(3, 24, 7, text.size.toByte()) + text,
            ArcfoxHudSync.roadPacket("A 路"))
    }
    @Test fun retriesPreviousGuidanceAfterAPartialWriteFailure() {
        val calls = mutableListOf<ArcfoxHudSync.Guidance?>()
        var fail = false
        val writer = ArcfoxHudWriter { next ->
            calls.add(next)
            if (fail) throw IllegalStateException("property write failed")
        }
        val a = ArcfoxHudSync.Guidance(7, 100, "A")
        val b = ArcfoxHudSync.Guidance(3, 90, "B")
        writer.transmit(a)
        fail = true
        org.junit.Assert.assertThrows(IllegalStateException::class.java) { writer.transmit(b) }
        fail = false
        org.junit.Assert.assertTrue(writer.transmit(a))
        assertEquals(listOf(a, b, a), calls)
        org.junit.Assert.assertFalse(writer.transmit(a))
    }

    @Test fun clearsAndRetriesAfterTheFirstActiveWritePartiallyFails() {
        var fail = true
        val calls = mutableListOf<ArcfoxHudSync.Guidance?>()
        val writer = ArcfoxHudWriter { next ->
            calls.add(next)
            if (fail) throw IllegalStateException("partial write")
        }
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            writer.transmit(ArcfoxHudSync.Guidance(7, 100, "A"))
        }
        org.junit.Assert.assertThrows(IllegalStateException::class.java) { writer.transmit(null) }
        fail = false
        org.junit.Assert.assertTrue(writer.transmit(null))
        org.junit.Assert.assertFalse(writer.mayHaveOutput)
        assertEquals(3, calls.size)
    }

    @Test fun reportsTheActualRouteExpiryAndEmptyListConditions() {
        var clock = 0L
        val route = BydHudRouteState(nanoTime = { clock })
        fun tlv(id: Int, vararg values: Int): ByteArray =
            byteArrayOf(0, (values.size + 4).toByte(), 0, id.toByte()) + values.map { it.toByte() }.toByteArray()
        assertEquals("no_active_route", route.visibilityReason())
        route.accept(0x5201, tlv(1, 1) + tlv(13, 0, 7))
        assertEquals("missing_maneuver_details", route.visibilityReason())
        route.accept(0x5202, tlv(1, 0, 7) + tlv(3, 1))
        assertEquals("guidance_ready", route.visibilityReason())
        route.accept(0x5201, tlv(13))
        clock = 3_000_000_000L
        assertEquals("empty_list_3s", route.visibilityReason())
        route.accept(0x5201, tlv(13, 0, 7))
        assertEquals("guidance_ready", route.visibilityReason())
        clock += 30_000_000_000L
        assertEquals("route_stale_30s", route.visibilityReason())
    }
}
