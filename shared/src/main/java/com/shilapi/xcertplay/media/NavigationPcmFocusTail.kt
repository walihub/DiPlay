package com.shilapi.xcertplay.media

/** Tracks only non-zero PCM16 frames; digital silence is retained in AudioTrack unchanged. */
internal class NavigationPcmFocusTail(private val frameBytes: Int) {
    private var signalEndFrame = 0L

    fun hasSignal(data: ByteArray, offset: Int, length: Int): Boolean =
        lastSignalFrame(data, offset, length) >= 0

    fun written(data: ByteArray, offset: Int, length: Int, startFrame: Long) {
        val last = lastSignalFrame(data, offset, length)
        if (last >= 0) signalEndFrame = startFrame + last + 1L
    }

    fun pending(playedFrames: Long): Boolean = playedFrames < signalEndFrame

    private fun lastSignalFrame(data: ByteArray, offset: Int, length: Int): Int {
        // AudioTrack PCM16 writes are sample/frame aligned. No loudness threshold: even the
        // quietest non-zero sample remains protected, including the other stereo channel.
        for (index in offset + length - 1 downTo offset) {
            if (data[index] != 0.toByte()) return (index - offset) / frameBytes
        }
        return -1
    }
}
