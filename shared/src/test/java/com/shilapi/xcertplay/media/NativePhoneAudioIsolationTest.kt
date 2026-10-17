package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.vehicle.arcfox.PhoneYield
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class NativePhoneAudioIsolationTest {
    @Test fun confirmedNativeOwnershipPreventsCarPlayCallPlaybackAndCapture() {
        val sink = AndroidMediaSink()
        try {
            AndroidMediaSink::class.java.getDeclaredMethod("yieldNativePhone", PhoneYield::class.java).apply { isAccessible = true }.invoke(sink, PhoneYield(true, true))
            val id = AudioStreamId(100,"telephony")
            val format = AudioFormat(AudioCodecKind.LPCM,16000,1,100,"telephony")
            sink.onAudioStarted(id,format,0)
            sink.onAudioRtp(id,format,byteArrayOf(),0)
            sink.onMicrophoneStarted(id,MicrophoneConfig("telephony",16000,1,100,20,InetAddress.getLoopbackAddress(),1234,ByteArray(32)))
            for (name in listOf("audioRenderers", "microphoneUplinks")) {
                val field = AndroidMediaSink::class.java.getDeclaredField(name).apply { isAccessible = true }
                assertTrue((field.get(sink) as Map<*,*>).isEmpty())
            }
            val owns = AndroidMediaSink::class.java.getDeclaredMethod("nativePhoneOwns",String::class.java).apply { isAccessible = true }
            for (type in listOf("media","default","speechRecognition")) assertEquals(false,owns.invoke(sink,type))
        } finally { sink.close() }
    }
    @Test fun onlyConfirmedArcfoxIncomingAlertsUseRingtoneStream() {
        val navigation = AudioChannelMapper.map("alert",100,AudioChannelMappingMode.MOBILE_COMPATIBLE,11)
        assertEquals(11, AudioChannelMapper.arcfoxRing("alert",navigation,true,false).streamType)
        assertEquals(navigation, AudioChannelMapper.arcfoxRing("default",navigation,true,true))
        assertEquals(navigation, AudioChannelMapper.arcfoxRing("alert",navigation,false,true))
        assertEquals(AudioChannel.RING, AudioChannelMapper.arcfoxRing("alert",navigation,true,true).channel)
        assertEquals(2, AudioChannelMapper.arcfoxRing("alert",navigation,true,true).streamType)
    }
}
