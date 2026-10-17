package com.shilapi.xcertplay

import android.content.Intent
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
        app.sendBroadcast(Intent("adayo.keyEvent.onKeyDown.N6").putExtra("InputKey", 16))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, shadowOf(controller.transportControls).lastPerformedAction)
        assertTrue(shadowOf(app).broadcastIntents.none { it.action == "cn.kuwo.kwmusicauto.action.MEDIA_BUTTON" })
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
        app.sendBroadcast(Intent("adayo.keyEvent.onKeyDown.N6").putExtra("InputKey", 15))
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
