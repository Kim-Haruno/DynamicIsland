package com.akira.island.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BlurCircular
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.IslandBus
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.data.Elasticity
import com.akira.island.data.ExpandStyle
import com.akira.island.data.FxRate
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.ChoiceDialog
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.GroupSlider
import com.akira.island.ui.GroupToggle
import com.akira.island.ui.Page
import com.akira.island.ui.Pos
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile
import com.akira.island.ui.Ui

@Composable
fun CapsuleScreen(open: (Screen) -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    Page(tr("Appearance")) {
        IslandPreview()

        SectionHeader(tr("Position and size"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.OpenWith, tr("Position and size"),
                tr("Capsule ${s.width}×${s.height} dp — fit it to the camera"),
                color = Tile.Indigo, onClick = { open(Screen.SIZE) }) { Arrow() }
            GroupItem(Pos.Last, Icons.Rounded.SwapHoriz, tr("Size with activity"),
                if (s.activeWidth == 0) tr("Thickness +${s.activeExtra} dp, width as without it")
                else tr("Thickness +${s.activeExtra}, width +${s.activeWidth} dp"),
                color = Tile.Purple, onClick = { open(Screen.LOOK) }) { Arrow() }
        }

        SectionHeader(tr("Color and glass"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.FormatColorFill, tr("Capsule color"), tr("Color, outline and shadow"),
                color = Tile.Orange, onClick = { open(Screen.COLORS) }) { Arrow() }
            GroupToggle(
                if (s.liquidGlass) Pos.Middle else Pos.Last, Icons.Rounded.WaterDrop, "Liquid Glass",
                tr("Glass with highlights and a glowing edge, like in iOS. Highlights move when you tilt the phone"),
                s.liquidGlass, color = Tile.Teal, badge = tr("Experimental"),
            ) { v -> SettingsRepo.update { copy(liquidGlass = v) } }
            if (s.liquidGlass) {
                GroupSlider(
                    Pos.Last, Icons.Rounded.Opacity, tr("Glass density"),
                    value = s.glassOpacity.toFloat(), range = 40f..95f, label = "${s.glassOpacity}%", color = Tile.Teal,
                ) { v -> SettingsRepo.update { copy(glassOpacity = v.toInt()) } }
            }
        }

        SectionHeader(tr("Animations"))
        Group {
            GroupItem(Pos.First, Icons.Rounded.Animation, tr("Island expansion"),
                "${s.expandStyle.title} · ${s.durationPercent}%",
                color = Tile.Blue, onClick = { open(Screen.ANIM) }) { Arrow() }
            GroupToggle(Pos.Middle, Icons.Rounded.BubbleChart, tr("Liquid expansion"),
                tr("The content grows together with the island and emerges from a blur"),
                s.liquidExpand, color = Tile.Indigo, badge = tr("New"),
            ) { v -> SettingsRepo.update { copy(liquidExpand = v) } }
            ElasticityItem(Pos.Middle, s, Tile.Teal)
            GroupItem(Pos.Last, Icons.Rounded.AutoAwesome, tr("Effects"), effectsSummary(s),
                color = Tile.Purple, onClick = { open(Screen.EFFECTS) }) { Arrow() }
        }
    }
}

@Composable
private fun ElasticityItem(pos: Pos, s: IslandSettings, color: Color) {
    var dialog by remember { mutableStateOf(false) }
    GroupItem(pos, Icons.Rounded.Waves, tr("Elasticity"), s.elasticity.title, color = color, onClick = { dialog = true })
    if (dialog) {
        ChoiceDialog(tr("Animation elasticity"), Elasticity.entries, s.elasticity, { it.title },
            { v -> SettingsRepo.update { copy(elasticity = v) } }) { dialog = false }
    }
}

private fun previewActivity() {
    IslandBus.widthPreviewUntil.value = SystemClock.elapsedRealtime() + 2_500
}

@Composable
fun LookScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    Page(tr("Size with activity"), onBack) {
        IslandPreview()
        Group {
            GroupSlider(
                Pos.First, Icons.Rounded.LineWeight, tr("Thickness"),
                value = s.activeExtra.toFloat(), range = 0f..14f, label = "+${s.activeExtra} dp", steps = 13, color = Tile.Purple,
            ) { v ->
                previewActivity()
                SettingsRepo.update { copy(activeExtra = v.toInt()) }
            }
            GroupSlider(
                Pos.Last, Icons.Rounded.SwapHoriz, tr("Width"),
                value = s.activeWidth.toFloat(), range = 0f..120f, steps = 23,
                label = if (s.activeWidth == 0) tr("As without it") else "+${s.activeWidth} dp", color = Tile.Blue,
            ) { v ->
                previewActivity()
                SettingsRepo.update { copy(activeWidth = (v / 5).toInt() * 5) }
            }
        }
    }
}

