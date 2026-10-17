package com.shilapi.xcertplay.vehicle.arcfox

import android.os.Binder
import android.os.Parcel
import android.bluetooth.BluetoothAdapter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class NativePhoneReaderTest {
    @Test fun readsOnlyCallListAndAudioStateWithTheCapturedParcelLayout() {
        val codes = mutableListOf<Int>()
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface("com.nforetek.bt.aidl.UiCommand")
                codes.add(code); reply!!.writeNoException()
                when(code) {
                    97 -> reply.writeInt(140)
                    101 -> {
                        reply.writeInt(1); reply.writeInt(1)
                        reply.writeParcelable(BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55"),0)
                        reply.writeInt(2); reply.writeString("call-uuid"); reply.writeInt(0)
                        reply.writeString("not-retained"); reply.writeInt(0); reply.writeInt(0)
                    }
                    else -> fail("Unexpected OEM operation $code")
                }
                return true
            }
        }
        assertEquals(NativePhoneAudio(listOf(NativePhoneCall("00:11:22:33:44:55",0,"call-uuid")),140), NativePhoneReader(binder).snapshot())
        assertEquals(listOf(101,97,101),codes)
    }
}
