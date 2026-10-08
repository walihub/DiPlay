package com.shilapi.xcertplay.orchestration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

/** Arcfox's long-press voice event, as handled by the supplied CarLife receiver. */
object ArcfoxHardKeys {
    private const val ACTION_LONG_PRESS = "adayo.keyEvent.onKeyLongPress.N6"
    private const val EXTRA_INPUT_KEY = "InputKey"

    fun isVoiceLongPress(action: String?, inputKey: Int): Boolean =
        action == ACTION_LONG_PRESS && (inputKey == 231 || inputKey == 219)

    fun register(context: Context, requestSiri: () -> Boolean): BroadcastReceiver? {
        if (!Build.FINGERPRINT.startsWith("AAOP_COCKPIT/")) return null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (isVoiceLongPress(intent.action, intent.getIntExtra(EXTRA_INPUT_KEY, -1))) {
                    Log.i("DiPlay-ArcfoxKeys", "voice long press: Siri requested=${requestSiri()}")
                }
            }
        }
        val filter = IntentFilter(ACTION_LONG_PRESS)
        return runCatching {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(receiver, filter)
            receiver
        }.onFailure { Log.w("DiPlay-ArcfoxKeys", "could not register voice key", it) }.getOrNull()
    }
}
