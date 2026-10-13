package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxHardKeys
import com.shilapi.xcertplay.airplay.CarPlayMediaButton

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33])
class ArcfoxHardKeysTest {
    @Test fun receiverReleasesTheKeyWhenUnregisteredAndDoesNotAbortOtherReceivers() {
        val old = android.os.Build.FINGERPRINT
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build::class.java, "FINGERPRINT", "AAOP_COCKPIT/test")
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        var ours = 0
        var native = 0
        val other = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) { native++ }
        }
        app.registerReceiver(other, android.content.IntentFilter("adayo.keyEvent.onKeyLongPress.N6"))
        val receiver = ArcfoxHardKeys.register(app) { ours++; true }!!
        try {
            val event = android.content.Intent("adayo.keyEvent.onKeyLongPress.N6").putExtra("InputKey", 231)
            app.sendBroadcast(event)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            org.junit.Assert.assertEquals(1, ours)
            org.junit.Assert.assertEquals(1, native)
            app.unregisterReceiver(receiver)
            app.sendBroadcast(event)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            org.junit.Assert.assertEquals(1, ours)
            org.junit.Assert.assertEquals(2, native)
        } finally {
            runCatching { app.unregisterReceiver(receiver) }
            app.unregisterReceiver(other)
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build::class.java, "FINGERPRINT", old)
        }
    }

    @Test fun onlyTheReferenceVoiceLongPressOpensSiri() {
        assertTrue(ArcfoxHardKeys.isVoiceLongPress("adayo.keyEvent.onKeyLongPress.N6", 231))
        assertTrue(ArcfoxHardKeys.isVoiceLongPress("adayo.keyEvent.onKeyLongPress.N6", 219))
        assertFalse(ArcfoxHardKeys.isVoiceLongPress("adayo.keyEvent.onKeyDown.N6", 231))
        assertFalse(ArcfoxHardKeys.isVoiceLongPress("adayo.keyEvent.onKeyLongPress.N6", 87))
    }

    @Test fun referenceN6MediaCodesOnlyFireOnKeyDown() {
        org.junit.Assert.assertEquals(CarPlayMediaButton.NEXT, ArcfoxHardKeys.mediaPress("adayo.keyEvent.onKeyDown.N6", 16))
        org.junit.Assert.assertEquals(CarPlayMediaButton.PREVIOUS, ArcfoxHardKeys.mediaPress("adayo.keyEvent.onKeyDown.N6", 15))
        org.junit.Assert.assertNull(ArcfoxHardKeys.mediaPress("adayo.keyEvent.onKeyUp.N6", 16))
        org.junit.Assert.assertNull(ArcfoxHardKeys.mediaPress("adayo.keyEvent.onKeyDown.N6", 87))
    }
}
