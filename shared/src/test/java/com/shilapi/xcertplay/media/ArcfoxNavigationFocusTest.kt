package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class ArcfoxNavigationFocusTest {
    private var now = 0L
    private var requests = 0
    private var releases = 0
    private var granted = true
    private fun focus() = ArcfoxNavigationFocus({ requests++; granted }, { releases++ }, { now })

    @Test fun onlyArcfoxNavigationOnAcceptedStream11IsEnabled() {
        assertTrue(usesArcfoxNavigationFocus("AAOP_COCKPIT/HS7013A/test", true, 11))
        assertFalse(usesArcfoxNavigationFocus("BYD/test", true, 11))
        assertFalse(usesArcfoxNavigationFocus("AAOP_COCKPIT/test", false, 11))
        assertFalse(usesArcfoxNavigationFocus("AAOP_COCKPIT/test", true, null))
        assertFalse(usesArcfoxNavigationFocus("AAOP_COCKPIT/test", true, 0))
    }

    @Test fun bufferedTailRetainsFocusAndNextPromptAcquiresAgain() {
        val focus = focus()
        focus.pcm(false); now = 100; focus.pcm(false)
        assertEquals(1, requests)
        now = 1000; focus.maintain(true, false)
        assertEquals(0, releases)
        focus.maintain(false, false)
        assertEquals(1, releases)
        focus.pcm(false)
        assertEquals(2, requests)
        focus.close(); focus.close()
        assertEquals(2, releases)
        focus.pcm(false)
        assertEquals(2, requests)
    }

    @Test fun lossAndPriorityVoiceCannotTriggerRepeatedFocusTakeover() {
        val focus = focus()
        focus.pcm(false); focus.lost()
        now = 200; focus.pcm(false)
        assertEquals(1, requests)
        now = 1000; focus.maintain(false, false)
        focus.pcm(true)
        assertEquals(1, requests)
        now = 2000; focus.maintain(false, false)
        focus.pcm(false)
        assertEquals(2, requests)
        focus.maintain(true, true)
        assertEquals(2, releases)
    }

    @Test fun staleLossDoesNotReleaseTheNextPrompt() {
        val focus = focus()
        focus.pcm(false)
        now = 1000; focus.maintain(false, false)
        focus.pcm(false)
        focus.lost { false }
        assertEquals(1, releases)
        focus.close()
        assertEquals(2, releases)
    }

    @Test fun deniedFocusRetriesAreBoundedAndNeverAbandonAnotherOwner() {
        granted = false
        val focus = focus()
        focus.pcm(false); now = 100; focus.pcm(false)
        assertEquals(1, requests)
        now = 600; granted = true; focus.pcm(false)
        assertEquals(2, requests)
        assertEquals(0, releases)
        focus.close()
        assertEquals(1, releases)
    }
}
