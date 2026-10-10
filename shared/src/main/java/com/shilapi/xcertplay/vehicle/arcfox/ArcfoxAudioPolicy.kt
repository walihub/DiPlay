package com.shilapi.xcertplay.vehicle.arcfox

import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.shilapi.xcertplay.compat.AudioFocusRequestCompat
import com.shilapi.xcertplay.vehicle.VehiclePromptFocus
import java.util.concurrent.atomic.AtomicLong

/** Arcfox ROM navigation focus, using the actual AudioTrack attributes. */
internal object ArcfoxAudioPolicy {
    fun navigationFocus(manager: AudioManager, attributes: AudioAttributes,
        report: (String) -> Unit): VehiclePromptFocus {
        val generationCounter = AtomicLong()
        var request: AudioFocusRequestCompat? = null
        lateinit var focus: ArcfoxNavigationFocus
        focus = ArcfoxNavigationFocus(
            request = {
                try {
                    val generation = generationCounter.incrementAndGet()
                    val listener = AudioManager.OnAudioFocusChangeListener { change ->
                        if (generation != generationCounter.get()) return@OnAudioFocusChangeListener
                        runCatching { report("Audio: Arcfox navigation focus change=$change") }
                        if (change == AudioManager.AUDIOFOCUS_LOSS ||
                            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            focus.lost { generation == generationCounter.get() }
                        }
                    }
                    val next = AudioFocusRequestCompat(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                        attributes, listener, Handler(Looper.getMainLooper()))
                    val result = next.request(manager)
                    runCatching { report("Audio: Arcfox navigation focus requested stream=11 usage=${next.attributes.usage} result=$result") }
                    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        request = next
                        true
                    } else {
                        generationCounter.incrementAndGet()
                        next.abandon(manager)
                        false
                    }
                } catch (error: Exception) {
                    runCatching { report("Audio: Arcfox navigation focus unavailable ${error.javaClass.simpleName}: ${error.message}") }
                    false
                }
            },
            abandon = {
                generationCounter.incrementAndGet()
                val previous = request
                request = null
                runCatching { previous?.abandon(manager) }
                runCatching { report("Audio: Arcfox navigation focus released") }
            },
        )
        return focus
    }
}
