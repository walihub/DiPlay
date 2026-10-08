package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.json.JSONObject

/** Candidate input used by the Arcfox meter service's dynamically registered CarPlay call receiver. */
object ArcfoxPhoneSync {
    private const val ACTION = "mfiservice.communication.call.state.update"
    private const val TARGET = "com.adayo.aaop_meterinfoservice"
    private const val CALL_UUID = "diplay-current-call"
    private const val TAG = "DiPlay-ArcfoxPhone"

    private var context: Context? = null
    private var current: CarPlayCallCard? = null
    private var shown: CarPlayCallCard? = null

    fun available(): Boolean = Build.FINGERPRINT.startsWith("AAOP_COCKPIT/")

    fun attach(context: Context) {
        this.context = context.applicationContext
    }

    @Synchronized fun onCard(card: CarPlayCallCard?) {
        current = card
        val app = context ?: return
        if (!available() || card == shown) return
        if (send(app, card)) shown = card
    }

    @Synchronized fun end() {
        current = null
        clear()
    }

    @Synchronized private fun clear() {
        val app = context ?: return
        if (shown != null && send(app, null)) shown = null
    }

    private fun send(context: Context, card: CarPlayCallCard?): Boolean {
        val payload = JSONObject().apply {
            put("AddressBookID", "")
            put("CallUUID", CALL_UUID)
            put("ConferenceGroup", "")
            put("Direction", direction(card))
            put("DisconnectReason", 0)
            put("DisplayName", card?.name.orEmpty())
            put("IsConferenced", 0)
            put("Label", "")
            put("RemoteID", "")
            put("Service", 0)
            put("StartTimestamp", (card?.activeSinceMillis ?: 0L) / 1000L)
            put("Status", status(card))
            put("deviceID", 0)
        }
        return runCatching {
            context.sendBroadcast(Intent(ACTION).setPackage(TARGET).putExtra("CallState", payload.toString()))
            true
        }.onFailure { Log.w(TAG, "meter call broadcast failed", it) }
            .getOrDefault(false)
    }

    internal fun direction(card: CarPlayCallCard?): Int = when (card?.phase) {
        CarPlayCallCard.Phase.RINGING -> 1
        CarPlayCallCard.Phase.DIALING, CarPlayCallCard.Phase.ACTIVE -> 2
        null -> 0
    }

    internal fun status(card: CarPlayCallCard?): Int = when (card?.phase) {
        CarPlayCallCard.Phase.RINGING -> 2
        CarPlayCallCard.Phase.DIALING -> 1
        CarPlayCallCard.Phase.ACTIVE -> 4
        null -> 0
    }
}
