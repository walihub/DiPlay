package com.shilapi.xcertplay

import android.hardware.display.DisplayManager
import android.view.Display
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.vehicle.VehicleProfile
import com.shilapi.xcertplay.vehicle.VehicleProfiles
import com.shilapi.xcertplay.vehicle.VehicleClusterAdapter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class ClusterNavigationOnlyTest {
    @Test fun arcfoxDefaultsToNavigationOnlyButAnExplicitChoiceWins() {
        val app = RuntimeEnvironment.getApplication()
        VehicleProfiles.save(app, VehicleProfile.DILINK_4)
        assertFalse(AirPlayPersistence.loadClusterNavigationOnly(app))
        VehicleProfiles.save(app, VehicleProfile.ARCFOX_T)
        assertTrue(AirPlayPersistence.loadClusterNavigationOnly(app))
        AirPlayPersistence.saveClusterNavigationOnly(app, false)
        assertFalse(AirPlayPersistence.loadClusterNavigationOnly(app))
    }

    @Test fun physicalMapIsRetainedButHiddenBetweenRoutesAndRestoredByDisablingThePolicy() {
        val app = RuntimeEnvironment.getApplication()
        ShadowBuild.setFingerprint("AAOP_COCKPIT/test")
        VehicleProfiles.save(app, VehicleProfile.ARCFOX_T)
        AirPlayPersistence.saveClusterMapEnabled(app, true)
        val id = ShadowDisplayManager.addDisplay("w1920dp-h720dp-mdpi", 5)
        shadowOf(app.getSystemService(DisplayManager::class.java).getDisplay(id)).apply {
            setName("HDMI 屏幕"); setFlags(Display.FLAG_PRESENTATION)
        }
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        host.setTheme(android.R.style.Theme_Material_NoActionBar)
        ReflectionHelpers.setField(host, "vehicleCluster\$delegate", lazyOf(object : VehicleClusterAdapter {}))
        val controller = mock(CarPlayController::class.java)
        ReflectionHelpers.setField(host, "controller", controller)
        ReflectionHelpers.callInstanceMethod<Unit>(host, "bindClusterNavigation",
            ReflectionHelpers.ClassParameter.from(CarPlayController::class.java, controller))
        val visibilityChanged: (Boolean) -> Unit = ReflectionHelpers.getField(host, "navigationVisibilityListener")
        fun refresh() = ReflectionHelpers.callInstanceMethod<Unit>(host, "ensureClusterPresentation")
        fun presentation(): ClusterMapPresentation? = ReflectionHelpers.getField(host, "clusterPresentation")
        try {
            refresh()
            assertNull(presentation())
            `when`(controller.navigationActiveSnapshot()).thenReturn(true)
            visibilityChanged.invoke(true)
            val shown = presentation()!!
            assertTrue(shown.isShowing)
            `when`(controller.navigationActiveSnapshot()).thenReturn(false)
            visibilityChanged.invoke(false)
            assertSame(shown, presentation())
            assertTrue(shown.isShowing)
            assertFalse(shown.mapVisible)
            `when`(controller.navigationActiveSnapshot()).thenReturn(true)
            visibilityChanged.invoke(true)
            assertSame(shown, presentation())
            assertTrue(shown.mapVisible)
            `when`(controller.navigationActiveSnapshot()).thenReturn(false)
            visibilityChanged.invoke(false)
            assertFalse(shown.mapVisible)
            AirPlayPersistence.saveClusterNavigationOnly(app, false)
            visibilityChanged.invoke(false)
            assertSame(shown, presentation())
            assertTrue(shown.mapVisible)
            AirPlayPersistence.saveClusterMapEnabled(app, false)
            refresh()
            assertNull(presentation())
        } finally {
            AirPlayPersistence.saveClusterMapEnabled(app, false)
            refresh()
            ShadowDisplayManager.removeDisplay(id)
        }
    }
}
