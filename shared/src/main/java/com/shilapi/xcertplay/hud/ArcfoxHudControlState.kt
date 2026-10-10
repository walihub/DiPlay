package com.shilapi.xcertplay.hud

/** A short iAP2 handoff gap is tolerated; an ended control channel is not a live navigation source. */
internal class ArcfoxHudControlState(private val nanoTime: () -> Long = System::nanoTime) {
    private val controls = mutableMapOf<Any, Int>()
    private var closedGeneration = -1
    private var closedAt = 0L

    @Synchronized fun opened(control: Any, generation: Int) {
        controls[control] = generation
        if (closedGeneration == generation) closedGeneration = -1
    }

    @Synchronized fun ended(control: Any) {
        val generation = controls.remove(control) ?: return
        if (generation >= closedGeneration && generation !in controls.values) {
            closedGeneration = generation
            closedAt = nanoTime()
        }
    }

    @Synchronized fun isAlive(generation: Int): Boolean = generation in controls.values ||
        (generation == closedGeneration && nanoTime() - closedAt < HANDOFF_GRACE_NS)

    companion object { private const val HANDOFF_GRACE_NS = 5_000_000_000L }
}
