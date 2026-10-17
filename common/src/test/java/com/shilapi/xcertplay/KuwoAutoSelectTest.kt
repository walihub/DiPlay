package com.shilapi.xcertplay

import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import local.arcfox.kuwo.AutoSelectionPolicy
import local.arcfox.kuwo.KuwoAutoSelect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowBuild
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class KuwoAutoSelectTest {
    private fun entry(token: String, playing: Boolean, pkg: String = AutoSelectionPolicy.KUWO) =
        AutoSelectionPolicy.State(token, pkg, playing)

    @Test fun openingPausedOrMissingPlayerDoesNotSelectAnything() {
        val policy = AutoSelectionPolicy()
        assertNull(policy.observe(emptyList()))
        assertNull(policy.observe(listOf(entry("kuwo", false))))
        assertNull(policy.observe(listOf(entry("native", true, "native"))))
    }
    @Test fun realNewPlaySelectsOnceAndMetadataOrMonitorRecreationDoesNotSteal() {
        val p = AutoSelectionPolicy()
        assertEquals("kuwo", p.observe(listOf(entry("kuwo", true))))
        repeat(20) { assertNull(p.observe(listOf(entry("kuwo", true)))) }
        assertNull(p.observe(listOf(entry("kuwo", false))))
        assertEquals("kuwo", p.observe(listOf(entry("kuwo", true))))
    }
    @Test fun existingOtherPlaybackDoesNotPreventANewKuwoPlayButExistingKuwoCannotReclaim() {
        val p = AutoSelectionPolicy()
        assertNull(p.observe(listOf(entry("native", true, "native"), entry("kuwo", true))))
        assertNull(p.observe(listOf(entry("kuwo", true))))
        p.observe(listOf(entry("native", true, "native"), entry("kuwo", false)))
        assertEquals("kuwo", p.observe(listOf(entry("native", true, "native"), entry("kuwo", true))))
    }
    @Test fun simultaneousNewPlayersFollowAndroidPriorityAndDisconnectClearsOldToken() {
        val p = AutoSelectionPolicy()
        p.observe(emptyList())
        assertNull(p.observe(listOf(entry("new", true, "native"), entry("kuwo", true))))
        p.observe(emptyList())
        assertEquals("kuwo2", p.observe(listOf(entry("kuwo2", true), entry("new2", true, "native"))))
    }
    @Test fun nativeOrCarPlayTakeoverCannotBeReversedByContinuingKuwoMetadataOrPluginRefresh() {
        val p = AutoSelectionPolicy()
        assertEquals("kuwo", p.observe(listOf(entry("kuwo", true))))
        assertNull(p.observe(listOf(entry("native", true, "com.arcfox.media"), entry("kuwo", true))))
        repeat(20) {
            assertNull(p.observe(listOf(entry("kuwo", true), entry("native", true, "com.arcfox.media"))))
        }
        assertNull(p.observe(listOf(entry("phone", true, "com.shihab.diplay"), entry("kuwo", true))))
        assertNull(p.observe(listOf(entry("kuwo", true)))) // OEM source may have no active MediaSession.
    }
    @Test fun waitsForExistingCarPlayPauseBeforePluginRecreationAndOnlySelectsOnce() {
        val p = AutoSelectionPolicy()
        p.candidate(listOf(entry("phone", true, "com.shihab.diplay"), entry("kuwo", false)), 101, 0)
        assertNull(p.candidate(listOf(entry("kuwo", true), entry("phone", true, "com.shihab.diplay")), 101, 100))
        assertEquals("kuwo", p.candidate(listOf(entry("kuwo", true), entry("phone", false, "com.shihab.diplay")), 101, 1100))
        assertNull(p.candidate(listOf(entry("kuwo", true)), 6, 2100))
    }
    @Test fun pendingSelectionExpiresOrYieldsToManualChoiceAndANewerPhonePlay() {
        fun pending(): AutoSelectionPolicy = AutoSelectionPolicy().apply {
            candidate(listOf(entry("phone", true, "com.shihab.diplay"), entry("kuwo", false)), 101, 0)
            candidate(listOf(entry("kuwo", true), entry("phone", true, "com.shihab.diplay")), 101, 100)
        }
        assertNull(pending().candidate(listOf(entry("kuwo", true)), 101, 6000))
        assertNull(pending().candidate(listOf(entry("kuwo", true)), 2, 1100))
        assertNull(pending().candidate(listOf(entry("newPhone", true, "com.shihab.diplay"), entry("kuwo", true)), 101, 1100))
    }
    private fun state(playing: Boolean) = PlaybackState.Builder()
        .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, 0, 1f).build()

    @Test fun realWatcherAutomaticallyChoosesPluginAndRejectsManualSelectionAndNewRivalWhileQueued() {
        ShadowBuild.setFingerprint("AAOP_COCKPIT/test")
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(MediaSessionManager::class.java)
        val session = MediaSession(app, "kuwo")
        val rivalSession = MediaSession(app, "native")
        val controller = MediaController(app, session.sessionToken)
        val rival = MediaController(app, rivalSession.sessionToken)
        shadowOf(controller).setPackageName(AutoSelectionPolicy.KUWO)
        shadowOf(controller).setPlaybackState(state(false))
        shadowOf(rival).setPackageName("native")
        shadowOf(rival).setPlaybackState(state(false))
        shadowOf(manager).addController(controller)
        shadowOf(manager).addController(rival)
        com.dudu.autoui.manage.music.s.selected = 2
        val fakeManager = com.dudu.autoui.manage.music.p.w()
        fakeManager.initializations = 0
        KuwoAutoSelect.watch(app)
        val watch = ReflectionHelpers.getStaticField<Any>(KuwoAutoSelect::class.java, "current")
        val worker = ReflectionHelpers.getField<Handler>(watch, "worker")
        fun tick() { ReflectionHelpers.callInstanceMethod<Void>(watch, "tick") }
        try {
            shadowOf(worker.looper).idle()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(2, com.dudu.autoui.manage.music.s.k())
            shadowOf(controller).setPlaybackState(state(true))
            tick()
            // An explicit user change after detection wins over the queued automatic selection.
            com.dudu.autoui.manage.music.s.selected = 9
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(9, com.dudu.autoui.manage.music.s.k())
            tick(); shadowOf(Looper.getMainLooper()).idle()
            assertEquals(9, com.dudu.autoui.manage.music.s.k())
            assertEquals(0, fakeManager.initializations)

            shadowOf(controller).setPlaybackState(state(false)); tick()
            shadowOf(controller).setPlaybackState(state(true)); tick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(6, com.dudu.autoui.manage.music.s.k())
            assertEquals(1, fakeManager.initializations)
            KuwoAutoSelect.watch(app) // Plugin reinitialization must keep the same observer history.
            assertSame(watch, ReflectionHelpers.getStaticField<Any>(KuwoAutoSelect::class.java, "current"))
            tick(); shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, fakeManager.initializations)

            com.dudu.autoui.manage.music.s.selected = 2
            shadowOf(controller).setPlaybackState(state(false)); tick()
            shadowOf(controller).setPlaybackState(state(true)); tick()
            shadowOf(rival).setPlaybackState(state(true))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(2, com.dudu.autoui.manage.music.s.k())
            assertEquals(1, fakeManager.initializations)
        } finally {
            KuwoAutoSelect.stop()
            shadowOf(manager).clearControllers()
            session.release(); rivalSession.release()
        }
    }
    @Test fun nonArcfoxDoesNotStartMonitorOrChangeSelection() {
        ShadowBuild.setFingerprint("generic/test")
        KuwoAutoSelect.watch(RuntimeEnvironment.getApplication())
        assertNull(ReflectionHelpers.getStaticField<Any?>(KuwoAutoSelect::class.java, "current"))
    }
}
