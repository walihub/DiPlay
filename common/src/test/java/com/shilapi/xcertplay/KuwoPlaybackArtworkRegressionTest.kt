package com.shilapi.xcertplay

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import local.arcfox.kuwo.Instrument
import local.arcfox.kuwo.KuwoBridge
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowBuild
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class KuwoPlaybackArtworkRegressionTest {
    class Token(private val token: MediaSession.Token) { fun e(): Any = token }
    class Compat(token: MediaSession.Token) { private val token = Token(token); fun c(): Any = token }
    class OriginalService(base: Context) : ContextWrapper(base) {
        var artworkRefreshes = 0
        @Suppress("UNUSED_PARAMETER") fun F(download: Boolean) { artworkRefreshes++ }
    }
    @Test fun pausedNonOwnerStillRequestsArtworkAndObservationNeverPausesKuwo() {
        ShadowBuild.setFingerprint("AAOP_COCKPIT/test")
        val app = RuntimeEnvironment.getApplication()
        val service = OriginalService(app)
        val session = MediaSession(app, "kuwo")
        val rival = MediaSession(app, "other")
        val compat = Compat(session.sessionToken)
        val worker = ReflectionHelpers.getStaticField<Handler>(KuwoBridge::class.java, "worker")
        val manager = app.getSystemService(android.media.session.MediaSessionManager::class.java)
        try {
            KuwoBridge.attach(service, compat)
            shadowOf(worker.looper).idle()
            val instance = ReflectionHelpers.getStaticField<Any>(KuwoBridge::class.java, "current")
            val controller = ReflectionHelpers.getField<MediaController>(instance, "controller")
            shadowOf(controller).setPackageName("cn.kuwo.kwmusiccar")
            shadowOf(controller).setPlaybackState(PlaybackState.Builder().setState(2, 0, 0f).build())
            shadowOf(controller).setMetadata(MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Song")
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, "https://example.test/art.png").build())
            ReflectionHelpers.callInstanceMethod<Void>(instance, "refresh")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, service.artworkRefreshes)
            val policy = ReflectionHelpers.getField<local.arcfox.kuwo.MediaPolicy>(instance, "policy")
            assertFalse(policy.owner)
            shadowOf(controller).setPlaybackState(PlaybackState.Builder().setState(3, 0, 1f).build())
            ReflectionHelpers.callInstanceMethod<Void>(instance, "refresh")
            val other = MediaController(app, rival.sessionToken)
            shadowOf(other).setPackageName("com.other.music")
            shadowOf(other).setPlaybackState(PlaybackState.Builder().setState(3, 0, 1f).build())
            shadowOf(manager).addController(other)
            ReflectionHelpers.callInstanceMethod<Void>(instance, "refresh")
            assertEquals(0L, shadowOf(controller.transportControls).lastPerformedAction)
        } finally {
            KuwoBridge.detach(compat)
            shadowOf(worker.looper).idle()
            shadowOf(Looper.getMainLooper()).idle()
            shadowOf(manager).clearControllers()
            session.release(); rival.release()
        }
    }
    @Test fun queuedArtworkRequestFromDisposedInstanceDoesNotCallOldService() {
        ShadowBuild.setFingerprint("AAOP_COCKPIT/test")
        val app = RuntimeEnvironment.getApplication()
        val service = OriginalService(app)
        val session = MediaSession(app, "old-kuwo")
        val compat = Compat(session.sessionToken)
        val worker = ReflectionHelpers.getStaticField<Handler>(KuwoBridge::class.java, "worker")
        try {
            KuwoBridge.attach(service, compat)
            shadowOf(worker.looper).idle()
            val instance = ReflectionHelpers.getStaticField<Any>(KuwoBridge::class.java, "current")
            val controller = ReflectionHelpers.getField<MediaController>(instance, "controller")
            shadowOf(controller).setMetadata(MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Old song")
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, "https://example.test/old.png").build())
            ReflectionHelpers.callInstanceMethod<Void>(instance, "refresh")
            // Dispose on the serial worker before the queued UI-thread F(true) is allowed to run.
            KuwoBridge.detach(compat)
            shadowOf(worker.looper).idle()
            assertNull(ReflectionHelpers.getStaticField<Any?>(KuwoBridge::class.java, "current"))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, service.artworkRefreshes)
        } finally {
            KuwoBridge.detach(compat)
            shadowOf(worker.looper).idle()
            shadowOf(Looper.getMainLooper()).idle()
            session.release()
        }
    }
    @Test fun sameTrackUriOnlySnapshotRetainsLateBitmapCoverButNextTrackClearsIt() {
        val sent = mutableListOf<JSONObject>()
        val image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val sender = Instrument({ sent += JSONObject(it); true }, { "kuwo_music_" + "a".repeat(64) + ".png" })
        val base = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "1")
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Song")
            .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, "https://example.test/art.png").build()
        try {
            assertTrue(sender.send(MediaMetadata.Builder(base)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, image).build(), 0, true, false))
            val cover = sent.last().getJSONObject("content").getString("albumpic")
            assertTrue(cover.startsWith("kuwo_music_"))
            assertTrue(sender.send(base, 1000, true, false))
            assertEquals(cover, sent.last().getJSONObject("content").getString("albumpic"))
            assertTrue(sender.send(MediaMetadata.Builder(base)
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "2").build(), 0, true, false))
            assertEquals("", sent.last().getJSONObject("content").getString("albumpic"))
        } finally { image.recycle() }
    }
}
