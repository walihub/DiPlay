package com.shilapi.xcertplay

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.vehicle.VehicleProfile
import com.shilapi.xcertplay.vehicle.VehicleProfiles
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArcfoxProfileUiScreenshotsTest {
    @Test fun autoSelectedArcfoxAndSafeAreaAreReachableAtLargeFontsAndRtl() {
        val old = Build.FINGERPRINT
        val app = RuntimeEnvironment.getApplication()
        val original = Configuration(app.resources.configuration)
        ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", "AAOP_COCKPIT/HS7013A/test")
        try {
            for ((width, locale, density) in listOf(Triple(600, "zh-rCN", "xhdpi"),
                Triple(1280, "zh-rCN", "mdpi"), Triple(1280, "ar", "mdpi"))) {
                RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h1200dp-$density")
                app.resources.updateConfiguration(Configuration(app.resources.configuration).apply { fontScale = 1.5f },
                    app.resources.displayMetrics)
                val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
                activity.setTheme(android.R.style.Theme_Material_NoActionBar)
                val root = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(0xff181f2a.toInt())
                    setPadding(24, 24, 24, 24)
                    layoutDirection = activity.resources.configuration.layoutDirection
                }
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "setupCarStep",
                    ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, root))
                val choices = descendants(root).filterIsInstance<TextView>().filter { it.isClickable }
                assertEquals(5, choices.size)
                assertTrue(choices.single { it.text.contains(activity.getString(R.string.settings_arcfox_t)) }
                    .text.startsWith("✓"))
                assertNull("Automatic detection must not require saving a choice", VehicleProfiles.confirmed(activity))
                AirPlayPersistence.saveClusterMapEnabled(activity, false)
                ReflectionHelpers.setField(activity, "settingsSectionFilter", setOf(SettingsSection.CLUSTER_MAP))
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "allSettingsSections",
                    ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, root))
                assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.contains("920") && it.text.contains("432") })
                val pxWidth = (width * activity.resources.displayMetrics.density).toInt()
                root.measure(View.MeasureSpec.makeMeasureSpec(pxWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                assertEquals(1, choices.map { it.height }.distinct().size)
                choices.forEach { assertTrue("Choice must remain inside the panel", it.width > 0 && it.height > 0) }
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val working = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
                val module = if (working.fileName.toString() == "common") working else working.resolve("common")
                val output = module.resolve("build/reports/cluster-navigation-ui/$locale-${width}dp-$density-font1.5.png")
                Files.createDirectories(output.parent)
                Files.newOutputStream(output).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                bitmap.recycle()
                activity.finish()
            }
        } finally {
            ReflectionHelpers.setStaticField(Build::class.java, "FINGERPRINT", old)
            RuntimeEnvironment.setQualifiers("en")
            app.resources.updateConfiguration(original, app.resources.displayMetrics)
        }
    }
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }

}
