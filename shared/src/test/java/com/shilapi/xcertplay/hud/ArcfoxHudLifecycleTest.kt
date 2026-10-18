package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxHudSync
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxHudWriter
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxHudControlState
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxHudSessionGate

import org.junit.Assert.*
import org.junit.Test

class ArcfoxHudLifecycleTest {
    @Test fun controlHandoffKeepsTheSourceLiveButVideoOnlyCannotKeepItForever() {
        var now = 0L
        val controls = ArcfoxHudControlState { now }
        val bootstrap = Any()
        val tunnel = Any()
        assertFalse(controls.isAlive(1))
        controls.opened(bootstrap, 1)
        controls.opened(tunnel, 1)
        controls.ended(bootstrap)
        now = 300_000_000_000L
        assertTrue(controls.isAlive(1))
        controls.ended(tunnel)
        now += 4_999_999_999L
        assertTrue(controls.isAlive(1))
        now++
        assertFalse(controls.isAlive(1))
    }

    @Test fun oldControlRunDoesNotKeepReplacementGenerationAlive() {
        var now = 0L
        val controls = ArcfoxHudControlState { now }
        val old = Any()
        val current = Any()
        controls.opened(old, 1)
        assertFalse(controls.isAlive(2))
        controls.opened(current, 2)
        controls.ended(current)
        now = 4_000_000_000L
        controls.ended(old)
        now = 5_000_000_000L
        assertFalse(controls.isAlive(2))
    }

    @Test fun parkedLiveSessionRetainsGuidanceButOtherCarsStillExpire() {
        var now = 0L
        var live = true
        val arcfox = BydHudRouteState(nanoTime = { now }, retainWhileSessionAlive = { live })
        val byd = BydHudRouteState(nanoTime = { now })
        for (route in listOf(arcfox, byd)) start(route)
        now = 300_000_000_000L
        assertNotNull(arcfox.currentApple())
        assertEquals("guidance_ready", arcfox.visibilityReason())
        assertNull(byd.currentApple())
        live = false
        assertNull(arcfox.currentApple())
        assertEquals("route_stale_30s", arcfox.visibilityReason())
    }

    @Test fun liveSessionDoesNotOverrideExplicitEndOrEmptyListExpiry() {
        for (endState in listOf(0, 2)) {
            val route = BydHudRouteState(retainWhileSessionAlive = { true })
            start(route)
            route.accept(0x5201, tlv(1, endState))
            assertNull(route.currentApple())
        }
        var now = 0L
        val route = BydHudRouteState(nanoTime = { now }, retainWhileSessionAlive = { true })
        start(route)
        route.accept(0x5201, tlv(13))
        now = 3_000_000_000L
        assertNull(route.currentApple())
        assertEquals("empty_list_3s", route.visibilityReason())
    }

    @Test fun replacementInvalidatesQueuedFramesAndOldControllerCannotClearIt() {
        val gate = ArcfoxHudSessionGate()
        val old = Any()
        val next = Any()
        val frame = gate.begin(old) { true }!!
        val clear = gate.end(old)!!
        val current = gate.begin(next) { true }!!
        assertFalse(gate.isCurrent(frame))
        assertFalse(gate.isCurrent(clear))
        assertTrue(gate.isCurrent(current))
        assertNull(gate.capture(old))
        assertNull(gate.end(old))
        assertNull(gate.begin(old) { true })
        assertNull(gate.endIfInactive())
        assertTrue(gate.isAlive())
    }

    @Test fun sameControllerReconnectInvalidatesItsPendingEnd() {
        val gate = ArcfoxHudSessionGate()
        val owner = Any()
        val first = gate.begin(owner) { true }!!
        val ended = gate.end(owner)!!
        assertNull(gate.capture(owner))
        assertFalse(gate.isAlive())
        val reconnected = gate.begin(owner) { true }!!
        assertFalse(gate.isCurrent(first))
        assertFalse(gate.isCurrent(ended))
        assertTrue(gate.isCurrent(reconnected))
    }

    @Test fun routeUpdatesAcrossVideoReconnectKeepTheirControlTokenButNotAcrossOwners() {
        val gate = ArcfoxHudSessionGate()
        val owner = Any()
        gate.begin(owner) { true }
        val navigation = gate.captureNavigation(owner)!!
        gate.end(owner)
        // A stop frame received from the still-open iAP2 channel must invalidate the cache,
        // including when it was queued just before the AirPlay reconnect.
        assertEquals(navigation, gate.captureNavigation(owner))
        gate.begin(owner) { true }
        assertTrue(gate.isNavigationCurrent(navigation))
        gate.resetNavigation(owner)
        assertFalse(gate.isNavigationCurrent(navigation))
        val current = gate.captureNavigation(owner)!!
        gate.begin(Any()) { true }
        assertFalse(gate.isNavigationCurrent(current))
        assertNull(gate.captureNavigation(owner))
    }

    @Test fun cachedRouteRequiresAnOpenControlNotJustItsHandoffGrace() {
        val controls = ArcfoxHudControlState { 0L }
        val token = Any()
        controls.opened(token, 7)
        assertTrue(controls.isOpen(7))
        assertFalse(controls.isOpen(8))
        controls.ended(token)
        assertTrue(controls.isAlive(7))
        assertFalse(controls.isOpen(7))
    }

    @Test fun connectionExistenceDoesNotInventAnActiveNavigationRoute() {
        val route = BydHudRouteState(retainWhileSessionAlive = { true })
        assertNull(route.currentApple())
        assertEquals("no_active_route", route.visibilityReason())
    }

