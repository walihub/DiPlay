package com.shilapi.xcertplay.vehicle

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class VehicleProfilesTest {
    @Test fun autoDetectionManualOverrideAndResetShareOneProfile() {
        val app = RuntimeEnvironment.getApplication()
        val fingerprint = "AAOP_COCKPIT/HS7013A/test"
        assertEquals(VehicleProfile.ARCFOX_T, VehicleProfiles.current(app, fingerprint))
        assertNull(VehicleProfiles.confirmed(app))
        VehicleProfiles.save(app, VehicleProfile.DILINK_4)
        assertFalse(VehicleProfiles.isArcfox(app, fingerprint))
        VehicleProfiles.save(app, VehicleProfile.UNKNOWN)
        assertTrue(VehicleProfiles.isArcfox(app, fingerprint))
        assertEquals(VehicleProfile.UNKNOWN, VehicleProfiles.current(app, "android/emulator"))
        VehicleProfiles.save(app, VehicleProfile.ARCFOX_T)
        assertTrue(VehicleProfiles.isArcfox(app, "android/emulator"))
    }

    @Test fun previousBydSelectionIsPreservedAndNewSelectionWins() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit()
            .putString("setup_dilink_generation", "dilink5").commit()
        assertEquals(VehicleProfile.DILINK_5, VehicleProfiles.current(app, "AAOP_COCKPIT/test"))
        VehicleProfiles.save(app, VehicleProfile.ARCFOX_T)
        assertEquals(VehicleProfile.ARCFOX_T, VehicleProfiles.current(app, "BYD/DiLink4"))
    }
}
