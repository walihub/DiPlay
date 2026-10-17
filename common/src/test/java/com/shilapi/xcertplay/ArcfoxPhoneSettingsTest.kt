package com.shilapi.xcertplay

import android.view.View
import android.widget.LinearLayout
import android.widget.Button
import android.graphics.Bitmap
import android.graphics.Canvas
import com.shilapi.xcertplay.vehicle.arcfox.ArcfoxNativePhone
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "en", manifest = Config.NONE)
class ArcfoxPhoneSettingsTest {
    @Test fun defaultRemainsSystemAndChoicePersists() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        activity.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
        assertFalse(ArcfoxNativePhone.enabled(activity))
        val content = LinearLayout(activity)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "arcfoxPhoneControls",
            ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, content))
        val button = content.getChildAt(0) as Button
        assertTrue(button.text.toString().contains("System default"))
        button.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        dialog.listView.performItemClick(null, 1, 1)
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(ArcfoxNativePhone.enabled(activity))
        assertFalse(PendingReconnect.isPending(null))
        ArcfoxNativePhone.save(activity, false)
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "en-w320dp-h800dp-xhdpi")
    fun compact() = render("compact", 320)
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "zh-rCN-w800dp-h800dp-xhdpi")
    fun full() = render("full", 800)
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "ar-ldrtl-w600dp-h800dp-xhdpi")
    fun arabic() = render("arabic", 600)
    private fun render(name: String, widthDp: Int) {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        activity.applicationInfo.flags = activity.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        val config = android.content.res.Configuration(activity.resources.configuration).apply { fontScale = 1.6f }
        @Suppress("DEPRECATION")
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; layoutDirection = config.layoutDirection }
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "arcfoxPhoneControls",
            ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, content))
        val width = (widthDp * activity.resources.displayMetrics.density).toInt()
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        content.layout(0, 0, width, content.measuredHeight)
        assertTrue(content.height > 0)
        val button = content.getChildAt(0) as Button
        assertTrue("Choice text must fit at large font scale", button.layout.height <= button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
        val bitmap = Bitmap.createBitmap(width, content.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(android.graphics.Color.rgb(28,32,42)); content.draw(canvas)
        File("build/arcfox-phone-screenshots").mkdirs()
        File("build/arcfox-phone-screenshots/$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
}
