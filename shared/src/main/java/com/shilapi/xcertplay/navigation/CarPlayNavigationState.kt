package com.shilapi.xcertplay.navigation

import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/** Session-local route activity, independent of maneuver details and their expiry deadlines. */
internal class CarPlayNavigationState {
    private var active = false
    @Synchronized fun snapshot(): Boolean = active

    /** Only complete RouteGuidanceUpdate TLVs can change visibility. NoRouteSet/Arrived end it. */
    @Synchronized fun accept(frame: Iap2Frame): Boolean? {
        if (frame.messageId != 0x5201) return null
        val data = frame.payload
        var offset = 0
        var state: Int? = null
        var currentManeuver = false
        while (offset < data.size) {
            if (data.size - offset < 4) return null
            val length = ((data[offset].toInt() and 255) shl 8) or (data[offset + 1].toInt() and 255)
            if (length < 4 || length > data.size - offset) return null
            val type = ((data[offset + 2].toInt() and 255) shl 8) or (data[offset + 3].toInt() and 255)
            if (type == 1 && length >= 5) state = data[offset + 4].toInt() and 255
            if (type == 0x0d && length >= 6) currentManeuver = true
            offset += length
        }
        val value = state ?: if (currentManeuver) 1 else return null
        // Same route-state interpretation as the existing HUD parser; empty maneuver lists and
        // missing turn metadata do not end a route or hide a map during recalculation.
        val next = value != 0 && value != 2
        if (next == active) return null
        active = next
        return next
    }

    @Synchronized fun clear(): Boolean? {
        if (!active) return null
        active = false
        return false
    }
}
