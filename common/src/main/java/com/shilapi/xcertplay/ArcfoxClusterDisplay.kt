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
    // Fixed iPhone layout region. Full-panel map background still covers the OEM map.
    const val MAP_VIEW_WIDTH = 920
    const val MAP_VIEW_HEIGHT = 610
    const val MAP_VIEW_LEFT = 500
    const val MAP_VIEW_TOP = 80
    const val DEFAULT_MARKER_Y_PERCENT = 60
    val MAP_VIEW_INSETS = AirPlayInsets(top = 80, bottom = 30, left = 500, right = 500)
    // Window 1002, CrossViewType=3 in the supplied Amap 7.5 MultipleScreen.json.
    const val SMALL_CARD_X_PERCENT = 18
    const val SMALL_CARD_Y_PERCENT = 55
    const val SMALL_CARD_SIZE_PERCENT = 40
    val SMALL_CARD_RECT = com.shilapi.xcertplay.airplay.ClusterTurnCardOverlay.CardRect(99, 206, 490, 382)

    fun smallCardRect(panelWidth: Int, panelHeight: Int, xPercent: Int, yPercent: Int,
        sizePercent: Int): com.shilapi.xcertplay.airplay.ClusterTurnCardOverlay.CardRect {
        require(panelWidth > 0 && panelHeight > 0)
        val reference = SMALL_CARD_RECT
        val scaleX = panelWidth.toDouble() / WIDTH
        val scaleY = panelHeight.toDouble() / HEIGHT
        val scale = sizePercent.coerceIn(30, 95).toDouble() / SMALL_CARD_SIZE_PERCENT
        val fit = minOf(scale, panelWidth / (reference.width * scaleX),
            panelHeight / (reference.height * scaleY))
        val width = Math.round(reference.width * scaleX * fit).toInt().coerceIn(1, panelWidth)
        val height = Math.round(reference.height * scaleY * fit).toInt().coerceIn(1, panelHeight)
        val centerX = (reference.left + reference.width / 2.0) * scaleX +
            (xPercent.coerceIn(10, 90) - SMALL_CARD_X_PERCENT) * panelWidth / 100.0
        val centerY = (reference.top + reference.height / 2.0) * scaleY +
            (yPercent.coerceIn(15, 70) - SMALL_CARD_Y_PERCENT) * panelHeight / 100.0
        return com.shilapi.xcertplay.airplay.ClusterTurnCardOverlay.CardRect(
            Math.round(centerX - width / 2.0).toInt().coerceIn(0, panelWidth - width),
            Math.round(centerY - height / 2.0).toInt().coerceIn(0, panelHeight - height), width, height)
    }

    fun isVehicle(fingerprint: String = android.os.Build.FINGERPRINT): Boolean =
        fingerprint.startsWith(FINGERPRINT_PREFIX)

    fun matches(fingerprint: String, name: String, width: Int, height: Int, displayId: Int): Boolean =
        fingerprint.startsWith(FINGERPRINT_PREFIX) && displayId != 0 &&
            name == DISPLAY_NAME && width == WIDTH && height == HEIGHT

    @Suppress("UNUSED_PARAMETER")
    fun streamConfig(content: CarPlayClusterDisplay.Content,
        horizontalStep: Int = 0, verticalStep: Int = 0,
        markerXPercent: Int? = null, markerYPercent: Int? = null): AirPlayDisplayConfig =
        CarPlayClusterDisplay.config(WIDTH, HEIGHT, scalePercent = 100, content = content).copy(
            viewArea = null,
            safeArea = MAP_VIEW_INSETS,
            safeAreaDrawOutside = true,
        )
}
