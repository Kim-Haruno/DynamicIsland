package com.akira.island.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.KeyboardVoice
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.akira.island.core.tr
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.Group
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.IconAction
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile

@Composable
fun CardsScreen(onBack: () -> Unit, onInstruction: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    fun set(block: IslandSettings.() -> IslandSettings) = SettingsRepo.update(block)

    Page(tr("Dynamic cards"), onBack,
        action = { IconAction(Icons.Rounded.Lightbulb, tr("Instructions"), onInstruction) }) {
        SectionHeader(tr("Music and calls"))
        Group {
            GroupToggle(Pos.First, Icons.Outlined.PlayCircle, tr("Player controls"),
                tr("When music is playing and the player is in the background"),
                s.cardMedia, color = Tile.Pink) { set { copy(cardMedia = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.Call, tr("Calls"),
                tr("Incoming calls with answer buttons, and the call time"),
                s.cardCall, color = Tile.Green) { set { copy(cardCall = it) } }
        }

        SectionHeader(tr("Timers and alarm"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.HourglassEmpty, tr("Timer and stopwatch"),
                tr("Clock's timer and stopwatch in the background — even with its notifications off"),
                s.cardTimer, color = Tile.Orange) { set { copy(cardTimer = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.TouchApp, tr("Clock buttons"),
                tr("Pause and reset in the island press Clock's own buttons. Off — they just open Clock"),
                s.clockAutoTap, color = Tile.Orange) { set { copy(clockAutoTap = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.KeyboardVoice, tr("Timer from assistant"),
                tr("“Set a timer for 5 minutes” from an assistant or widget can go to the island"),
                s.timerFromAssistant, color = Tile.Pink) { set { copy(timerFromAssistant = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.Bedtime, tr("Alarm"),
                tr("12 hours before an alarm — the wake-up time and a sleep dial, like on iPhone"),
                s.cardAlarm, color = Tile.Indigo) { set { copy(cardAlarm = it) } }
        }

        SectionHeader(tr("System"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.FlashlightOn, tr("Flashlight controls"),
                tr("When the flashlight is on"),
                s.cardTorch, color = Tile.Yellow) { set { copy(cardTorch = it) } }
            GroupToggle(Pos.Middle, Icons.Rounded.OfflineBolt, tr("Charging indicator"),
                tr("When the device is charging"),
                s.cardCharging, color = Tile.Green) { set { copy(cardCharging = it) } }
            GroupToggle(Pos.Last, Icons.Rounded.Downloading, tr("Progress indicator"),
                tr("Downloads and uploads in Google Play, Telegram and other apps"),
                s.cardProgress, color = Tile.Teal) { set { copy(cardProgress = it) } }
        }

        SectionHeader(tr("On the road"))
        GroupToggle(Pos.Single, Icons.Rounded.Navigation, tr("Navigation, taxi and delivery"),
            tr("Google and Yandex Maps, Navigator, 2GIS, Yandex Go, Uber and others — distance and time in the capsule, details when expanded"),
            s.cardLive, color = Tile.Teal) { set { copy(cardLive = it) } }

        SectionHeader(tr("Sports"))
        GroupToggle(Pos.Single, Icons.Rounded.EmojiEvents, tr("Live sports"),
            tr("Google, FotMob, SofaScore, FlashScore and others — real-time score, team badges and live match events"),
            s.cardSports, color = Tile.Indigo) { set { copy(cardSports = it) } }

        SectionHeader(tr("Surprises"))
        GroupToggle(Pos.Single, Icons.Rounded.AutoAwesome, tr("Easter eggs"),
            tr("The island recognizes one special song and puts on a little show to its beat. Which one? Try to guess"),
            s.easterEggs, color = Tile.Red) { set { copy(easterEggs = it) } }
    }
}
