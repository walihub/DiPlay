package com.shilapi.xcertplay.vehicle.arcfox

import com.shilapi.xcertplay.hud.CarPlayCallCard.Phase
import org.junit.Assert.*
import org.junit.Test

class NativePhoneOwnershipTest {
    private val policy = NativePhoneOwnership()
    private fun state(call: Int = 0, audio: Int = 140, phone: String = "phone", id: String = "one") =
        NativePhoneAudio(listOf(NativePhoneCall(phone, call, id)), audio)
    @Test fun unknownOrDifferentPhoneCannotSilenceCarPlay() {
        assertEquals(PhoneYield(), policy.update(null, Phase.ACTIVE, state()))
        assertEquals(PhoneYield(), policy.update("other", Phase.ACTIVE, state()))
        assertEquals(PhoneYield(), policy.update("phone", Phase.ACTIVE, null))
        assertEquals(PhoneYield(), policy.update("phone", Phase.ACTIVE, state(audio = 120)))
    }
    @Test fun nativeRingingOnlyYieldsRing() {
        assertEquals(PhoneYield(false, true), policy.update("phone", Phase.RINGING, state(call = 4, audio = 100)))
    }
    @Test fun confirmedBluetoothCallYieldsAndManualHandsetTransferDoesNotRestartCapture() {
        assertEquals(PhoneYield(true, true), policy.update("phone", Phase.ACTIVE, state()))
        assertEquals(PhoneYield(true, true), policy.update("phone", Phase.ACTIVE, state(audio = 100)))
        assertEquals(PhoneYield(true, true), policy.update("phone", Phase.ACTIVE, null))
        assertEquals(PhoneYield(), policy.update("phone", null, null))
    }
    @Test fun newCallWithoutAnObservedIdleDoesNotInheritOwnership() {
        policy.update("phone", Phase.ACTIVE, state())
        assertEquals(PhoneYield(false, true), policy.update("phone", Phase.RINGING, state(call = 4, audio = 100, id = "two")))
    }
    @Test fun emptyNativeCallListReleasesOwnership() {
        policy.update("phone", Phase.ACTIVE, state())
        assertEquals(PhoneYield(), policy.update("phone", Phase.ACTIVE, NativePhoneAudio(emptyList(), 100)))
    }
}
