package com.shilapi.xcertplay.vehicle.arcfox

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class ArcfoxMediaCoordinationTest {
    private fun selection() = Intent(ArcfoxMediaCoordination.ACTION)
        .putExtra("origin", "com.dudu.autoui").putExtra("reason", "source-change")
        .putExtra("sourceId", 3).putExtra("observedAt", 1000L)

    @Test fun acceptsOnlyFreshNativeSourceTransitions() {
        assertTrue(ArcfoxMediaCoordination.isFreshNativeSelection(selection(), 1000))
        assertTrue(ArcfoxMediaCoordination.isFreshNativeSelection(selection().putExtra("reason", "native-play"), 2000))
        assertFalse(ArcfoxMediaCoordination.isFreshNativeSelection(selection(), 11001))
        assertFalse(ArcfoxMediaCoordination.isFreshNativeSelection(selection(), 999))
        assertFalse(ArcfoxMediaCoordination.isFreshNativeSelection(selection().putExtra("reason", "metadata"), 1000))
        assertFalse(ArcfoxMediaCoordination.isFreshNativeSelection(selection().putExtra("sourceId", -1), 1000))
        assertFalse(ArcfoxMediaCoordination.isFreshNativeSelection(selection().putExtra("sourceId", 19), 1000))
        assertFalse(ArcfoxMediaCoordination.isFreshNativeSelection(selection().putExtra("origin", "other"), 1000))
    }
}
