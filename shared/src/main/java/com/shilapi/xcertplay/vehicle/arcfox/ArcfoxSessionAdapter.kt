package com.shilapi.xcertplay.vehicle.arcfox

import android.content.Context
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.hud.BydHudRouteState
import com.shilapi.xcertplay.vehicle.VehicleSessionAdapter

/** Owns the HUD lease and control-channel state independently of BYD navigation outputs. */
internal class ArcfoxSessionAdapter(context: Context, private val owner: Any) : VehicleSessionAdapter {
    private val controls = ArcfoxHudControlState()
    @Volatile private var generationProvider: (() -> Int)? = null
    @Volatile private var navigationGeneration: Int? = null
    @Volatile private var navigationContinuity = false

    init { ArcfoxHudSync.attach(context.applicationContext) }

    override fun onSessionStarted(alive: () -> Boolean, generation: () -> Int) {
        generationProvider = generation
        // The controller first attaches us from its constructor, before its generation field
        // is initialized. With no route to restore, defer that callback until actual input.
        ArcfoxHudSync.begin(owner, navigationContinuity && retainNavigationForReconnect(generation())) {
            alive() && controls.isAlive(generation())
        }
    }

    override fun onSessionEnded() = ArcfoxHudSync.end(owner)
    @Synchronized override fun retainNavigationForReconnect(generation: Int): Boolean =
        navigationContinuity && navigationGeneration == generation && controls.isOpen(generation)
    @Synchronized override fun onControlOpened(token: Any, generation: Int) = controls.opened(token, generation)
    @Synchronized override fun onControlEnded(token: Any) {
        controls.ended(token)
        // A newly opened socket is not proof that a route survived a control-channel gap.
        // Bootstrap/tunnel handoff remains supported while either old or new control is open.
        if (navigationGeneration?.let(controls::isOpen) != true) navigationContinuity = false
    }
    @Synchronized override fun onNavigation(frame: Iap2Frame, currentControl: Boolean) {
        if (currentControl && (frame.messageId == BydHudRouteState.ROUTE_GUIDANCE_UPDATE ||
                frame.messageId == BydHudRouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE)) {
            val generation = generationProvider?.invoke() ?: return
            if (navigationGeneration != null &&
                (navigationGeneration != generation || !navigationContinuity))
                ArcfoxHudSync.resetNavigation(owner)
            navigationGeneration = generation
            navigationContinuity = true
            ArcfoxHudSync.onFrame(owner, frame)
        }
    }
}
