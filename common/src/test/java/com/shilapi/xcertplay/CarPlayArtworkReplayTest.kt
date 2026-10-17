package com.shilapi.xcertplay

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.os.Looper
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadows.ShadowMediaSession
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, shadows = [CarPlayArtworkReplayTest.MetadataRecordingShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class CarPlayArtworkReplayTest {
    @Implements(MediaSession::class)
    class MetadataRecordingShadow : ShadowMediaSession() {
        var published: MediaMetadata? = null
        @Implementation
        fun setMetadata(value: MediaMetadata?) { published = value }
    }

    private var controller: CarPlayController? = null

    @After fun close() {
        controller?.let(CarPlayMediaKeys::detach)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun setup(): Pair<Any, MediaSession> {
        val expected = mock(CarPlayController::class.java).also { controller = it }
        val app = RuntimeEnvironment.getApplication()
        val session = MediaSession(app, "ArtworkReplayTest")
        ReflectionHelpers.setField(CarPlayMediaKeys, "controller", expected)
        ReflectionHelpers.setField(CarPlayMediaKeys, "appContext", app)
        ReflectionHelpers.setField(CarPlayMediaKeys, "session", session)
        ReflectionHelpers.setField(CarPlayMediaKeys, "nowPlaying", CarPlayNowPlaying())
        val queue = ReflectionHelpers.getField<NowPlayingArtworkQueue<Any>>(CarPlayMediaKeys, "artworkQueue")
        return queue.newSession().also { ReflectionHelpers.setField(CarPlayMediaKeys, "artworkOwner", it) } to session
    }

    private fun decoded(owner: Any, id: Int, bitmap: Bitmap?) {
        val resultClass = Class.forName("com.shilapi.xcertplay.CarPlayMediaKeys\$AmbientArtwork")
        val result = bitmap?.let { resultClass.declaredConstructors.first().apply { isAccessible = true }
            .newInstance(it, 0xff123456.toInt(), byteArrayOf(1, 2, 3)) }
        CarPlayMediaKeys::class.java.getDeclaredMethod("onArtworkDecoded", Any::class.java,
            Int::class.javaPrimitiveType, resultClass).apply { isAccessible = true }
            .invoke(CarPlayMediaKeys, owner, id, result)
    }

    @Test fun decodedArtworkBeforeMetadataIsReplayedIntoTheMatchingMediaSession() {
        val (owner, session) = setup()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        decoded(owner, 129, bitmap)
        `when`(controller!!.nowPlayingSnapshot()).thenReturn(CarPlayNowPlaying(title = "Song", artworkTransferId = 129))
        CarPlayMediaKeys::class.java.getDeclaredMethod("onNowPlayingChanged", CarPlayController::class.java)
            .apply { isAccessible = true }.invoke(CarPlayMediaKeys, controller)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(bitmap, requireNotNull(Shadow.extract<MetadataRecordingShadow>(session).published).getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))
        val payloads = ReflectionHelpers.getField<NowPlayingArtworkPayloads>(CarPlayMediaKeys, "artworkPayloads")
        assertNotNull(payloads.matching(129))
        assertNull(payloads.matching(128))
    }

    @Test fun failedDecodeDoesNotCompleteCoverRecoveryOrRetainValidPayload() {
        val (owner, _) = setup()
        ReflectionHelpers.setField(CarPlayMediaKeys, "nowPlaying", CarPlayNowPlaying(artworkTransferId = 129))
        decoded(owner, 129, null)
        val ready = CarPlayMediaKeys::class.java.getDeclaredMethod("hasCurrentDecodedArtwork")
            .apply { isAccessible = true }.invoke(CarPlayMediaKeys)
        assertEquals(false, ready)
        val payloads = ReflectionHelpers.getField<NowPlayingArtworkPayloads>(CarPlayMediaKeys, "artworkPayloads")
        assertNull(payloads.matching(129))
    }
}
