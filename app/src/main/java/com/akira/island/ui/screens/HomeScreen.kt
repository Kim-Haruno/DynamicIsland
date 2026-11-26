package com.akira.island.ui.screens

import android.Manifest
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.akira.island.core.ChargingSpeed
import com.akira.island.core.ChargingTints
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.akira.island.R
import com.akira.island.core.AlarmInfo
import com.akira.island.core.AppMessage
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.SportInfo
import com.akira.island.core.MediaInfo
import com.akira.island.core.MsgAction
import com.akira.island.core.Notice
import com.akira.island.core.NoticeStyle
import com.akira.island.core.PayReady
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.island.Act
import com.akira.island.island.CompactContent
import com.akira.island.island.Ios
import com.akira.island.island.activeHeight
import com.akira.island.island.glassSurface
import com.akira.island.island.islandGlow
import com.akira.island.island.islandShape
import com.akira.island.island.rememberHaptic
import com.akira.island.service.NotificationParser
import com.akira.island.service.SystemBanners
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.ItemIcon
import com.akira.island.ui.Page
import com.akira.island.ui.Perms
import com.akira.island.ui.PillAction
import com.akira.island.ui.Pos
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile
import com.akira.island.ui.ToggleCard
import com.akira.island.ui.Ui
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class PermState(val accessibility: Boolean, val notifications: Boolean, val calls: Boolean, val bluetooth: Boolean) {
    val missing: List<String>
        get() = listOfNotNull(
            tr("accessibility").takeIf { !accessibility },
            tr("notifications").takeIf { !notifications },
            tr("calls").takeIf { !calls },
            "Bluetooth".takeIf { !bluetooth },
        )
}

@Composable
internal fun <T> rememberOnResume(read: () -> T): Pair<T, () -> Unit> {
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    return remember(tick) { read() } to { tick++ }
}

@Composable
private fun rememberPermState(): Pair<PermState, () -> Unit> {
    val context = LocalContext.current
    return rememberOnResume {
        PermState(Perms.accessibility(context), Perms.notifications(context), Perms.calls(context), Perms.bluetooth(context))
    }
}

@Composable
fun HomeScreen(open: (Screen) -> Unit) {
    val context = LocalContext.current
    val s by SettingsRepo.state.collectAsState()
    val running by IslandBus.serviceRunning.collectAsState()
    val (perms, _) = rememberPermState()
    val missing = perms.missing

    Page(tr("Dynamic Island")) {
        IslandPreview()

        if (!perms.accessibility) {
            StartCard(onOpen = { Perms.openAccessibility(context) }, onHelp = { open(Screen.INSTRUCTION) })
        }

        val popUp by SystemBanners.popUp.collectAsState()
        val doubled = popUp.filterValues { it }.keys.firstOrNull()
        if (doubled != null && !s.bannerHintHidden) {
            DoubleBannerCard(
                NotificationParser.appLabel(context, doubled),
                onFix = { open(Screen.BANNERS) },
                onHide = { SettingsRepo.update { copy(bannerHintHidden = true) } },
            )
        }

        ToggleCard(
            Icons.Rounded.PowerSettingsNew,
            tr("Island on"),
            when {
                !perms.accessibility -> tr("First turn on the service in Accessibility")
                running -> tr("The capsule shows over the camera")
                else -> tr("The service is starting…")
            },
            s.enabled, color = Tile.Green,
        ) { v -> SettingsRepo.update { copy(enabled = v) } }

        Group {
            GroupItem(
                Pos.First, Icons.Rounded.VerifiedUser, tr("Permissions"),
                if (missing.isEmpty()) tr("All granted") else tr("Needed: ") + missing.joinToString(", "),
                color = if (missing.isEmpty()) Tile.Green else Tile.Orange,
                onClick = { open(Screen.PERMISSIONS) },
            ) { if (missing.isEmpty()) Arrow() else CountBadge(missing.size) }
            KeepAliveItem(Pos.Middle, open)
            BanksItem(Pos.Middle, open)
            GroupItem(
                Pos.Last, Icons.Rounded.Science, tr("Try it"),
                tr("An event, message, call, payment — right in the island"),
                color = Tile.Purple, onClick = { open(Screen.TESTS) },
            ) { Arrow() }
        }
    }
}

