package com.shilapi.xcertplay.vehicle.arcfox

import com.shilapi.xcertplay.*

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Own only the menuwidget slot requested by this DiPlay host, never a previous host's slot. */
internal object ArcfoxClusterLink {
    private val lease = ArcfoxClusterLinkLease()
    private val writer = ArcfoxClusterLinkWriter()
    private val transport = ArcfoxFgeTransport()
    private val executor by lazy {
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "DiPlay-ArcfoxCluster").apply { isDaemon = true }
        }.also { it.scheduleWithFixedDelay(::tick, 1, 1, TimeUnit.SECONDS) }
    }

    @Synchronized fun request(host: Any, shown: Boolean) {
        if (!ArcfoxClusterDisplay.isVehicle()) return
        if (!lease.request(host, shown)) return
        executor.execute(::tick)
    }

    @Synchronized fun release(host: Any) {
        if (!lease.release(host)) return
        executor.execute(::tick)
    }

    private fun tick() {
        val desired = synchronized(this) { lease.visible }
        writer.sync(desired, SystemClock.elapsedRealtime(), transport::send)
    }

    fun diagnosticReport(): String = writer.report()
}

internal class ArcfoxClusterLinkLease {
    private var owner: Any? = null
    var visible = false
        private set

    fun request(host: Any, shown: Boolean): Boolean {
        if (shown) owner = host else if (owner !== host) return false
        if (visible == shown) return false
        visible = shown
        return true
    }

    fun release(host: Any): Boolean {
        if (owner !== host) return false
        visible = false
        owner = null
        return true
    }
}

/** Successful transactions are cached; failed writes and a possibly partial show remain retryable. */
internal class ArcfoxClusterLinkWriter {
    private var mayOwnSlot = false
    private var lastShown = false
    private var lastSuccess = Long.MIN_VALUE
    private var attempts = 0
    private var failures = 0
    private var error: String? = null

    @Synchronized fun sync(shown: Boolean, now: Long, send: (Boolean) -> Unit) {
        if (!shown && !mayOwnSlot) return
        if (shown && lastShown && now - lastSuccess < 5_000) return
        if (shown) mayOwnSlot = true
        attempts++
        try {
            send(shown)
            lastShown = shown
            mayOwnSlot = shown
            lastSuccess = now
            error = null
            Log.i("DiPlay-ArcfoxCluster", "menuwidget status=${if (shown) 1 else 2} transaction succeeded")
        } catch (failure: Exception) {
            // A service restart must not leave the successful-state cache suppressing retries.
            lastShown = false
            failures++
            val cause = (failure as? java.lang.reflect.InvocationTargetException)?.targetException ?: failure
            error = cause.javaClass.simpleName + ": " + cause.message.orEmpty().take(180)
            Log.w("DiPlay-ArcfoxCluster", "menuwidget status=${if (shown) 1 else 2} failed: $error")
        }
    }

    @Synchronized fun report(): String =
        "menuwidget id=224; requested region=(99,206,490,382); mayOwnSlot=$mayOwnSlot; " +
            "lastShown=$lastShown; attempts=$attempts; failures=$failures; lastSuccess=$lastSuccess; error=${error ?: "none"}; " +
            "transaction success does not confirm instrument pixels"
}

/** Minimal HIDL client, using the APK-observed parcel layout without depending on its vendor SDK. */
internal class ArcfoxFgeTransport(private val classes: (String) -> Class<*> = { Class.forName(it) }) {
    fun send(shown: Boolean) {
        val binderClass = classes("android.os.HwBinder")
        val parcelClass = classes("android.os.HwParcel")
        val blobClass = classes("android.os.HwBlob")
        val remoteClass = classes("android.os.IHwBinder")
        val remote = binderClass.getMethod("getService", String::class.java, String::class.java,
            Boolean::class.javaPrimitiveType).invoke(null, DESCRIPTOR, "default", false)
            ?: error("Arcfox fgecomservice is unavailable")
        val request = parcelClass.getConstructor().newInstance()
        val reply = parcelClass.getConstructor().newInstance()
        try {
            parcelClass.getMethod("writeInterfaceToken", String::class.java).invoke(request, DESCRIPTOR)
            val blob = blobClass.getConstructor(Int::class.javaPrimitiveType).newInstance(40)
            blobClass.getMethod("putInt32", Long::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(blob, 0L, 224)
            val putString = blobClass.getMethod("putString", Long::class.javaPrimitiveType, String::class.java)
            putString.invoke(blob, 8L, payload(shown))
            putString.invoke(blob, 24L, "")
            parcelClass.getMethod("writeBuffer", blobClass).invoke(request, blob)
            remoteClass.getMethod("transact", Int::class.javaPrimitiveType, parcelClass, parcelClass,
                Int::class.javaPrimitiveType).invoke(remote, 1, request, reply, 0)
            parcelClass.getMethod("verifySuccess").invoke(reply)
        } finally {
            runCatching { parcelClass.getMethod("releaseTemporaryStorage").invoke(request) }
            runCatching { parcelClass.getMethod("release").invoke(request) }
            runCatching { parcelClass.getMethod("release").invoke(reply) }
        }
    }

    companion object {
        const val DESCRIPTOR = "android.hardware.fgecomservice@1.0::IFgeComService"
        fun payload(shown: Boolean): String =
            "{\"command\":{\"id\":\"Navi\",\"sub\":\"menuwidget\"},\"content\":{\"status\":${if (shown) 1 else 2}}}"
    }
}
