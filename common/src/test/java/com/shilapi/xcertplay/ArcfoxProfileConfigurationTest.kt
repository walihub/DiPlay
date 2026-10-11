package com.shilapi.xcertplay

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import org.robolectric.util.ReflectionHelpers
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayInsets
import com.shilapi.xcertplay.airplay.SafeAreaRect
import com.shilapi.xcertplay.vehicle.VehicleProfile
import com.shilapi.xcertplay.vehicle.VehicleProfiles
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxClusterDisplay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class ArcfoxProfileConfigurationTest {
    @Test fun mirrorSelectionShowsTheEditorInsideTheActualAdvancedCardEvenWithMapOff() {
        val app = RuntimeEnvironment.getApplication()
        VehicleProfiles.save(app, VehicleProfile.ARCFOX_T)
        AirPlayPersistence.saveClusterMapEnabled(app, false)
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        ReflectionHelpers.setField(activity, "settingsSectionFilter", setOf(SettingsSection.CLUSTER_MAP))
        val root = LinearLayout(activity)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "allSettingsSections",
            ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, root))
        fun descendants(view: View): List<View> = buildList {
            add(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
        }
        val labels = descendants(root).filterIsInstance<TextView>().map { it.text.toString() }
        assertEquals(1, labels.count { it.startsWith(activity.getString(R.string.cluster_safe_area_edit)) })
        assertFalse(labels.any { it == activity.getString(R.string.adb_cluster_activity_mode) })
        assertFalse(labels.any { it == activity.getString(R.string.platform21_cluster_mode) })
        assertEquals(SettingsCategory.ADVANCED,
            SettingsInformationArchitecture.sectionsByCategory.entries.single { SettingsSection.CLUSTER_MAP in it.value }.key)
    }

    @Test fun manualArcfoxUsesTheFullCanvasWithAnIndependentSafeArea() {
        val app = RuntimeEnvironment.getApplication()
        VehicleProfiles.save(app, VehicleProfile.DILINK_4)
        val byd = SafeAreaRect(300, 100, 1500, 620)
        AirPlayPersistence.saveClusterSafeAreaRect(app, byd)
        AirPlayPersistence.saveAdbClusterEnabled(app, true)
        VehicleProfiles.save(app, VehicleProfile.ARCFOX_T)
        assertNull(AirPlayPersistence.loadClusterSafeAreaRect(app))
        assertFalse(AirPlayPersistence.loadAdbClusterEnabled(app))
        assertFalse(AirPlayPersistence.loadLegacyClusterEnabled(app))
        val rect = SafeAreaRect(450, 90, 1470, 680)
        AirPlayPersistence.saveClusterSafeAreaRect(app, rect)
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        val config = CarPlayHostActivity::class.java.getDeclaredMethod("clusterDisplayConfig")
            .apply { isAccessible = true }.invoke(host) as AirPlayDisplayConfig
        assertEquals(1920, config.widthPixels)
        assertEquals(720, config.heightPixels)
        assertEquals(AirPlayInsets(90, 40, 450, 450), config.safeArea)
        assertNull(config.viewArea)
        assertEquals(true, config.safeAreaDrawOutside)
        AirPlayPersistence.clearClusterSafeAreaRect(app)
        assertEquals(SafeAreaRect(500, 80, 1420, 690), ArcfoxClusterDisplay.defaultSafeAreaRect())
        VehicleProfiles.save(app, VehicleProfile.DILINK_4)
        assertEquals(byd, AirPlayPersistence.loadClusterSafeAreaRect(app))
        assertTrue(AirPlayPersistence.loadAdbClusterEnabled(app))
    }
}
