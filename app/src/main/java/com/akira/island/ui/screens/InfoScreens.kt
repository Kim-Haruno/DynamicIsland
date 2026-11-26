package com.akira.island.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.R
import com.akira.island.core.tr
import com.akira.island.service.LiveDebug
import com.akira.island.service.PayDebug
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.Page
import com.akira.island.ui.Perms
import com.akira.island.ui.PillAction
import com.akira.island.ui.Pos
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui

@Composable
fun InstructionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Page(tr("Instructions"), onBack) {
        Step(1, tr("Accessibility"),
            tr(
                "Open Settings → Accessibility → Installed apps → Dynamic Island and turn the service on. Only such a " +
                    "service can draw over the status bar, next to the camera.",
            ))
        Step(2, tr("If the switch is grayed out (Android 13+)"), restrictedHelp())
        Row { PillAction(tr("Open App info")) { Perms.openAppDetails(context) } }
        Step(3, tr("Notification access"),
            tr(
                "Needed for the music, download progress and Google Clock timer and stopwatch cards. The island doesn't " +
                    "store or send notifications anywhere.",
            ))
        Row { PillAction(tr("Open notification access")) { Perms.openNotifications(context) } }
        Step(4, tr("Calls"),
            tr(
                "On the Home tab open Permissions → Calls and allow access to the phone, call log and contacts. Then the " +
                    "island shows incoming calls with any dialer, shows the name from your contacts, and the answer and " +
                    "decline buttons work.",
            ))
        Step(5, tr("Place the capsule over the camera"),
            tr(
                "Appearance tab → Position and size. While the screen is open, the island is red: move it until the red sits " +
                    "exactly over the camera, pick the width and height and tap “Save”.",
            ))
        Step(6, tr("Battery (Xiaomi, Huawei, Samsung…)"),
            tr(
                "If the island disappears, allow the app to autostart and remove battery restrictions — otherwise the " +
                    "firmware kills the service.",
            ))
        Row {
            PillAction(tr("Battery")) { Perms.openBattery(context) }
            if (Perms.hasAutostart()) {
                Spacer(Modifier.width(8.dp))
                PillAction(tr("Autostart")) { Perms.openAutostart(context) }
            }
        }
        Step(7, tr("Replies and payments"),
            tr(
                "Under a messenger notification you get the notification's own buttons. “Reply” opens an input field right" +
                    " in the island. When a payment app shows its phone payment screen, the island shows “Hold near reader”, " +
                    "and after paying — a check mark with the amount. You can try all of this: Home → Try it.",
            ))
        Step(8, tr("Navigation"),
            tr(
                "While a navigation app is open (2GIS, Yandex Navigator, Maps and others), the island reads the distance " +
                    "to the next turn, the street and the arrival time from its screen, and recognizes the turn arrow from the" +
                    " picture. Other apps' screens aren't read. If the island disappears after an update, turn it off and on " +
                    "again in Accessibility.",
            ))
        Step(9, tr("Gestures"),
            tr(
                "Tap — expand, pull down — expand, pull up or touch outside the island — collapse, push sideways — next or" +
                    " previous track, tap the bubble — expand the second activity.",
            ))
    }
}

