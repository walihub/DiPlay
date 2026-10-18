package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxSessionAdapter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ArcfoxSessionReconnectTest {
    @Test fun onlyContinuousCurrentControlAllowsCachedRouteRecovery() {
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", "test/non_vehicle")
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }
        val adapter = ArcfoxSessionAdapter(context, Any())
        var generation = 4
        adapter.onSessionStarted({ true }, { generation })
        val first = Any()
        val handoff = Any()
        adapter.onControlOpened(first, generation)
        assertFalse(adapter.retainNavigationForReconnect(generation))
        adapter.onNavigation(Iap2Frame(0x5201, byteArrayOf(0, 5, 0, 1, 1)), true)
        assertTrue(adapter.retainNavigationForReconnect(generation))
        adapter.onControlOpened(handoff, generation)
        adapter.onControlEnded(first)
        assertTrue(adapter.retainNavigationForReconnect(generation))
        adapter.onSessionEnded()
        adapter.onSessionStarted({ true }, { generation })
        assertTrue(adapter.retainNavigationForReconnect(generation))
        adapter.onControlEnded(handoff)
        assertFalse(adapter.retainNavigationForReconnect(generation))
        adapter.onControlOpened(Any(), generation)
        assertFalse(adapter.retainNavigationForReconnect(generation))
        generation++
        adapter.onControlOpened(Any(), generation)
        assertFalse(adapter.retainNavigationForReconnect(generation))
        adapter.onNavigation(Iap2Frame(0x5201, byteArrayOf(0, 5, 0, 1, 1)), false)
        assertFalse(adapter.retainNavigationForReconnect(generation))
        adapter.onNavigation(Iap2Frame(0x5201, byteArrayOf(0, 5, 0, 1, 1)), true)
        assertTrue(adapter.retainNavigationForReconnect(generation))
    }
}