@Composable
private fun StartCard(onOpen: () -> Unit, onHelp: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Ui.Primary.copy(alpha = 0.14f))
            .border(1.dp, Ui.Primary.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
            .padding(20.dp),
    ) {
        Text(tr("Turn on the island"), color = Ui.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            tr(
                "Only an accessibility service can draw the capsule next to the camera. Open Accessibility → Dynamic " +
                    "Island and turn it on.",
            ),
            color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().clip(CircleShape).background(Ui.Primary).clickable(onClick = onOpen).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(tr("Open Accessibility"), color = Ui.OnPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(Modifier.height(16.dp))
            Text(restrictedHelp(), color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().clip(CircleShape).border(1.dp, Ui.Primary, CircleShape)
                    .clickable { Perms.openAppDetails(context) }.padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tr("Open App info"), color = Ui.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(top = 4.dp).clip(CircleShape).clickable(onClick = onHelp).padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(tr("Switch grayed out? Instructions"), color = Ui.Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

internal fun restrictedHelp(): String = tr(
    "Android says “Restricted setting” or “App was denied access”? That's how it protects apps not from Google Play:\n" +
        "1. Try to turn the service on once — that window shows up, close it.\n" +
        "2. Open App info → “⋮” in the top right → “Allow restricted settings”.\n" +
        "3. Go back to Accessibility and turn the island on.",
)

@Composable
private fun DoubleBannerCard(app: String, onFix: () -> Unit, onHide: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ui.Card).padding(20.dp),
    ) {
        Text(tr("Messages show up twice?"), color = Ui.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            tr(
                "Both the island and $app itself show the message. Turn off its “Pop on screen” — only the island will " +
                    "remain, and the notification stays in the shade.",
            ),
            color = Ui.Sub, fontSize = 14.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillAction(tr("Set up"), onClick = onFix)
            PillAction(tr("Hide"), onClick = onHide)
        }
    }
}

@Composable
fun PermissionsScreen(onBack: () -> Unit, open: (Screen) -> Unit) {
    val context = LocalContext.current
    val (perms, refresh) = rememberPermState()
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val callLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refresh()
        if (result.values.none { it } && !Perms.calls(context)) Perms.openAppDetails(context)
    }

    Page(tr("Permissions"), onBack,
        subtitle = tr("The island doesn't store or send your notifications, calls or screens: permissions are only used to show events")) {
        Group {
            GroupItem(
                Pos.First, Icons.Rounded.AccessibilityNew, tr("Accessibility"),
                if (perms.accessibility) tr("On — the island draws over the status bar")
                else tr("Needed: without it there's no island"),
                onClick = { Perms.openAccessibility(context) }, color = Tile.Blue,
            ) { Status(perms.accessibility) }
            GroupItem(
                Pos.Middle, Icons.Rounded.Notifications, tr("Notification access"),
                if (perms.notifications) tr("On — music, messages, progress and timers")
                else tr("Needed for music, messages, progress and timers"),
                onClick = { Perms.openNotifications(context) }, color = Tile.Red,
            ) { Status(perms.notifications) }
            GroupItem(
                Pos.Middle, Icons.Rounded.Call, tr("Calls"),
                if (perms.calls) tr("Incoming calls, names from contacts, answer button")
                else tr("So the island shows incoming calls"),
                onClick = { if (perms.calls) Perms.openAppDetails(context) else callLauncher.launch(Perms.CALL_PERMISSIONS) },
                color = Tile.Green,
            ) { Status(perms.calls) }
            GroupItem(
                Pos.Last, Icons.Rounded.Bluetooth, "Bluetooth",
                if (perms.bluetooth) tr("Headphone names when they connect")
                else tr("To see headphones connect"),
                onClick = {
                    if (!perms.bluetooth && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    else Perms.openAppDetails(context)
                },
                color = Tile.Indigo,
            ) { Status(perms.bluetooth) }
        }

        SectionHeader(tr("If it doesn't turn on"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.Lightbulb, tr("Instructions"),
                tr("Grayed-out switch on Android 13+, battery and autostart"),
                onClick = { open(Screen.INSTRUCTION) }, color = Tile.Yellow) { Arrow() }
            KeepAliveItem(Pos.Middle, open)
            AppDetailsItem(Pos.Last)
        }
    }
}

private class Demo(val icon: ImageVector, val title: String, val caption: String, val color: Color, val run: () -> Unit)

@Composable
fun TestsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val demos = remember { demos(context) }
    Page(tr("Try it"), onBack,
        subtitle = tr("Tap — the example shows up in the island at the top of the screen")) {
        demos.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { d ->
                    DemoTile(d, Modifier.weight(1f).fillMaxHeight()) {
                        haptic()
                        d.run()
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoTile(d: Demo, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Ui.Card).clickable(onClick = onClick).padding(16.dp)) {
        ItemIcon(d.icon, d.color, 40.dp)
        Spacer(Modifier.height(14.dp))
        Text(d.title, color = Ui.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(d.caption, color = Ui.Sub, fontSize = 13.sp, lineHeight = 17.sp)
    }
}

private var demoChargingIndex = 0

private fun demos(context: Context): List<Demo> = listOf(
    Demo(Icons.Rounded.Bolt, tr("Event"), tr("Charging 75% at full width"), Tile.Green) {
        val speeds = arrayOf(ChargingSpeed.SLOW, ChargingSpeed.STANDARD, ChargingSpeed.FAST)
        val speed = speeds[(demoChargingIndex++) % speeds.size]
        val wattsStr = when (speed) {
            ChargingSpeed.SLOW -> " · ~3 W"
            ChargingSpeed.STANDARD -> " · ~10 W"
            ChargingSpeed.FAST -> " · ~25 W"
        }
        IslandBus.post(
            Notice(
                title = tr(speed.label),
                state = "75%$wattsStr",
                icon = Ios.load(context, R.drawable.ios_flash),
                tint = ChargingTints.colorFor(speed),
                brush = ChargingTints.brushFor(speed),
                battery = 75,
            )
        )
    },
    Demo(Icons.Rounded.NotificationsOff, tr("Ring mode"),
        tr("The capsule shakes, the bell swings"), Tile.Red) {
        IslandBus.post(Notice("", tr("Silent"), Ios.load(context, R.drawable.ios_notifications_off), Tints.Red, style = NoticeStyle.PILL))
    },
    Demo(Icons.Rounded.ChatBubble, tr("Message"),
        tr("With Reply and Mark as read buttons"), Tile.Blue) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val test = PendingIntent.getBroadcast(context, 0, Intent("com.akira.island.TEST").setPackage(context.packageName), flags)
        val input = RemoteInput.Builder("reply").setLabel(tr("Reply")).build()
        IslandBus.post(
            Notice(
                tr("Anna"), icon = Ios.load(context, R.drawable.ios_chatbubble_ellipses), tint = Tints.Blue,
                style = NoticeStyle.MESSAGE,
                extraMs = 4_500,
                message = AppMessage("test", context.packageName, tr("Island"),
                    tr("Hi! This is how a notification looks in the island"),
                    null, NotificationParser.icon(context, context.packageName), null,
                    actions = listOf(
                        MsgAction(tr("Reply"), test, listOf(input)),
                        MsgAction(tr("Mark as read"), test),
                    ),
                    history = listOf(tr("How are you?"), tr("Got a minute?"),
                        tr("Hi! This is how a notification looks in the island")),
                ),
            ),
        )
    },
    Demo(Icons.Rounded.Call, tr("Incoming call"), tr("You can answer or decline"), Tile.Green) {
        Controls.showTestCall()
    },
    Demo(Icons.Rounded.HourglassBottom, tr("Timer"), tr("A one-minute countdown"), Tile.Orange) {
        Controls.startTimer(1)
    },
    Demo(Icons.Rounded.Alarm, tr("Alarm"), tr("Wake-up time and how long you can sleep"), Tile.Indigo) {
        IslandBus.post(
            Notice(
                title = tr("Alarm"),
                icon = Ios.load(context, R.drawable.ios_alarm),
                tint = Tints.Orange,
                style = NoticeStyle.ALARM,
                alarm = AlarmInfo(System.currentTimeMillis() + (8 * 60 + 15) * 60_000L, null, null),
                extraMs = 2_000,
            ),
        )
    },
    Demo(Icons.Rounded.Nfc, tr("Payment"), tr("Hold near reader, then Paid with the amount"), Tile.Teal) {
        val pkg = context.packageName
        IslandBus.payReady.value = PayReady(pkg, NotificationParser.icon(context, pkg))
        IslandBus.scope.launch {
            delay(3000)
            if (IslandBus.payReady.value?.pkg == pkg) IslandBus.payReady.value = null
            IslandBus.post(
                Notice(
                    tr("Paid"), tr("$12.50"), Ios.load(context, R.drawable.ios_checkmark_circle), Tints.Green,
                    caption = tr("Island"), style = NoticeStyle.PAY_DONE, extraMs = 900,
                ),
            )
        }
    },
    Demo(Icons.Rounded.Navigation, tr("Navigation"), tr("The turn arrow and meters to it"), Tile.Purple) {
        Controls.showTestLive(context)
    },
    Demo(Icons.Rounded.EmojiEvents, tr("Live Match"), tr("Live match score and latest event"), Tile.Indigo) {
        val homeBadge = createTeamBadge("ARS", Color(0xFFEF0107))
        val awayBadge = createTeamBadge("CHE", Color(0xFF034694))
        val testSport = SportInfo(
            key = "test-sport",
            pkg = context.packageName,
            competition = "Premier League",
            homeTeam = "Arsenal",
            awayTeam = "Chelsea",
            homeScore = "2",
            awayScore = "1",
            homeBadge = homeBadge,
            awayBadge = awayBadge,
            latestEvent = "GOAL! Saka scores with a brilliant strike!",
            postTime = System.currentTimeMillis(),
            open = null,
        )
        IslandBus.sports.value = IslandBus.sports.value + ("test-sport" to testSport)
        IslandBus.scope.launch {
            delay(15_000)
            IslandBus.sports.value = IslandBus.sports.value - "test-sport"
        }
    },
    Demo(Icons.Rounded.Vibration, tr("Haptic feedback"), tr("Test sequence of ticks, clicks, and confirm haptics"), Tile.Pink) {
        val view = android.view.View(context)
        IslandBus.post(
            Notice(
                title = tr("Haptics"),
                state = tr("Test"),
                icon = Ios.load(context, R.drawable.ios_flash),
                tint = Tints.Purple,
                style = NoticeStyle.PILL,
                extraMs = 1200,
            ),
        )
        IslandBus.scope.launch {
            Controls.performHapticFeedback(context, view, android.view.HapticFeedbackConstants.CLOCK_TICK)
            delay(220)
            Controls.performHapticFeedback(context, view, android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            delay(260)
            Controls.performHapticFeedback(
                context,
                view,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) android.view.HapticFeedbackConstants.CONFIRM
                else android.view.HapticFeedbackConstants.KEYBOARD_TAP,
            )
        }
    },
)

private fun createTeamBadge(text: String, color: Color): androidx.compose.ui.graphics.ImageBitmap {
    val size = 96
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = android.graphics.Color.rgb((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    val textPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = android.graphics.Color.WHITE
        textSize = 32f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val x = size / 2f
    val y = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(text, x, y, textPaint)
    return bitmap.asImageBitmap()
}

@Composable
internal fun Status(ok: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (ok) Tints.Green.copy(alpha = 0.18f) else Tints.Orange.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(if (ok) tr("Yes") else tr("No"), color = if (ok) Tints.Green else Tints.Orange, fontSize = 13.sp)
    }
}

@Composable
private fun CountBadge(n: Int) {
    Box(Modifier.size(26.dp).clip(CircleShape).background(Tile.Orange), contentAlignment = Alignment.Center) {
        Text("$n", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Arrow() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Ui.Sub)
}

@Composable
fun IslandPreview(expanded: Boolean = false) {
    val s by SettingsRepo.state.collectAsState()
    val demo = remember {
        MediaInfo(
            key = "demo", pkg = "", title = tr("Demo"), artist = "", album = null, art = null,
            accent = Tints.Lavender, durationMs = 0, positionMs = 0, positionTime = SystemClock.elapsedRealtime(),
            speed = 1f, playing = true, canSeek = false,
        )
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3B3860), Color(0xFF1F1E27)))),
        contentAlignment = Alignment.TopCenter,
    ) {
        val act = if (expanded) Act.Idle else Act.MediaA(demo)
        val corner = (if (expanded) s.height.dp else activeHeight(s)) / 2
        val shape = islandShape(corner, s)
        Box(
            Modifier
                .padding(top = if (s.notch) 0.dp else 18.dp)
                .then(if (!expanded && s.fxGlow && !s.reduceEffects) Modifier.drawBehind { islandGlow(demo.accent, 1f, corner.toPx(), s.notch) } else Modifier)
                .clip(shape)
                .then(
                    if (s.liquidGlass) Modifier.glassSurface(shape, Color(s.color), s.glassOpacity / 100f)
                    else Modifier.background(Color(s.color)),
                )
                .then(if (s.outline) Modifier.border(s.outlineWidth.dp, if (s.outlineFromArt) Tints.Lavender else Color(s.outlineColor), shape) else Modifier)
                .animateContentSize(),
        ) {
            CompactContent(act, s, 400.dp)
        }
    }
}
