package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxNavigationFocus
import org.junit.Assert.*
import org.junit.Test

class NavigationPcmFocusTailTest {
    @Test fun silentPaddingDoesNotHoldDuckingAfterLastSpokenFrame() {
        var now = 0L
        var requests = 0
        var releases = 0
        val focus = ArcfoxNavigationFocus({ requests++; true }, { releases++ }, { now })
        val tail = NavigationPcmFocusTail(2)
        val speech = byteArrayOf(1, 0, 0, 0)
        if (tail.hasSignal(speech, 0, speech.size)) focus.pcm(false)
        tail.written(speech, 0, speech.size, 0)
        val silence = ByteArray(480)
        for (tick in 1..100) {
            now = tick * 10L
            // A live iPhone stream can keep delivering these packets after the prompt.
            if (tail.hasSignal(silence, 0, silence.size)) focus.pcm(false)
            tail.written(silence, 0, silence.size, 2L + tick * 240L)
            focus.maintain(tail.pending(1), false)
        }
        assertEquals(1, requests)
        assertEquals(1, releases)
        assertFalse(tail.pending(1))
    }

    @Test fun queuedQuietestStereoSampleKeepsFocusUntilItsActualFramePlays() {
        val tail = NavigationPcmFocusTail(4)
        // A single least-significant bit on the right stereo channel is still sound.
        val samples = byteArrayOf(0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0)
        assertTrue(tail.hasSignal(samples, 0, samples.size))
        tail.written(samples, 0, samples.size, 100)
        assertTrue(tail.pending(101))
        assertFalse(tail.pending(102))
    }

    @Test fun onlySuccessfullyWrittenSliceExtendsTail() {
        val tail = NavigationPcmFocusTail(2)
        val data = byteArrayOf(1, 0, 0, 0, 2, 0)
        tail.written(data, 0, 2, 0)
        assertFalse(tail.pending(1))
        // Failed/unwritten data is not counted. The later accepted slice is counted.
        tail.written(data, 4, 2, 1)
        assertTrue(tail.pending(1))
        assertFalse(tail.pending(2))
        assertFalse(tail.hasSignal(data, 2, 2))
    }

    @Test fun continuouslyQueuedSilentPacketsReleaseAndNextSpeechRequestsBeforeWriting() {
        var now = 0L
        var releases = 0
        var requests = 0
        var held = false
        val focus = ArcfoxNavigationFocus({ requests++; held = true; true },
            { releases++; held = false }, { now })
        val tail = NavigationPcmFocusTail(2)
        val speech = byteArrayOf(1, 0)
        val silence = ByteArray(480)
        val packets = java.util.ArrayDeque<ByteArray>().apply { add(silence); add(silence) }
        focus.pcm(false)
        tail.written(speech, 0, 2, 0)
        for (tick in 1..100) {
            now = tick * 10L
            // Arrival while a blocking write runs keeps at least one packet queued all the time.
            packets.add(silence)
            val decodedPcm = packets.removeFirst()
            if (tail.hasSignal(decodedPcm, 0, decodedPcm.size)) focus.pcm(false)
            tail.written(decodedPcm, 0, decodedPcm.size, 1L + tick * 240L)
            assertFalse(packets.isEmpty())
            // Queued network packets do not own an AudioTrack frame yet.
            focus.maintain(tail.pending(1), false)
        }
        assertEquals(1, releases)
        assertEquals(1, requests)
        assertFalse(held)
        // Every codec's decoded output passes this same pre-write request point.
        if (tail.hasSignal(speech, 0, 2)) focus.pcm(false)
        assertTrue(held)
        assertEquals(2, requests)
        tail.written(speech, 0, 2, 1000)
        now += 250
        focus.maintain(tail.pending(1000), false)
        assertEquals(1, releases)
        focus.maintain(tail.pending(1001), false)
        assertEquals(2, releases)
    }

    @Test fun playbackHeadWrapUsesExistingExtendedFramePosition() {
        val progress = AudioBufferProgress(2)
        repeat(8) { progress.written(1_073_741_824) }
        progress.written(40)
        val totalFrames = 0x1_0000_0000L + 20
        val tail = NavigationPcmFocusTail(2)
        tail.written(byteArrayOf(1, 0), 0, 2, 0x1_0000_0000L + 4)
        assertTrue(tail.pending(totalFrames - progress.queuedBytes(-1) / 2))
        assertTrue(tail.pending(totalFrames - progress.queuedBytes(0) / 2))
        assertFalse(tail.pending(totalFrames - progress.queuedBytes(5) / 2))
    }

    @Test fun digitalSilenceNeverCreatesPendingNavigation() {
        val tail = NavigationPcmFocusTail(2)
        val silence = ByteArray(512)
        assertFalse(tail.hasSignal(silence, 0, silence.size))
        assertFalse(tail.hasSignal(silence, 0, 0))
        tail.written(silence, 0, silence.size, 0)
        assertFalse(tail.pending(0))
    }
}
