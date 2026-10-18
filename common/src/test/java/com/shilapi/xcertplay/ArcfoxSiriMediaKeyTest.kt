package com.shilapi.xcertplay

import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ArcfoxSiriMediaKeyTest {
    private val controller = mock(CarPlayController::class.java)
    private fun field(name: String, value: Any?) = CarPlayMediaKeys.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.set(CarPlayMediaKeys, value)
    private fun send(index: Int) = CarPlayMediaKeys.javaClass.getDeclaredMethod("send", Int::class.javaPrimitiveType, String::class.java)
        .apply { isAccessible = true }.invoke(CarPlayMediaKeys, index, "arcfox-media-service")

    @After fun clear() {
        field("controller", null)
        field("vehicleMusicExclusive", false)
        field("lastArcfoxIndex", -1)
        field("lastArcfoxSource", "")
        field("lastArcfoxAt", 0L)
    }

    @Test fun consumedSiriKeyOnlySuppressesTheMatchingLateMediaCallbackForTheExistingWindow() {
        field("controller", controller)
        field("vehicleMusicExclusive", true)
        CarPlayMediaKeys.arcfoxMediaKeyConsumedAsSiri(KeyEvent.KEYCODE_MEDIA_NEXT)
        send(CarPlayMediaButton.NEXT)
        verify(controller, never()).sendMediaButton(CarPlayMediaButton.NEXT)
        // Another direction is a separate real action and is not swallowed.
        send(CarPlayMediaButton.PREVIOUS)
        verify(controller).sendMediaButton(CarPlayMediaButton.PREVIOUS)
        CarPlayMediaKeys.arcfoxMediaKeyConsumedAsSiri(KeyEvent.KEYCODE_MEDIA_NEXT)
        ShadowSystemClock.advanceBy(Duration.ofMillis(351))
        send(CarPlayMediaButton.NEXT)
        verify(controller).sendMediaButton(CarPlayMediaButton.NEXT)
    }

    @Test fun nonArcfoxMediaBehaviorDoesNotAcquireASiriSuppressionWindow() {
        field("controller", controller)
        field("vehicleMusicExclusive", false)
        CarPlayMediaKeys.arcfoxMediaKeyConsumedAsSiri(KeyEvent.KEYCODE_MEDIA_NEXT)
        send(CarPlayMediaButton.NEXT)
        verify(controller).sendMediaButton(CarPlayMediaButton.NEXT)
    }
}
