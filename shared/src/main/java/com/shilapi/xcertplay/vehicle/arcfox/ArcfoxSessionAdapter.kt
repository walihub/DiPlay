package com.shilapi.xcertplay.vehicle.arcfox

import android.content.Context
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.hud.BydHudRouteState
import com.shilapi.xcertplay.vehicle.VehicleSessionAdapter

/** Owns the HUD lease and control-channel state independently of BYD navigation outputs. */
internal class ArcfoxSessionAdapter(context: Context, private val owner: Any) : VehicleSessionAdapter {
    private val controls = ArcfoxHudControlState()

    init { ArcfoxHudSync.attach(context.applicationContext) }

    override fun onSessionStarted(alive: () -> Boolean, generation: () -> Int) {
        ArcfoxHudSync.begin(owner) { alive() && controls.isAlive(generation()) }
    }

    override fun onSessionEnded() = ArcfoxHudSync.end(owner)
    override fun onControlOpened(token: Any, generation: Int) = controls.opened(token, generation)
    override fun onControlEnded(token: Any) = controls.ended(token)
    override fun onNavigation(frame: Iap2Frame, currentControl: Boolean) {
        if (currentControl && (frame.messageId == BydHudRouteState.ROUTE_GUIDANCE_UPDATE ||
                frame.messageId == BydHudRouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE)) {
            ArcfoxHudSync.onFrame(owner, frame)
        }
    }
}
