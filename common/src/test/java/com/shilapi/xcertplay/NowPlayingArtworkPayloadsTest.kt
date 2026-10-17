package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class NowPlayingArtworkPayloadsTest {
    @Test fun decodedArtworkCanWaitForItsExactMetadataReference() {
        val cache = NowPlayingArtworkPayloads()
        val png = byteArrayOf(1, 2, 3)
        cache.decoded(129, png)
        assertNull(cache.matching(null))
        assertNull(cache.matching(128))
        assertSame(png, cache.matching(129))
    }

    @Test fun failedDecodeIsNotAValidPayloadAndDoesNotLeavePreviousBytes() {
        val cache = NowPlayingArtworkPayloads()
        cache.decoded(129, byteArrayOf(1))
        cache.decoded(129, null)
        assertNull(cache.matching(129))
        cache.decoded(129, byteArrayOf())
        assertNull(cache.matching(129))
    }

    @Test fun cachesAreBoundedAndSessionClearDropsOldArtwork() {
        val cache = NowPlayingArtworkPayloads(2)
        repeat(3) { cache.decoded(it, byteArrayOf(it.toByte())) }
        assertNull(cache.matching(0))
        assertNotNull(cache.matching(2))
        cache.remove(1)
        assertNull(cache.matching(1))
        cache.clear()
        assertNull(cache.matching(2))
    }
}
