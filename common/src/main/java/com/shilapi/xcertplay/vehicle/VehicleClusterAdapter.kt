package com.shilapi.xcertplay.vehicle

import android.content.Context
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxClusterAdapter

/** Host lifecycle boundary; no physical instrument service is used on other vehicles. */
internal interface VehicleClusterAdapter {
    fun onSessionStarted() {}
    fun onSessionEnded() {}
    fun onClusterVisibilityChanged(controller: CarPlayController?, shown: Boolean) {}
    fun onTurnCardVisibilityChanged(shown: Boolean) {}
    fun release() {}
}

internal fun vehicleClusterAdapter(context: Context): VehicleClusterAdapter = SelectedClusterAdapter(context)

/** Apply a changed profile at the next session, keeping active output ownership stable. */
private class SelectedClusterAdapter(private val context: Context) : VehicleClusterAdapter {
    private var profile = VehicleProfiles.current(context)
    private fun create(): VehicleClusterAdapter =
        if (profile == VehicleProfile.ARCFOX_T) ArcfoxClusterAdapter(context) else DefaultClusterAdapter
    private var delegate = create()
    override fun onSessionStarted() {
        val next = VehicleProfiles.current(context)
        if (next != profile) {
            delegate.release()
            profile = next
            delegate = create()
        }
        delegate.onSessionStarted()
    }
    override fun onSessionEnded() = delegate.onSessionEnded()
    override fun onClusterVisibilityChanged(controller: CarPlayController?, shown: Boolean) =
        delegate.onClusterVisibilityChanged(controller, shown)
    override fun onTurnCardVisibilityChanged(shown: Boolean) = delegate.onTurnCardVisibilityChanged(shown)
    override fun release() = delegate.release()
}

private object DefaultClusterAdapter : VehicleClusterAdapter
