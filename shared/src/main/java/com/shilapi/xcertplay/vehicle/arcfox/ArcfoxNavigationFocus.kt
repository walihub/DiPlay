package com.shilapi.xcertplay.vehicle.arcfox

import com.shilapi.xcertplay.vehicle.VehiclePromptFocus

/** One prompt owns focus only while PCM is arriving or still queued for playback. */
internal class ArcfoxNavigationFocus(
    private val request: () -> Boolean,
    private val abandon: () -> Unit,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) : VehiclePromptFocus {
    private var held = false
    private var interrupted = false
    private var closed = false
    private var lastPcm = Long.MIN_VALUE
    private var lastAttempt = Long.MIN_VALUE

    @Synchronized
    override fun pcm(priorityVoice: Boolean) {
        if (closed) return
        lastPcm = nowMillis()
        if (priorityVoice) {
            interrupted = true
            release()
        } else if (!held && !interrupted &&
            (lastAttempt == Long.MIN_VALUE || lastPcm - lastAttempt >= 500)) {
            lastAttempt = lastPcm
            held = request()
        }
    }

    @Synchronized
    override fun maintain(pending: Boolean, priorityVoice: Boolean) {
        if (closed) return
        if (priorityVoice) {
            interrupted = true
            release()
        }
        if (!pending && lastPcm != Long.MIN_VALUE && nowMillis() - lastPcm >= 700) {
            release()
            interrupted = false
            lastAttempt = Long.MIN_VALUE
        }
    }

    @Synchronized
    fun lost(isCurrent: () -> Boolean = { true }) {
        if (closed || !isCurrent()) return
        interrupted = true
        release()
    }

    @Synchronized
    override fun close() {
        closed = true
        release()
    }

    private fun release() {
        if (held) {
            held = false
            abandon()
        }
    }
}

internal fun usesArcfoxNavigationFocus(fingerprint: String, navigation: Boolean, legacyStream: Int?): Boolean =
    fingerprint.startsWith("AAOP_COCKPIT/") && navigation && legacyStream == 11
