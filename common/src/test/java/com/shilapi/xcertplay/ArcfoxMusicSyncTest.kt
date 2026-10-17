package com.shilapi.xcertplay

import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxMusicDispatch
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxMusicPacket
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxMusicPublisher
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxMusicSync
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ArcfoxMusicSyncTest {
    private var time = 0L
    private var reachable = true
    private val packets = mutableListOf<ArcfoxMusicPacket>()
    private lateinit var publisher: ArcfoxMusicPublisher
    private val song = CarPlayNowPlaying("Song", "Album", "Artist", 7,
        durationMillis = 180_000, elapsedMillis = 15_000, playing = true)

    @Before fun setUp() {
        publisher = ArcfoxMusicPublisher({ time }) {
            if (reachable) { packets.add(it); ArcfoxMusicDispatch.DISPATCHED }
            else ArcfoxMusicDispatch.UNAVAILABLE
        }
    }

    @Test fun regularProgressCorrectionsAreThrottled() {
        publisher.update(song)
        time = 100
        publisher.update(song.copy(elapsedMillis = 15_200))
        assertEquals(1, packets.size)
        time = 1_000
        publisher.tick()
        assertEquals(16_100L, packets.last().progressMs)
        assertFalse(packets.last().includeCover)
    }

    @Test fun forwardAndBackwardSeeksPublishImmediatelyWithoutWaitingForProgressTick() {
        publisher.update(song)
        time = 100
        publisher.update(song.copy(elapsedMillis = 65_000))
        assertEquals(2, packets.size)
        assertEquals(65_000L, packets.last().progressMs)
        assertFalse(packets.last().includeCover)
        time = 200
        publisher.update(song.copy(elapsedMillis = 5_000))
        assertEquals(3, packets.size)
        assertEquals(5_000L, packets.last().progressMs)
    }

    @Test fun smallPausedSeekAlsoPublishesImmediately() {
        val paused = song.copy(playing = false)
        publisher.update(paused)
        time = 100
        publisher.update(paused.copy(elapsedMillis = 15_500))
        assertEquals(2, packets.size)
        assertEquals(15_500L, packets.last().progressMs)
    }

    @Test fun usesMonotonicElapsedTimeAndFreezesOnPauseWithoutRewindingRetainedPosition() {
        publisher.update(song)
        time = 5_000
        publisher.update(song.copy(playing = false))
        assertEquals(20_000L, packets.last().progressMs)
        time = 15_000
        publisher.tick()
        assertEquals(20_000L, packets.last().progressMs)
        publisher.update(song)
        time = 16_000
        publisher.tick()
        assertEquals(21_000L, packets.last().progressMs)
    }

    @Test fun currentSongArtworkIsSeparatedAndLatePreviousArtworkIsRejected() {
        publisher.update(song)
        publisher.artwork(7, byteArrayOf(1, 2, 3), song)
        assertArrayEquals(byteArrayOf(1, 2, 3), packets.last().cover)
        time = 1_000
        publisher.tick()
        assertFalse(packets.last().includeCover)
        val next = song.copy(title = "Next", artworkTransferId = 8, elapsedMillis = 0)
        publisher.update(next)
        assertTrue(packets.last().includeCover)
        assertNull(packets.last().cover)
        val count = packets.size
        publisher.artwork(7, byteArrayOf(4), song)
        assertEquals(count, packets.size)
    }

    @Test fun artworkWithReusedIdIsClearedOnAlbumChangeAndOversizedArtRejected() {
        publisher.update(song)
        publisher.artwork(7, byteArrayOf(1), song)
        val next = song.copy(title = "Next", album = "Next album", elapsedMillis = 0)
        publisher.update(next)
        assertNull(packets.last().cover)
        val count = packets.size
        publisher.artwork(7, ByteArray(ArcfoxMusicSync.MAX_COVER_BYTES + 1), next)
        assertEquals(count, packets.size)
    }

    @Test fun lyricTitleUpdatesKeepCurrentDecodedCover() {
        publisher.update(song)
        publisher.artwork(7, byteArrayOf(1, 2, 3), song)
        time = 1_000
        publisher.update(song.copy(title = "lyric line", elapsedMillis = 16_000))
        assertArrayEquals(byteArrayOf(1, 2, 3), packets.last().cover)
        assertFalse(packets.last().includeCover)
    }

    @Test fun unavailableReceiverRecoveryAndLowFrequencyCoverSnapshotRestoreState() {
        reachable = false
        publisher.update(song)
        publisher.artwork(7, byteArrayOf(9), song)
        assertTrue(packets.isEmpty())
        reachable = true
        time = 1_000
        publisher.tick()
        assertEquals("Song", packets.single().song.title)
        assertArrayEquals(byteArrayOf(9), packets.single().cover)
        time = 11_000
        publisher.tick()
        assertFalse(packets.last().includeCover)
        time = 61_000
        publisher.tick()
        assertTrue(packets.last().includeCover)
        assertArrayEquals(byteArrayOf(9), packets.last().cover)
    }

    @Test fun ownershipLossDoesNotOverwriteNativeMetadataAndReacquisitionRestoresCover() {
        publisher.update(song)
        publisher.artwork(7, byteArrayOf(9), song)
        val count = packets.size
        publisher.setSourceActive(false)
        time = 10_000
        publisher.tick()
        publisher.update(song.copy(elapsedMillis = 30_000))
        assertEquals(count, packets.size)
        assertFalse(publisher.shouldTick)
        publisher.setSourceActive(true)
        assertEquals(30_000L, packets.last().progressMs)
        assertArrayEquals(byteArrayOf(9), packets.last().cover)
        publisher.setSourceActive(false)
        val after = packets.size
        publisher.clear()
        assertEquals(after, packets.size)
        assertFalse(publisher.shouldTick)
    }

    @Test fun clearStopsTicksAndActiveSourceSendsEmptySnapshot() {
        publisher.update(song)
        publisher.clear()
        assertEquals("", packets.last().song.title)
        assertFalse(packets.last().song.playing)
        val count = packets.size
        time = 60_000
        publisher.tick()
        assertEquals(count, packets.size)
        assertFalse(publisher.shouldTick)
    }

    @Test fun pauseKeepsTenSecondStateSnapshotButDoesNotGenerateOneSecondTraffic() {
        publisher.update(song.copy(playing = false))
        time = 1_000
        publisher.tick()
        assertEquals(1, packets.size)
        time = 10_000
        publisher.tick()
        assertEquals(2, packets.size)
        assertEquals(15_000L, packets.last().progressMs)
    }

}
