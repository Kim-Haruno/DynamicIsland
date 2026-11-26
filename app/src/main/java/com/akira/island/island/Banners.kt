package com.akira.island.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.R
import com.akira.island.core.Notice
import com.akira.island.core.Tints
import com.akira.island.data.IslandSettings

private val Ink = Color(0xFF1C1B22)

@Composable
fun SentBanner(n: Notice, s: IslandSettings, wideWidth: Dp) {
    val height = noticeHeight(s)
    val pop = remember(n.id) { Animatable(if (s.reduceEffects) 1f else 0.4f) }
    LaunchedEffect(n.id) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f)) }
    Row(
        Modifier.width(wideWidth).height(height).padding(horizontal = height * 0.32f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(height * 0.42f).scale(pop.value).clip(CircleShape).background(n.tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ImageVector.vectorResource(R.drawable.ios_check), null, tint = onAccent(n.tint), modifier = Modifier.size(height * 0.26f))
            }
            Spacer(Modifier.width(10.dp))
            Text(n.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(Modifier.width(compactGap(s)))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (n.state != null) {
                Text(n.state, color = Tints.Gray, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AppMark(n: Notice, size: Dp) {
    if (n.appIcon != null) {
        Image(n.appIcon, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)))
        return
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .background(Brush.linearGradient(listOf(Color(0xFF2B2350), Color(0xFF0E0D16)))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size * 0.56f, size * 0.2f)
                .clip(CircleShape)
                .background(Color.Black)
                .border(size * 0.025f, Tints.Lavender, CircleShape),
        )
    }
}

@Composable
private fun RowScope.CardButton(
    text: String,
    bg: Color,
    fg: Color,
    bold: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    Row(
        Modifier
            .weight(1f)
            .height(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bg)
            .clickable(interaction, indication = null) {
                haptic()
                onClick()
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = 15.sp, maxLines = 1,
            style = Digits.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold))
    }
}
