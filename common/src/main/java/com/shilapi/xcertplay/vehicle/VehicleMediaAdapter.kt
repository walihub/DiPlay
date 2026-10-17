package com.shilapi.xcertplay.vehicle

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxMediaRouteRecovery
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxMusicSync
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxMediaCoordination

/** Extra instrument metadata and ROM media recovery, separate from the Android media session. */
internal object VehicleMediaAdapter {
    @Volatile private var profileEnabled = false
    private var musicOwner: com.shilapi.xcertplay.vehicle.arcfox.ArcfoxMediaArbitrator? = null
    private var sessionContext: Context? = null
    private var sessionController: com.shilapi.xcertplay.orchestration.CarPlayController? = null
    private var carPlayOwnsMusic = false
    private var sourceReceiver: android.content.BroadcastReceiver? = null
    fun onSessionStarted(context: Context, controller: com.shilapi.xcertplay.orchestration.CarPlayController) {
        profileEnabled = VehicleProfiles.isArcfox(context)
        sessionContext = context.applicationContext
        sessionController = controller
        carPlayOwnsMusic = false
        if (profileEnabled) ArcfoxMusicSync.setSourceActive(context, false)
        if (profileEnabled && VehicleIdentity.isArcfox())
            sourceReceiver = ArcfoxMediaCoordination.register(context.applicationContext) {
                if (sessionController === controller && carPlayOwnsMusic)
                    com.shilapi.xcertplay.CarPlayMediaKeys.pauseForVehicleMusic(controller)
            }
        if (profileEnabled && VehicleIdentity.isArcfox())
            musicOwner = com.shilapi.xcertplay.vehicle.arcfox.ArcfoxMediaArbitrator(context.applicationContext) {
                com.shilapi.xcertplay.CarPlayMediaKeys.pauseForVehicleMusic(controller)
            }
    }
    fun onCarPlayMusicStarted() {
        if (!profileEnabled || carPlayOwnsMusic) return
        carPlayOwnsMusic = true
        sessionContext?.let { ArcfoxMusicSync.setSourceActive(it, true) }
        musicOwner?.carPlayStarted()
    }
    fun onVehicleMusicSelected(expected: com.shilapi.xcertplay.orchestration.CarPlayController) {
        if (!profileEnabled || sessionController !== expected) return
        val wasOwner = carPlayOwnsMusic
        carPlayOwnsMusic = false
        sessionContext?.let {
            ArcfoxMusicSync.setSourceActive(it, false)
            if (wasOwner) ArcfoxMediaCoordination.notifyYielded(it)
        }
    }
    fun ownsCarPlayMusic(): Boolean = profileEnabled && carPlayOwnsMusic
    fun onNowPlaying(context: Context, update: CarPlayNowPlaying) {
        if (profileEnabled) ArcfoxMusicSync.update(context, update)
    }
    fun onArtwork(context: Context, id: Int, bytes: ByteArray, update: CarPlayNowPlaying) {
        if (profileEnabled) ArcfoxMusicSync.artwork(context, id, bytes, update)
    }
    fun onSessionEnded(context: Context?) {
        sourceReceiver?.let { receiver ->
            sessionContext?.let { runCatching { it.unregisterReceiver(receiver) } }
        }
        sourceReceiver = null
        musicOwner?.close()
        musicOwner = null
        ArcfoxMusicSync.clear(context)
        profileEnabled = false
        carPlayOwnsMusic = false
        sessionController = null
        sessionContext = null
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
