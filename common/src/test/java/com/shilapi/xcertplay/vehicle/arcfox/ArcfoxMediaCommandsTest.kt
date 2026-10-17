package com.shilapi.xcertplay.vehicle.arcfox

import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArcfoxMediaCommandsTest {
    @Test fun mapsOnlyCarPlaySourceCommands() {
        assertEquals(CarPlayMediaButton.PLAY,
            ArcfoxMediaCommands.command(ArcfoxMediaCommands.SOURCE, "MSG_INPUT_PLAY"))
        assertEquals(CarPlayMediaButton.PAUSE,
            ArcfoxMediaCommands.command(ArcfoxMediaCommands.SOURCE, "MSG_INPUT_STOP"))
        assertEquals(CarPlayMediaButton.NEXT,
            ArcfoxMediaCommands.command(ArcfoxMediaCommands.SOURCE, "MSG_INPUT_NEXT"))
        assertEquals(CarPlayMediaButton.PREVIOUS,
            ArcfoxMediaCommands.command(ArcfoxMediaCommands.SOURCE, "MSG_INPUT_PREV"))
        assertNull(ArcfoxMediaCommands.command("ADAYO_SOURCE_BT_AUDIO", "MSG_INPUT_NEXT"))
        assertNull(ArcfoxMediaCommands.command(ArcfoxMediaCommands.SOURCE, "MSG_INPUT_FAST_FORWARD"))
    }
}
