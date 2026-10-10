package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcfoxClusterDisplayTest {
    private val fingerprint = "AAOP_COCKPIT/msmnile_gvmq/msmnile_gvmq:11/test"

    @Test fun fixedSafeAreaExcludesBothSidesAndTopAndBottom() {
        val area = ArcfoxClusterDisplay.MAP_VIEW_INSETS
        assertEquals(500, area.left)
        assertEquals(500, area.right)
        assertEquals(80, area.top)
        assertEquals(30, area.bottom)
        assertEquals(920, ArcfoxClusterDisplay.WIDTH - area.left - area.right)
        assertEquals(610, ArcfoxClusterDisplay.HEIGHT - area.top - area.bottom)
    }

    @Test fun selectsOnlyTheObservedPhysicalInstrument() {
        assertTrue(ArcfoxClusterDisplay.matches(fingerprint, "HDMI 屏幕", 1920, 720, 2))
        assertFalse(ArcfoxClusterDisplay.matches(fingerprint, "HDMI 屏幕", 3840, 720, 0))
        assertFalse(ArcfoxClusterDisplay.matches(fingerprint, "HDMI 屏幕", 1920, 1080, 1))
        assertFalse(ArcfoxClusterDisplay.matches("BYD/test", "HDMI 屏幕", 1920, 720, 2))
    }

    @Test fun savedMarkerPositionsCannotShrinkOrMoveTheFixedSafeArea() {
        for (x in listOf(5, 50, 95)) for (y in listOf(5, 60, 75, 95)) {
            val config = ArcfoxClusterDisplay.streamConfig(CarPlayClusterDisplay.Content.MAP_WITH_CUSTOM_CARD,
                markerXPercent = x, markerYPercent = y)
            assertEquals(ArcfoxClusterDisplay.MAP_VIEW_INSETS, config.safeArea)
            assertEquals(CarPlayClusterDisplay.MAP_URL, config.initialUrl)
        }
    }

    @Test fun smallCardMatchesAmapCrossViewAndScalesWithThePanel() {
        assertEquals(ArcfoxClusterDisplay.SMALL_CARD_RECT,
            ArcfoxClusterDisplay.smallCardRect(1920, 720, 18, 55, 40))
        val scaled = ArcfoxClusterDisplay.smallCardRect(960, 360, 18, 55, 40)
        assertEquals(50, scaled.left)
        assertEquals(103, scaled.top)
        assertEquals(245, scaled.width)
        assertEquals(191, scaled.height)
        val moved = ArcfoxClusterDisplay.smallCardRect(1920, 720, 28, 55, 40)
        assertEquals(291, moved.left)
        assertEquals(206, moved.top)
        val large = ArcfoxClusterDisplay.smallCardRect(1920, 720, 90, 70, 95)
        assertTrue(large.left >= 0 && large.top >= 0)
        assertTrue(large.left + large.width <= 1920 && large.top + large.height <= 720)
    }

    @Test fun requestsANativeClusterStream() {
        val config = ArcfoxClusterDisplay.streamConfig(CarPlayClusterDisplay.Content.MAP)
        assertEquals(1920, config.widthPixels)
        assertEquals(720, config.heightPixels)
        assertEquals(CarPlayClusterDisplay.MAP_URL, config.initialUrl)
    }
    @Test fun fullCanvasAndOutsideDrawingRemainEnabledWithoutVideoCropping() {
        val config = ArcfoxClusterDisplay.streamConfig(CarPlayClusterDisplay.Content.MAP,
            horizontalStep = 1, verticalStep = -1)
        val safe = config.safeArea!!
        assertEquals(960, (safe.left + config.widthPixels - safe.right) / 2)
        assertEquals(385, (safe.top + config.heightPixels - safe.bottom) / 2)
        assertEquals(null, config.viewArea)
        assertEquals(true, config.safeAreaDrawOutside)
    }
}
