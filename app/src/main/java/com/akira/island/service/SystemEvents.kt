package com.akira.island.service

import android.app.KeyguardManager
import android.app.NotificationManager
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.akira.island.core.BatteryInfo
import com.akira.island.core.ChargingSpeed
import com.akira.island.core.ChargingTints
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.Tints
import com.akira.island.R
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.island.Ios
import kotlin.math.abs

class SystemEvents(private val context: Context) {
    private fun ic(id: Int) = Ios.load(context, id)

    private val main = Handler(Looper.getMainLooper())
    private val startedAt = SystemClock.elapsedRealtime()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val s get() = SettingsRepo.current

    private var lowWarned = false
    private var usbConnected: Boolean? = null
    private var brightnessMode = -1
    private var dnd: Boolean? = null
    private val headsetBattery = HashMap<String, Int>()
    private val wifiNetworks = HashSet<Network>()
    private var lastPowerChange = 0L

    private val warmingUp get() = SystemClock.elapsedRealtime() - startedAt < 3000

    private fun notice(enabled: Boolean, n: Notice) {
        if (enabled && !warmingUp) IslandBus.post(n)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = handle(intent, isInitialStickyBroadcast)
    }

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.post {
                val wasEmpty = wifiNetworks.isEmpty()
                wifiNetworks += network
                if (wasEmpty) notice(s.evWifiOn, Notice("Wi-Fi", tr("Connected"), ic(R.drawable.ios_wifi), Tints.Blue))
            }
        }

        override fun onLost(network: Network) {
            main.post {
                if (wifiNetworks.remove(network) && wifiNetworks.isEmpty()) {
                    notice(s.evWifiOff, Notice("Wi-Fi", tr("Disconnected"), ic(R.drawable.ios_wifi), Tints.Gray))
                }
            }
        }
    }

    private val brightnessObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            val mode = Settings.System.getInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0,
            )
            if (mode == brightnessMode) return
            brightnessMode = mode
            val auto = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            notice(
                s.evBrightness,
                Notice(
                    tr("Brightness"),
                    if (auto) tr("Auto") else tr("Manual"),
                    if (auto) ic(R.drawable.ios_sunny) else ic(R.drawable.ios_contrast),
                    Tints.Yellow,
                ),
            )
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BT_BATTERY_CHANGED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(USB_STATE)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        dnd = isDnd()
        val power = context.getSystemService(PowerManager::class.java)
        IslandBus.screenOn.value = power?.isInteractive ?: true
        IslandBus.powerSave.value = power?.isPowerSaveMode == true
        IslandBus.locked.value = isLocked()
        IslandBus.keyguard.value = isKeyguard()
        brightnessMode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, brightnessObserver,
        )

        runCatching {
            connectivity?.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                wifiCallback,
            )
        }
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        runCatching { context.contentResolver.unregisterContentObserver(brightnessObserver) }
        runCatching { connectivity?.unregisterNetworkCallback(wifiCallback) }
    }

    private fun handle(intent: Intent, initialSticky: Boolean) {
        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> onBattery(intent)

            Intent.ACTION_POWER_CONNECTED -> {
                lastPowerChange = SystemClock.elapsedRealtime()
                ChargeMeter.reset()
                // A charger takes a moment to negotiate its speed, so the first reading is too low. Sample a few times
                // right after plugging in (not continuously), then announce with the speed that was actually reached.
                for (delayMs in longArrayOf(500, 1200)) main.postDelayed({ resampleBattery() }, delayMs)
                main.postDelayed({
                    val b = IslandBus.battery.value
                    val level = b.level
                    val speed = b.speed
                    notice(
                        s.evChargeStart,
                        Notice(
                            title = tr(speed.label),
                            state = if (level >= 0) "$level%" else null,
                            icon = ic(R.drawable.ios_flash),
                            tint = ChargingTints.colorFor(speed),
                            brush = ChargingTints.brushFor(speed),
                            battery = level.takeIf { it >= 0 },
                        ),
                    )
                }, 1300)
                main.postDelayed({ resampleBattery() }, 2400)
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                lastPowerChange = SystemClock.elapsedRealtime()
                ChargeMeter.reset()
                val level = IslandBus.battery.value.level
                notice(
                    s.evChargeStop,
                    Notice(tr("Unplugged"), if (level >= 0) "$level%" else null, ic(R.drawable.ios_battery_full), Tints.Orange,
                        battery = level.takeIf { it >= 0 }),
                )
            }

            Intent.ACTION_USER_PRESENT -> onUnlocked()

            Intent.ACTION_SCREEN_OFF -> {
                IslandBus.screenOn.value = false
                if (keyguard?.isDeviceSecure == true) IslandBus.locked.value = true
                IslandBus.reply.value = null
                if (IslandBus.timer.value?.ringing == true) Controls.cancelTimer()
                Controls.collapse()
            }

            Intent.ACTION_SCREEN_ON -> {
                IslandBus.screenOn.value = true
                IslandBus.locked.value = isLocked()
                IslandBus.keyguard.value = isKeyguard()
            }

            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                IslandBus.powerSave.value = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                val on = isDnd()
                if (on == dnd) return
                dnd = on
                notice(
                    s.evDnd,
                    Notice(tr("Do Not Disturb"), if (on) tr("On") else tr("Off"), ic(R.drawable.ios_moon),
                        if (on) Tints.Purple else Tints.Gray),
                )
            }

            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val on = intent.getBooleanExtra("state", false)
                if (on) notice(s.evAirplaneOn, Notice(tr("Airplane Mode"), tr("On"), ic(R.drawable.ios_airplane), Tints.Orange))
                else notice(s.evAirplaneOff, Notice(tr("Airplane Mode"), tr("Off"), ic(R.drawable.ios_airplane), Tints.Gray))
            }

            AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                val n = when (intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)) {
                    AudioManager.RINGER_MODE_NORMAL -> Notice("", tr("Ring"), ic(R.drawable.ios_notifications), Color.White, style = NoticeStyle.PILL)
                    AudioManager.RINGER_MODE_VIBRATE -> Notice("", tr("Vibrate"), ic(R.drawable.ios_phone_portrait),
                        Tints.Purple, style = NoticeStyle.PILL)
                    AudioManager.RINGER_MODE_SILENT -> Notice("", tr("Silent"), ic(R.drawable.ios_notifications_off),
                        Tints.Red, style = NoticeStyle.PILL)
                    else -> null
                }
                if (n != null) notice(s.evRinger, n)
            }

            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    ?: return
                val name = runCatching { device.name }.getOrNull()
                val isAudio = runCatching {
                    device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
                }.getOrDefault(false)
                if (!isAudio) return
                if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                    val battery = headsetBattery[device.address]
                    notice(
                        s.evHeadsetOn,
                        Notice(name ?: tr("Headphones"), null, ic(R.drawable.ios_headset), Tints.Green,
                            ring = battery?.let { it / 100f }, caption = tr("Connected")),
                    )
                } else {
                    headsetBattery.remove(device.address)
                    notice(s.evHeadsetOff, Notice(name ?: tr("Headphones"), null, ic(R.drawable.ios_headset), Tints.Gray,
                        caption = tr("Disconnected")))
                }
            }

            BT_BATTERY_CHANGED -> {
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    ?: return
                val level = intent.getIntExtra(EXTRA_BT_BATTERY, -1)
                if (level >= 0) headsetBattery[device.address] = level
            }

            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val dev = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                notice(s.evUsbOn, Notice(dev?.productName ?: tr("USB device"), tr("Connected"),
                    ic(R.drawable.ios_hardware_chip), Tints.Teal))
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val dev = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                notice(s.evUsbOff, Notice(dev?.productName ?: tr("USB device"), tr("Disconnected"),
                    ic(R.drawable.ios_hardware_chip), Tints.Gray))
            }

            USB_STATE -> {
                val connected = intent.getBooleanExtra("connected", false)
                val prev = usbConnected
                usbConnected = connected
                if (initialSticky || prev == null || prev == connected) return
                main.postDelayed({
                    if (abs(SystemClock.elapsedRealtime() - lastPowerChange) < 3000) return@postDelayed
                    if (connected) notice(s.evUsbOn, Notice("USB", tr("Connected"), ic(R.drawable.ios_hardware_chip), Tints.Teal))
                    else notice(s.evUsbOff, Notice("USB", tr("Disconnected"), ic(R.drawable.ios_hardware_chip), Tints.Gray))
                }, 1500)
            }
        }
    }

    private var lastUnlock = 0L

    fun onUnlocked() {
        IslandBus.locked.value = false
        IslandBus.keyguard.value = false
        val now = SystemClock.elapsedRealtime()
        if (now - lastUnlock < 3_000) return
        lastUnlock = now
        notice(s.evUnlock, Notice("", null, ic(R.drawable.ios_lock_open), Color.White, style = NoticeStyle.PILL))
    }

    private fun isLocked(): Boolean = keyguard?.isDeviceLocked == true

    private fun isKeyguard(): Boolean = keyguard?.isKeyguardLocked == true

    private fun isDnd(): Boolean {
        val filter = notifications?.currentInterruptionFilter ?: return false
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    private fun onBattery(intent: Intent) {
        val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = if (raw >= 0) raw * 100 / scale else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val charging = plugged != 0 &&
            (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        val voltageRaw = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val voltageMv = voltageRaw.takeIf { it > 0 }
        val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val tempC = tempRaw.takeIf { it > 0 }?.let { it / 10f }

        var watts: Float? = null
        var milliAmps: Int? = null
        val speed = if (charging || plugged != 0) {
            val metrics = ChargeMeter.readMetrics(context, voltageMv ?: 0, charging, plugged, status)
            watts = metrics?.watts?.toFloat()
            milliAmps = metrics?.milliAmps
            detectChargingSpeed(intent)
                ?: metrics?.watts?.let { ChargeMeter.classify(it) }
                ?: IslandBus.battery.value.takeIf { it.charging }?.speed
                ?: ChargingSpeed.STANDARD
        } else {
            ChargeMeter.reset()
            ChargingSpeed.STANDARD
        }
        IslandBus.battery.value = BatteryInfo(
            level = level,
            charging = charging,
            speed = speed,
            watts = watts,
            milliamps = milliAmps,
            voltageMv = voltageMv,
            tempC = tempC,
        )

        if (charging || level > 5) {
            lowWarned = false
        } else if (level >= 0 && !lowWarned) {
            lowWarned = true
            notice(s.evLowBattery, Notice(tr("Low Battery"), "$level%", ic(R.drawable.ios_battery_dead), Tints.Red, battery = level))
        }
    }

    /** Re-reads the sticky battery state, for the few samples right after plugging in. */
    private fun resampleBattery() {
        resampleBattery(context)
    }

    companion object {
        /**
         * Uses the charger-side current and voltage advertised in ACTION_BATTERY_CHANGED when present.
         * These extras are hidden Android implementation details, so absent or invalid values fall back
         * to the measured battery-side power.
         */
        private fun detectChargingSpeed(intent: Intent): ChargingSpeed? {
            val maxCurrentMicroAmps = intent.getIntExtra("max_charging_current", -1)
            val maxVoltageMicroVolts = intent.getIntExtra("max_charging_voltage", -1)
            if (maxCurrentMicroAmps <= 0 || maxVoltageMicroVolts <= 0) return null

            val chargerWatts = (maxCurrentMicroAmps / 1_000_000.0) *
                (maxVoltageMicroVolts / 1_000_000.0)
            return when {
                chargerWatts >= 18.0 -> ChargingSpeed.FAST
                chargerWatts < 7.5 -> ChargingSpeed.SLOW
                else -> ChargingSpeed.STANDARD
            }
        }
        const val BT_BATTERY_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BT_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL"
        const val USB_STATE = "android.hardware.usb.action.USB_STATE"

        fun resampleBattery(context: Context) {
            val intent = runCatching {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }.getOrNull() ?: return
            val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val level = if (raw >= 0) raw * 100 / scale else -1
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val charging = plugged != 0 &&
                (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
            val voltageRaw = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val voltageMv = voltageRaw.takeIf { it > 0 }
            val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val tempC = tempRaw.takeIf { it > 0 }?.let { it / 10f }

            var watts: Float? = null
            var milliAmps: Int? = null
            val speed = if (charging || plugged != 0) {
                val metrics = ChargeMeter.readMetrics(context, voltageMv ?: 0, charging, plugged, status)
                watts = metrics?.watts?.toFloat()
                milliAmps = metrics?.milliAmps
                detectChargingSpeed(intent)
                    ?: metrics?.watts?.let { ChargeMeter.classify(it) }
                    ?: IslandBus.battery.value.takeIf { it.charging }?.speed
                    ?: ChargingSpeed.STANDARD
            } else {
                ChargingSpeed.STANDARD
            }
            IslandBus.battery.value = BatteryInfo(
                level = level,
                charging = charging,
                speed = speed,
                watts = watts,
                milliamps = milliAmps,
                voltageMv = voltageMv,
                tempC = tempC,
            )
        }
    }
}
