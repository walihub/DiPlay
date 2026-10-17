package com.shilapi.xcertplay.vehicle.arcfox

import android.Manifest
import org.junit.Assert.*
import org.junit.Test

class ArcfoxRuntimePermissionsTest {
    @Test fun grantsOnlyMissingMicrophoneAndForegroundLocationThenIsIdempotent() {
        val granted = mutableSetOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        val calls = mutableListOf<String>()
        fun run() = ArcfoxRuntimePermissions.grantMissing(true,true,granted::contains,{ calls += it; granted += it })
        assertEquals("system_granted", run())
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.ACCESS_FINE_LOCATION),calls)
        assertEquals("already_granted", run())
        assertEquals(2,calls.size)
    }
    @Test fun nonArcfoxAndUnsignedBuildsDoNotAttemptPrivilegedOperations() {
        val grant: (String) -> Unit = { fail("Must not grant $it") }
        assertEquals("not_arcfox",ArcfoxRuntimePermissions.grantMissing(false,true,{false},grant))
        assertEquals("system_grant_permission_missing",ArcfoxRuntimePermissions.grantMissing(true,false,{false},grant))
    }
    @Test fun rejectedMicrophoneDoesNotPreventLocationAndIsNotReportedAsGranted() {
        val granted = mutableSetOf<String>()
        val failed = mutableListOf<String>()
        val result = ArcfoxRuntimePermissions.grantMissing(true,true,granted::contains,{
            if(it == Manifest.permission.RECORD_AUDIO) throw SecurityException("OEM policy")
            granted += it
        },{ permission,_ -> failed += permission })
        assertEquals("remaining=${Manifest.permission.RECORD_AUDIO}",result)
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO),failed)
        assertEquals(setOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION),granted)
    }
    @Test fun successfulBinderCallWithoutActualGrantIsNotReportedAsSuccess() {
        assertTrue(ArcfoxRuntimePermissions.grantMissing(true,true,{false},{}).startsWith("remaining="))
    }
}
