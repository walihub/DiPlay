package com.shilapi.xcertplay

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Looper
import local.arcfox.kuwo.KuwoExternalVehicle
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KuwoExternalVehicleTest {
    private val app = RuntimeEnvironment.getApplication()
    private lateinit var session: MediaSession
    private lateinit var controller: MediaController
    private val sent = mutableListOf<String>()
    private var originalSink: KuwoExternalVehicle.Sink? = null
    @Before fun prepare() {
        ShadowBuild.setFingerprint("AAOP_COCKPIT/test")
        KuwoExternalVehicle.stop()
        com.dudu.autoui.manage.music.s.selected = 6
        originalSink = ReflectionHelpers.getStaticField(KuwoExternalVehicle::class.java, "sink")
        ReflectionHelpers.setStaticField(KuwoExternalVehicle::class.java, "sink",
            KuwoExternalVehicle.Sink { metadata, _, _, _ -> sent += metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(); true })
        session = MediaSession(app, "original-kuwo")
        controller = MediaController(app, session.sessionToken)
        shadowOf(controller).setPackageName("cn.kuwo.kwmusiccar")
        shadowOf(controller).setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Song").build())
        shadowOf(controller).setPlaybackState(PlaybackState.Builder().setState(3, 0, 1f)
            .setActions(PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS).build())
    }
    @After fun cleanup() {
        KuwoExternalVehicle.stop()
        KuwoExternalVehicle.setMetadataProvider(null)
        ReflectionHelpers.setStaticField(KuwoExternalVehicle::class.java, "sink", originalSink)
        com.dudu.autoui.manage.music.s.selected = 2
        session.release()
    }
    @Test fun originalSessionPublishesAndWheelUsesOnlyStandardTransport() {
        KuwoExternalVehicle.update(app, listOf(controller))
        assertEquals(listOf("Song"), sent)
        app.sendBroadcast(Intent("adayo.keyEvent.onKeyDown.N6").putExtra("InputKey", 87))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, shadowOf(controller.transportControls).lastPerformedAction)
        assertTrue(shadowOf(app).broadcastIntents.none { it.action == "cn.kuwo.kwmusicauto.action.MEDIA_BUTTON" })
    }
    @Test fun savedKuwoSelectionControlsAlreadyPlayingSessionBehindOlderRival() {
        val rivalSession = MediaSession(app, "older-native")
        try {
            val rival = MediaController(app, rivalSession.sessionToken)
            shadowOf(rival).setPackageName("com.arcfox.media")
            shadowOf(rival).setPlaybackState(PlaybackState.Builder().setState(3, 0, 1f).build())
            KuwoExternalVehicle.update(app, listOf(rival, controller))
            assertEquals(listOf("Song"), sent)
            app.sendBroadcast(Intent("adayo.keyEvent.onKeyDown.N6").putExtra("InputKey", 16))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, shadowOf(controller.transportControls).lastPerformedAction)
        } finally { rivalSession.release() }
    }
    @Test fun selectedKuwoUsesOriginalSessionEvenWithNonstandardPlaybackUsage() {
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build()
        val info = ReflectionHelpers.callConstructor(MediaController.PlaybackInfo::class.java,
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, 1),
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, 2),
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, 15),
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, 5),
            ReflectionHelpers.ClassParameter.from(AudioAttributes::class.java, attributes),
            ReflectionHelpers.ClassParameter.from(String::class.java, null))
        shadowOf(controller).setPlaybackInfo(info)
        KuwoExternalVehicle.update(app, listOf(controller))
        app.sendBroadcast(Intent("adayo.keyEvent.onKeyDown.N6").putExtra("InputKey", 88))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(PlaybackState.ACTION_SKIP_TO_PREVIOUS, shadowOf(controller.transportControls).lastPerformedAction)
    }
    @Test fun carPlayPlayingBlocksSavedKuwoSelection() {
        val phoneSession = MediaSession(app, "phone")
        try {
            val phone = MediaController(app, phoneSession.sessionToken)
            shadowOf(phone).setPackageName("com.shihab.diplay")
            shadowOf(phone).setPlaybackState(PlaybackState.Builder().setState(3, 0, 1f).build())
            KuwoExternalVehicle.update(app, listOf(phone, controller))
            app.sendBroadcast(Intent("adayo.keyEvent.onKeyDown.N6").putExtra("InputKey", 16))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0L, shadowOf(controller.transportControls).lastPerformedAction)
        } finally { phoneSession.release() }
    }
    @Test fun nativeControllerSelectionBlocksStaleKuwoMetadataAndWheelEvenWithoutNativeSession() {
        com.dudu.autoui.manage.music.s.selected = 101
        KuwoExternalVehicle.update(app, listOf(controller))
        assertTrue(sent.isEmpty())
        app.sendBroadcast(Intent("adayo.keyEvent.onKeyDown.N6").putExtra("InputKey", 16))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0L, shadowOf(controller.transportControls).lastPerformedAction)
    }
    @Test fun removedSessionUnregistersKeysAndNeverPausesOriginalPlayer() {
        KuwoExternalVehicle.update(app, listOf(controller))
        KuwoExternalVehicle.update(app, emptyList())
        app.sendBroadcast(Intent("adayo.keyEvent.onKeyDown.N6").putExtra("InputKey", 88))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0L, shadowOf(controller.transportControls).lastPerformedAction)
        assertNull(ReflectionHelpers.getStaticField<Any?>(KuwoExternalVehicle::class.java, "current"))
    }
    @Test fun materializationIsAllowedForPausedNonOwnerButInstrumentRemainsSilent() {
        shadowOf(controller).setPlaybackState(PlaybackState.Builder().setState(2, 0, 0f).build())
        var requested = false
        KuwoExternalVehicle.setMetadataProvider { _, current -> requested = true; current.metadata }
        KuwoExternalVehicle.update(app, listOf(controller))
        assertTrue(requested)
        assertTrue(sent.isEmpty())
    }
}
