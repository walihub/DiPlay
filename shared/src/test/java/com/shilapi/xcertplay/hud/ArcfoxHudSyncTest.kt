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
}
