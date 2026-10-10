package com.shilapi.xcertplay

import android.media.AudioManager

/** Reclaim the media route once when CarPlay starts playing; never change the saved volume. */
internal object ArcfoxMediaRouteRecovery {
    internal interface MediaMuteControl {
        fun isMuted(): Boolean
        fun unmute()
    }

    fun recover(fingerprint: String, channel: Int, focusGranted: Boolean, audio: AudioManager,
        packageName: String = "com.shihab.diplay",
        serviceFactory: () -> MediaMuteControl? = { platformMediaControl(packageName) },
        report: (String) -> Unit = {}): Boolean {
        if (!fingerprint.startsWith("AAOP_COCKPIT/") || channel !in listOf(0, 3) || !focusGranted) return false
        // This ROM's AudioManager reads the MCU's master mute, whereas AudioService keeps
        // a separate software stream mute. A false public result does not mean PCM is audible.
        val service = serviceFactory()
        if (service != null) {
            val muted = service.isMuted()
            report("Arcfox media mute service=$muted public=${audio.isStreamMute(AudioManager.STREAM_MUSIC)}")
            if (!muted) return false
            service.unmute()
            val remaining = service.isMuted()
            report("Arcfox media unmute serviceRemaining=$remaining stream=3")
            return !remaining
        }
        report("Arcfox media service unavailable; public API fallback")
        if (!audio.isStreamMute(AudioManager.STREAM_MUSIC)) return false
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        return true
    }

    private fun platformMediaControl(packageName: String): MediaMuteControl? = runCatching {
        val getter = AudioManager::class.java.getDeclaredMethod("getService").apply { isAccessible = true }
        val service = getter.invoke(null) ?: return@runCatching null
        val muted = service.javaClass.getMethod("isStreamMute", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        // The captured Android 11 ROM exposes the four-argument IAudioService method.
        val adjust = service.javaClass.getMethod("adjustStreamVolume", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
            .apply { isAccessible = true }
        object : MediaMuteControl {
            override fun isMuted(): Boolean = muted.invoke(service, AudioManager.STREAM_MUSIC) as Boolean
            override fun unmute() {
                adjust.invoke(service, AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0, packageName)
            }
        }
    }.getOrNull()
}
