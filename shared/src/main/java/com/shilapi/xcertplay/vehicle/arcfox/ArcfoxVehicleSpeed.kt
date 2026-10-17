package com.shilapi.xcertplay.vehicle.arcfox

import android.content.Context
import android.os.SystemClock
import com.shilapi.xcertplay.transport.*
import com.shilapi.xcertplay.vehicle.arcfox.data.ArcfoxCarReader
import kotlin.math.abs

/** Separate from BYD's ADB path; only constructed for the selected Arcfox profile. */
class ArcfoxVehicleSpeed(context: Context) : VehicleSpeedSource {
    private val buffer = ArcfoxMotionBuffer()
    private val reader = ArcfoxCarReader(context, intArrayOf(ArcfoxCarReader.SPEED, ArcfoxCarReader.GEAR)) { values ->
        val speed = values[0]
        buffer.accept(speed?.number?.toDouble(), values[1]?.number?.toInt(),
            speed?.timestampNanos?.div(1_000_000), SystemClock.elapsedRealtime())
    }
    override fun start() { buffer.clear(); reader.start(250) }
    override fun stop() { reader.close(); buffer.clear() }
    override fun drain(): VehicleSpeedReading? = buffer.drain(SystemClock.elapsedRealtime())
}

internal class ArcfoxMotionBuffer {
    private var gear: VehicleGear? = null
    private var lastTimestamp: Long? = null
    private val samples = ArrayList<VehicleSpeedSample>()
    @Synchronized fun clear() { gear = null; lastTimestamp = null; samples.clear() }
    @Synchronized fun accept(speed: Double?, rawGear: Int?, timestamp: Long?, now: Long) {
        // Android VehicleGear bit values, not BYD's 1=P/2=R/3=N/4=D enum.
        val next = when (rawGear) {
            4 -> VehicleGear.PARK
            2 -> VehicleGear.REVERSE
            1 -> VehicleGear.NEUTRAL
            8, 16, 32, 64, 128, 256 -> VehicleGear.DRIVE
            else -> null
        }
        if (next == null || speed == null || !speed.isFinite() || abs(speed) > 83.334 ||
            timestamp == null || timestamp <= 0 || now - timestamp !in 0..2000) {
            clear(); return
        }
        if (next != gear) samples.clear() // Never label old samples with a new direction.
        gear = next
        if (timestamp == lastTimestamp) return // A cached HAL value is not a new sample.
        if (lastTimestamp != null && timestamp < lastTimestamp!!) samples.clear()
        lastTimestamp = timestamp
        if (samples.size == 40) samples.removeAt(0)
        samples += VehicleSpeedSample(timestamp, abs(speed)) // PERF_VEHICLE_SPEED is m/s; direction is in gear.
    }
    @Synchronized fun drain(now: Long): VehicleSpeedReading? {
        val current = gear ?: return null
        if (lastTimestamp == null || now - lastTimestamp!! !in 0..2000) { clear(); return null }
        if (samples.isEmpty()) return null
        return VehicleSpeedReading(current, samples.toList()).also { samples.clear() }
    }
}
