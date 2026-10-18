package com.shilapi.xcertplay.vehicle.arcfox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Looper
import android.util.Log
import com.shilapi.xcertplay.hud.BydCarPlayCall
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Message 114 is the same instrument output used by MIS_ArcfoxLinkMusic.
 * This sender never registers a vendor callback or changes a native connection state.
 * A successful call confirms HIDL dispatch, not instrument rendering.
 */
internal object ArcfoxDirectMusicTransport {
    private val transport = ArcfoxDirectMusicSender(
        ArcfoxMusicHidl()::send,
        ArcfoxMusicCoverStore(File("/mnt/ota/media"))::write,
    )

    fun trySend(context: Context, packet: ArcfoxMusicPacket): Boolean {
        // File I/O and synchronous vendor Binder calls belong on the publisher's serial worker.
        if (Looper.myLooper() == Looper.getMainLooper()) return false
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return try {
            // Direct metadata must yield during ringing/voice/communication modes.
            // The publisher retains state and retries after normal audio mode resumes.
            if (!canPublishDirect(audio.mode, BydCarPlayCall.current() != null)) return false
            // Meter MuteStateUtils uses 1 for audible, 0 for muted.
            transport.send(packet, muted = audio.isStreamMute(AudioManager.STREAM_MUSIC))
        } catch (error: Exception) {
            Log.w("DiPlay-ArcfoxMusic", "Instrument media dispatch unavailable", error)
            false
        }
    }

    internal fun canPublishDirect(audioMode: Int, hasCarPlayCall: Boolean): Boolean =
        audioMode == AudioManager.MODE_NORMAL && !hasCarPlayCall

}

/** Transport-independent encoder. Called only by the serial music publisher. */
internal class ArcfoxDirectMusicSender(
    private val dispatch: (String) -> Boolean,
    private val writeCover: (ByteArray) -> String,
) {
    private var coverName = ""
    private var artworkKey: List<Any?>? = null
    private var lastCoverFailureAt: Long? = null

    fun send(packet: ArcfoxMusicPacket, muted: Boolean): Boolean = try {
        val nextArtworkKey = listOf(packet.song.artist, packet.song.album, packet.song.artworkId)
        if (artworkKey != nextArtworkKey) {
            coverName = ""
            artworkKey = nextArtworkKey
        }
        val bytes = packet.cover
        if (packet.includeCover && bytes == null) {
            coverName = ""
        } else if (bytes != null) {
            // The store only writes missing artwork, so a restart or external cache cleanup
            // recovers without retransferring artwork on every progress update.
            // Artwork failure must not freeze text/progress on a fixed native route.
            // Keep retrying from the packet's retained bytes on subsequent updates.
            val written = try { writeCover(bytes) } catch (error: Exception) {
                val time = android.os.SystemClock.elapsedRealtime()
                if (lastCoverFailureAt?.let { time - it >= 5_000L } != false) {
                    lastCoverFailureAt = time
                    Log.w("DiPlay-ArcfoxMusic", "Cover cache failed uid=${android.os.Process.myUid()} directory=/mnt/ota/media", error)
                }
                null
            }
            if (written == null) {
                coverName = ""
            } else {
                // A malformed returned path is a contract violation, not an artwork I/O failure.
                check(written.startsWith(ArcfoxMusicCoverStore.PREFIX) && '/' !in written)
                coverName = written
            }
        }
        val content = JSONObject()
            .put("mediatype", 19)
            .put("songname", packet.song.title)
            .put("singername", packet.song.artist)
            .put("totalplaytime", seconds(packet.song.durationMs))
            .put("curplaytime", seconds(packet.progressMs.coerceAtLeast(0L).let {
                if (packet.song.durationMs > 0L) it.coerceAtMost(packet.song.durationMs) else it
            }))
            .put("status", if (packet.song.playing) "play" else "pause")
            .put("mute", if (muted) 0 else 1)
            .put("albumpic", coverName)
            .put("singlelyric", "")
            .put("lyric", "")
        val json = JSONObject()
            .put("command", JSONObject().put("id", "meidainfo").put("sub", "id3info"))
            .put("content", content).toString()
        json.toByteArray(Charsets.UTF_8).size <= 64 * 1024 && dispatch(json)
    } catch (_: Exception) {
        false
    }

    private fun seconds(ms: Long): Int {
        val value = ms.coerceAtLeast(0L)
        // Match MIS_ArcfoxLinkMusic.parseTime: exactly 500 ms rounds down.
        return (value / 1000 + if (value % 1000 > 500) 1 else 0)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

/** Only content-addressed DiPlay files are ever created or replaced. */
internal class ArcfoxMusicCoverStore(private val directory: File) {
    private data class Verified(val length: Long, val modified: Long)
    private val verified = LinkedHashMap<String, Verified>()

    private fun remember(target: File) {
        verified.remove(target.name)
        verified[target.name] = Verified(target.length(), target.lastModified())
        while (verified.size > 4) verified.remove(verified.keys.first())
    }
    fun write(bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= ArcfoxMusicSync.MAX_COVER_BYTES)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val name = "$PREFIX$digest.png"
        check(directory.isDirectory || directory.mkdirs()) { "Cover directory unavailable: $directory" }
        val target = File(directory, name)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth in 1..4096 && options.outHeight in 1..4096)
        require(options.outWidth.toLong() * options.outHeight <= 4_194_304L)
        val stamp = Verified(target.length(), target.lastModified())
        if (target.isFile && target.canRead() && verified[name] == stamp) return name
        val bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        // File existence is not proof that the consumer can decode its contents. Recover a
        // truncated/corrupt cache entry rather than reporting an unusable album filename.
        if (target.isFile && target.canRead() && target.length() > 0) {
            val cached = BitmapFactory.decodeFile(target.absolutePath)
            val valid = cached != null && cached.width == bitmap.width && cached.height == bitmap.height
            cached?.recycle()
            if (valid) { remember(target); bitmap.recycle(); return name }
        }
        val temporary = File.createTempFile(PREFIX, ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.fd.sync()
            }
            check(temporary.setReadable(true, false)) { "Cover setReadable failed: $temporary" }
            check(temporary.renameTo(target)) { "Cover rename failed: $temporary -> $target" }
            check(target.isFile && target.canRead())
            remember(target)
            // Defer deletion so the instrument has time to consume a previous filename.
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            directory.listFiles()?.filter {
                it.name.matches(Regex("${PREFIX}[0-9a-f]{64}\\.png")) && it != target && it.lastModified() < cutoff
            }?.forEach { it.delete() }
            return name
        } finally {
            bitmap.recycle()
            temporary.delete()
        }
    }

    companion object { const val PREFIX = "diplay_music_" }
}
