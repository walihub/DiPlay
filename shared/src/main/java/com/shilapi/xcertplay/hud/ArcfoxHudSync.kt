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
    private var sent: Guidance? = null
    private var ownsHud = false

    fun available(): Boolean = Build.FINGERPRINT.startsWith("AAOP_COCKPIT/")

    fun attach(context: Context) {
        if (!available()) return
        worker.execute {
            this.context = context.applicationContext
            if (staleCheck == null) staleCheck = worker.scheduleAtFixedRate({
                transmit(route.currentApple()?.let(::guidance)?.takeIf { it.icon != 0 })
            }, 1, 1, TimeUnit.SECONDS)
        }
    }

    fun onFrame(frame: Iap2Frame) {
        if (!available()) return
        worker.execute {
            val change = route.accept(frame.messageId, frame.payload)
            if (change == BydHudRouteChange.NONE) return@execute
            val next = route.currentApple()?.let(::guidance)?.takeIf { it.icon != 0 }
            transmit(next)
        }
    }

    fun end() {
        if (!available()) return
        worker.execute {
            route.clear()
            transmit(null)
            sent = null
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
        if (next == sent || (next == null && !ownsHud)) return
        try {
            val manager = properties()
            val integer: Method = manager.javaClass.getMethod("setIntProperty",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            fun set(id: Int, value: Int) = integer.invoke(manager, id, 0, value)
            if (next == null) {
                set(STATUS, 1)
                set(ICON, 0)
                set(DISTANCE, 0)
                set(BAR, 0)
                ownsHud = false
            } else {
                set(ICON, next.icon)
                set(DISTANCE, next.distance.coerceIn(0, 2_000_000))
                set(BAR, next.bar.coerceIn(0, 100))
                roadPacket(next.road)?.let { bytes ->
                    manager.javaClass.getMethod("setProperty", Class::class.java,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Any::class.java)
                        .invoke(manager, ByteArray::class.java, ROAD_TEXT, 0, bytes)
                }
                set(STATUS, 0)
                ownsHud = true
            }
            sent = next
        } catch (error: Exception) {
            propertyManager = null
            Log.w(TAG, "HUD output unavailable", error)
        }
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