    @Test fun periodicResendRestoresGuidanceAfterAnotherSourceClearsIt() {
        var now = 0L
        var vehicle: ArcfoxHudSync.Guidance? = null
        val calls = mutableListOf<ArcfoxHudSync.Guidance?>()
        val first = ArcfoxHudSync.Guidance(7, 100, "A")
        val writer = ArcfoxHudWriter(nanoTime = { now }, matches = { it == vehicle }) {
            vehicle = it
            calls.add(it)
        }
        assertTrue(writer.transmit(first))
        vehicle = null // A different navigation source clears HUD.
        now = 4_999_999_999L
        assertFalse(writer.transmit(first, keepAlive = true))
        now++
        assertTrue(writer.transmit(first, keepAlive = true))
        assertEquals(first, vehicle)
        assertEquals(listOf(first, first), calls)
        assertTrue(writer.transmit(null))
        now += 10_000_000_000L
        assertFalse(writer.transmit(null, keepAlive = true))
        assertEquals(listOf(first, first, null), calls)
    }

    @Test fun duplicateFramesDoNotPostponeKeepaliveAndEachSuccessfulWriteRestartsThePeriod() {
        var now = 0L
        val calls = mutableListOf<ArcfoxHudSync.Guidance?>()
        val writer = ArcfoxHudWriter(nanoTime = { now }, output = calls::add)
        val first = ArcfoxHudSync.Guidance(7, 100, "A")
        writer.transmit(first)
        for (second in 1L..4L) {
            now = second * 1_000_000_000L
            assertFalse(writer.transmit(first))
            assertFalse(writer.transmit(first, keepAlive = true))
        }
        now = 5_000_000_000L
        assertTrue(writer.transmit(first, keepAlive = true))
        now = 9_000_000_000L
        assertFalse(writer.transmit(first, keepAlive = true))
        now = 10_000_000_000L
        assertTrue(writer.transmit(first, keepAlive = true))
        assertEquals(listOf(first, first, first), calls)
    }

    @Test fun failedKeepaliveRetriesTheCompleteGuidanceOnTheNextTick() {
        var now = 0L
        var fail = false
        val calls = mutableListOf<ArcfoxHudSync.Guidance?>()
        val writer = ArcfoxHudWriter(nanoTime = { now }) {
            calls.add(it)
            if (fail) error("property service unavailable")
        }
        val first = ArcfoxHudSync.Guidance(7, 100, "A")
        writer.transmit(first)
        now = 5_000_000_000L
        fail = true
        assertThrows(IllegalStateException::class.java) { writer.transmit(first, keepAlive = true) }
        now = 6_000_000_000L
        fail = false
        assertTrue(writer.transmit(first, keepAlive = true))
        assertEquals(listOf(first, first, first), calls)
    }

    @Test fun changedGuidanceIsImmediateAndCompetingOutputDoesNotLockOutNavigation() {
        var matching = true
        val calls = mutableListOf<ArcfoxHudSync.Guidance?>()
        val writer = ArcfoxHudWriter(matches = { matching }, output = calls::add)
        val first = ArcfoxHudSync.Guidance(7, 100, "A")
        val changed = ArcfoxHudSync.Guidance(3, 90, "B")
        assertTrue(writer.transmit(first))
        matching = false
        assertTrue(writer.transmit(changed))
        assertFalse(writer.transmit(null)) // Do not clear someone else's current output.
        assertTrue(writer.transmit(first))
        assertEquals(listOf(first, changed, first), calls)
    }

    @Test fun unavailableReadbackDefersOwnedClearAndRetriesWhenReadRecovers() {
        val calls = mutableListOf<ArcfoxHudSync.Guidance?>()
        var matching: Boolean? = true
        val writer = ArcfoxHudWriter(matches = { matching }, output = calls::add)
        val first = ArcfoxHudSync.Guidance(7, 100, "A")
        writer.transmit(first)
        matching = null
        assertFalse(writer.transmit(null))
        assertTrue(writer.mayHaveOutput)
        matching = true
        assertTrue(writer.transmit(null))
        assertEquals(listOf(first, null), calls)
    }

    @Test fun explicitNewSessionDoesNotReuseThePriorSessionsDedupeCache() {
        val calls = mutableListOf<ArcfoxHudSync.Guidance?>()
        val writer = ArcfoxHudWriter(output = calls::add)
        val first = ArcfoxHudSync.Guidance(7, 100, "A")
        writer.transmit(first)
        assertFalse(writer.transmit(first))
        writer.resume()
        assertTrue(writer.transmit(first))
        assertEquals(listOf(first, first), calls)
    }

    @Test fun partiallyWrittenUnknownOutputIsNotDestructivelyCleared() {
        val calls = mutableListOf<ArcfoxHudSync.Guidance?>()
        var fail = true
        val writer = ArcfoxHudWriter(matches = { null }) { next ->
            calls.add(next)
            if (fail) error("partial write")
        }
        assertThrows(IllegalStateException::class.java) {
            writer.transmit(ArcfoxHudSync.Guidance(7, 100, "A"))
        }
        assertFalse(writer.transmit(null))
        assertEquals(1, calls.size)
        // Unknown partial cleanup must not block a later fresh navigation session forever.
        fail = false
        writer.resume()
        assertTrue(writer.transmit(ArcfoxHudSync.Guidance(3, 90, "B")))
    }

    private fun start(route: BydHudRouteState) {
        route.accept(0x5201, tlv(1, 1) + tlv(13, 0, 7))
        route.accept(0x5202, tlv(1, 0, 7) + tlv(3, 1))
        assertNotNull(route.currentApple())
    }

    private fun tlv(id: Int, vararg values: Int): ByteArray =
        byteArrayOf(0, (values.size + 4).toByte(), 0, id.toByte()) + values.map { it.toByte() }.toByteArray()
}
