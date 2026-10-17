package com.shilapi.xcertplay.vehicle.arcfox

import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.*
import com.shilapi.xcertplay.hud.BydCarPlayCall
import com.shilapi.xcertplay.hud.CarPlayCallCard
import java.io.Closeable

/** Opt-in, read-only observation. Never disconnects HFP or writes OEM phone state. */
object ArcfoxNativePhone {
    private const val KEY = "arcfox_native_phone_priority"
    @Volatile private var identity: Pair<Any, String>? = null
    fun enabled(context: Context) = context.getSharedPreferences("xcertplay_airplay", 0).getBoolean(KEY, false)
    fun save(context: Context, enabled: Boolean) { context.getSharedPreferences("xcertplay_airplay", 0).edit().putBoolean(KEY, enabled).apply() }
    @Synchronized fun connected(owner: Any, address: String) { identity = owner to address }
    @Synchronized fun disconnected(owner: Any) { if (identity?.first === owner) identity = null }
    internal fun address(): String? = identity?.second
}

internal data class NativePhoneCall(val address: String?, val state: Int, val identity: String = "call")
internal data class NativePhoneAudio(val calls: List<NativePhoneCall>, val audioState: Int)
internal data class PhoneYield(val call: Boolean = false, val ring: Boolean = false)

/** Once Bluetooth owns this call, a manual move to the handset must not restart CarPlay capture. */
internal class NativePhoneOwnership {
    private var yielded = false
    private var phone: String? = null
    private var callIds = emptySet<String>()
    fun update(address: String?, phase: CarPlayCallCard.Phase?, native: NativePhoneAudio?): PhoneYield {
        if (address == null || phase == null || phone != address) {
            yielded = false
            phone = address
            callIds = emptySet()
        }
        if (address == null || phase == null) return PhoneYield()
        val calls = native?.calls?.filter { it.state != 7 }
        val ids = calls.orEmpty().map { it.identity }.toSet()
        if (native != null && (ids.isEmpty() || (callIds.isNotEmpty() && ids.intersect(callIds).isEmpty()))) yielded = false
        if (native != null) callIds = ids
        val matched = !calls.isNullOrEmpty() && calls.all { it.address.equals(address, true) }
        if (matched && native?.audioState == 140 && calls!!.any { it.state in 0..3 }) yielded = true
        val ringing = phase == CarPlayCallCard.Phase.RINGING && matched && calls!!.any { it.state == 4 || it.state == 5 }
        return PhoneYield(yielded, ringing || yielded)
    }
}

/** The parcel layout is from the captured nFore UiCommand/NfHfpClientCall, not Android's HFP client API. */
internal class NativePhoneReader(private val binder: IBinder) {
    private fun <T> read(code: Int, body: (Parcel) -> T): T {
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("com.nforetek.bt.aidl.UiCommand")
            check(binder.transact(code, data, reply, 0))
            reply.readException()
            return body(reply)
        } finally { data.recycle(); reply.recycle() }
    }
    private fun calls(): List<NativePhoneCall> = read(101) { reply ->
            val count = reply.readInt()
            check(count in 0..16)
            buildList {
                repeat(count) {
                    check(reply.readInt() == 1)
                    @Suppress("DEPRECATION")
                    val device = reply.readParcelable<BluetoothDevice>(BluetoothDevice::class.java.classLoader)
                    val id = reply.readInt(); val uuid = reply.readString()
                    val state = reply.readInt()
                    reply.readString(); reply.readInt(); reply.readInt() // number, multiparty, outgoing
                    add(NativePhoneCall(device?.address, state, uuid ?: id.toString()))
                }
            }
        }
    fun snapshot(): NativePhoneAudio {
        val before = calls()
        val audio = read(97) { it.readInt() }
        val after = calls()
        check(before == after) { "Phone state changed during snapshot" }
        return NativePhoneAudio(after, audio)
    }
}

internal class NativePhoneMonitor(context: Context, private val changed: (PhoneYield) -> Unit) : Closeable, ServiceConnection {
    private val app = context.applicationContext
    private val thread = HandlerThread("DiPlay-native-phone-observer").apply { start() }
    private val handler = Handler(thread.looper)
    private val ownership = NativePhoneOwnership()
    @Volatile private var stopped = false
    @Volatile private var reader: NativePhoneReader? = null
    private var bound = false
    private var last = PhoneYield()
    private val poll = object : Runnable {
        override fun run() {
            if (stopped) return
            val phase = BydCarPlayCall.current()?.phase
            val snapshot = if (phase == null) null else runCatching { reader?.snapshot() }.getOrNull()
            val next = ownership.update(ArcfoxNativePhone.address(), phase, snapshot)
            if (next != last) { last = next; changed(next) }
            if (!stopped) handler.postDelayed(this, 250)
        }
    }
    init {
        bound = runCatching {
            app.bindService(Intent("com.nforetek.bt.CUSTOMER_SERVICE").setClassName(
                "com.nforetek.bt.customer.service", "com.nforetek.bt.customer.service.BtService"), this, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        handler.post(poll)
    }
    override fun onServiceConnected(name: ComponentName, service: IBinder) { if (!stopped) reader = NativePhoneReader(service) }
    override fun onServiceDisconnected(name: ComponentName) { reader = null }
    override fun close() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        if (bound) runCatching { app.unbindService(this) }
        bound = false; reader = null
        thread.quitSafely()
    }
}
