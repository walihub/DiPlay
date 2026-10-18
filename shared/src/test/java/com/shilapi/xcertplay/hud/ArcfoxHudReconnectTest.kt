package com.shilapi.xcertplay.hud

import android.os.Build
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxHudSync
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ArcfoxHudReconnectTest {
    @Test fun sameControlReconnectRestoresGuidanceWithoutInventingANewRoute() {
        withHud { owner ->
            start(owner)
            ArcfoxHudSync.end(owner)
            drain()
            ArcfoxHudSync.begin(owner, restoreNavigation = true) { true }
            drain()
            assertReason("guidance_ready")
        }
    }

    @Test fun stopReceivedWhileVideoIsDisconnectedPreventsRouteResurrection() {
        withHud { owner ->
            start(owner)
            ArcfoxHudSync.end(owner)
            drain()
            ArcfoxHudSync.onFrame(owner, Iap2Frame(0x5201, tlv(1, 0)))
            ArcfoxHudSync.begin(owner, restoreNavigation = true) { true }
            drain()
            assertReason("no_active_route")
        }
    }

    @Test fun fullControlReconnectClearsTheOldRouteUntilPhoneSendsNewGuidance() {
        withHud { owner ->
            start(owner)
            ArcfoxHudSync.end(owner)
            drain()
            ArcfoxHudSync.begin(owner, restoreNavigation = false) { true }
            drain()
            assertReason("no_active_route")
        }
    }

    private fun withHud(test: (Any) -> Unit) {
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", "AAOP_COCKPIT/test")
        val owner = Any()
        ArcfoxHudSync.begin(owner) { true }
        drain()
        try { test(owner) } finally { ArcfoxHudSync.end(owner); drain() }
    }

    private fun start(owner: Any) {
        ArcfoxHudSync.onFrame(owner, Iap2Frame(0x5201, tlv(1, 1) + tlv(13, 0, 7)))
        ArcfoxHudSync.onFrame(owner, Iap2Frame(0x5202, tlv(1, 0, 7) + tlv(3, 1)))
        drain()
        assertReason("guidance_ready")
    }

    private fun drain() {
        val worker = ReflectionHelpers.getField<ScheduledExecutorService>(ArcfoxHudSync, "worker")
        worker.submit {}.get(5, TimeUnit.SECONDS)
    }

    private fun assertReason(reason: String) {
        val report = ArcfoxHudSync.diagnosticReport()
        assertTrue(report, report.substringBefore('\n').contains("reason=$reason "))
    }

    private fun tlv(id: Int, vararg values: Int): ByteArray =
        byteArrayOf(0, (values.size + 4).toByte(), 0, id.toByte()) + values.map(Int::toByte).toByteArray()
}
