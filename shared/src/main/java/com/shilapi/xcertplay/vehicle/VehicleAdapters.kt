package com.shilapi.xcertplay.vehicle

import android.content.Context
import android.os.Build
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxHardKeys
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxHudSync
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxSessionAdapter

/** Additional vehicle outputs. Other vehicles retain the upstream implementation unchanged. */
interface VehicleSessionAdapter {
    fun onSessionStarted(alive: () -> Boolean, generation: () -> Int) {}
    fun onSessionEnded() {}
    /** Reuse route state only while the same physical iAP2 control generation is open. */
    fun retainNavigationForReconnect(generation: Int): Boolean = false
    fun onControlOpened(token: Any, generation: Int) {}
    fun onControlEnded(token: Any) {}
    fun onNavigation(frame: Iap2Frame, currentControl: Boolean) {}
}

object VehicleIdentity {
    fun isArcfox(fingerprint: String = Build.FINGERPRINT): Boolean =
        fingerprint.startsWith("AAOP_COCKPIT/")
}

object VehicleAdapters {
    private object DefaultSession : VehicleSessionAdapter

    fun session(context: Context, owner: Any): VehicleSessionAdapter =
        if (VehicleProfiles.isArcfox(context)) ArcfoxSessionAdapter(context, owner) else DefaultSession

    fun registerVoiceKey(context: Context, sendMedia: (Int) -> Boolean = { false },
        interceptMediaKey: (Int) -> Boolean = { false }, requestSiri: () -> Boolean) =
        if (VehicleProfiles.isArcfox(context)) ArcfoxHardKeys.register(context, sendMedia, interceptMediaKey, requestSiri) else null

    /** Profile-specific input selection; Arcfox never probes BYD's autoservice. */
    fun motionEnabled(context: Context): Boolean =
        if (VehicleProfiles.isArcfox(context))
            com.shilapi.xcertplay.hud.BydOutputSettings.wheelSpeedToIphone(context, defaultValue = true)
        else com.shilapi.xcertplay.hud.BydOutputSettings.wheelSpeedToIphoneActive(context)

    fun wheelSpeed(context: Context): com.shilapi.xcertplay.transport.VehicleSpeedSource =
        if (VehicleProfiles.isArcfox(context)) com.shilapi.xcertplay.vehicle.arcfox.ArcfoxVehicleSpeed(context)
        else com.shilapi.xcertplay.hud.BydNavigationOutputs.wheelSpeed(context)

    fun diagnosticReport(): String = ArcfoxHudSync.diagnosticReport()
}
