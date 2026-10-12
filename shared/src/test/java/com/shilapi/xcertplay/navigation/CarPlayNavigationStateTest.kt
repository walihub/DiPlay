package com.shilapi.xcertplay.navigation

import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import org.junit.Assert.*
import org.junit.Test

class CarPlayNavigationStateTest {
    private fun frame(state: Int) = Iap2Frame(0x5201, byteArrayOf(0, 5, 0, 1, state.toByte()))
    @Test fun routeStartsWithoutTurnDetailsAndEndsOnCancelArrivalOrDisconnect() {
        val state = CarPlayNavigationState()
        assertFalse(state.snapshot())
        assertEquals(true, state.accept(frame(1)))
        assertNull(state.accept(frame(1)))
        assertNull(state.accept(Iap2Frame(0x5201, byteArrayOf(0, 4, 0, 13))))
        assertTrue("An empty turn list must not hide navigation", state.snapshot())
        assertEquals(false, state.accept(frame(2)))
        assertEquals(true, state.accept(frame(1)))
        assertEquals(false, state.accept(frame(0)))
        state.accept(frame(1))
        assertEquals(false, state.clear())
        assertFalse(state.snapshot())
    }
    @Test fun aCurrentManeuverListRestoresNavigationWithoutRequiringCachedTurnDetails() {
        val state = CarPlayNavigationState()
        assertEquals(true, state.accept(Iap2Frame(0x5201, byteArrayOf(0, 6, 0, 13, 0, 4))))
        assertTrue(state.snapshot())
        assertEquals(false, state.accept(Iap2Frame(0x5201,
            frame(0).payload + byteArrayOf(0, 6, 0, 13, 0, 4))))
    }
    @Test fun malformedAndOtherFramesCannotAlterActivity() {
        val state = CarPlayNavigationState()
        assertNull(state.accept(Iap2Frame(0x5202, frame(1).payload)))
        assertFalse(state.snapshot())
        state.accept(frame(1))
        assertNull(state.accept(Iap2Frame(0x5201, frame(0).payload + byteArrayOf(0))))
        assertTrue(state.snapshot())
    }
}
