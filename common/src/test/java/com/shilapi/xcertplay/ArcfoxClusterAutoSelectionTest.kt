package com.shilapi.xcertplay

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ArcfoxClusterAutoSelectionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var originalFingerprint: String
    private var displayId = -1

    @Before fun setUp() {
        originalFingerprint = Build.FINGERPRINT
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", "AAOP_COCKPIT/test")
        context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        displayId = ShadowDisplayManager.addDisplay("w1920dp-h720dp-mdpi", 5)
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(displayId)
        shadowOf(display).apply {
            setName("HDMI 屏幕")
            setFlags(Display.FLAG_PRESENTATION)
        }
    }

    @After fun tearDown() {
        context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        ShadowDisplayManager.removeDisplay(displayId)
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", originalFingerprint)
    }

    @Test fun observedArcfoxScreenIsSelectedAndEnabledByDefault() {
        assertEquals(displayId, ClusterMapPresentation.findDisplay(context)?.displayId)
        assertTrue(AirPlayPersistence.loadClusterMapEnabled(context))
    }

    @Test fun mapTextureCoversThePhysicalCanvasWithoutExposingNativeMap() {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(displayId)
        val presentation = ClusterMapPresentation(context, display, onSurface = {})
        try {
            presentation.show()
            fun descendants(view: android.view.View): List<android.view.View> =
                listOf(view) + if (view is android.view.ViewGroup)
                    (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            val texture = descendants(presentation.window!!.decorView)
                .filterIsInstance<android.view.TextureView>().single()
            assertEquals(null, texture.clipBounds)
            assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, texture.layoutParams.width)
            assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, texture.layoutParams.height)
            assertEquals(60, AirPlayPersistence.loadClusterMarkerYPercent(context))
            AirPlayPersistence.saveClusterMarkerYPercent(context, 70)
            assertEquals(70, AirPlayPersistence.loadClusterMarkerYPercent(context))
        } finally {
            presentation.dismiss()
        }
    }

    @Test fun userCanDisableAutomaticMapProjection() {
        AirPlayPersistence.saveClusterMapEnabled(context, false)
        assertFalse(AirPlayPersistence.loadClusterMapEnabled(context))
    }

    @Test fun incompatibleScreenDoesNotEnableProjection() {
        shadowOf(context.getSystemService(DisplayManager::class.java).getDisplay(displayId)).setName("Passenger display")
        assertFalse(AirPlayPersistence.loadClusterMapEnabled(context))
    }
}
