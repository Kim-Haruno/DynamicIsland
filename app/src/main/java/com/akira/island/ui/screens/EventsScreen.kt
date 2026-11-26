package com.akira.island.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.AirplanemodeInactive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.akira.island.core.tr
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Group
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile

@Composable
fun EventsScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    fun set(block: IslandSettings.() -> IslandSettings) = SettingsRepo.update(block)

    Page(tr("Events"), onBack) {
        SectionHeader(tr("Battery"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Bolt, tr("Charging started"), null,
                s.evChargeStart, color = Tile.Green) { set { copy(evChargeStart = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.PowerOff, tr("Charging stopped"), null,
                s.evChargeStop, color = Tile.Gray) { set { copy(evChargeStop = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.BatteryAlert, tr("Low battery"),
                tr("When the battery drops to 5%"),
                s.evLowBattery, color = Tile.Red) { set { copy(evLowBattery = it) } }
        }

        SectionHeader(tr("Connections"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Wifi, tr("Wi-Fi connected"), null,
                s.evWifiOn, color = Tile.Blue) { set { copy(evWifiOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.WifiOff, tr("Wi-Fi disconnected"), null,
                s.evWifiOff, color = Tile.Gray) { set { copy(evWifiOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.BluetoothConnected, tr("Headphones connected"),
                tr("Over Bluetooth, with the charge if it's known"),
                s.evHeadsetOn, color = Tile.Indigo) { set { copy(evHeadsetOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.BluetoothDisabled, tr("Headphones disconnected"), null,
                s.evHeadsetOff, color = Tile.Gray) { set { copy(evHeadsetOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Usb, tr("USB device connected"), null,
                s.evUsbOn, color = Tile.Teal) { set { copy(evUsbOn = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.UsbOff, tr("USB device disconnected"), null,
                s.evUsbOff, color = Tile.Gray) { set { copy(evUsbOff = it) } }
        }

        SectionHeader(tr("Modes"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.AirplanemodeActive, tr("Airplane mode on"), null,
                s.evAirplaneOn, color = Tile.Orange) { set { copy(evAirplaneOn = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.AirplanemodeInactive, tr("Airplane mode off"), null,
                s.evAirplaneOff, color = Tile.Gray) { set { copy(evAirplaneOff = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.DoNotDisturbOn, tr("Do Not Disturb"),
                tr("A moon and the state when it changes"),
                s.evDnd, color = Tile.Indigo) { set { copy(evDnd = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Vibration, tr("Ring mode"),
                tr("Ring, vibrate or silent"),
                s.evRinger, color = Tile.Pink) { set { copy(evRinger = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.BrightnessAuto, tr("Brightness mode"),
                tr("When adaptive brightness is turned on or off"),
                s.evBrightness, color = Tile.Yellow) { set { copy(evBrightness = it) } }
        }

        SectionHeader(tr("Payments"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Nfc, tr("Hold near reader"),
                tr("When a phone payment is on screen — Mir Pay, T-Pay, SberPay, Yandex Pay and others. Stays until you pay or leave the app"),
                s.evPayReady, color = Tile.Blue) { set { copy(evPayReady = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.AccountBalance, tr("Hint when opening a bank"),
                tr("Hold near reader as soon as you open a bank or wallet app, not only on the payment screen"),
                s.payHintOnOpen, color = Tile.Gray) { set { copy(payHintOnOpen = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.CheckCircle, tr("Paid"),
                tr("A check mark and the amount — from the wallet screen, a notification or a bank SMS"),
                s.evPaySuccess, color = Tile.Green) { set { copy(evPaySuccess = it) } }
        }

        SectionHeader(tr("Other"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Alarm, tr("Alarm set"),
                tr("Wake-up time and how long you can sleep"),
                s.evAlarmSet, color = Tile.Orange) { set { copy(evAlarmSet = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.Lock, tr("Lock on the lock screen"),
                tr("While the phone is locked, the capsule shows a lock"),
                s.evLock, color = Tile.Indigo) { set { copy(evLock = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.LockOpen, tr("Device unlock"),
                tr("A lock opens when you unlock the phone"),
                s.evUnlock, color = Tile.Purple) { set { copy(evUnlock = it) } }
        }
    }
}
