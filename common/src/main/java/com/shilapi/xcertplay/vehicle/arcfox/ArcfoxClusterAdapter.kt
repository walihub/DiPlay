package com.shilapi.xcertplay.vehicle.arcfox

import android.content.Context
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.vehicle.VehicleClusterAdapter

internal class ArcfoxClusterAdapter(private val owner: Context) : VehicleClusterAdapter {
    private var suspended = false
    override fun onSessionStarted() { suspended = false }
    override fun onSessionEnded() {
        suspended = true
        ArcfoxClusterLink.release(owner)
    }
    override fun onClusterVisibilityChanged(controller: CarPlayController?, shown: Boolean) =
        ArcfoxWheelZoom.update(owner, controller, shown)
    override fun onTurnCardVisibilityChanged(shown: Boolean) =
        ArcfoxClusterLink.request(owner, shown && !suspended)
    override fun release() {
        ArcfoxWheelZoom.release(owner)
        ArcfoxClusterLink.release(owner)
    }
}
