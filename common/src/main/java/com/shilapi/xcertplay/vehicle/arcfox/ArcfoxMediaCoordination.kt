package com.shilapi.xcertplay.vehicle.arcfox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.shilapi.xcertplay.vehicle.VehicleIdentity
import com.shilapi.xcertplay.vehicle.VehicleProfiles

/** Optional same-signature desktop observer supplements OEM sources without a MediaSession. */
internal object ArcfoxMediaCoordination {
    const val ACTION = "com.shihab.diplay.action.ARCFOX_NATIVE_MEDIA_SELECTED"
    const val PERMISSION = "com.shihab.diplay.permission.ARCFOX_MEDIA_COORDINATION"
    const val YIELDED = "com.shihab.diplay.action.ARCFOX_CARPLAY_MEDIA_YIELDED"

    fun notifyYielded(context: Context) {
        runCatching {
            context.sendBroadcast(Intent(YIELDED).setPackage("com.dudu.autoui")
                .putExtra("origin", "com.shihab.diplay")
                .putExtra("observedAt", SystemClock.elapsedRealtime()), PERMISSION)
        }.onFailure { Log.w("DiPlay-ArcfoxMusic", "Desktop ownership notification unavailable", it) }
    }

    internal fun isFreshNativeSelection(intent: Intent, now: Long): Boolean {
        if (intent.action != ACTION || intent.getStringExtra("origin") != "com.dudu.autoui") return false
        if (intent.getStringExtra("reason") !in setOf("source-change", "native-play")) return false
        if (intent.getIntExtra("sourceId", -1) !in 0..7) return false
        val observed = intent.getLongExtra("observedAt", -1)
        return observed > 0 && now >= observed && now - observed <= 10_000
    }

    fun register(context: Context, onNativeSelection: () -> Unit): BroadcastReceiver? {
        if (!VehicleProfiles.isArcfox(context) || !VehicleIdentity.isArcfox()) return null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (isFreshNativeSelection(intent, SystemClock.elapsedRealtime())) onNativeSelection()
            }
        }
        return runCatching {
            val filter = IntentFilter(ACTION)
            if (Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(receiver, filter, PERMISSION, null, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(receiver, filter, PERMISSION, null)
            receiver
        }.onFailure { Log.w("DiPlay-ArcfoxMusic", "Desktop source observation unavailable", it) }.getOrNull()
    }
}
