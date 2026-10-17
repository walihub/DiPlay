package com.shilapi.xcertplay

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import java.io.IOException
import java.nio.file.Files
import local.arcfox.kuwo.Instrument
import local.arcfox.kuwo.KeyPolicy
import local.arcfox.kuwo.KuwoBridge
import local.arcfox.kuwo.KuwoCardBridge
import local.arcfox.kuwo.MediaPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.media.session.MediaSession
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class KuwoArcfoxAdapterTest {
    @Test fun ownershipChangesOnlyOnPlaybackEdgesAndNotOldPlayingSnapshots() {
        val p = MediaPolicy()
        p.baselineRivals(setOf("native"))
        assertTrue(p.own(true, true))
        assertTrue(p.owner)
        assertFalse(p.own(true, true))
        assertFalse(p.rivals(setOf("native")))
        assertTrue(p.rivals(setOf("native", "carplay")))
        assertFalse(p.owner)
        repeat(10) { assertFalse(p.own(true, true)); assertFalse(p.owner) }
        assertFalse(p.own(false, true))
        assertTrue(p.own(true, true))
        assertTrue(p.owner)
    }
    @Test fun initialOlderPlayerCannotClaimAndRemovingRivalDoesNotSteal() {
        val p = MediaPolicy()
        p.baselineRivals(setOf("newer"))
        assertFalse(p.own(true, false))
        assertFalse(p.rivals(emptySet<Any>()))
        assertFalse(p.owner)
        p.own(false, true)
        assertTrue(p.own(true, true))
    }
    @Test fun progressFreezesClampsAndDoesNotOverflow() {
        assertEquals(12500L, MediaPolicy.position(10000, 1000, 1.25f, true, 3000, 20000))
        assertEquals(10000L, MediaPolicy.position(10000, 1000, 1f, false, 3000, 20000))
        assertEquals(11000L, MediaPolicy.position(10000, 1000, 1f, true, 90000, 11000))
        assertEquals(Long.MAX_VALUE, MediaPolicy.position(Long.MAX_VALUE - 1, 1, 100f, true, 9999, 0))
        assertEquals(1, MediaPolicy.seconds(1500))
        assertEquals(2, MediaPolicy.seconds(1501))
        assertEquals(0, MediaPolicy.seconds(-1))
    }
    @Test fun onlyProvenRawWheelCodesAndCrossPathDuplicatesAreHandled() {
        assertEquals("MEDIA_NEXT", KeyPolicy.raw("adayo.keyEvent.onKeyDown.N6", 16))
        assertEquals("MEDIA_PRE", KeyPolicy.raw("adayo.keyEvent.onKeyDown.N6", 15))
        assertNull(KeyPolicy.raw("adayo.keyEvent.onKeyUp.N6", 16))
        assertNull(KeyPolicy.raw("adayo.keyEvent.onKeyDown.N6", 231))
        val keys = KeyPolicy()
        assertTrue(keys.accept("MEDIA_NEXT", "raw", 1000))
        assertFalse(keys.accept("MEDIA_NEXT", "standard", 1200))
        assertTrue(keys.accept("MEDIA_NEXT", "raw", 1250))
        assertTrue(keys.accept("MEDIA_PRE", "standard", 1300))
        assertTrue(keys.accept("MEDIA_NEXT", "standard", 1701))
    }
    @Test fun receiverGateLeavesOtherCarsAndUnrelatedActionsAlone() {
        ShadowBuild.setFingerprint("generic/test")
        val intent = Intent("cn.kuwo.kwmusicauto.action.MEDIA_BUTTON").putExtra("EXTRA", "MEDIA_NEXT")
        assertTrue(KuwoBridge.allow(intent))
        ShadowBuild.setFingerprint("AAOP_COCKPIT/test")
        assertTrue(KuwoBridge.allow(Intent("other")))
        assertTrue(KuwoBridge.allow(intent.putExtra("EXTRA", "MEDIA_PAUSE")))
    }
    private fun metadata(art: Bitmap? = null, title: String = "Song"): MediaMetadata =
        MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID, title)
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Artist")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 5500)
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art).build()

    @Test fun encodesExactNativeMediaFieldsAndBounds() {
        val root = JSONObject(Instrument.encode("Song", "Artist", 5500, Long.MAX_VALUE, true, false, "cover.png"))
        assertEquals("meidainfo", root.getJSONObject("command").getString("id"))
        assertEquals("id3info", root.getJSONObject("command").getString("sub"))
        val c = root.getJSONObject("content")
        assertEquals(19, c.getInt("mediatype"))
        assertEquals(5, c.getInt("totalplaytime"))
        assertEquals(5, c.getInt("curplaytime"))
        assertEquals(1, c.getInt("mute"))
        assertEquals("play", c.getString("status"))
        assertEquals("cover.png", c.getString("albumpic"))
    }
    @Test fun artworkFailureNeverBlocksTextAndTrackChangeClearsOldCover() {
        val sent = mutableListOf<JSONObject>()
        var denied = true
        val image = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val sender = Instrument({ sent += JSONObject(it); true }, {
            if (denied) throw IOException("denied")
            "kuwo_music_" + "a".repeat(64) + ".png"
        })
        assertTrue(sender.send(metadata(image), 1501, true, false))
        assertEquals("Song", sent.last().getJSONObject("content").getString("songname"))
        assertEquals("", sent.last().getJSONObject("content").getString("albumpic"))
        denied = false
        assertTrue(sender.send(metadata(image), 2000, true, false))
        assertTrue(sent.last().getJSONObject("content").getString("albumpic").startsWith("kuwo_music_"))
        assertTrue(sender.send(metadata(title = "Next"), 0, false, true))
        assertEquals("", sent.last().getJSONObject("content").getString("albumpic"))
        assertEquals("pause", sent.last().getJSONObject("content").getString("status"))
        image.recycle()
    }
    @Test fun failedNativeDispatchStaysUnavailableAndCanRetry() {
        var available = false
        var calls = 0
        val sender = Instrument({ calls++; available }, { error("no image expected") })
        assertFalse(sender.send(metadata(), 0, true, false))
        available = true
        assertTrue(sender.send(metadata(), 1000, true, false))
        assertEquals(2, calls)
        assertFalse(Instrument.Hidl().send("{}")) // Generic Android has no Arcfox HAL.
    }
    @Test fun coverStorePublishesOnlyOwnContentAddressedFilesAndRestoresMissingFile() {
        val dir = Files.createTempDirectory("kuwo-cover").toFile()
        try {
            val store = Instrument.CoverStore(dir)
            val unrelated = dir.resolve("diplay_music_keep.png").apply { writeText("keep"); setLastModified(1) }
            val old = dir.resolve("kuwo_music_" + "b".repeat(64) + ".png").apply { writeText("old"); setLastModified(1) }
            val image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val name = store.write(image)
            assertTrue(name.matches(Regex("kuwo_music_[a-f0-9]{64}\\.png")))
            assertTrue(dir.resolve(name).length() > 0)
            assertTrue(unrelated.exists())
            assertFalse(old.exists())
            dir.resolve(name).delete()
            assertEquals(name, store.write(image))
            assertTrue(dir.resolve(name).exists())
            image.recycle()
        } finally { dir.deleteRecursively() }
    }
    @Test fun duduTransportUsesOnlySupportedActionsOnTheSelectedKuwoSession() {
        val session = MediaSession(RuntimeEnvironment.getApplication(), "kuwo")
        try {
            val c = MediaController(RuntimeEnvironment.getApplication(), session.sessionToken)
            shadowOf(c).setPlaybackState(PlaybackState.Builder().setState(3, 0, 1f)
                .setActions(PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_PLAY).build())
            val transport = shadowOf(c.transportControls)
            KuwoCardBridge.dispatch(c, 6)
            assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, transport.lastPerformedAction)
            KuwoCardBridge.dispatch(c, 5)
            KuwoCardBridge.dispatch(c, 2)
            assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, transport.lastPerformedAction)
            KuwoCardBridge.dispatch(c, 1)
            assertEquals(PlaybackState.ACTION_PLAY, transport.lastPerformedAction)
        } finally { session.release() }
    }
    class SupportToken(private val token: MediaSession.Token) { fun e(): Any = token }
    class SupportSession(token: MediaSession.Token) { private val wrapped = SupportToken(token); fun c(): Any = wrapped }

    @Test fun actualKuwoBridgeAttachesToOriginalTokenRoutesRawKeysAndReleasesReceiver() {
        ShadowBuild.setFingerprint("AAOP_COCKPIT/test")
        val app = RuntimeEnvironment.getApplication()
        val session = MediaSession(app, "kuwo")
        val compat = SupportSession(session.sessionToken)
        val worker = ReflectionHelpers.getStaticField<android.os.Handler>(KuwoBridge::class.java, "worker")
        try {
            KuwoBridge.attach(app, compat)
            shadowOf(worker.looper).idle()
            val instance = ReflectionHelpers.getStaticField<Any>(KuwoBridge::class.java, "current")
            assertNotNull(instance)
            val controller = ReflectionHelpers.getField<MediaController>(instance, "controller")
            assertEquals(session.sessionToken, controller.sessionToken)
            shadowOf(controller).setPlaybackState(PlaybackState.Builder().setState(3, 0, 1f)
                .setActions(PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS).build())
            ReflectionHelpers.callInstanceMethod<Void>(instance, "refresh")
            app.sendBroadcast(Intent("adayo.keyEvent.onKeyDown.N6").putExtra("InputKey", 16))
            shadowOf(worker.looper).idle()
            val commands = shadowOf(app).broadcastIntents.filter { it.action == "cn.kuwo.kwmusicauto.action.MEDIA_BUTTON" }
            assertEquals(1, commands.size)
            assertEquals("MEDIA_NEXT", commands.single().getStringExtra("EXTRA"))
            assertEquals("cn.kuwo.kwmusiccar", commands.single().`package`)
            KuwoBridge.detach(compat)
            shadowOf(worker.looper).idle()
            assertNull(ReflectionHelpers.getStaticField<Any?>(KuwoBridge::class.java, "current"))
        } finally { KuwoBridge.detach(compat); shadowOf(worker.looper).idle(); session.release() }
    }
    class Card {
        var title = ""
        var artist = ""
        var art: Bitmap? = null
        var coverType = 1
        var lyricActive = false
        var lyricProgress = -1
        var lyricRows = 0
        var position = 0
        var playing = false
        fun a(t: String?, artist: String?, cover: String?) { title = t.orEmpty(); this.artist = artist.orEmpty(); lyricActive = false }
        fun b(cover: String?, unused: String?, unused2: String?) { art = null }
        fun a(key: String?, image: Bitmap?, update: Boolean) {
            // Original music.p.b(String, Bitmap, boolean) prioritizes a nonempty URL.
            coverType = if (!key.isNullOrEmpty()) 2 else if (image != null) 3 else 1
            art = if (coverType == 3) image else null
        }
        fun a(rows: List<*>, offset: Int) { lyricRows = rows.size; lyricActive = rows.isNotEmpty() }
        fun a(p: Int, d: Int) { position = p; if (lyricActive) lyricProgress = p }
        fun a(p: Boolean, update: Boolean) { playing = p }
    }
    @Test fun actualDuduPluginShowsOnlyKuwoAndStopsStaleUiAndCommands() {
        ShadowBuild.setFingerprint("AAOP_COCKPIT/test")
        val app = RuntimeEnvironment.getApplication()
        val session = MediaSession(app, "Kuwo")
        val otherSession = MediaSession(app, "Other")
        val manager = app.getSystemService(android.media.session.MediaSessionManager::class.java)
        val c = MediaController(app, session.sessionToken)
        val other = MediaController(app, otherSession.sessionToken)
        shadowOf(c).setPackageName("cn.kuwo.kwmusiccar")
        val image = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        shadowOf(c).setMetadata(metadata(image))
        shadowOf(c).setExtras(android.os.Bundle().apply {
            // Original player already loaded its unmarked lyrics before Dudu selected this plugin.
            putString("AUDIO_LYRIC", """{"resultCode":20000,"AUDIO_LYRIC":[{"startTime":0,"time":5500,"text":"Line"}]}""")
        })
        shadowOf(c).setPlaybackState(PlaybackState.Builder().setState(3, 15000, 1f)
            .setActions(PlaybackState.ACTION_SKIP_TO_NEXT).build())
        shadowOf(other).setPackageName("com.native.music")
        shadowOf(other).setPlaybackState(PlaybackState.Builder().setState(3, 0, 1f).build())
        shadowOf(manager).addController(other)
        shadowOf(manager).addController(c)
        val owner = Any()
        val card = Card()
        com.dudu.autoui.manage.music.p.w().c = owner
        assertTrue(KuwoCardBridge.start(app, owner, card))
        val instance = ReflectionHelpers.getStaticField<Any>(KuwoCardBridge::class.java, "current")
        val worker = ReflectionHelpers.getField<android.os.Handler>(instance, "worker")
        try {
            shadowOf(worker.looper).idle()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals("Song", card.title)
            assertEquals("Artist", card.artist)
            assertEquals(3, card.coverType)
            assertSame(image, card.art)
            assertTrue("Initial unmarked snapshot must activate lyrics after title", card.lyricActive)
            assertEquals(1, card.lyricRows)
            assertEquals(5500, card.lyricProgress)
            // Cover-only metadata re-emits title too; unchanged lyrics must reactivate the real UI.
            shadowOf(c).setMetadata(metadata(Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888)))
            KuwoCardBridge.refresh(owner)
            shadowOf(worker.looper).idle()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue("Artwork/title update must not leave lyrics disabled", card.lyricActive)
            assertEquals(5500, card.lyricProgress)
            // Unmodified Kuwo does not supply ARCFOX_LYRIC owner tags. A genuinely new live
            // extras callback remains usable, while startup/retained extras are not replayed.
            val originalExtras = android.os.Bundle().apply {
                putString("AUDIO_LYRIC", """{"resultCode":20000,"AUDIO_LYRIC":[{"startTime":0,"time":5500,"text":"Original unmodified player"}]}""")
            }
            shadowOf(c).setExtras(originalExtras)
            val callback = ReflectionHelpers.getField<MediaController.Callback>(instance, "mediaCallback")
            callback.onExtrasChanged(originalExtras)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(card.lyricActive)
            assertEquals(1, card.lyricRows)
            KuwoCardBridge.refresh(owner)
            shadowOf(worker.looper).idle()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(card.lyricActive)
            assertEquals(5500, card.position) // duration bound, not other player's position
            assertTrue(card.playing)
            KuwoCardBridge.command(owner, 6)
            shadowOf(worker.looper).idle()
            assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, shadowOf(c.transportControls).lastPerformedAction)
            assertEquals(0L, shadowOf(other.transportControls).lastPerformedAction)
            // A UI update queued for this plugin must not overwrite a newly selected player.
            shadowOf(c).setMetadata(metadata(title = "Must not appear"))
            KuwoCardBridge.refresh(owner)
            shadowOf(worker.looper).idle()
            com.dudu.autoui.manage.music.p.w().c = Any()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals("Song", card.title)
            KuwoCardBridge.command(owner, 6)
            KuwoCardBridge.stop(owner)
            assertTrue(KuwoCardBridge.command(owner, 6)) // Handled no-op: never fall back to the obsolete SDK.
        } finally {
            KuwoCardBridge.stop(owner)
            com.dudu.autoui.manage.music.p.w().c = null
            shadowOf(manager).clearControllers()
            session.release(); otherSession.release()
        }
    }
}
