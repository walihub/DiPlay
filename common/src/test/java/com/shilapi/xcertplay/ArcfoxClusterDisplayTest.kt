package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcfoxClusterDisplayTest {
    private val fingerprint = "AAOP_COCKPIT/msmnile_gvmq/msmnile_gvmq:11/test"

    @Test fun selectsOnlyTheObservedPhysicalInstrument() {
        assertTrue(ArcfoxClusterDisplay.matches(fingerprint, "HDMI 屏幕", 1920, 720, 2))
        assertFalse(ArcfoxClusterDisplay.matches(fingerprint, "HDMI 屏幕", 3840, 720, 0))
        assertFalse(ArcfoxClusterDisplay.matches(fingerprint, "HDMI 屏幕", 1920, 1080, 1))
        assertFalse(ArcfoxClusterDisplay.matches("BYD/test", "HDMI 屏幕", 1920, 720, 2))
    }

    @Test fun requestsANativeClusterStream() {
        val config = ArcfoxClusterDisplay.streamConfig(CarPlayClusterDisplay.Content.MAP)
        assertEquals(1920, config.widthPixels)
        assertEquals(720, config.heightPixels)
        assertEquals(CarPlayClusterDisplay.MAP_URL, config.initialUrl)
    }
    @Test fun putsTheMarkerAtHorizontalCentreAndThreeQuarterHeight() {
        val config = ArcfoxClusterDisplay.streamConfig(CarPlayClusterDisplay.Content.MAP)
        val safe = config.safeArea!!
        assertEquals(960, (safe.left + config.widthPixels - safe.right) / 2)
        assertEquals(540, (safe.top + config.heightPixels - safe.bottom) / 2)
        assertEquals(true, config.safeAreaDrawOutside)
        val moved = ArcfoxClusterDisplay.streamConfig(CarPlayClusterDisplay.Content.MAP,
            horizontalStep = 1, verticalStep = -1)
        val area = moved.safeArea!!
        assertEquals(1152, (area.left + moved.widthPixels - area.right) / 2)
        assertEquals(468, (area.top + moved.heightPixels - area.bottom) / 2)
    }
}
