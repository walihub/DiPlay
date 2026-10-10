package com.shilapi.xcertplay.vehicle

import android.media.AudioAttributes
import android.media.AudioManager
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxAudioPolicy
import com.shilapi.xcertplay.vehicle.arcfox.usesArcfoxNavigationFocus

internal interface VehiclePromptFocus {
    fun pcm(priorityVoice: Boolean)
    fun maintain(pending: Boolean, priorityVoice: Boolean)
    fun close()
}

/** OEM exceptions are applied only to the matching firmware and selected stream. */
internal object VehicleAudioPolicy {
    fun assistantLegacyStream(fingerprint: String): Int = if (VehicleIdentity.isArcfox(fingerprint)) 17 else 0
    fun needsNavigationFocus(fingerprint: String, channel: Int): Boolean =
        usesArcfoxNavigationFocus(fingerprint, true, channel)

    fun navigationFocus(fingerprint: String, navigation: Boolean, legacyStream: Int?,
        manager: AudioManager?, attributes: AudioAttributes, report: (String) -> Unit): VehiclePromptFocus? =
        if (manager != null && usesArcfoxNavigationFocus(fingerprint, navigation, legacyStream))
            ArcfoxAudioPolicy.navigationFocus(manager, attributes, report) else null
}