@Composable
fun AnimScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var styleDialog by remember { mutableStateOf(false) }

    Page(tr("Island expansion"), onBack, action = {
        Text(tr("Reset"), color = Ui.Sub, fontSize = 16.sp, modifier = Modifier.clickable { SettingsRepo.resetAnimations() })
    }) {
        Group {
            GroupItem(Pos.First, Icons.Rounded.Animation, tr("Expansion style"), s.expandStyle.title, color = Tile.Purple,
                onClick = { styleDialog = true })
            GroupSlider(
                Pos.Middle, Icons.Rounded.Speed, tr("Duration"),
                value = s.durationPercent.toFloat(), range = 0f..100f, label = "${s.durationPercent}%", color = Tile.Indigo,
            ) { v -> SettingsRepo.update { copy(durationPercent = v.toInt()) } }
            GroupSlider(
                Pos.Middle, Icons.Rounded.Schedule, tr("Event display"),
                value = s.noticeDurationMs / 1000f, range = 1.5f..6f, label = tr("%.1f s").format(s.noticeDurationMs / 1000f), color = Tile.Orange,
            ) { v -> SettingsRepo.update { copy(noticeDurationMs = (v * 1000).toInt()) } }
            GroupToggle(Pos.Last, Icons.Rounded.BlurOn, tr("Reduce effects"),
                tr("Can help on less powerful devices"),
                s.reduceEffects, color = Tile.Gray) { v -> SettingsRepo.update { copy(reduceEffects = v) } }
        }
    }

    if (styleDialog) {
        ChoiceDialog(tr("Expansion style"), ExpandStyle.entries, s.expandStyle, { it.title },
            { v -> SettingsRepo.update { copy(expandStyle = v) } }) { styleDialog = false }
    }
}

private fun effectsSummary(s: IslandSettings): String {
    if (s.reduceEffects) return tr("Off: Reduce effects is on")
    val on = listOfNotNull(
        tr("blur").takeIf { s.fxBlur },
        tr("equalizer").takeIf { s.fxEqualizer },
        tr("glow").takeIf { s.fxGlow },
        tr("payment waves").takeIf { s.fxPay },
        tr("shake").takeIf { s.fxShake },
    )
    return if (on.isEmpty()) tr("All off") else on.joinToString(", ").replaceFirstChar { it.uppercase() }
}

@Composable
fun EffectsScreen(onBack: () -> Unit) {
    val s by SettingsRepo.state.collectAsState()
    var rateDialog by remember { mutableStateOf(false) }
    Page(tr("Effects"), onBack, action = {
        Text(tr("Reset"), color = Ui.Sub, fontSize = 16.sp, modifier = Modifier.clickable { SettingsRepo.resetEffects() })
    }) {
        IslandPreview()
        if (s.reduceEffects) {
            Text(
                tr("Reduce effects is on in Island expansion — while it's on, there are no effects"),
                color = Tints.Orange, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        SectionHeader(tr("Motion"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.BlurCircular, tr("Blur on change"),
                tr("The island's content flows with a blur, like on iPhone (Android 12+)"),
                s.fxBlur, color = Tile.Blue) { v -> SettingsRepo.update { copy(fxBlur = v) } }
            GroupToggle(Pos.Middle, Icons.Rounded.GraphicEq, tr("Live equalizer"),
                tr("The music and call bars dance. Off — they stand still, which saves power"),
                s.fxEqualizer, color = Tile.Pink) { v -> SettingsRepo.update { copy(fxEqualizer = v) } }
            GroupItem(Pos.Middle, Icons.Rounded.Speed, tr("Equalizer frame rate"),
                tr("${s.fxRate.title}. Also the glowing dot of the flashlight and navigation"),
                color = Tile.Indigo, onClick = { rateDialog = true })
            GroupToggle(Pos.Last, Icons.Rounded.NotificationsActive, tr("Shake"),
                tr("The capsule shakes when the ring mode changes, and a ringing timer's bell swings"),
                s.fxShake, color = Tile.Orange) { v -> SettingsRepo.update { copy(fxShake = v) } }
        }

        SectionHeader(tr("Glow"))
        Group {
            GroupToggle(Pos.First, Icons.Rounded.Flare, tr("Artwork glow"),
                tr("A soft halo in the artwork's color around the island while music plays"),
                s.fxGlow, color = Tile.Purple) { v -> SettingsRepo.update { copy(fxGlow = v) } }
            GroupToggle(Pos.Last, Icons.Rounded.Contactless, tr("Payment waves"),
                tr("NFC waves around the island while waiting for the reader, and a burst of sparks on Paid"),
                s.fxPay, color = Tile.Teal) { v -> SettingsRepo.update { copy(fxPay = v) } }
        }

        SectionHeader(tr("Battery"))
        GroupToggle(Pos.Single, Icons.Rounded.BatterySaver, tr("Battery saving"),
            tr("In Android's battery saver mode the island skips live effects by itself"),
            s.batterySaver, color = Tile.Green) { v -> SettingsRepo.update { copy(batterySaver = v) } }
    }

    if (rateDialog) {
        ChoiceDialog(tr("Equalizer frame rate"), FxRate.entries, s.fxRate, { it.title },
            { v -> SettingsRepo.update { copy(fxRate = v) } }) { rateDialog = false }
    }
}
