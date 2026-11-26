package com.akira.island.service

import android.content.Context
import android.os.BatteryManager
import com.akira.island.core.ChargingSpeed
import kotlin.math.abs

/**
 * Measures how fast the phone is actually charging and sorts it into slow / standard / super fast.
 *
 * The old code guessed from "max_charging_current" and "max_charging_voltage", which many phones leave at fixed
 * or zero values, and it read the speed once, at plug-in, before the charger had finished negotiating.
 * This reads the live current (BATTERY_PROPERTY_CURRENT_NOW) and battery voltage, which every phone reports.
 *
 * Notes:
 *  - The number is power going INTO the battery, so it is a little lower than the charger's rating
 *    (a 25 W charger shows about 20 W) and drops while the screen is busy. The thresholds allow for that.
 *  - A tier needs two readings in a row. The tier of a session is the best sustained reading, so a short dip
 *    or the slowdown near 100 % doesn't change the colour, and a single spike can't make it "super fast".
 *  - Some phones report mA instead of µA, or the opposite sign; both are handled.
 */
object ChargeMeter {
    /** Below this (watts into the battery) is slow: a 5 W adapter, a laptop USB port, a weak wireless pad. */
    const val SLOW_BELOW_W = 5.0

    /** From this is super fast: about a 25 W charger or better. */
    const val FAST_FROM_W = 15.0

    private var prev = 0.0
    private var samples = 0
    private var peak = 0.0

    data class LiveMetrics(
        val watts: Double,
        val milliAmps: Int?,
    )

    /** Call when the charger is unplugged or just plugged in: a new session starts from scratch. */
    fun reset() {
        prev = 0.0
        samples = 0
        peak = 0.0
    }

    /** Reads live power in watts and current in mA (with sign normalization). */
    fun readMetrics(
        context: Context,
        voltageMv: Int,
        charging: Boolean,
        plugged: Int,
        status: Int,
    ): LiveMetrics? {
        val vMv = if (voltageMv >= 2500) voltageMv else 4000
        val bm = context.getSystemService(BatteryManager::class.java) ?: return null
        val raw = runCatching { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrNull() ?: return null
        if (raw == Int.MIN_VALUE || raw == 0) return null
        val micro = abs(raw.toLong()).let { if (it < 20_000) it * 1000 else it }
        val watts = micro / 1_000_000.0 * vMv / 1000.0
        if (watts !in 0.2..150.0) return null

        val absMa = (micro / 1000).toInt()
        val milliAmps = when {
            plugged != 0 && status == BatteryManager.BATTERY_STATUS_FULL -> 0
            charging -> absMa
            else -> -absMa
        }
        return LiveMetrics(watts, milliAmps)
    }

    /** Power flowing into the battery right now in watts, or null if this phone doesn't report it. */
    fun readWatts(context: Context, voltageMv: Int): Double? {
        return readMetrics(
            context = context,
            voltageMv = voltageMv,
            charging = true,
            plugged = BatteryManager.BATTERY_PLUGGED_AC,
            status = BatteryManager.BATTERY_STATUS_CHARGING,
        )?.watts
    }

    /** The tier for the current wattage reading. */
    fun classify(watts: Double): ChargingSpeed {
        samples++
        if (samples == 1) {
            peak = watts
        } else {
            peak = maxOf(peak, watts)
        }
        prev = watts
        return when {
            peak >= FAST_FROM_W -> ChargingSpeed.FAST
            peak < SLOW_BELOW_W -> ChargingSpeed.SLOW
            else -> ChargingSpeed.STANDARD
        }
    }
}
