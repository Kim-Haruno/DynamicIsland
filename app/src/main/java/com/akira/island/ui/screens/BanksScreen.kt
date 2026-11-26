package com.akira.island.ui.screens

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.akira.island.R
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.tr
import com.akira.island.service.IslandTile
import com.akira.island.ui.Group
import com.akira.island.ui.GroupItem
import com.akira.island.ui.Page
import com.akira.island.ui.Perms
import com.akira.island.ui.Pos
import com.akira.island.ui.Screen
import com.akira.island.ui.SectionHeader
import com.akira.island.ui.Tile

@Composable
fun BanksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val running by IslandBus.serviceRunning.collectAsState()

    Page(tr("Bank app won't open"), onBack,
        subtitle = tr("Your bank says it detected a third-party app or accessibility service?")) {
        InfoText(
            tr(
                "Banks do this to protect against fraud: while any accessibility service not from Google Play is on, " +
                    "they won't let you in. The island reads nothing in your bank, but there's no way around the check — " +
                    "only turning the island off for a while. The easiest way is a tile in quick settings: tap it — the " +
                    "island turns off and you can use the bank; tap it again to turn the island back on.",
            ),
        )
        Group {
            val tileAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            if (tileAsk) {
                GroupItem(
                    Pos.First, Icons.Rounded.AddBox, tr("Add the Island tile"),
                    tr("To quick settings — one tap"),
                    color = Tile.Blue, onClick = { addTile(context) },
                ) { Arrow() }
            }
            GroupItem(
                if (tileAsk) Pos.Middle else Pos.First, Icons.Rounded.PowerSettingsNew, tr("Turn the island off now"),
                if (running) tr("Turn it back on in Accessibility or with the tile")
                else tr("The island is already off"),
                color = Tile.Orange, onClick = { if (running) Controls.turnOffIsland() },
            )
            GroupItem(
                Pos.Last, Icons.Rounded.Lock, tr("Turn the island on"),
                tr("Opens Accessibility — Dynamic Island → turn on"),
                color = Tile.Green, onClick = { Perms.openAccessibility(context) },
            ) { Arrow() }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            SectionHeader(tr("The quick settings tile"))
            InfoText(
                tr("Open quick settings fully, tap the pencil and drag the Island tile to the others."),
            )
        }
    }
}

private fun addTile(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val bar = context.getSystemService(StatusBarManager::class.java) ?: return
    runCatching {
        bar.requestAddTileService(
            ComponentName(context, IslandTile::class.java), context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_tab_island), context.mainExecutor,
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                Toast.makeText(context, tr("The tile is already in quick settings"), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
internal fun BanksItem(pos: Pos, open: (Screen) -> Unit) {
    GroupItem(pos, Icons.Rounded.AccountBalance, tr("Bank app won't open"),
        tr("A tile to turn the island off for a while"),
        onClick = { open(Screen.BANKS) }, color = Tile.Indigo) { Arrow() }
}
