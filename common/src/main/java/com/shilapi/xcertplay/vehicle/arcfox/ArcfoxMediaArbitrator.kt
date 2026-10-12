package com.shilapi.xcertplay.vehicle.arcfox

import android.content.Context
import android.media.AudioAttributes
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/** Observes real music-session start transitions; metadata refreshes never reclaim ownership. */
internal class ArcfoxMediaArbitrator(private val context: Context, private val pauseCarPlay: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private data class Observed(val controller: MediaController, val callback: MediaController.Callback, var playing: Boolean)
    private val observed = LinkedHashMap<MediaSession.Token, Observed>()
    @Volatile private var closed = false
    private var phoneStartedAt = Long.MIN_VALUE
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        handler.post { if (!closed) synchronizeSessions(sessions.orEmpty(), false) }
    }

    init {
        handler.post {
        if (closed) return@post
        runCatching {
            manager?.addOnActiveSessionsChangedListener(listener, null, handler)
            synchronizeSessions(manager?.getActiveSessions(null).orEmpty(), true)
        }.onFailure { Log.w(TAG, "media session observation unavailable", it) }
        }
    }

    fun carPlayStarted() {
        val time = SystemClock.elapsedRealtime()
        handler.post {
            if (closed) return@post
            phoneStartedAt = time
            runCatching { synchronizeSessions(manager?.getActiveSessions(null).orEmpty(), true) }
            pauseOthers(null)
        }
    }

    private fun synchronizeSessions(sessions: List<MediaController>, baseline: Boolean) {
        if (closed) return
        val tokens = sessions.map { it.sessionToken }.toSet()
        observed.keys.filter { it !in tokens }.forEach { token ->
            observed.remove(token)?.let { it.controller.unregisterCallback(it.callback) }
        }
        sessions.filter { it.packageName != context.packageName }.forEach { controller ->
            val token = controller.sessionToken
            if (token !in observed) {
                val playing = isPlayingMusic(controller, controller.playbackState)
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        val item = observed[token] ?: return
                        if (closed) return
                        val next = isPlayingMusic(controller, state)
                        val started = next && !item.playing
                        item.playing = next
                        if (started) nativeStarted(token, state)
                    }
                }
                observed[token] = Observed(controller, callback, playing)
                controller.registerCallback(callback, handler)
                if (!baseline && playing) nativeStarted(token, controller.playbackState)
            }
        }
    }

    private fun nativeStarted(token: MediaSession.Token, state: PlaybackState?) {
        // A late registration snapshot of music that was already playing cannot beat a newer
        // CarPlay start. Vendors reporting no timestamp still use the observed start transition.
        val updated = state?.lastPositionUpdateTime ?: 0L
        if (updated > 0 && updated < phoneStartedAt) return
        Log.i(TAG, "native music started source=${observed[token]?.controller?.packageName}")
        pauseCarPlay()
        pauseOthers(token)
    }

    private fun pauseOthers(winner: MediaSession.Token?) {
        observed.forEach { (token, item) ->
            val state = item.controller.playbackState
            if (token != winner && isPlayingMusic(item.controller, state) &&
                state != null && state.actions and PlaybackState.ACTION_PAUSE != 0L) {
                runCatching { item.controller.transportControls.pause() }
                    .onFailure { Log.w(TAG, "native music pause failed", it) }
            }
        }
    }

    private fun isPlayingMusic(controller: MediaController, state: PlaybackState?): Boolean {
        if (state?.state != PlaybackState.STATE_PLAYING) return false
        val attributes = controller.playbackInfo?.audioAttributes ?: return false
        return attributes.usage == AudioAttributes.USAGE_MEDIA ||
            attributes.contentType == AudioAttributes.CONTENT_TYPE_MUSIC
    }

    fun close() {
        closed = true
        handler.post {
        runCatching { manager?.removeOnActiveSessionsChangedListener(listener) }
        observed.values.forEach { runCatching { it.controller.unregisterCallback(it.callback) } }
        observed.clear()
        }
    }
    private companion object { const val TAG = "DiPlay-ArcfoxMusicOwner" }
}
