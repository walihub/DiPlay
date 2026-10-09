package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import java.lang.reflect.Method
import java.nio.charset.Charset
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Platform-signed DiPlay writes the same Arcfox vehicle properties as NaviService. */
internal object ArcfoxHudSync {
    private const val TAG = "DiPlay-ArcfoxHUD"
    private const val PERMISSION = "android.car.permission.CAR_VENDOR_EXTENSION"
    private const val STATUS = 557853441
    private const val ICON = 557853245
    private const val DISTANCE = 557853246
    private const val BAR = 557853247
    private const val ROAD_TEXT = 560997119

    internal data class Guidance(val icon: Int, val distance: Int, val road: String, val bar: Int = 0)

    private val route = BydHudRouteState()
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "diplay-arcfox-hud").apply { isDaemon = true }
    }
    private var staleCheck: ScheduledFuture<*>? = null
    private var context: Context? = null
    private var car: Any? = null
    private var propertyManager: Any? = null
    private val writer = ArcfoxHudWriter(::writeProperties)
    private var writeStage = "idle"
    private var routeFrames = 0L
    private var maneuverFrames = 0L
    private var lastRouteAt = 0L
    private var lastManeuverAt = 0L
    private var lastReason = "not_started"
    private var lastReadback = "not_read"
    private val events = ArrayDeque<String>()
    @Volatile private var report = "not_started"

    fun diagnosticReport(): String = report

    private fun event(message: String) {
        events.addLast("${System.currentTimeMillis()} $message")
        while (events.size > 64) events.removeFirst()
        Log.i(TAG, message)
    }

    private fun refreshReport() {
        report = "routeFrames=$routeFrames maneuverFrames=$maneuverFrames " +
            "lastRouteAt=$lastRouteAt lastManeuverAt=$lastManeuverAt " +
            "reason=$lastReason stage=$writeStage pendingOutput=${writer.mayHaveOutput} readback=$lastReadback\n" +
            events.joinToString("\n")
    }

    private fun publish(readback: Boolean = false) {
        val maneuver = route.currentApple()
        val next = maneuver?.let(::guidance)?.takeIf { it.icon != 0 }
        val reason = if (maneuver != null && next == null) "unsupported_apple_type=${maneuver.type}"
            else route.visibilityReason()
        if (reason != lastReason) { lastReason = reason; event("visibility=$reason") }
        transmit(next)
        if (readback && next != null) probeVehicle(next)
        refreshReport()
    }

    fun available(): Boolean = Build.FINGERPRINT.startsWith("AAOP_COCKPIT/")

    fun attach(context: Context) {
        if (!available()) return
        worker.execute {
            this.context = context.applicationContext
            if (staleCheck == null) staleCheck = worker.scheduleAtFixedRate({
                publish(readback = true)
            }, 1, 1, TimeUnit.SECONDS)
        }
    }

    fun onFrame(frame: Iap2Frame) {
        if (!available()) return
        worker.execute {
            if (frame.messageId == BydHudRouteState.ROUTE_GUIDANCE_UPDATE) {
                routeFrames++; lastRouteAt = System.currentTimeMillis()
            } else if (frame.messageId == BydHudRouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE) {
                maneuverFrames++; lastManeuverAt = System.currentTimeMillis()
            }
            route.accept(frame.messageId, frame.payload)
            publish()
        }
    }

    fun end() {
        if (!available()) return
        worker.execute {
            route.clear()
            transmit(null)
            event("session_end")
            lastReason = "session_end"
            refreshReport()
            staleCheck?.cancel(false)
            staleCheck = null
            car?.let { connection ->
                runCatching { connection.javaClass.getMethod("disconnect").invoke(connection) }
            }
            car = null
            propertyManager = null
        }
    }

    private fun properties(): Any {
        propertyManager?.let { return it }
        val app = context ?: error("Arcfox HUD not attached")
        check(app.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            "CAR_VENDOR_EXTENSION not granted; a matching platform signature is required"
        }
        val carClass = Class.forName("android.car.Car")
        val connectedCar = car ?: carClass.getMethod("createCar", Context::class.java).invoke(null, app)
            ?: error("Car service unavailable")
        car = connectedCar
        val connected = carClass.getMethod("isConnected")
        val connecting = carClass.getMethod("isConnecting")
        if (connected.invoke(connectedCar) != true && connecting.invoke(connectedCar) != true)
            carClass.getMethod("connect").invoke(connectedCar)
        for (attempt in 0 until 60) {
            if (connected.invoke(connectedCar) == true) break
            Thread.sleep(50L)
        }
        check(connected.invoke(connectedCar) == true) { "Car service did not connect" }
        val manager = carClass.getMethod("getCarManager", String::class.java).invoke(connectedCar, "property")
            ?: error("Car property service unavailable")
        propertyManager = manager
        return manager
    }

    private fun transmit(next: Guidance?) {
        try {
            if (writer.transmit(next)) event("write_ok active=${next != null} icon=${next?.icon ?: 0}")
        } catch (error: Exception) {
            propertyManager = null
            val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
            event("write_failed stage=$writeStage cause=${cause.javaClass.simpleName}")
            Log.w(TAG, "HUD output unavailable stage=$writeStage", error)
        }
    }

    /** Read only: detect whether the VHAL values still match; never reassert competing output. */
    private fun probeVehicle(expected: Guidance) {
        val manager = propertyManager ?: return
        var stage = "getIntProperty"
        val observation = try {
            val read = manager.javaClass.getMethod("getIntProperty",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            fun get(id: Int): Int {
                stage = "property=$id"
                return read.invoke(manager, id, 0) as Int
            }
            val status = get(STATUS)
            val icon = get(ICON)
            val distance = get(DISTANCE)
            val matches = status == 0 && icon == expected.icon &&
                distance == expected.distance.coerceIn(0, 2_000_000)
            "${if (matches) "match" else "mismatch"} status=$status icon=$icon distance=$distance"
        } catch (error: Exception) {
            val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
            "unavailable $stage cause=${cause.javaClass.simpleName}"
        }
        if (observation != lastReadback) {
            lastReadback = observation
            event("readback=$observation")
        }
    }

    private fun writeProperties(next: Guidance?) {
        writeStage = "connect_property_service"
        val manager = properties()
        val integer: Method = manager.javaClass.getMethod("setIntProperty",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        fun set(id: Int, value: Int) {
            writeStage = "property=$id"
            integer.invoke(manager, id, 0, value)
        }
        if (next == null) {
            set(STATUS, 1); set(ICON, 0); set(DISTANCE, 0); set(BAR, 0)
        } else {
            set(ICON, next.icon)
            set(DISTANCE, next.distance.coerceIn(0, 2_000_000))
            set(BAR, next.bar.coerceIn(0, 100))
            roadPacket(next.road)?.let { bytes ->
                writeStage = "property=$ROAD_TEXT"
                manager.javaClass.getMethod("setProperty", Class::class.java,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Any::class.java)
                    .invoke(manager, ByteArray::class.java, ROAD_TEXT, 0, bytes)
            }
            set(STATUS, 0)
        }
        writeStage = "complete"
    }

    /** NaviService sends 03 18 07, one-byte GBK payload length, then the road bytes. */
    internal fun roadPacket(road: String): ByteArray? {
        val text = road.replace("\\s+".toRegex(), "").toByteArray(Charset.forName("GBK"))
        if (text.isEmpty() || text.size > 251) return null
        return byteArrayOf(3, 24, 7, text.size.toByte()) + text
    }

    internal fun guidance(maneuver: BydAppleManeuver): Guidance {
        val frame = BydClusterFrame.from(maneuver)
        return Guidance(icon(frame.icon, frame.roundaboutExit), frame.distanceMeters, frame.road)
    }

    /** AMap icon -> the HUD value used by NaviService's ClusterHudManger. */
    internal fun icon(amap: Int, exit: Int): Int = when (amap) {
        2 -> 7; 3 -> 3; 4 -> 8; 5 -> 2; 6 -> 6; 7 -> 4; 8 -> 5; 9 -> 1
        11 -> 87 + (exit.coerceIn(1, 10) - 1)
        12 -> 97 + (exit.coerceIn(1, 10) - 1)
        15 -> 24; 17 -> 80; 18 -> 81; 19 -> 82
        else -> 0
    }
}

/** Tracks successful writes, invalidating the cache after even a partial vehicle-property failure. */
internal class ArcfoxHudWriter(private val output: (ArcfoxHudSync.Guidance?) -> Unit) {
    private var valid = false
    private var sent: ArcfoxHudSync.Guidance? = null
    var mayHaveOutput = false
        private set

    fun transmit(next: ArcfoxHudSync.Guidance?): Boolean {
        if ((valid && next == sent) || (next == null && !mayHaveOutput)) return false
        // A failed multi-property write may already have changed some properties.
        if (next != null) mayHaveOutput = true
        try {
            output(next)
            sent = next
            valid = true
            mayHaveOutput = next != null
            return true
        } catch (error: Exception) {
            valid = false
            throw error
        }
    }
}
