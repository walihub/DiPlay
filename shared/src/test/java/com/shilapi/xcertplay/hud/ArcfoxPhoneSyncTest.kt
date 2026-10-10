package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxPhoneSync

import android.app.Application
import android.os.Build
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ArcfoxPhoneSyncTest {
    private lateinit var application: Application
    private lateinit var originalFingerprint: String

    @Before fun setUp() {
        originalFingerprint = Build.FINGERPRINT
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", "AAOP_COCKPIT/test")
        application = RuntimeEnvironment.getApplication()
        ArcfoxPhoneSync.attach(application)
        ArcfoxPhoneSync.end()
        Shadows.shadowOf(application).clearBroadcastIntents()
    }

    @After fun tearDown() {
        ArcfoxPhoneSync.end()
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", originalFingerprint)
    }

    @Test fun mapsTheReferenceCallPhases() {
        val ringing = CarPlayCallCard(CarPlayCallCard.Phase.RINGING, "Caller")
        val dialing = CarPlayCallCard(CarPlayCallCard.Phase.DIALING, "Caller")
        val active = CarPlayCallCard(CarPlayCallCard.Phase.ACTIVE, "Caller")
        assertEquals(1, ArcfoxPhoneSync.direction(ringing))
        assertEquals(2, ArcfoxPhoneSync.status(ringing))
        assertEquals(1, ArcfoxPhoneSync.status(dialing))
        assertEquals(4, ArcfoxPhoneSync.status(active))
        assertEquals(0, ArcfoxPhoneSync.status(null))
    }

    @Test fun broadcastsCallAndClearsItOnEnd() {
        ArcfoxPhoneSync.onCard(CarPlayCallCard(CarPlayCallCard.Phase.RINGING, "Test Caller"))
        val start = Shadows.shadowOf(application).broadcastIntents.single()
        assertEquals("mfiservice.communication.call.state.update", start.action)
        assertEquals("com.adayo.aaop_meterinfoservice", start.`package`)
        val call = JSONObject(start.getStringExtra("CallState")!!)
        assertEquals("Test Caller", call.getString("DisplayName"))
        assertEquals(1, call.getInt("Direction"))
        assertEquals(2, call.getInt("Status"))

        ArcfoxPhoneSync.end()
        val clear = Shadows.shadowOf(application).broadcastIntents.last()
        assertEquals(0, JSONObject(clear.getStringExtra("CallState")!!).getInt("Status"))
    }

    @Test fun ignoresNonArcfoxBuilds() {
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", "BYD/test")
        ArcfoxPhoneSync.onCard(CarPlayCallCard(CarPlayCallCard.Phase.RINGING, "Test Caller"))
        assertTrue(Shadows.shadowOf(application).broadcastIntents.isEmpty())
    }
}
