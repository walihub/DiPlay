package com.shilapi.xcertplay.vehicle.arcfox

import android.car.Car
import com.shilapi.xcertplay.vehicle.arcfox.data.ArcfoxCarReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class ArcfoxCarReaderTest {
    @Test fun reflectsOnlyAvailableNumbersAndRejectsErrorStatus() {
        Car.properties.clear(); Car.dead = false
        Car.properties[ArcfoxCarReader.SPEED] = Car.Property(0, 10.0f)
        Car.properties[ArcfoxCarReader.GEAR] = Car.Property(1, 8)
        val queue = LinkedBlockingQueue<Array<ArcfoxCarReader.Value?>>()
        val reader = ArcfoxCarReader(RuntimeEnvironment.getApplication(),
            intArrayOf(ArcfoxCarReader.SPEED, ArcfoxCarReader.GEAR)) { queue.offer(it) }
        try {
            reader.start(20)
            var sample = queue.poll(2, TimeUnit.SECONDS)!!
            if (sample[0] == null) sample = queue.poll(2, TimeUnit.SECONDS)!! // initial asynchronous binding
            assertEquals(10.0, sample[0]!!.number.toDouble(), 0.0)
            assertNull(sample[1])
            Car.properties[ArcfoxCarReader.SPEED] = Car.Property(0, "not a number")
            queue.clear()
            var invalid = queue.poll(2, TimeUnit.SECONDS)!!
            if (invalid[0] != null) invalid = queue.poll(2, TimeUnit.SECONDS)!!
            assertNull(invalid[0])
        } finally { reader.close() }
        assertTrue(Car.last.disconnected)
    }
    @Test fun lateDisconnectFromPreviousConnectionCannotCloseNewConnection() {
        Car.properties.clear(); Car.dead = false
        val queue = LinkedBlockingQueue<Boolean>()
        val reader = ArcfoxCarReader(RuntimeEnvironment.getApplication(), intArrayOf()) { queue.offer(true) }
        try {
            reader.start(20); assertNotNull(queue.poll(2, TimeUnit.SECONDS))
            val old = Car.last
            reader.close(); queue.clear()
            reader.start(20); assertNotNull(queue.poll(2, TimeUnit.SECONDS))
            val current = Car.last
            assertNotSame(old, current)
            old.connection.onServiceDisconnected(null)
            assertFalse(current.disconnected)
        } finally { reader.close() }
    }
}
