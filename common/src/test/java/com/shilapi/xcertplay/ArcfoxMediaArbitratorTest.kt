package com.shilapi.xcertplay

import android.media.AudioAttributes
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxMediaArbitrator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.mockito.ArgumentCaptor
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class ArcfoxMediaArbitratorTest {
    @Test fun latestMusicStartPausesTheOtherSourceButMetadataAndClosedCallbacksCannotReclaim() {
        val app = RuntimeEnvironment.getApplication()
        val session = MediaSession(app, "native")
        val native = MediaController(app, session.sessionToken)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
        val info = ReflectionHelpers.callConstructor(MediaController.PlaybackInfo::class.java,
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, 1),
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, 2),
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, 15),
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, 5),
            ReflectionHelpers.ClassParameter.from(AudioAttributes::class.java, attributes))
        val controls = native.transportControls
        fun state(playing: Boolean) = PlaybackState.Builder().setState(
            if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, 0, 1f)
            .setActions(PlaybackState.ACTION_PAUSE).build()
        shadowOf(native).setPackageName("com.arcfox.media")
        shadowOf(native).setPlaybackInfo(info)
        shadowOf(native).setPlaybackState(state(true))
        val manager = app.getSystemService(MediaSessionManager::class.java)
        shadowOf(manager).addController(native)
        var phonePauses = 0
        val owner = ArcfoxMediaArbitrator(app) { phonePauses++ }
        val callback = ArgumentCaptor.forClass(MediaController.Callback::class.java)
        try {
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, phonePauses) // Existing native playback is a baseline, not a new play action.
            val registered = shadowOf(native).callbacks.single()
            owner.carPlayStarted()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(PlaybackState.ACTION_PAUSE, shadowOf(controls).lastPerformedAction)
            registered.onPlaybackStateChanged(state(false))
            registered.onPlaybackStateChanged(state(true))
            assertEquals(1, phonePauses)
            registered.onPlaybackStateChanged(state(true))
            assertEquals(1, phonePauses)
            owner.close()
            registered.onPlaybackStateChanged(state(false))
            registered.onPlaybackStateChanged(state(true))
            assertEquals(1, phonePauses)
        } finally { owner.close(); session.release(); shadowOf(Looper.getMainLooper()).idle() }
    }
}
