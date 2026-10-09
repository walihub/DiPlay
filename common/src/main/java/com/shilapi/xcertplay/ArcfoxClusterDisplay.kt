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

    fun matches(fingerprint: String, name: String, width: Int, height: Int, displayId: Int): Boolean =
        fingerprint.startsWith(FINGERPRINT_PREFIX) && displayId != 0 &&
            name == DISPLAY_NAME && width == WIDTH && height == HEIGHT

    fun streamConfig(content: CarPlayClusterDisplay.Content,
        horizontalStep: Int = 0, verticalStep: Int = 0): AirPlayDisplayConfig =
        CarPlayClusterDisplay.config(
            WIDTH, HEIGHT, scalePercent = 100, content = content,
            horizontalStep = horizontalStep, verticalStep = verticalStep,
            // Safe-area centre is (50%, 75%): centred horizontally, one quarter below centre.
            baseSafeArea = AirPlayInsets(top = 50, bottom = 0, left = 0, right = 0),
        )
}
