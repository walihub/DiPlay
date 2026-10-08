package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.shilapi.xcertplay.media.CarPlayNowPlaying

/** The exported CarPlay music receiver in the supplied Arcfox Link APK. */
internal object ArcfoxMusicSync {
    private const val TAG = "DiPlay-ArcfoxMusic"
    private const val PACKAGE = "com.carlinx.arcfox.carlink"
    private const val RECEIVER = "$PACKAGE.CarLifeMusicReceiver"
    private const val ACTION = "$PACKAGE.action.CARPLAY_MUSIC"
    const val MAX_COVER_BYTES = 512 * 1024

    internal data class Song(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val playing: Boolean,
        val artworkId: Int?,
    )

    private var last: Song? = null
    private var coverId: Int? = null
    private var cover: ByteArray? = null

    fun available(): Boolean = Build.FINGERPRINT.startsWith("AAOP_COCKPIT/")

    @Synchronized fun update(context: Context, info: CarPlayNowPlaying) {
        if (!available()) return
        val next = song(info)
        if (next.artworkId != coverId) {
            coverId = null
            cover = null
        }
        if (next == last) return
        if (send(context, next, info.elapsedMillis)) last = next
    }

    @Synchronized fun artwork(context: Context, id: Int, bytes: ByteArray, info: CarPlayNowPlaying) {
        if (!available() || info.artworkTransferId != id || bytes.isEmpty() || bytes.size > MAX_COVER_BYTES) return
        coverId = id
        cover = bytes.copyOf()
        val next = song(info)
        if (send(context, next, info.elapsedMillis)) last = next
    }

    @Synchronized fun clear(context: Context?) {
        if (context != null && available() && last != null) send(context, song(CarPlayNowPlaying()), null)
        last = null
        coverId = null
        cover = null
    }

    internal fun song(info: CarPlayNowPlaying): Song = Song(
        info.title.orEmpty(), info.artist.orEmpty(), info.album.orEmpty(),
        info.durationMillis?.coerceAtLeast(0L) ?: 0L, info.playing, info.artworkTransferId,
    )

    private fun send(context: Context, song: Song, elapsedMillis: Long?): Boolean {
        val intent = Intent(ACTION).apply {
            component = ComponentName(PACKAGE, RECEIVER)
            putExtra("title", song.title)
            putExtra("artist", song.artist)
            putExtra("album", song.album)
            putExtra("duration", song.durationMs)
            putExtra("progress", (elapsedMillis ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
            putExtra("playing", song.playing)
            if (song.artworkId == coverId) cover?.let { putExtra("cover", it) }
        }
        return runCatching { context.sendBroadcast(intent); true }
            .onFailure { Log.w(TAG, "music relay failed", it) }.getOrDefault(false)
    }
}
