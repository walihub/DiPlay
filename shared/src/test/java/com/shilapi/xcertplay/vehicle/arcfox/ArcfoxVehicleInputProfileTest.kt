package com.shilapi.xcertplay.vehicle.arcfox

import android.content.Context
import com.shilapi.xcertplay.hud.BydOutputSettings
import com.shilapi.xcertplay.vehicle.VehicleAdapters
import com.shilapi.xcertplay.vehicle.VehicleProfile
import com.shilapi.xcertplay.vehicle.VehicleProfiles
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class ArcfoxVehicleInputProfileTest {
    @Test fun selectedArcfoxEnablesItsOwnReaderWithoutEnablingBydAdbSettings() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("diplay_byd_outputs", Context.MODE_PRIVATE).edit().clear().commit()
        VehicleProfiles.save(app, VehicleProfile.ARCFOX_T)
        assertTrue(VehicleAdapters.motionEnabled(app))
        assertTrue(VehicleAdapters.wheelSpeed(app) is ArcfoxVehicleSpeed)
        assertFalse(BydOutputSettings.wheelSpeedToIphone(app)) // No automatic BYD ADB probe.
        BydOutputSettings.setWheelSpeedToIphone(app, false)
        assertFalse(VehicleAdapters.motionEnabled(app)) // Respect an explicit disabled preference.
    }
    @Test fun switchingBackToBydRetainsUpstreamDefault() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("diplay_byd_outputs", Context.MODE_PRIVATE).edit().clear().commit()
        VehicleProfiles.save(app, VehicleProfile.DILINK_4)
        assertFalse(VehicleAdapters.motionEnabled(app))
        assertFalse(VehicleAdapters.wheelSpeed(app) is ArcfoxVehicleSpeed)
    }
}
