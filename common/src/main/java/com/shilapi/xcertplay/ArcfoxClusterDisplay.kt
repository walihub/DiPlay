package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayInsets
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay

/** The HS7013A's physical instrument display, as observed in its read-only display dump. */
internal object ArcfoxClusterDisplay {
    private const val FINGERPRINT_PREFIX = "AAOP_COCKPIT/"
    private const val DISPLAY_NAME = "HDMI 屏幕"
    const val WIDTH = 1920
    const val HEIGHT = 720
    // Amap 7.5 MultipleScreen.json: large instrument UI origin (85,130).
    // With DiPlay's default 40% card (222x84), the 1% centre grid gives (81,130).
    const val SMALL_CARD_X_PERCENT = 10
    const val SMALL_CARD_Y_PERCENT = 24

    fun isVehicle(fingerprint: String = android.os.Build.FINGERPRINT): Boolean =
        fingerprint.startsWith(FINGERPRINT_PREFIX)

    fun matches(fingerprint: String, name: String, width: Int, height: Int, displayId: Int): Boolean =
        fingerprint.startsWith(FINGERPRINT_PREFIX) && displayId != 0 &&
            name == DISPLAY_NAME && width == WIDTH && height == HEIGHT

    fun streamConfig(content: CarPlayClusterDisplay.Content,
        horizontalStep: Int = 0, verticalStep: Int = 0,
        markerXPercent: Int? = null, markerYPercent: Int? = null): AirPlayDisplayConfig =
        CarPlayClusterDisplay.config(
            WIDTH, HEIGHT, scalePercent = 100, content = content,
            horizontalStep = horizontalStep, verticalStep = verticalStep,
            markerXPercent = markerXPercent, markerYPercent = markerYPercent,
            // Safe-area centre is (50%, 75%): centred horizontally, one quarter below centre.
            baseSafeArea = AirPlayInsets(top = 50, bottom = 0, left = 0, right = 0),
        )
}
