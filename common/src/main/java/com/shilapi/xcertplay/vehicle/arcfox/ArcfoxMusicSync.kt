package com.shilapi.xcertplay.vehicle.arcfox

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.shilapi.xcertplay.media.CarPlayNowPlaying

/** Publishes only while CarPlay owns media. All file/Binder work runs on a serial worker. */
internal object ArcfoxMusicSync {
    const val MAX_COVER_BYTES = 2 * 1024 * 1024

    internal data class Song(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val playing: Boolean,
        val artworkId: Int?,
    )

    private val worker by lazy {
        Handler(HandlerThread("ArcfoxMusicSync").apply { start() }.looper)
    }
    private var context: Context? = null
    private val publisher = ArcfoxMusicPublisher(SystemClock::elapsedRealtime) { packet ->
        context?.let {
            if (ArcfoxLinkMusicTransport.trySend(it, packet)) ArcfoxMusicDispatch.DISPATCHED
            else ArcfoxMusicDispatch.UNAVAILABLE
        } ?: ArcfoxMusicDispatch.UNAVAILABLE
    }
    private val tick = object : Runnable {
        override fun run() {
            publisher.tick()
            if (publisher.shouldTick) worker.postDelayed(this, ArcfoxMusicPublisher.PROGRESS_INTERVAL_MS)
        }
    }

    fun available(): Boolean = Build.FINGERPRINT.startsWith("AAOP_COCKPIT/")

    fun update(context: Context, info: CarPlayNowPlaying) {
        if (!available()) return
        val app = context.applicationContext
        worker.post {
            this.context = app
            publisher.update(info)
            scheduleTick()
        }
    }

    fun artwork(context: Context, id: Int, bytes: ByteArray, info: CarPlayNowPlaying) {
        if (!available() || bytes.isEmpty() || bytes.size > MAX_COVER_BYTES) return
        val app = context.applicationContext
        val copy = bytes.copyOf()
        worker.post {
            this.context = app
            publisher.artwork(id, copy, info)
            scheduleTick()
        }
    }

    /** Losing ownership never sends an empty packet over the new owner's metadata. */
    fun setSourceActive(context: Context, active: Boolean) {
        if (!available()) return
        val app = context.applicationContext
        worker.post {
            this.context = app
            publisher.setSourceActive(active)
            scheduleTick()
        }
    }

    fun clear(context: Context?) {
        val app = context?.applicationContext
        worker.post {
            if (app != null) this.context = app
            publisher.clear(publish = app != null && available())
            worker.removeCallbacks(tick)
            this.context = null
        }
    }

    private fun scheduleTick() {
        worker.removeCallbacks(tick)
        if (publisher.shouldTick) worker.postDelayed(tick, ArcfoxMusicPublisher.PROGRESS_INTERVAL_MS)
    }

    internal fun song(info: CarPlayNowPlaying): Song = Song(
        info.title.orEmpty(), info.artist.orEmpty(), info.album.orEmpty(),
        info.durationMillis?.coerceAtLeast(0L) ?: 0L, info.playing, info.artworkTransferId,
    )
}

