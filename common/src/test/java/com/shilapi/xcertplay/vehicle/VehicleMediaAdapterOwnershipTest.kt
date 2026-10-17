package com.shilapi.xcertplay.vehicle

import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxMediaCoordination
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class VehicleMediaAdapterOwnershipTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @Before fun arcfox() { VehicleProfiles.save(app, VehicleProfile.ARCFOX_T) }
    @After fun clear() {
        VehicleMediaAdapter.onSessionEnded(app)
        VehicleProfiles.save(app, VehicleProfile.UNKNOWN)
    }

    @Test fun nativeSelectionStopsOwnershipAndOnlyANewPhonePlayRestoresIt() {
        val controller = mock(CarPlayController::class.java)
        VehicleMediaAdapter.onSessionStarted(app, controller)
        assertFalse(VehicleMediaAdapter.ownsCarPlayMusic())
        VehicleMediaAdapter.onCarPlayMusicStarted()
        assertTrue(VehicleMediaAdapter.ownsCarPlayMusic())
        Shadows.shadowOf(app).clearBroadcastIntents()
        VehicleMediaAdapter.onVehicleMusicSelected(controller)
        assertFalse(VehicleMediaAdapter.ownsCarPlayMusic())
        val yielded = Shadows.shadowOf(app).broadcastIntents.single()
        assertEquals(ArcfoxMediaCoordination.YIELDED, yielded.action)
        assertEquals("com.dudu.autoui", yielded.`package`)
        assertEquals("com.shihab.diplay", yielded.getStringExtra("origin"))
        VehicleMediaAdapter.onVehicleMusicSelected(controller)
        assertFalse(VehicleMediaAdapter.ownsCarPlayMusic())
        assertEquals(1, Shadows.shadowOf(app).broadcastIntents.size)
        VehicleMediaAdapter.onCarPlayMusicStarted()
        assertTrue(VehicleMediaAdapter.ownsCarPlayMusic())
    }

    @Test fun oldControllerCannotYieldReplacementSession() {
        val old = mock(CarPlayController::class.java)
        val current = mock(CarPlayController::class.java)
        VehicleMediaAdapter.onSessionStarted(app, old)
        VehicleMediaAdapter.onSessionEnded(app)
        VehicleMediaAdapter.onSessionStarted(app, current)
        VehicleMediaAdapter.onCarPlayMusicStarted()
        VehicleMediaAdapter.onVehicleMusicSelected(old)
        assertTrue(VehicleMediaAdapter.ownsCarPlayMusic())
        VehicleMediaAdapter.onSessionEnded(app)
        assertFalse(VehicleMediaAdapter.ownsCarPlayMusic())
    }
}
