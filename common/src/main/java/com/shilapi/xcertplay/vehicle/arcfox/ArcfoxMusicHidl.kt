package com.shilapi.xcertplay.vehicle.arcfox

/** Extracted IFgeComService Proxy ABI: transaction 1, synchronous, Message size 40.
 * Does not register/unregister any client, especially the wheel subscription at service ID 14.
 */
internal class ArcfoxMusicHidl {
    private var remote: Any? = null

    fun send(json: String): Boolean {
        var request: Any? = null
        var reply: Any? = null
        return try {
            val parcel = Class.forName("android.os.HwParcel")
            val blob = Class.forName("android.os.HwBlob")
            val binder = Class.forName("android.os.IHwBinder")
            val endpoint = remote ?: Class.forName("android.os.HwBinder")
                .getMethod("getService", String::class.java, String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, SERVICE, "default", false)?.also { remote = it } ?: return false
            request = parcel.getConstructor().newInstance()
            reply = parcel.getConstructor().newInstance()
            parcel.getMethod("writeInterfaceToken", String::class.java).invoke(request, SERVICE)
            val data = blob.getConstructor(Int::class.javaPrimitiveType).newInstance(40)
            blob.getMethod("putInt32", Long::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(data, 0L, 114)
            blob.getMethod("putString", Long::class.javaPrimitiveType, String::class.java)
                .invoke(data, 8L, json)
            blob.getMethod("putString", Long::class.javaPrimitiveType, String::class.java)
                .invoke(data, 24L, "")
            parcel.getMethod("writeBuffer", blob).invoke(request, data)
            binder.getMethod("transact", Int::class.javaPrimitiveType, parcel, parcel, Int::class.javaPrimitiveType)
                .invoke(endpoint, 1, request, reply, 0)
            parcel.getMethod("verifySuccess").invoke(reply)
            true
        } catch (_: Exception) {
            // Includes absent HAL, permission/SELinux rejection, transaction errors and binder death.
            remote = null
            false
        } finally {
            request?.let { runCatching { it.javaClass.getMethod("releaseTemporaryStorage").invoke(it) } }
            reply?.let { runCatching { it.javaClass.getMethod("release").invoke(it) } }
        }
    }

    companion object { private const val SERVICE = "android.hardware.fgecomservice@1.0::IFgeComService" }
}
