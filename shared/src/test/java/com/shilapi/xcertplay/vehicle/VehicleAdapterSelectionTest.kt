package com.shilapi.xcertplay.vehicle

import android.os.Build
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VehicleAdapterSelectionTest {
    @Test fun nonArcfoxSessionNeverReadsArcfoxLivenessOrRegistersVoiceKeys() {
        val old = Build.FINGERPRINT
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", "BYD/DiLink4/test")
        try {
            val app = RuntimeEnvironment.getApplication()
            val session = VehicleAdapters.session(app, Any())
            val token = Any()
            session.onSessionStarted({ error("OEM liveness must not run") }, { error("OEM generation must not run") })
            session.onControlOpened(token, 1)
            session.onNavigation(Iap2Frame(0x5201, byteArrayOf()), true)
            session.onControlEnded(token)
            session.onSessionEnded()
            assertNull(VehicleAdapters.registerVoiceKey(app) { error("OEM voice key must not run") })
        } finally {
            ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", old)
        }
    }

    @Test fun oemAudioExceptionsOnlyApplyToMatchingFirmwareAndNavigationChannel() {
        assertEquals(17, VehicleAudioPolicy.assistantLegacyStream("AAOP_COCKPIT/HS7013A/test"))
        assertEquals(0, VehicleAudioPolicy.assistantLegacyStream("BYD/DiLink4/test"))
        for (channel in listOf(0, 3, 17)) {
            assertFalse(VehicleAudioPolicy.needsNavigationFocus("AAOP_COCKPIT/test", channel))
        }
        assertTrue(VehicleAudioPolicy.needsNavigationFocus("AAOP_COCKPIT/test", 11))
        assertFalse(VehicleAudioPolicy.needsNavigationFocus("BYD/DiLink4/test", 11))
    }
}
