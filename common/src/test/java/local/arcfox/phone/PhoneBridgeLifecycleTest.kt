package local.arcfox.phone

import android.app.Service
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PhoneBridgeLifecycleTest {
    @Test fun recoveryHostHasStartedForegroundLifetimeIndependentOfClientBinding() {
        val controller = Robolectric.buildService(PhoneBridgeService::class.java).create()
        val service = controller.get()
        try {
            assertNotNull(Shadows.shadowOf(service).lastForegroundNotification)
            assertEquals(Service.START_STICKY, service.onStartCommand(null, 0, 1))
            val app = Shadows.shadowOf(RuntimeEnvironment.getApplication())
            val started = generateSequence { app.nextStartedService }.map { it.component?.className }.toList()
            assertTrue("host must retain its own started lifetime", PhoneBridgeService::class.java.name in started)
            val leaseIntent = Intent("arcfox.phone.lease.v1")
            val binder = service.onBind(leaseIntent)
            assertNotNull(binder)
            assertNull(service.onBind(Intent("unrelated")))
            service.onUnbind(leaseIntent)
            assertSame(binder, service.onBind(leaseIntent))
        } finally { controller.destroy() }
    }

    @Test fun replacementAndBootStartRecoveryButUnrelatedBroadcastDoesNot() {
        val app = RuntimeEnvironment.getApplication()
        val shadow = Shadows.shadowOf(app)
        val receiver = PhoneBridgeBootReceiver()
        receiver.onReceive(app, Intent("unrelated"))
        assertNull(shadow.nextStartedService)
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED)) {
            receiver.onReceive(app, Intent(action))
            assertEquals(PhoneBridgeService::class.java.name,
                shadow.nextStartedService.component!!.className)
        }
    }
}
