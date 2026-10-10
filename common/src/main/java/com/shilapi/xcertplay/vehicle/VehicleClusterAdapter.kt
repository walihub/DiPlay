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

internal fun vehicleClusterAdapter(context: Context): VehicleClusterAdapter =
    if (VehicleIdentity.isArcfox()) ArcfoxClusterAdapter(context) else DefaultClusterAdapter

private object DefaultClusterAdapter : VehicleClusterAdapter
