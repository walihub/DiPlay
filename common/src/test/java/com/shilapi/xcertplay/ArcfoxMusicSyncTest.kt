package com.shilapi.xcertplay

import android.app.Application
import android.os.Build
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ArcfoxMusicSyncTest {
    private lateinit var app: Application
    private lateinit var fingerprint: String

    @Before fun setUp() {
        fingerprint = Build.FINGERPRINT
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", "AAOP_COCKPIT/test")
        app = RuntimeEnvironment.getApplication()
        ArcfoxMusicSync.clear(app)
        Shadows.shadowOf(app).clearBroadcastIntents()
    }

    @After fun tearDown() {
        ArcfoxMusicSync.clear(app)
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", fingerprint)
    }

    @Test fun relaysCarPlaySongToExportedArcfoxReceiverAndClearsIt() {
        val song = CarPlayNowPlaying("Song", "Album", "Artist", 7, durationMillis = 180_000,
            elapsedMillis = 15_000, playing = true)
        ArcfoxMusicSync.update(app, song)
        val start = Shadows.shadowOf(app).broadcastIntents.single()
        assertEquals("com.carlinx.arcfox.carlink.action.CARPLAY_MUSIC", start.action)
        assertEquals("com.carlinx.arcfox.carlink", start.component?.packageName)
        assertEquals("com.carlinx.arcfox.carlink.CarLifeMusicReceiver", start.component?.className)
        assertEquals("Song", start.getStringExtra("title"))
        assertEquals("Artist", start.getStringExtra("artist"))
        assertEquals("Album", start.getStringExtra("album"))
        assertEquals(180_000L, start.getLongExtra("duration", -1))
        assertEquals(15_000, start.getIntExtra("progress", -1))
        assertTrue(start.getBooleanExtra("playing", false))

        ArcfoxMusicSync.update(app, song.copy(elapsedMillis = 15_500))
        assertEquals(1, Shadows.shadowOf(app).broadcastIntents.size)
        ArcfoxMusicSync.clear(app)
        val stop = Shadows.shadowOf(app).broadcastIntents.last()
        assertEquals("", stop.getStringExtra("title"))
        assertFalse(stop.getBooleanExtra("playing", true))
    }

    @Test fun artworkRespectsTheReferenceBinderLimit() {
        val song = CarPlayNowPlaying(title = "Song", artworkTransferId = 7)
        ArcfoxMusicSync.update(app, song)
        ArcfoxMusicSync.artwork(app, 7, byteArrayOf(1, 2, 3), song)
        assertEquals(3, Shadows.shadowOf(app).broadcastIntents.last().getByteArrayExtra("cover")?.size)
        val count = Shadows.shadowOf(app).broadcastIntents.size
        ArcfoxMusicSync.artwork(app, 7, ByteArray(ArcfoxMusicSync.MAX_COVER_BYTES + 1), song)
        assertEquals(count, Shadows.shadowOf(app).broadcastIntents.size)
    }

    @Test fun otherCarsDoNotReceiveArcfoxMusic() {
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", "BYD/test")
        ArcfoxMusicSync.update(app, CarPlayNowPlaying(title = "Song", playing = true))
        assertTrue(Shadows.shadowOf(app).broadcastIntents.isEmpty())
    }
}
