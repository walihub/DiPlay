package com.shilapi.xcertplay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.shilapi.xcertplay.hud.ClusterTurnGuidance
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArcfoxTurnCardRenderingTest {
    @Test fun navigationAndArrivalInformationStayInsideTheReferenceCrossView() {
        val context = RuntimeEnvironment.getApplication()
        val fingerprint = android.os.Build.FINGERPRINT
        val oldMode = AirPlayPersistence.loadClusterSmallWindowMode(context)
        ShadowBuild.setFingerprint("AAOP_COCKPIT/test")
        try {
            AirPlayPersistence.saveClusterSmallWindowMode(context, 1)
            val view = ClusterTurnCardView(context)
            view.setLayout(18, 55, 40)
            view.setGuidance(ClusterTurnGuidance(2, 0, 800, "机场高速",
                arrivalEpochSeconds = 1800000000, remainingSeconds = 2400, remainingMeters = 28000))
            view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 1920, 720)
            val bitmap = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val pixels = IntArray(1920 * 720)
            bitmap.getPixels(pixels, 0, 1920, 0, 0, 1920, 720)
            val rect = ArcfoxClusterDisplay.SMALL_CARD_RECT
            for (y in 0 until 720) for (x in 0 until 1920) {
                if ((x < rect.left || x >= rect.left + rect.width || y < rect.top || y >= rect.top + rect.height) &&
                    pixels[y * 1920 + x].ushr(24) != 0) fail("Card pixel outside CrossView at ($x,$y)")
            }
            assertTrue(bitmap.getPixel(344, 397).ushr(24) > 0)
            val output = java.io.File("build/reports/arcfox-crossview-card.png")
            output.parentFile.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            ShadowBuild.setFingerprint(fingerprint)
            AirPlayPersistence.saveClusterSmallWindowMode(context, oldMode)
        }
    }
}
