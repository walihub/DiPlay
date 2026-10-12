package com.shilapi.xcertplay.vehicle

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxMediaRouteRecovery
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxMusicSync

/** Extra instrument metadata and ROM media recovery, separate from the Android media session. */
internal object VehicleMediaAdapter {
    @Volatile private var profileEnabled = false
    private var musicOwner: com.shilapi.xcertplay.vehicle.arcfox.ArcfoxMediaArbitrator? = null
    fun onSessionStarted(context: Context, controller: com.shilapi.xcertplay.orchestration.CarPlayController) {
        profileEnabled = VehicleProfiles.isArcfox(context)
        if (profileEnabled && VehicleIdentity.isArcfox())
            musicOwner = com.shilapi.xcertplay.vehicle.arcfox.ArcfoxMediaArbitrator(context.applicationContext) {
                com.shilapi.xcertplay.CarPlayMediaKeys.pauseForVehicleMusic(controller)
            }
    }
    fun onCarPlayMusicStarted() { musicOwner?.carPlayStarted() }
    fun onNowPlaying(context: Context, update: CarPlayNowPlaying) {
        if (profileEnabled) ArcfoxMusicSync.update(context, update)
    }
    fun onArtwork(context: Context, id: Int, bytes: ByteArray, update: CarPlayNowPlaying) {
        if (profileEnabled) ArcfoxMusicSync.artwork(context, id, bytes, update)
    }
    fun onSessionEnded(context: Context?) {
        musicOwner?.close()
        musicOwner = null
        ArcfoxMusicSync.clear(context)
        profileEnabled = false
    }

    fun onPlaybackStarted(context: Context?, channel: Int, focusGranted: Boolean) {
        if (context == null || !profileEnabled || !VehicleIdentity.isArcfox()) return
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            ArcfoxMediaRouteRecovery.recover(Build.FINGERPRINT, channel, focusGranted, audio,
                packageName = context.packageName, report = { Log.i("DiPlay-ArcfoxMusic", it) })
        }.onFailure { Log.w("DiPlay-ArcfoxMusic", "media route resume failed", it) }
    }
}
