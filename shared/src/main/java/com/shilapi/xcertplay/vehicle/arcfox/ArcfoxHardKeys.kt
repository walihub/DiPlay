package com.shilapi.xcertplay.vehicle.arcfox

import com.shilapi.xcertplay.orchestration.*
import com.shilapi.xcertplay.airplay.CarPlayMediaButton

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

/** Arcfox's long-press voice event, as handled by the supplied CarLife receiver. */
object ArcfoxHardKeys {
    private const val ACTION_DOWN = "adayo.keyEvent.onKeyDown.N6"
    private const val ACTION_LONG_PRESS = "adayo.keyEvent.onKeyLongPress.N6"
    private const val EXTRA_INPUT_KEY = "InputKey"

    fun isVoiceLongPress(action: String?, inputKey: Int): Boolean =
        action == ACTION_LONG_PRESS && (inputKey == 231 || inputKey == 219)

    /** CarLife's ArcfoxHardKeyReceiver maps these raw N6 codes to next/previous. */
    fun mediaPress(action: String?, inputKey: Int): Int? = if (action == ACTION_DOWN) when (inputKey) {
        16 -> CarPlayMediaButton.NEXT
        15 -> CarPlayMediaButton.PREVIOUS
        else -> null
    } else null

    fun register(context: Context, sendMedia: (Int) -> Boolean = { false }, requestSiri: () -> Boolean): BroadcastReceiver? {
        if (!Build.FINGERPRINT.startsWith("AAOP_COCKPIT/")) return null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val inputKey = intent.getIntExtra(EXTRA_INPUT_KEY, -1)
                if (isVoiceLongPress(intent.action, inputKey)) {
                    Log.i("DiPlay-ArcfoxKeys", "voice long press: Siri requested=${requestSiri()}")
                }
                mediaPress(intent.action, inputKey)?.let { press ->
                    Log.i("DiPlay-ArcfoxKeys", "wheel media input=$inputKey sent=${sendMedia(press)}")
                }
            }
        }
        val filter = IntentFilter(ACTION_LONG_PRESS).apply { addAction(ACTION_DOWN) }
        return runCatching {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(receiver, filter)
            receiver
        }.onFailure { Log.w("DiPlay-ArcfoxKeys", "could not register voice key", it) }.getOrNull()
    }
}
