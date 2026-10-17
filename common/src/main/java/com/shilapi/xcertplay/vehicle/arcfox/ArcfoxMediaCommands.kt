package com.shilapi.xcertplay.vehicle.arcfox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.vehicle.VehicleIdentity
import com.shilapi.xcertplay.vehicle.VehicleProfiles

/** The OEM media service broadcasts transport commands to its selected source. */
internal object ArcfoxMediaCommands {
    const val ACTION = "android.adayo.input.media.send"
    const val SOURCE = "ADAYO_SOURCE_CARPLAY_MEDIA"

    fun command(source: String?, message: String?): Int? {
        if (source != SOURCE) return null
        return when (message) {
            "MSG_INPUT_PLAY" -> CarPlayMediaButton.PLAY
            "MSG_INPUT_STOP" -> CarPlayMediaButton.PAUSE
            "MSG_INPUT_NEXT" -> CarPlayMediaButton.NEXT
            "MSG_INPUT_PREV" -> CarPlayMediaButton.PREVIOUS
            else -> null
        }
    }

    fun register(context: Context, dispatch: (Int) -> Unit): BroadcastReceiver? {
        if (!VehicleProfiles.isArcfox(context) || !VehicleIdentity.isArcfox()) return null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                command(intent.getStringExtra("SourceInfo"), intent.getStringExtra("MessageID"))
                    ?.let(dispatch)
            }
        }
        return runCatching {
            val filter = IntentFilter(ACTION)
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(receiver, filter)
            receiver
        }.onFailure { Log.w("DiPlay-ArcfoxMedia", "media command receiver unavailable", it) }.getOrNull()
    }
}
