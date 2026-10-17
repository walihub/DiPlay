package com.shilapi.xcertplay

import android.content.Context
import android.view.View
import local.arcfox.pip.PipLaunchOverride
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PipLaunchOverrideTest {
    class Pip(context: Context, private val type: Int) : View(context) {
        var target = ""
        fun getMinimalismItemType() = type
        fun setAppClazz(value: String) { target = value }
    }
    class App(@JvmField val b: String)
    private val app = RuntimeEnvironment.getApplication()
    @Before fun clean() {
        app.getSharedPreferences("dudu_pip_launch_override", 0).edit().clear().commit()
    }
    @Test fun selectionUpdatesCardImmediatelyAndPersistsWithoutChangingDefaultNavigation() {
        val global = app.getSharedPreferences("original-navigation", 0)
        global.edit().putString("SDATA_NORMAL_NAVI_CLASS", "com.autonavi.amapauto").commit()
        val view = Pip(app, 106)
        assertEquals("com.autonavi.amapauto", PipLaunchOverride.resolve(view, "com.autonavi.amapauto"))
        assertTrue(PipLaunchOverride.select(view, App("cn.kuwo.kwmusiccar")))
        assertEquals("cn.kuwo.kwmusiccar", view.target)
        val reopened = Pip(app, 106)
        assertEquals("cn.kuwo.kwmusiccar", PipLaunchOverride.resolve(reopened, "com.autonavi.amapauto"))
        assertEquals("com.autonavi.amapauto", global.getString("SDATA_NORMAL_NAVI_CLASS", ""))
    }
    @Test fun otherPipSlotsCannotReadOrOverwriteNavigationCardOverride() {
        assertTrue(PipLaunchOverride.select(Pip(app, 106), App("cn.kuwo.kwmusiccar")))
        for (type in listOf(102, 103, 104)) {
            val other = Pip(app, type)
            assertEquals("other-slot", PipLaunchOverride.resolve(other, "other-slot"))
            assertFalse(PipLaunchOverride.select(other, App("com.other.app")))
            assertEquals("", other.target)
        }
        assertEquals("cn.kuwo.kwmusiccar", PipLaunchOverride.resolve(Pip(app, 106), "native"))
    }
    @Test fun invalidSelectionDoesNotChangeStoredLaunchTarget() {
        val view = Pip(app, 106)
        assertFalse(PipLaunchOverride.select(view, App("")))
        assertFalse(PipLaunchOverride.select(view, null))
        assertEquals("native", PipLaunchOverride.resolve(view, "native"))
    }
}
