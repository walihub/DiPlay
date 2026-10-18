package com.shilapi.xcertplay.vehicle.arcfox

import com.shilapi.xcertplay.hud.*

/** Tokens invalidate queued updates/clears when a controller ends or another one takes over. */
internal class ArcfoxHudSessionGate {
    private var owner: Any? = null
    private var revision = 0L
    private var navigationRevision = 0L
    private var ended = true
    private var alive: () -> Boolean = { false }
    private val retiredOwners = java.util.WeakHashMap<Any, Boolean>()

    @Synchronized fun begin(nextOwner: Any, sessionAlive: () -> Boolean): Long? {
        if (retiredOwners.containsKey(nextOwner)) return null
        if (owner !== nextOwner || ended) {
            if (owner !== nextOwner) {
                owner?.let { retiredOwners[it] = true }
                navigationRevision++
            }
            revision++
            owner = nextOwner
            ended = false
        }
        alive = sessionAlive
        return revision
    }

    @Synchronized fun capture(expectedOwner: Any): Long? =
        revision.takeIf { owner === expectedOwner && !ended }

    /** A disconnected video session may still receive current iAP2 route/stop updates. */
    @Synchronized fun captureNavigation(expectedOwner: Any): Long? =
        navigationRevision.takeIf { owner === expectedOwner }

    @Synchronized fun isNavigationCurrent(token: Long): Boolean =
        owner != null && token == navigationRevision

    @Synchronized fun resetNavigation(expectedOwner: Any): Long? {
        if (owner !== expectedOwner) return null
        return ++navigationRevision
    }

    @Synchronized fun current(): Long? = revision.takeIf { owner != null }

    @Synchronized fun isCurrent(token: Long): Boolean = owner != null && token == revision

    @Synchronized fun isAlive(): Boolean = !ended && runCatching(alive).getOrDefault(false)

    @Synchronized fun isEnded(): Boolean = ended

    @Synchronized fun end(expectedOwner: Any): Long? {
        if (owner !== expectedOwner || ended) return null
        ended = true
        alive = { false }
        return ++revision
    }

    /** Global service cleanup must not retire a newer live controller. */
    @Synchronized fun endIfInactive(): Long? {
        if (isAlive()) return null
        return owner?.let { end(it) }
    }
}
