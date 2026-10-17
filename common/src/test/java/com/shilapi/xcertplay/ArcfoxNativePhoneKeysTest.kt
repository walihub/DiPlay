package com.shilapi.xcertplay

import android.os.Build
import android.view.KeyEvent
import com.shilapi.xcertplay.hud.BydOutputSettings
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class ArcfoxNativePhoneKeysTest {
    @Test fun legacyBydPreferenceCannotCaptureNativePhoneKeysOnArcfox() {
        val app = RuntimeEnvironment.getApplication()
        val fingerprint = Build.FINGERPRINT
        val previous = BydOutputSettings.carPlayCallControls(app)
        val controller = mock(CarPlayController::class.java)
        try {
            ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", "AAOP_COCKPIT/test")
            BydOutputSettings.setCarPlayCallControls(app, true)
            for (key in listOf(KeyEvent.KEYCODE_CALL, KeyEvent.KEYCODE_ENDCALL)) {
                for (down in listOf(true, false)) {
                    assertFalse(CarPlayCallKeys.onKey(app, key, down, controller))
                }
            }
            verifyNoInteractions(controller)
        } finally {
            BydOutputSettings.setCarPlayCallControls(app, previous)
            ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", fingerprint)
        }
    }
}
