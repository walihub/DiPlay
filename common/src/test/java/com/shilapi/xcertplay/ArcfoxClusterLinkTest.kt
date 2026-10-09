package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33], manifest = org.robolectric.annotation.Config.NONE)
class ArcfoxClusterLinkTest {
    @Test fun anOldHostCannotClearTheNewHostsInstrumentSlot() {
        val lease = ArcfoxClusterLinkLease()
        val old = Any()
        val current = Any()
        assertFalse(lease.request(old, false))
        assertTrue(lease.request(old, true))
        lease.request(current, true)
        assertFalse(lease.release(old))
        assertFalse(lease.request(old, false))
        assertTrue(lease.visible)
        assertTrue(lease.release(current))
        assertFalse(lease.visible)
    }

    @Test fun showRetriesFailedTransactionsAndClearsAPossiblyPartialShow() {
        val writer = ArcfoxClusterLinkWriter()
        val sent = mutableListOf<Boolean>()
        writer.sync(true, 0) { sent += it; throw IllegalStateException("service unavailable") }
        writer.sync(true, 1000) { sent += it }
        writer.sync(true, 2000) { sent += it }
        writer.sync(false, 2001) { sent += it; throw IllegalStateException("restart") }
        writer.sync(false, 3001) { sent += it }
        writer.sync(false, 4001) { sent += it }
        assertEquals(listOf(true, true, false, false), sent)
        assertTrue(writer.report().contains("failures=2"))
        val partial = ArcfoxClusterLinkWriter()
        val cleanup = mutableListOf<Boolean>()
        partial.sync(true, 0) { throw IllegalStateException("partial") }
        partial.sync(false, 1) { cleanup += it }
        assertEquals(listOf(false), cleanup)
    }

    @Test fun neverClearsAnUnownedSlotAndRefreshesAnActiveSlotAfterFiveSeconds() {
        val writer = ArcfoxClusterLinkWriter()
        val sent = mutableListOf<Boolean>()
        writer.sync(false, 0) { sent += it }
        writer.sync(true, 1) { sent += it }
        writer.sync(true, 5000) { sent += it }
        writer.sync(true, 5001) { sent += it }
        assertEquals(listOf(true, true), sent)
    }

    @Test fun sendsTheObservedHidlMessageWithoutRegisteringOrStartingTheService() {
        FakeBinder.remote = FakeRemote()
        val transport = ArcfoxFgeTransport { name -> when (name) {
            "android.os.HwBinder" -> FakeBinder::class.java
            "android.os.HwParcel" -> FakeParcel::class.java
            "android.os.HwBlob" -> FakeBlob::class.java
            "android.os.IHwBinder" -> FakeRemote::class.java
            else -> error(name)
        } }
        transport.send(true)
        val remote = FakeBinder.remote
        assertEquals(ArcfoxFgeTransport.DESCRIPTOR, FakeBinder.descriptor)
        assertEquals("default", FakeBinder.instance)
        assertFalse(FakeBinder.retry)
        assertEquals(1, remote.code)
        assertEquals(0, remote.flags)
        assertEquals(ArcfoxFgeTransport.DESCRIPTOR, remote.request.token)
        assertEquals(40, remote.request.blob!!.size)
        assertEquals(224, remote.request.blob!!.ints[0L])
        assertEquals(ArcfoxFgeTransport.payload(true), remote.request.blob!!.strings[8L])
        assertEquals("", remote.request.blob!!.strings[24L])
        assertTrue(remote.reply.verified)
        assertTrue(remote.reply.released && remote.request.released && remote.request.temporaryReleased)
        transport.send(false)
        assertEquals(ArcfoxFgeTransport.payload(false), remote.request.blob!!.strings[8L])
    }

    class FakeBlob(val size: Int) {
        val ints = mutableMapOf<Long, Int>()
        val strings = mutableMapOf<Long, String>()
        fun putInt32(offset: Long, value: Int) { ints[offset] = value }
        fun putString(offset: Long, value: String) { strings[offset] = value }
    }
    class FakeParcel {
        var token = ""
        var blob: FakeBlob? = null
        var verified = false
        var released = false
        var temporaryReleased = false
        fun writeInterfaceToken(value: String) { token = value }
        fun writeBuffer(value: FakeBlob) { blob = value }
        fun verifySuccess() { verified = true }
        fun releaseTemporaryStorage() { temporaryReleased = true }
        fun release() { released = true }
    }
    class FakeRemote {
        var code = -1
        var flags = -1
        lateinit var request: FakeParcel
        lateinit var reply: FakeParcel
        fun transact(code: Int, request: FakeParcel, reply: FakeParcel, flags: Int) {
            this.code = code; this.request = request; this.reply = reply; this.flags = flags
        }
    }
    object FakeBinder {
        var remote = FakeRemote()
        var descriptor = ""
        var instance = ""
        var retry = true
        @JvmStatic fun getService(descriptor: String, instance: String, retry: Boolean): FakeRemote {
            this.descriptor = descriptor; this.instance = instance; this.retry = retry
            return remote
        }
    }
}
