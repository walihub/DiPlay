package local.arcfox.phone

import android.content.*
import android.content.pm.PackageManager
import android.os.*
import java.time.Duration
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** Exercises the exact Dudu service source against captured vendor Binder contracts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhoneLeaseBinderTest {
    private val target = "12:34:56:78:9A:BC"
    private val other = "22:34:56:78:9A:BC"
    private lateinit var service: PhoneLeaseBinder
    private lateinit var app: Context
    private lateinit var packages: PackageManager
    private val connected = mutableSetOf<String>()
    private val requests = mutableListOf<Pair<Int, String?>>()
    private var delayDisconnect = false
    private var rejectDisconnect = false
    private var nativeSecondCall = false
    private var nativeFirstCall = false
    private var phoneConnected = false
    private val reported = mutableSetOf<Int>()
    private val lamps = mutableListOf<Int>()
    class Lamp(private val values: MutableList<Int>) {
        fun setIntProperty(property: Int, area: Int, value: Int) {
            assertEquals(557845456, property); assertEquals(0, area); values += value
        }
    }
    class Lifetime : Binder() {
        private val deaths = mutableListOf<IBinder.DeathRecipient>()
        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) { deaths += recipient }
        override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean = deaths.remove(recipient)
        fun die() { deaths.toList().forEach { it.binderDied() } }
    }
    private val vendor = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            data.enforceInterface("com.nforetek.bt.aidl.UiCommand")
            reply!!.writeNoException()
            when (code) {
                213 -> fail("phone leases must never change the dual-device setting")
                215 -> reply.writeStringList(connected.toList())
                95 -> reply.writeInt(if (data.readString() in connected) 1 else 0)
                98, 99 -> {
                    val address = data.readString(); requests += code to address
                    val accepted = if (code == 98) address !in connected else address in connected && !rejectDisconnect
                    if (code == 98) connected += address!!
                    else if (accepted && !delayDisconnect) connected.remove(address)
                    reply.writeInt(if (accepted) 1 else 0)
                }
                101, 103 -> {
                    val active = if (code == 101) nativeFirstCall else nativeSecondCall
                    reply.writeInt(if (active) 1 else 0)
                    if (active) {
                        reply.writeInt(1) // typed-list item present
                        reply.writeParcelable(null, 0)
                        reply.writeInt(1); reply.writeString("00000000-0000-0000-0000-000000000001")
                        reply.writeInt(0) // ACTIVE
                        reply.writeString(""); reply.writeInt(0); reply.writeInt(0)
                    }
                }
                else -> return false
            }
            return true
        }
    }
    private val phone = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            data.enforceInterface("com.adayo.phonelinkproxy.IPhoneLink")
            reply!!.writeNoException()
            when (code) {
                5 -> {
                    assertEquals(3, data.readInt()); reply.writeInt(1)
                    reply.writeByte(if (phoneConnected) 1 else 0); reply.writeByte(0)
                    reply.writeByte(if (reported.isEmpty()) 0 else 1)
                }
                47 -> {
                    val active = data.readInt() == 1; assertEquals(3, data.readInt()); val volume = data.readInt()
                    if (active) reported += volume else reported -= volume
                }
                else -> return false
            }
            return true
        }
    }
    @Before fun setup() {
        packages = mock(PackageManager::class.java)
        `when`(packages.getPackagesForUid(anyInt())).thenReturn(arrayOf("com.shihab.diplay"))
        `when`(packages.checkSignatures(anyString(), anyString())).thenReturn(PackageManager.SIGNATURE_MATCH)
        app = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getPackageName() = "com.dudu.autoui"
            override fun getPackageManager() = packages
            override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean {
                val component = intent.component!!
                connection.onServiceConnected(component, if (component.packageName == "com.adayo.phonelink") phone else vendor)
                return true
            }
            override fun unbindService(connection: ServiceConnection) {}
        }
        startService()
    }
    private fun startService() {
        service = PhoneLeaseBinder(app)
        val outputs = ReflectionHelpers.getField<Any>(service, "outputs")
        ReflectionHelpers.setField(outputs, "propertyManager", Lamp(lamps))
        service.start()
    }
    @After fun stop() { service.stop() }
    private fun packet() = Parcel.obtain().apply {
        writeInterfaceToken("arcfox.phone.lease.v1"); writeString("com.shihab.diplay")
    }
    private fun acquire(token: IBinder, address: String? = target): Long {
        val data = packet(); val reply = Parcel.obtain()
        try {
            data.writeStrongBinder(token); data.writeString(address)
            assertTrue(service.transact(1, data, reply, 0)); reply.readException(); return reply.readLong()
        } finally { data.recycle(); reply.recycle() }
    }
    private fun transaction(code: Int, lease: Long, phase: Int? = null): Int {
        val data = packet(); val reply = Parcel.obtain()
        try {
            data.writeLong(lease); phase?.let { data.writeInt(it) }
            assertTrue(service.transact(code, data, reply, 0)); reply.readException(); return reply.readInt()
        } finally { data.recycle(); reply.recycle() }
    }
    private fun advance() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30)) }
    @Test fun secondaryTargetOnlyIsReleasedAndRestoredOnDeath() {
        connected += listOf(target, other)
        val token = Lifetime(); val lease = acquire(token)
        assertTrue(lease > 0); assertEquals(1, transaction(2, lease))
        assertEquals(setOf(other), connected)
        token.die(); advance()
        assertEquals(setOf(target, other), connected)
        assertTrue(requests.all { it.second == target })
    }
    @Test fun lostAcquireReplyIsIdempotentAndNewSessionCannotBeReleasedByOldLease() {
        connected += target
        val token = Lifetime(); val first = acquire(token)
        assertEquals(first, acquire(token))
        assertEquals(1, requests.count { it.first == 99 })
        assertEquals(1, transaction(4, first)); advance()
        val next = acquire(Lifetime())
        assertTrue(next > first)
        assertEquals(0, transaction(4, first)); assertEquals(1, transaction(2, next))
    }
    @Test fun deathDuringPendingDisconnectRetainsRecoveryUntilDelayedDisconnectCompletes() {
        connected += target; delayDisconnect = true
        val token = Lifetime(); val lease = acquire(token)
        assertTrue(lease > 0); assertEquals(0, transaction(2, lease))
        token.die()
        // The accepted vendor disconnect finishes after Binder death.
        connected -= target
        advance()
        assertTrue("late disconnect must not strand the target", target in connected)
    }
    @Test fun secondPhoneNativeCallPreventsLampOverwrite() {
        connected += target
        val lease = acquire(Lifetime()); nativeSecondCall = true
        transaction(3, lease, 1)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("native phone owns lamp", lamps.isEmpty())
    }
    @Test fun untrustedUidCannotAcquireOrDisconnect() {
        connected += target
        `when`(packages.getPackagesForUid(anyInt())).thenReturn(arrayOf("untrusted.app"))
        assertEquals(0, acquire(Lifetime())); assertTrue(requests.isEmpty())
    }
    @Test fun signatureMismatchCannotAcquire() {
        connected += target
        `when`(packages.checkSignatures(anyString(), anyString())).thenReturn(PackageManager.SIGNATURE_NO_MATCH)
        assertEquals(0, acquire(Lifetime())); assertTrue(requests.isEmpty())
    }
    @Test fun ringingActiveAndReleaseClearOnlyOwnedOutputs() {
        connected += target
        val lease = acquire(Lifetime())
        assertEquals(1, transaction(3, lease, 1)); Shadows.shadowOf(Looper.getMainLooper()).idle(); assertEquals(setOf(5), reported)
        assertEquals(1, transaction(3, lease, 3)); Shadows.shadowOf(Looper.getMainLooper()).idle(); assertEquals(setOf(2), reported)
        assertEquals(1, transaction(4, lease)); advance()
        assertTrue(reported.isEmpty()); assertEquals(1, lamps.first()); assertTrue(2 in lamps); assertEquals(0, lamps.last())
    }
    @Test fun hfpThatReconnectsDuringLeaseIsRestoredOnRelease() {
        val lease = acquire(Lifetime()) // not connected initially
        connected += target
        assertEquals(0, transaction(2, lease))
        assertFalse(target in connected)
        assertEquals(1, transaction(4, lease)); advance()
        assertTrue(target in connected)
    }
    @Test fun duplicateReleaseIsAcknowledgedWithoutNewAcquire() {
        connected += target
        val lease = acquire(Lifetime())
        assertEquals(1, transaction(4, lease))
        val before = requests.size
        assertEquals(1, transaction(4, lease))
        assertEquals(before, requests.size)
    }
    @Test fun serviceRestartKeepsPendingDisconnectRecovery() {
        // Simulate process death: only persisted state survives, not onDestroy/release callbacks.
        val prefs = app.getSharedPreferences("arcfox_phone_lease", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pending", true).putString("target", target)
            .putBoolean("restore", true).putBoolean("disconnectIssued", true)
            .putLong("generation", 12).commit()
        service.stop()
        connected += target
        startService()
        connected -= target // old asynchronous disconnect completes after service restart
        advance()
        assertTrue(target in connected)
    }
    @Test fun nativeCallBeforeAcquireIsNotDisconnected() {
        connected += target; nativeFirstCall = true
        assertEquals(0, acquire(Lifetime())); assertTrue(requests.isEmpty())
    }

    @Test fun idleHostDoesNotTouchPhoneAndSessionsNeverChangeDualDeviceSetting() {
        advance()
        assertTrue(requests.isEmpty()); assertTrue(lamps.isEmpty())
        assertTrue(reported.isEmpty())
        val token = Lifetime(); val lease = acquire(token)
        assertTrue(lease > 0)
        assertEquals(lease, acquire(token))
    }

    @Test fun idleLeaseDoesNotClearUnownedPhoneOutputs() {
        connected += target
        val lease = acquire(Lifetime())
        assertEquals(1, transaction(3, lease, 0))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(lamps.isEmpty())
        assertFalse(app.getSharedPreferences("arcfox_phone_lease", Context.MODE_PRIVATE).getBoolean("touched", false))
    }
    @Test fun nativeSecondPhoneTemporarilyRevokesPhoneOwnership() {
        connected += target
        val lease = acquire(Lifetime())
        assertEquals(1, transaction(2, lease))
        nativeSecondCall = true
        assertEquals(0, transaction(2, lease))
        nativeSecondCall = false
        assertEquals(1, transaction(2, lease))
    }

    @Test fun recoveryDoesNotExpireBeforeAQueuedDisconnectActuallyRuns() {
        connected += target; delayDisconnect = true
        val token = Lifetime(); assertTrue(acquire(token) > 0)
        token.die()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(40))
        assertTrue("uncertain disconnect must remain recoverable",
            app.getSharedPreferences("arcfox_phone_lease", Context.MODE_PRIVATE).getBoolean("pending", false))
        connected -= target
        advance()
        assertTrue(target in connected)
    }

    private fun hfpEvent(address: String, state: Int) {
        val device = android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
        app.sendBroadcast(Intent("android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED")
            .putExtra("android.bluetooth.device.extra.DEVICE", device)
            .putExtra("android.bluetooth.profile.extra.STATE", state))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun rapidDisconnectReconnectBroadcastCompletesRecovery() {
        connected += target; delayDisconnect = true
        val token = Lifetime(); assertTrue(acquire(token) > 0); token.die()
        // Both transitions occur between polling intervals; broadcasts retain the evidence.
        hfpEvent(target, 0)
        hfpEvent(target, 2)
        advance()
        assertFalse(app.getSharedPreferences("arcfox_phone_lease", Context.MODE_PRIVATE).getBoolean("pending", false))
    }
    @Test fun otherPhonesDisconnectedEventCannotCompleteOurPendingRecovery() {
        connected += listOf(target, other); delayDisconnect = true
        val token = Lifetime(); assertTrue(acquire(token) > 0); token.die()
        hfpEvent(other, 0); hfpEvent(other, 2); advance()
        assertTrue(app.getSharedPreferences("arcfox_phone_lease", Context.MODE_PRIVATE).getBoolean("pending", false))
    }
    @Test fun temporarilyRejectedDisconnectCanRetryWithoutPermanentlyBlockingSession() {
        connected += target; rejectDisconnect = true
        val token = Lifetime(); val lease = acquire(token)
        assertTrue("keep durable lease while vendor is busy", lease > 0)
        assertEquals(0, transaction(2, lease))
        rejectDisconnect = false
        transaction(2, lease)
        assertEquals(1, transaction(2, lease))
        token.die(); advance(); assertTrue(target in connected)
    }

    @Test fun rejectedDisconnectThenImmediateCloseDoesNotBlockNextSession() {
        connected += target; rejectDisconnect = true
        val token = Lifetime(); assertTrue(acquire(token) > 0)
        token.die(); advance()
        assertTrue(target in connected)
        assertFalse("a definite rejection cannot leave a nonexistent disconnect pending",
            app.getSharedPreferences("arcfox_phone_lease", Context.MODE_PRIVATE).getBoolean("pending", false))
        rejectDisconnect = false
        assertTrue(acquire(Lifetime()) > 0)
    }

    @Test fun wiredSessionWithoutAddressCanOwnOutputsOnlyWhenNoHfpExists() {
        val lease = acquire(Lifetime(), null)
        assertTrue(lease > 0); assertEquals(1, transaction(2, lease))
        connected += other
        assertEquals(0, transaction(2, lease))
        transaction(4, lease); advance()
        assertTrue(requests.isEmpty())
    }
    @Test fun unknownTargetNeverDisconnectsAnExistingPhone() {
        connected += other
        val lease = acquire(Lifetime(), null)
        if (lease != 0L) assertEquals(0, transaction(2, lease))
        assertTrue(requests.isEmpty()); assertEquals(setOf(other), connected)
    }

}
