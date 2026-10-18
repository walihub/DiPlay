package com.shilapi.xcertplay

import android.content.Context
import android.os.Looper
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.orchestration.*
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import com.shilapi.xcertplay.transport.UsbDeviceId
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], shadows = [DiPlayAuthenticationTest.NavigationOutputs::class])
@LooperMode(LooperMode.Mode.PAUSED)
class DiPlayAuthenticationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val directory get() = File(context.noBackupFilesDir, "offline-mfi")

    @Before fun setUp() {
        // The JVM test host has no vehicle SOME/IP gateway.
        shadowOf(context).declareActionUnbindable("com.ts.car.someip.SomeIpServerService")
        DiPlayBootstrap::class.java.getDeclaredField("ready").apply { isAccessible = true }
            .setBoolean(null, false)
    }

    @Test fun freshInstallStillDefaultsToLocalAuthentication() {
        assertEquals(MfiTarget.LOCAL, AirPlayPersistence.loadMfiTarget(context))
        assertThrows(Exception::class.java) { DiPlayBootstrap.ensure(context, MfiTarget.LOCAL) }
    }

    @Test fun savedUsbChoiceSurvivesBootstrapWithoutLocalAssets() {
        AirPlayPersistence.saveMfiTarget(context, MfiTarget.USB_CH341)
        DiPlayBootstrap.ensure(context, AirPlayPersistence.loadMfiTarget(context))
        assertEquals(MfiTarget.USB_CH341, AirPlayPersistence.loadMfiTarget(context))
        assertFalse(directory.exists())
        assertFalse(File(context.noBackupFilesDir, "offline-mfi-staging").exists())
    }

    @Test fun usbBootstrapDoesNotReadAnExistingBrokenLocalIdentity() {
        assertTrue(directory.mkdirs())
        File(directory, "identity.pk8").writeText("invalid local identity")
        DiPlayBootstrap.ensure(context, MfiTarget.USB_CH341)
        assertEquals("invalid local identity", File(directory, "identity.pk8").readText())
        assertFalse(File(directory, "certificate.p7b").exists())
        assertThrows(Exception::class.java) { DiPlayBootstrap.ensure(context, MfiTarget.LOCAL) }
    }

    @Test fun switchingFromUsbToLocalStillRequiresLocalIdentity() {
        DiPlayBootstrap.ensure(context, MfiTarget.USB_CH341)
        assertThrows(Exception::class.java) { DiPlayBootstrap.ensure(context, MfiTarget.LOCAL) }
    }

    @Test fun usbControllerWaitsForHardwareWithoutLocalAssets() {
        val statuses = startAuthentication(MfiTarget.USB_CH341)
        assertTrue(statuses.contains(CarPlayStatus.WaitingForMfi))
        assertFalse(statuses.any { it is CarPlayStatus.Failed })
        assertFalse(directory.exists())
    }

    @Test fun existingLocalDirectoryCannotOverrideUsbControllerSelection() {
        assertTrue(directory.mkdirs())
        File(directory, "identity.pk8").writeText("invalid local identity")
        val statuses = startAuthentication(MfiTarget.USB_CH341)
        assertTrue(statuses.contains(CarPlayStatus.WaitingForMfi))
        assertFalse(statuses.any { it is CarPlayStatus.Failed })
    }

    @Test fun missingLocalIdentityFailsWithoutFallingBackToUsb() {
        val statuses = startAuthentication(MfiTarget.LOCAL)
        assertTrue(statuses.any { it is CarPlayStatus.Failed })
        assertFalse(statuses.contains(CarPlayStatus.WaitingForMfi))
    }

    @Test fun arcfoxWirelessControllerCanAttachBeforeItsGenerationFieldIsInitialized() {
        com.shilapi.xcertplay.vehicle.VehicleProfiles.save(context,
            com.shilapi.xcertplay.vehicle.VehicleProfile.ARCFOX_T)
        val statuses = startAuthentication(MfiTarget.LOCAL, transport = CarPlayTransport.WIRELESS)
        // Missing test credentials should report an ordinary authentication error, not throw
        // while constructing the wireless controller before its fields have been initialized.
        assertTrue(statuses.any { it is CarPlayStatus.Failed })
        assertFalse(statuses.contains(CarPlayStatus.MfiReady))
    }

    private fun nativeContext(): Context {
        val base = context
        val baseResources = base.resources
        val resources = object : android.content.res.Resources(baseResources.assets,
            baseResources.displayMetrics, baseResources.configuration) {
            override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int =
                if (name == "arcfox_native_mfi" && defType == "bool") 0x7fff1234
                else super.getIdentifier(name, defType, defPackage)
            override fun getBoolean(id: Int): Boolean = if (id == 0x7fff1234) true else super.getBoolean(id)
        }
        return object : android.content.ContextWrapper(base) {
            override fun getResources() = resources
            override fun getApplicationContext(): Context = this
        }
    }

    @Test fun nativeBuildSkipsExistingOfflineIdentityInsteadOfLoadingIt() {
        assertTrue(directory.mkdirs())
        File(directory, "identity.pk8").writeText("not a valid offline identity")
        DiPlayBootstrap.ensure(nativeContext(), MfiTarget.LOCAL)
        assertEquals("not a valid offline identity", File(directory, "identity.pk8").readText())
        assertFalse(File(directory, "certificate.p7b").exists())
    }

    @Test fun nativeBuildRejectsOtherFirmwareWithoutOfflineFallback() {
        val statuses = startAuthentication(MfiTarget.LOCAL, nativeContext())
        val failed = statuses.filterIsInstance<CarPlayStatus.Failed>()
        assertTrue(failed.toString(), failed.any { it.message.contains("Native MFi test build requires") })
        assertFalse(statuses.contains(CarPlayStatus.MfiReady))
        assertFalse(directory.exists())
    }

    // Authentication tests do not have a real SOME/IP service. Avoid starting a periodic OEM
    // output worker that can bind again after Robolectric replaces the application context.
    @Implements(com.shilapi.xcertplay.hud.BydNavigationOutputs::class, isInAndroidSdk = false)
    class NavigationOutputs {
        @Implementation fun start(context: Context) = Unit
    }

    private fun startAuthentication(target: MfiTarget, testContext: Context = context,
        transport: CarPlayTransport = CarPlayTransport.WIRED): List<CarPlayStatus> {
        val statuses = mutableListOf<CarPlayStatus>()
        val controller = CarPlayController(testContext,
            CarPlayRuntimeConfig(mfiTarget = target, transport = transport,
                ch341Devices = if (target == MfiTarget.USB_CH341) listOf(UsbDeviceId(0x1a86, 0x5512)) else emptyList(),
                identification = Iap2IdentificationConfig("test", "test", "test", "test", "1", "1", 3)),
            AirPlayConfig("test", "02:00:00:00:00:02", "02:00:00:00:00:01", "1", AirPlayDisplayConfig(1920, 990)),
            AirPlayIdentity.generate(), PairingStore(), object : AirPlaySessionListener {}, object : AirPlayMediaHandler {},
            { statuses.add(it) },
        )
        try {
            controller.javaClass.getDeclaredMethod("startMfi").apply { isAccessible = true }.invoke(controller)
            val executor = controller.javaClass.getDeclaredField("executor").apply { isAccessible = true }
                .get(controller) as ExecutorService
            executor.submit {}.get(5, TimeUnit.SECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            return statuses.toList()
        } finally {
            controller.close()
            assertTrue(controller.awaitClosed(2_000))
        }
    }
}
