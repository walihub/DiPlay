package com.shilapi.xcertplay.vehicle.arcfox

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ArcfoxLinkMusicTransportTest {
    private fun packet(cover: ByteArray? = null, include: Boolean = false) = ArcfoxMusicPacket(
        ArcfoxMusicSync.Song("Song", "Artist", "Album", 5500L, true, 1),
        9000L, cover, include,
    )

    @Test fun explicitReceiverAndTypedOriginalContract() {
        val bytes = byteArrayOf(1, 2, 3)
        val intent = ArcfoxLinkMusicTransport.intent(packet(bytes, true))
        assertEquals("com.carlinx.arcfox.carlink.action.CARPLAY_MUSIC", intent.action)
        assertEquals("com.carlinx.arcfox.carlink.CarLifeMusicReceiver", intent.component!!.className)
        assertEquals("Song", intent.getStringExtra("title"))
        assertEquals(5500L, intent.getLongExtra("duration", -1))
        assertEquals(5500, intent.getIntExtra("progress", -1))
        assertTrue(intent.getBooleanExtra("playing", false))
        assertArrayEquals(bytes, intent.getByteArrayExtra("cover"))
        assertFalse(intent.hasExtra("phone"))
    }

    @Test fun progressIsSuppressedAndEveryEmittedFrameRetainsArtwork() {
        val frame = packet(byteArrayOf(1))
        assertFalse(ArcfoxLinkMusicTransport.shouldSend(frame.song, frame))
        assertTrue(ArcfoxLinkMusicTransport.shouldSend(null, frame))
        assertTrue(ArcfoxLinkMusicTransport.shouldSend(frame.song, frame.copy(includeCover = true)))
        assertTrue(ArcfoxLinkMusicTransport.shouldSend(frame.song, frame.copy(song = frame.song.copy(title = "Next lyric"))))
        assertTrue(ArcfoxLinkMusicTransport.intent(frame).hasExtra("cover"))
        assertFalse(ArcfoxLinkMusicTransport.intent(packet(ByteArray(512 * 1024 + 1), true)).hasExtra("cover"))
        assertFalse(ArcfoxLinkMusicTransport.intent(packet(null, true)).hasExtra("cover"))
    }
}
