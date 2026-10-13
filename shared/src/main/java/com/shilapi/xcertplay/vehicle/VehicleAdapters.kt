package com.shilapi.xcertplay.vehicle

import android.content.Context
import android.os.Build
import com.shilapi.xcertplay.hud.CarPlayCallCard
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxHardKeys
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxPhoneSync
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxHudSync
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxSessionAdapter

/** Additional vehicle outputs. Other vehicles retain the upstream implementation unchanged. */
interface VehicleSessionAdapter {
    fun onSessionStarted(alive: () -> Boolean, generation: () -> Int) {}
    fun onSessionEnded() {}
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

    fun registerVoiceKey(context: Context, sendMedia: (Int) -> Boolean = { false }, requestSiri: () -> Boolean) =
        if (VehicleProfiles.isArcfox(context)) ArcfoxHardKeys.register(context, sendMedia, requestSiri) else null

    fun attachCalls(context: Context) = ArcfoxPhoneSync.attach(context)
    fun onCall(card: CarPlayCallCard?) = ArcfoxPhoneSync.onCard(card)
    fun endCalls() = ArcfoxPhoneSync.end()
    fun diagnosticReport(): String = ArcfoxHudSync.diagnosticReport()
}
