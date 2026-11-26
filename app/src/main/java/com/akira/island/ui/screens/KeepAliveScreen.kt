package com.akira.island.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.service.KeepAliveService
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Perms
import com.akira.island.ui.Perms.Vendor
import com.akira.island.ui.Pos
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile

@Composable
fun KeepAliveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vendor = Perms.vendor()
    val (battery, _) = rememberOnResume { Perms.batteryUnrestricted(context) }
    val s by SettingsRepo.state.collectAsState()

    Page(tr("Keep the island running"), onBack,
        subtitle = tr("Your phone's firmware may stop the island in the background — then it disappears until you turn it on again")) {
        GroupToggle(
            Pos.Single, Icons.Rounded.PushPin, tr("Keep the island loaded"),
            tr("The island stays even if you swipe the app away from recent apps. Android may show a quiet notification"),
            s.keepAlive, color = Tile.Purple,
        ) { v ->
            SettingsRepo.update { copy(keepAlive = v) }
            KeepAliveService.sync(context)
        }

        Group {
            GroupItem(
                if (Perms.hasAutostart()) Pos.First else Pos.Single, Icons.Rounded.BatteryChargingFull,
                tr("Unrestricted battery"),
                batteryHint(vendor), onClick = { Perms.openBattery(context) }, color = Tile.Green,
            ) { Status(battery) }
            if (Perms.hasAutostart()) {
                GroupItem(
                    Pos.Last, Icons.Rounded.RocketLaunch, tr("Autostart"),
                    tr("Turn it on for Dynamic Island — then it starts by itself after a reboot or being closed"),
                    onClick = { Perms.openAutostart(context) }, color = Tile.Orange,
                ) { Arrow() }
            }
        }

        SectionHeader(tr("Lock it in recent apps"))
        InfoText(lockHint(vendor))

        SectionHeader(tr("If the island still disappears"))
        Group {
            GroupItem(
                Pos.First, Icons.Rounded.Lock, tr("Accessibility"),
                tr("Turn the Dynamic Island service off and on again"),
                onClick = { Perms.openAccessibility(context) }, color = Tile.Blue,
            ) { Arrow() }
            AppDetailsItem(Pos.Last)
        }
    }
}

@Composable
internal fun KeepAliveItem(pos: Pos, open: (Screen) -> Unit) {
    GroupItem(pos, Icons.Rounded.BatteryChargingFull, tr("Keep the island running"),
        tr("Battery, autostart and locking in recent apps"),
        onClick = { open(Screen.KEEPALIVE) }, color = Tile.Green) { Arrow() }
}

@Composable
internal fun AppDetailsItem(pos: Pos) {
    val context = LocalContext.current
    GroupItem(pos, Icons.Rounded.Security, tr("App info in Android"),
        tr("Restricted settings, permissions, battery"),
        onClick = { Perms.openAppDetails(context) }, color = Tile.Gray)
}

private fun batteryHint(v: Vendor): String = when (v) {
    Vendor.XIAOMI -> tr("Battery saver or “Activity control” → “No restrictions”")
    Vendor.SAMSUNG -> tr("Battery → Unrestricted, and remove the island from Sleeping apps")
    Vendor.OPPO -> tr("Battery usage → allow background activity")
    else -> tr("Find Dynamic Island and choose “Don't optimize” or “Unrestricted”")
}

private fun lockHint(v: Vendor): String = when (v) {
    Vendor.XIAOMI -> tr(
        "Open recent apps, long-press the Dynamic Island card and tap the lock. HyperOS and MIUI don't close a " +
            "locked app, even when you clear recent apps.",
    )
    Vendor.OPPO -> tr(
        "Open recent apps, tap “⋮” above the Dynamic Island card (or pull it down) and choose “Lock”.",
    )
    Vendor.SAMSUNG -> tr(
        "Open recent apps, tap the icon above the Dynamic Island card and choose “Keep open”.",
    )
    else -> tr(
        "Open recent apps and pull the Dynamic Island card down or long-press it — a lock appears. A locked app " +
            "isn't closed when you clear recent apps.",
    )
}