@Composable
private fun Step(n: Int, title: String, text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ui.Card).padding(18.dp),
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Ui.Primary), contentAlignment = Alignment.Center) {
            Text("$n", color = Ui.OnPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Ui.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(4.dp))
            Text(text, color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun AppInfoScreen(open: (Screen) -> Unit) {
    val version = "1.6.5"

    Page(tr("About")) {
        AppHeader(version)

        SectionHeader(tr("Help"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.Lightbulb, tr("Instructions"),
                tr("How to turn the island on with Android 13+"),
                onClick = { open(Screen.INSTRUCTION) }, color = Tile.Yellow) { Arrow() }
            KeepAliveItem(Pos.Middle, open)
            GroupItem(Pos.Middle, Icons.Rounded.Navigation, tr("Navigation diagnostics"),
                tr("What a navigation, taxi or delivery app sends — if the distance doesn't show"),
                onClick = { open(Screen.DIAG) }, color = Tile.Teal) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.CreditCard, tr("Payment diagnostics"),
                tr("What the island saw when you paid — if “Paid” didn't show"),
                onClick = { open(Screen.PAYDIAG) }, color = Tile.Green) { Arrow() }
            GroupItem(Pos.Middle, Icons.Rounded.Info, tr("About the island"),
                tr("What it does and where the idea comes from"),
                onClick = { open(Screen.ABOUT) }, color = Tile.Indigo) { Arrow() }
            AppDetailsItem(Pos.Last)
        }
    }
}

@Composable
private fun AppHeader(version: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ui.Card).padding(vertical = 26.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)).background(Ui.Primary), contentAlignment = Alignment.Center) {
            Image(ImageVector.vectorResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(126.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(tr("Dynamic Island"), color = Ui.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(tr("Version $version"), color = Ui.Sub, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
        Text("Developer: McNeil Maseko", color = Ui.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun IdeaScreen(onBack: () -> Unit) {
    Page(tr("About the island"), onBack) {
        IslandPreview()
        SectionHeader(tr("What it does"))
        InfoText(
            tr(
                "An iPhone-style capsule over your Android camera: music with artwork, an equalizer and synced lyrics, a " +
                    "timer with a progress ring, a stopwatch, download progress, a flashlight with brightness control, " +
                    "notifications with replies, payments, navigation, charging and system events. A second activity goes into" +
                    " a bubble next to the island.",
            ),
        )
        SectionHeader(tr("Where the idea comes from"))
        InfoText(
            tr(
                "The idea and behavior come from the PC version, Dynamic Island for Windows " +
                    "(github.com/mihailkotovski/DynamicIsland): spring shape animations, the player on click, a sideways push " +
                    "to change tracks, a 1 to 99 minute timer with a ring around pause and an alarm, lyrics from LRCLIB.",
            ),
        )
        SectionHeader(tr("How it works"))
        InfoText(
            tr(
                "The capsule is drawn by an accessibility service, music data comes from the system media sessions, " +
                    "progress and timers from notifications. Animations run only while the capsule shows something; Reduce " +
                    "effects turns off the equalizer and the jelly.",
            ),
        )
    }
}

@Composable
internal fun InfoText(text: String) {
    Text(
        text, color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ui.Card).padding(20.dp),
    )
}

@Composable
fun DiagScreen(onBack: () -> Unit) {
    val dumps by LiveDebug.dumps.collectAsState()
    DumpScreen(
        tr("Navigation diagnostics"),
        tr(
            "Start a navigation, taxi or delivery app and come back here: below is what the app sends in its " +
                "notification. If the distance or the turn arrow doesn't show in the island, tap “Copy all” and send this " +
                "text.",
        ),
        tr("Nothing yet: no navigation, taxi or delivery notification has arrived."),
        dumps.values.toList(), onBack,
    )
}

@Composable
fun PayDiagScreen(onBack: () -> Unit) {
    val log by PayDebug.log.collectAsState()
    DumpScreen(
        tr("Payment diagnostics"),
        tr(
            "Pay with your phone and come back here: below is what the island saw (the payment screen, a bank " +
                "notification or SMS) and how it understood it. If “Paid” didn't show, tap “Copy all” and send the text. " +
                "Entries stay only in the phone's memory and are gone after a restart.",
        ),
        tr("Nothing yet: the island hasn't seen a payment screen or a bank notification."),
        log, onBack,
    )
}

@Composable
private fun DumpScreen(title: String, intro: String, empty: String, items: List<String>, onBack: () -> Unit) {
    val context = LocalContext.current
    Page(title, onBack) {
        InfoText(intro)
        if (items.isEmpty()) {
            Text(empty, color = Ui.Sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
        } else {
            Row {
                PillAction(tr("Copy all")) {
                    val clip = ClipData.newPlainText(title, items.joinToString("\n\n"))
                    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                }
            }
            items.forEach { dump ->
                Text(
                    dump, color = Ui.Text, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ui.Card).padding(16.dp),
                )
            }
        }
    }
}
