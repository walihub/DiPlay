package com.shilapi.xcertplay.vehicle.arcfox

import com.shilapi.xcertplay.*

import android.util.Log
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Passive OEM subscription shared by all hosts; no focus requirement and no OEM output. */
internal object ArcfoxWheelZoom {
    private const val TAG = "DiPlay-ArcfoxWheel"
    private val target = ArcfoxWheelZoomTarget()
    private var transport: ArcfoxWheelHidl? = null
    private var lastConnection: Boolean? = null
    private val worker by lazy {
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "DiPlay-ArcfoxWheel").apply { isDaemon = true }
        }.also { it.scheduleWithFixedDelay(::connect, 30, 30, TimeUnit.SECONDS) }
    }

    fun update(owner: Any, controller: com.shilapi.xcertplay.orchestration.CarPlayController?, shown: Boolean) {
        if (!ArcfoxClusterDisplay.isVehicle()) return
        if (!shown || controller == null) { release(owner); return }
        if (target.attach(owner, controller::dashboardMapRoute, controller::zoomDashboardMap))
            worker.execute(::connect)
    }

    fun release(owner: Any) { target.detach(owner) }

    private fun connect() {
        if (!target.attached()) return
        try {
            val client = transport ?: ArcfoxWheelHidl { json ->
                try {
                    target.prepare(json)?.let { delivery -> worker.execute {
                        Log.i(TAG, "instrument zoom queued=${delivery()}")
                    } }
                } catch (error: Exception) { Log.w(TAG, "invalid vehicle control", error) }
            }.also { transport = it }
            val connected = client.ensureConnected()
            if (connected != lastConnection) Log.i(TAG, "passive subscription registered=$connected serviceId=14")
            lastConnection = connected
        } catch (error: Exception) {
            if (lastConnection != false) Log.w(TAG, "vehicle subscription unavailable", error)
            lastConnection = false
        } catch (error: LinkageError) {
            // Generic Android/emulators may not have the hidden vehicle HIDL framework.
            if (lastConnection != false) Log.w(TAG, "vehicle HIDL framework unavailable", error)
            lastConnection = false
        }
    }
}

/** Direction-only CarPlay API: absolute AMap zoom levels/defaults must not become guessed steps. */
internal object ArcfoxWheelZoomCommand {
    fun decode(json: String): Boolean? {
        val root = JSONObject(json)
        val command = root.optJSONObject("command") ?: return null
        if (!"Navi".equals(command.optString("id"), ignoreCase = true) ||
            command.optString("sub") != "guidecontrol") return null
        val data = root.optJSONObject("content") ?: return null
        if (data.optBoolean("zoomDefault", false)) return null
        return when (data.optString("zoom")) { "up" -> true; "down" -> false; else -> null }
    }
}

/** A queued event belongs to its host and physical stream, never a replacement route. */
internal class ArcfoxWheelZoomTarget {
    private var owner: Any? = null
    private var route: () -> Any? = { null }
    private var zoom: (Boolean) -> Boolean = { false }
    private var generation = 0L

    @Synchronized fun attach(host: Any, currentRoute: () -> Any?, send: (Boolean) -> Boolean): Boolean {
        val changed = owner !== host
        if (changed) generation++
        owner = host
        route = currentRoute
        zoom = send
        return changed
    }
    @Synchronized fun detach(host: Any) {
        if (owner !== host) return
        owner = null
        generation++
        route = { null }
        zoom = { false }
    }
    @Synchronized fun attached(): Boolean = owner != null
    @Synchronized fun prepare(json: String): (() -> Boolean)? {
        val direction = ArcfoxWheelZoomCommand.decode(json) ?: return null
        if (owner == null) return null
        val capturedRoute = route() ?: return null
        val capturedGeneration = generation
        return { synchronized(this) {
            owner != null && generation == capturedGeneration && route() == capturedRoute && zoom(direction)
        } }
    }
}
