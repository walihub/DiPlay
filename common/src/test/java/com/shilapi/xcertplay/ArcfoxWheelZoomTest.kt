package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class ArcfoxWheelZoomTest {
    private fun message(zoom: String, extra: String = "") =
        """{"command":{"id":"Navi","sub":"guidecontrol"},"content":{"zoom":"$zoom"$extra}}"""

    @Test fun matchesTheRecordedVehicleDirectionsAndStringDefaultFlag() {
        assertEquals(true, ArcfoxWheelZoomCommand.decode(message("up", ",\"zoomDefault\":\"false\",\"zoomlevel\":-1")))
        assertEquals(false, ArcfoxWheelZoomCommand.decode(message("down")))
        assertEquals(true, ArcfoxWheelZoomCommand.decode(message("up").replace("Navi", "nAvI")))
    }

    @Test fun absoluteLevelsAndDefaultRequestsNeverInventCarPlaySteps() {
        assertNull(ArcfoxWheelZoomCommand.decode(message("", ",\"zoomlevel\":17")))
        assertNull(ArcfoxWheelZoomCommand.decode(message("up", ",\"zoomDefault\":true")))
        assertNull(ArcfoxWheelZoomCommand.decode(message("down", ",\"zoomDefault\":\"true\"")))
        assertNull(ArcfoxWheelZoomCommand.decode(message("invalid")))
    }

    @Test fun otherCommandsAndMissingContentDoNotZoom() {
        assertNull(ArcfoxWheelZoomCommand.decode(message("up").replace("Navi", "Music")))
        assertNull(ArcfoxWheelZoomCommand.decode(message("up").replace("guidecontrol", "key")))
        assertNull(ArcfoxWheelZoomCommand.decode("{}"))
        assertThrows(org.json.JSONException::class.java) { ArcfoxWheelZoomCommand.decode("not json") }
    }

    @Test fun requiresAnAttachedVisiblePhysicalMap() {
        val target = ArcfoxWheelZoomTarget()
        val calls = mutableListOf<Boolean>()
        assertNull(target.prepare(message("up")))
        target.attach(Any(), { null }) { calls.add(it); true }
        assertNull(target.prepare(message("up")))
        assertTrue(calls.isEmpty())
    }

    @Test fun deliversEachPressWithoutASecondBroadcastPath() {
        val target = ArcfoxWheelZoomTarget()
        val route = Any()
        val calls = mutableListOf<Boolean>()
        target.attach(Any(), { route }) { calls.add(it); true }
        for (direction in listOf("down", "down", "up")) assertTrue(target.prepare(message(direction))!!.invoke())
        assertEquals(listOf(false, false, true), calls)
    }

    @Test fun delayedEventsCannotAffectAReplacedPausedOrDisconnectedStream() {
        for (replacement in listOf(Any(), null)) {
            val target = ArcfoxWheelZoomTarget()
            var route: Any? = Any()
            val calls = mutableListOf<Boolean>()
            target.attach(Any(), { route }) { calls.add(it); true }
            val delivery = target.prepare(message("up"))!!
            route = replacement
            assertFalse(delivery())
            assertTrue(calls.isEmpty())
        }
    }

    @Test fun oldHostCannotDetachNewHostAndItsPendingEventCannotReachIt() {
        val target = ArcfoxWheelZoomTarget()
        val old = Any()
        val current = Any()
        val route = Any()
        val calls = mutableListOf<Boolean>()
        target.attach(old, { route }) { calls.add(it); true }
        val oldDelivery = target.prepare(message("up"))!!
        target.attach(current, { route }) { calls.add(it); true }
        target.detach(old)
        assertFalse(oldDelivery())
        assertTrue(target.prepare(message("down"))!!.invoke())
        assertEquals(listOf(false), calls)
        val pending = target.prepare(message("up"))!!
        target.detach(current)
        assertFalse(pending())
        assertFalse(target.attached())
    }

    @Test fun detachAndReattachInvalidatesPendingEventsEvenOnTheSameRoute() {
        val target = ArcfoxWheelZoomTarget()
        val owner = Any()
        val route = Any()
        target.attach(owner, { route }) { true }
        val pending = target.prepare(message("up"))!!
        target.detach(owner)
        target.attach(owner, { route }) { true }
        assertFalse(pending())
    }
}