/** Clock-driven state machine, independent of Android scheduling and transport implementation. */
internal class ArcfoxMusicPublisher(
    private val now: () -> Long,
    private val send: (ArcfoxMusicPacket) -> ArcfoxMusicDispatch,
) {
    companion object {
        const val PROGRESS_INTERVAL_MS = 1_000L
        const val SNAPSHOT_INTERVAL_MS = 10_000L
        const val COVER_RECOVERY_INTERVAL_MS = 60_000L
        // Small iPhone timestamp corrections are normal; larger discontinuities are seeks.
        const val SEEK_DISCONTINUITY_MS = 2_000L
    }

    private var current: ArcfoxMusicSync.Song? = null
    private var currentInfo: CarPlayNowPlaying? = null
    private var sourceActive = true
    private var rawPosition: Long? = null
    private var position = 0L
    private var anchoredAt = 0L
    private var cover: ByteArray? = null
    private var coverDirty = true
    private var stateDirty = true
    private var lastDispatchAt: Long? = null
    private var lastAttemptAt: Long? = null
    private var lastCoverAt: Long? = null
    private var lastSnapshotAt: Long? = null
    private var lastProgress: Long? = null
    private var transportWasUnavailable = false

    val shouldTick: Boolean get() = current != null && sourceActive

    fun update(info: CarPlayNowPlaying) {
        val next = ArcfoxMusicSync.song(info)
        val previous = current
        val time = now()
        val trackChanged = currentInfo?.let { com.shilapi.xcertplay.albumTrackChanged(it, info) } ?: true
        val artworkChanged = previous?.artworkId != next.artworkId
        val previousPosition = projected(time)
        val elapsed = info.elapsedMillis?.coerceAtLeast(0L)
        val positionChanged = elapsed != null && elapsed != rawPosition
        val discontinuity = elapsed?.let { maxOf(it, previousPosition) - minOf(it, previousPosition) } ?: 0L
        val seek = !trackChanged && positionChanged &&
            (discontinuity >= SEEK_DISCONTINUITY_MS || previous?.playing == false && discontinuity > 0L)
        if (trackChanged || artworkChanged) {
            cover = null
            coverDirty = true
        }
        if (trackChanged) {
            position = elapsed ?: 0L
        } else if (elapsed != null && positionChanged) {
            position = elapsed
        } else {
            // NowPlaying is retained: unchanged elapsedMillis on a metadata update is not a seek.
            position = previousPosition
        }
        rawPosition = elapsed
        anchoredAt = time
        stateDirty = stateDirty || next != previous || seek
        current = next
        currentInfo = info
        publish(time)
    }

    fun artwork(id: Int, bytes: ByteArray, info: CarPlayNowPlaying) {
        val song = current ?: return
        if (song != ArcfoxMusicSync.song(info) || song.artworkId != id || bytes.isEmpty() ||
            bytes.size > ArcfoxMusicSync.MAX_COVER_BYTES) return
        if (cover?.contentEquals(bytes) == true) return
        cover = bytes.copyOf()
        coverDirty = true
        stateDirty = true
        publish(now())
    }

    fun setSourceActive(active: Boolean) {
        if (sourceActive == active) return
        sourceActive = active
        if (active) {
            stateDirty = true
            coverDirty = true
            publish(now())
        }
    }

    fun tick() = publish(now())

    fun clear(publish: Boolean = true) {
        if (publish && sourceActive && current != null) {
            send(ArcfoxMusicPacket(ArcfoxMusicSync.song(CarPlayNowPlaying()), 0, null, true))
        }
        current = null
        currentInfo = null
        sourceActive = true
        rawPosition = null
        position = 0
        cover = null
        coverDirty = true
        stateDirty = true
        lastDispatchAt = null
        lastAttemptAt = null
        lastCoverAt = null
        lastSnapshotAt = null
        lastProgress = null
        transportWasUnavailable = false
    }

    private fun projected(time: Long): Long {
        val song = current
        val elapsed = if (song?.playing == true) (time - anchoredAt).coerceAtLeast(0L) else 0L
        val result = position + elapsed.coerceAtMost(Long.MAX_VALUE - position)
        return if (song != null && song.durationMs > 0) result.coerceAtMost(song.durationMs) else result
    }

    private fun publish(time: Long) {
        val song = current ?: return
        if (!sourceActive) return
        val progress = projected(time)
        val snapshotDue = lastSnapshotAt?.let { time - it >= SNAPSHOT_INTERVAL_MS } ?: true
        val progressDue = progress != lastProgress &&
            (lastDispatchAt?.let { time - it >= PROGRESS_INTERVAL_MS } ?: true)
        val recoverCover = cover != null &&
            (lastCoverAt?.let { time - it >= COVER_RECOVERY_INTERVAL_MS } ?: true)
        if (!stateDirty && !snapshotDue && !progressDue && !recoverCover && !transportWasUnavailable) return
        if (transportWasUnavailable && lastAttemptAt?.let { time - it < PROGRESS_INTERVAL_MS } == true) return
        val includeCover = coverDirty || recoverCover || transportWasUnavailable
        lastAttemptAt = time
        // Retain bytes for native service/file recovery. Native sends only its cached path.
        val result = send(ArcfoxMusicPacket(song, progress, cover, includeCover))
        if (result == ArcfoxMusicDispatch.UNAVAILABLE) {
            transportWasUnavailable = true
            return
        }
        transportWasUnavailable = false
        stateDirty = false
        coverDirty = false
        lastDispatchAt = time
        lastProgress = progress
        if (snapshotDue) lastSnapshotAt = time
        if (includeCover) lastCoverAt = time
    }
}
