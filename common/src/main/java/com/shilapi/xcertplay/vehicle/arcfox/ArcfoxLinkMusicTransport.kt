package com.shilapi.xcertplay.vehicle.arcfox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/** ARCFOX Link owns the shared artwork directory. Music only: never changes phone state. */
internal object ArcfoxLinkMusicTransport {
    private const val PACKAGE = "com.carlinx.arcfox.carlink"
    private val receiver = ComponentName(PACKAGE, "$PACKAGE.CarLifeMusicReceiver")
    private const val MAX_BROADCAST_COVER_BYTES = 512 * 1024
    private var lastSong: ArcfoxMusicSync.Song? = null

    fun trySend(context: Context, packet: ArcfoxMusicPacket): Boolean = try {
        val info = context.packageManager.getReceiverInfo(receiver, 0)
        if (!info.enabled || !info.exported || !info.applicationInfo.enabled) false
        else {
            // The v17 receiver creates a fresh MusicInfo each time. Progress-only packets
            // cannot omit artwork safely, and resending artwork every second creates files.
            if (shouldSend(lastSong, packet)) context.sendBroadcast(intent(packet))
            lastSong = packet.song
            true // Delivery is asynchronous; this is not an instrument acknowledgement.
        }
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (error: Exception) {
        Log.w("DiPlay-ArcfoxMusic", "ARCFOX Link music dispatch unavailable", error)
        false
    }

    internal fun shouldSend(previous: ArcfoxMusicSync.Song?, packet: ArcfoxMusicPacket): Boolean =
        previous != packet.song || packet.includeCover

    internal fun intent(packet: ArcfoxMusicPacket): Intent = Intent("$PACKAGE.action.CARPLAY_MUSIC").apply {
        component = receiver
        putExtra("title", packet.song.title)
        putExtra("artist", packet.song.artist)
        putExtra("album", packet.song.album)
        putExtra("duration", packet.song.durationMs)
        val progress = packet.progressMs.coerceAtLeast(0L).let {
            if (packet.song.durationMs > 0) it.coerceAtMost(packet.song.durationMs) else it
        }
        putExtra("progress", progress.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        putExtra("playing", packet.song.playing)
        // Every emitted frame is complete: the receiver does not retain the prior cover.
        packet.cover?.takeIf {
            it.isNotEmpty() && it.size <= MAX_BROADCAST_COVER_BYTES
        }?.let { putExtra("cover", it) }
    }
}
