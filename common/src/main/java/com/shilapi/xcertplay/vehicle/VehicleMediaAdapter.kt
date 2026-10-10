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
    fun onNowPlaying(context: Context, update: CarPlayNowPlaying) = ArcfoxMusicSync.update(context, update)
    fun onArtwork(context: Context, id: Int, bytes: ByteArray, update: CarPlayNowPlaying) =
        ArcfoxMusicSync.artwork(context, id, bytes, update)
    fun onSessionEnded(context: Context?) = ArcfoxMusicSync.clear(context)

    fun onPlaybackStarted(context: Context?, channel: Int, focusGranted: Boolean) {
        if (context == null || !VehicleIdentity.isArcfox()) return
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            ArcfoxMediaRouteRecovery.recover(Build.FINGERPRINT, channel, focusGranted, audio,
                packageName = context.packageName, report = { Log.i("DiPlay-ArcfoxMusic", it) })
        }.onFailure { Log.w("DiPlay-ArcfoxMusic", "media route resume failed", it) }
    }
}
