package com.shilapi.xcertplay

import android.media.AudioManager
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class ArcfoxMediaRouteRecoveryTest {
    @Test fun onlyGrantedArcfoxDefaultOrMediaRouteCanUnmuteWithoutChangingVolume() {
        for (channel in listOf(0, 3)) {
            val audio = mock(AudioManager::class.java)
            `when`(audio.isStreamMute(3)).thenReturn(true)
            assertTrue(ArcfoxMediaRouteRecovery.recover("AAOP_COCKPIT/test", channel, true, audio))
            verify(audio).adjustStreamVolume(3, AudioManager.ADJUST_UNMUTE, 0)
            verify(audio, never()).setStreamVolume(anyInt(), anyInt(), anyInt())
        }
    }

    @Test fun otherCarsRoutesAndDeniedFocusNeverTouchAudioManager() {
        val audio = mock(AudioManager::class.java)
        assertFalse(ArcfoxMediaRouteRecovery.recover("BYD/test", 3, true, audio))
        assertFalse(ArcfoxMediaRouteRecovery.recover("AAOP_COCKPIT/test", 11, true, audio))
        assertFalse(ArcfoxMediaRouteRecovery.recover("AAOP_COCKPIT/test", 3, false, audio))
        verifyNoInteractions(audio)
    }

    @Test fun alreadyAudibleMediaIsUntouched() {
        val audio = mock(AudioManager::class.java)
        assertFalse(ArcfoxMediaRouteRecovery.recover("AAOP_COCKPIT/test", 3, true, audio))
        verify(audio, never()).adjustStreamVolume(anyInt(), anyInt(), anyInt())
    }

    @Test fun romPublicFalseDoesNotHideServiceMuteAndVolumeIsNeverChanged() {
        val audio = mock(AudioManager::class.java)
        `when`(audio.isStreamMute(3)).thenReturn(false)
        val service = mock(ArcfoxMediaRouteRecovery.MediaMuteControl::class.java)
        `when`(service.isMuted()).thenReturn(true, false)
        assertTrue(ArcfoxMediaRouteRecovery.recover("AAOP_COCKPIT/test", 0, true, audio,
            serviceFactory = { service }))
        verify(service).unmute()
        verify(audio, never()).adjustStreamVolume(anyInt(), anyInt(), anyInt())
        verify(audio, never()).setStreamVolume(anyInt(), anyInt(), anyInt())
    }

    @Test fun serviceAlreadyUnmutedIsNotChangedEvenWhenPublicApiDisagrees() {
        val audio = mock(AudioManager::class.java)
        `when`(audio.isStreamMute(3)).thenReturn(true)
        val service = mock(ArcfoxMediaRouteRecovery.MediaMuteControl::class.java)
        `when`(service.isMuted()).thenReturn(false)
        assertFalse(ArcfoxMediaRouteRecovery.recover("AAOP_COCKPIT/test", 3, true, audio,
            serviceFactory = { service }))
        verify(service, never()).unmute()
        verify(audio, never()).adjustStreamVolume(anyInt(), anyInt(), anyInt())
    }

    @Test fun serviceAccessIsGatedByVehicleChannelAndGrantedFocus() {
        val audio = mock(AudioManager::class.java)
        val factory: () -> ArcfoxMediaRouteRecovery.MediaMuteControl? = { error("Must not access service") }
        assertFalse(ArcfoxMediaRouteRecovery.recover("BYD/test", 3, true, audio, serviceFactory = factory))
        assertFalse(ArcfoxMediaRouteRecovery.recover("AAOP_COCKPIT/test", 11, true, audio, serviceFactory = factory))
        assertFalse(ArcfoxMediaRouteRecovery.recover("AAOP_COCKPIT/test", 3, false, audio, serviceFactory = factory))
        verifyNoInteractions(audio)
    }
}
