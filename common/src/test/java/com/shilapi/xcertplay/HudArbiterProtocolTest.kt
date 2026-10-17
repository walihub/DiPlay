package com.shilapi.xcertplay

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.*
import local.arcfox.hud.HudArbiter
import local.arcfox.hud.HudSelection
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class HudArbiterProtocolTest {
    private val phone = "com.shihab.diplay"
    private lateinit var packages: PackageManager
    private val worker: Handler get() = ReflectionHelpers.getStaticField(HudArbiter::class.java, "worker")
    private val selection: HudSelection get() = ReflectionHelpers.getStaticField(HudArbiter::class.java, "selection")
    class Lifetime : Binder() {
        private lateinit var death: IBinder.DeathRecipient
        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) { death = recipient }
        fun die() { death.binderDied() }
    }
    @Before fun start() {
        val previous: Handler? = ReflectionHelpers.getStaticField(HudArbiter::class.java, "worker")
        previous?.looper?.quit()
        ReflectionHelpers.setStaticField(HudArbiter::class.java, "worker", null)
        packages = mock(PackageManager::class.java)
        `when`(packages.getPackagesForUid(anyInt())).thenReturn(arrayOf(phone))
        `when`(packages.checkSignatures(anyInt(), anyInt())).thenReturn(PackageManager.SIGNATURE_MATCH)
        val app = RuntimeEnvironment.getApplication()
        HudArbiter.attach(object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getPackageManager(): PackageManager = packages
        })
    }
    @After fun stop() { worker.looper.quit() }
    private fun <T> serial(block: () -> T): T = FutureTask<T>(block).also { worker.post(it) }.get(3, TimeUnit.SECONDS)
    private fun packet(name: String) = Parcel.obtain().apply { writeInterfaceToken("arcfox.hud.sources.v1"); writeString(name) }
    private fun register(token: Lifetime): Long {
        val data = packet(phone); val reply = Parcel.obtain()
        return try { data.writeStrongBinder(token); assertTrue(HudArbiter.binder().transact(1, data, reply, 0));
            reply.readException(); reply.readLong() } finally { data.recycle(); reply.recycle() }
    }
    private fun update(lease: Long, sequence: Long, active: Boolean, name: String = phone) {
        val data = packet(name);val reply = Parcel.obtain()
        try {
            data.writeLong(lease); data.writeLong(sequence)
            data.writeBundle(Bundle().apply { putBoolean("active", active); putInt("icon", 1); putInt("distance", 100) })
            assertTrue(HudArbiter.binder().transact(2, data, reply, 0));reply.readException()
        } finally { data.recycle();reply.recycle() }
    }
    @Test fun oldLeasesDuplicateSequencesAndOldDeathsCannotClearTheNewSource() {
        val first = Lifetime();val oldLease = register(first);update(oldLease, 1, true)
        assertTrue(serial { selection.get(phone).active })
        val current = Lifetime();val lease = register(current);update(lease, 1, true)
        update(oldLease, 200, false);update(lease, 1, false);first.die()
        assertTrue(serial { selection.get(phone).active })
        assertEquals(lease, serial { selection.get(phone).lease })
        current.die()
        assertFalse(serial { selection.get(phone).active })
    }
    @Test fun forgedPackageAndWrongSignatureAreRejectedBeforeQueuedStateChanges() {
        val lease = register(Lifetime());update(lease, 1, true)
        try { update(lease, 2, false, "com.autonavi.amaparcfox"); fail("UID cannot claim another package") }
        catch (_: SecurityException) { }
        `when`(packages.checkSignatures(anyInt(), anyInt())).thenReturn(PackageManager.SIGNATURE_NO_MATCH)
        try { update(lease, 2, false);fail("Signing identity must be checked") }
        catch (_: SecurityException) { }
        assertTrue(serial { selection.get(phone).active })
    }

    @Test fun nativeNavigationEndClearsItsCachedGuidanceWithoutEndingThePhoneSource() {
        val fingerprint = Build.FINGERPRINT
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", "AAOP_COCKPIT/test")
        try {
            val lease = register(Lifetime()); update(lease, 1, true)
            assertTrue(HudArbiter.nativeInt(557853441, 0))
            assertTrue(HudArbiter.nativeInt(557853245, 7))
            assertTrue(HudArbiter.nativeRoad(byteArrayOf(3, 24, 7, 0)))
            assertTrue(HudArbiter.nativeInt(557853441, 1))
            serial {
                val native = selection.get("com.autonavi.amapauto")
                assertFalse(native.active); assertEquals(0, native.icon); assertNull(native.road)
                assertTrue(selection.get(phone).active)
            }
        } finally { ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", fingerprint) }
    }

    @Test fun unrelatedVehicleValuesAndPacketsPassThroughUntouched() {
        val fingerprint = Build.FINGERPRINT
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", "AAOP_COCKPIT/test")
        try {
            // Phone lamp and arbitrary non-HUD properties must reach the original implementation.
            for (id in listOf(557845456, 0, 557853244, 557853248)) {
                assertFalse(HudArbiter.nativeInt(id, 1))
            }
            assertFalse(HudArbiter.nativeRoad(byteArrayOf(3, 24, 8, 0)))
            assertFalse(HudArbiter.nativeRoad(byteArrayOf(3, 24, 7)))
            assertFalse(HudArbiter.nativeRoad(byteArrayOf(0, 24, 7, 0)))
        } finally { ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", fingerprint) }
    }
}
