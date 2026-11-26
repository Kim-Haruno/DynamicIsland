package com.akira.island.core

import android.app.PendingIntent
import android.app.RemoteInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.concurrent.atomic.AtomicLong

data class MediaInfo(
    val key: String,
    val pkg: String,
    val title: String,
    val artist: String,
    val album: String?,
    val art: ImageBitmap?,
    val accent: Color,
    val durationMs: Long,
    val positionMs: Long,
    val positionTime: Long,
    val speed: Float,
    val playing: Boolean,
    val canSeek: Boolean,
    val like: PlayerAction? = null,
    val dislike: PlayerAction? = null,
) {
    fun positionAt(now: Long): Long {
        val p = if (playing) positionMs + ((now - positionTime) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

data class PlayerAction(val action: String, val on: Boolean)

data class LyricLine(val timeMs: Long, val text: String)

enum class LyricsStatus { LOADING, MISSING, FOUND }

data class Lyrics(val key: String, val lines: List<LyricLine>, val synced: Boolean = true) {
    fun indexAt(pos: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= pos) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}

data class ProgressInfo(
    val key: String,
    val pkg: String,
    val title: String,
    val icon: ImageBitmap?,
    val progress: Int,
    val max: Int,
    val indeterminate: Boolean,
    val postTime: Long,
) {
    val fraction: Float get() = if (max > 0) (progress.toFloat() / max).coerceIn(0f, 1f) else 0f
}

data class ChronoInfo(
    val key: String,
    val pkg: String,
    val title: String,
    val icon: ImageBitmap?,
    val base: Long,
    val countDown: Boolean,
    val postTime: Long,
    val stop: PendingIntent? = null,
    val pausedMs: Long? = null,
    val pause: PendingIntent? = null,
    val resume: PendingIntent? = null,
    val reset: PendingIntent? = null,
) {
    fun elapsed(nowWall: Long): Long = pausedMs ?: if (countDown) (base - nowWall).coerceAtLeast(0) else nowWall - base

    fun finished(nowWall: Long): Boolean = pausedMs == null &&
        (countDown && nowWall > base + DONE_MS || !countDown && base > nowWall + 60_000)

    companion object {
        const val DONE_MS = 3_000L
    }

    val isRecording: Boolean
        get() = isScreenRecording(pkg, title, "", "")
}

fun isScreenRecording(pkg: String, title: String, text: String = "", big: String = ""): Boolean {
    val combined = "$pkg $title $text $big".lowercase()
    return "screenrecord" in combined ||
           "screen record" in combined ||
           "screen recording" in combined ||
           "screen recorder" in combined
}

enum class CallState {
    CALLING,
    RINGING,
    CONNECTED,
    ENDED
}

data class CallInfo(
    val key: String,
    val pkg: String,
    val name: String,
    val avatar: ImageBitmap?,
    val incoming: Boolean,
    val base: Long,
    val postTime: Long,
    val answer: PendingIntent?,
    val decline: PendingIntent?,
    val hangUp: PendingIntent?,
    val open: PendingIntent?,
    val state: CallState = CallState.CONNECTED,
) {
    fun statusText(): String = when (state) {
        CallState.CALLING -> tr("Calling…")
        CallState.RINGING -> tr("Ringing…")
        CallState.CONNECTED -> formatDuration(System.currentTimeMillis() - base)
        CallState.ENDED -> tr("Ended")
    }
}

enum class LiveKind { NAV, RIDE, DELIVERY }

data class SportInfo(
    val key: String,
    val pkg: String,
    val competition: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: String,
    val awayScore: String,
    val homeBadge: ImageBitmap?,
    val awayBadge: ImageBitmap?,
    val latestEvent: String?,
    val postTime: Long,
    val open: PendingIntent?,
)

enum class Turn { STRAIGHT, LEFT, RIGHT, UTURN }

data class LiveInfo(
    val key: String,
    val pkg: String,
    val kind: LiveKind,
    val title: String,
    val text: String,
    val sub: String,
    val icon: ImageBitmap?,
    val art: ImageBitmap?,
    val postTime: Long,
    val open: PendingIntent?,
    val distance: String? = null,
    val eta: String? = null,
    val turn: Turn? = null,
    val expiresAt: Long = 0,
)

data class TorchState(
    val on: Boolean = false,
    val cameraId: String? = null,
    val level: Int = 1,
    val maxLevel: Int = 1,
)

data class TimerState(
    val totalMs: Long,
    val endAt: Long,
    val pausedLeft: Long? = null,
    val ringing: Boolean = false,
) {
    fun left(now: Long): Long = pausedLeft ?: (endAt - now).coerceAtLeast(0)
    val paused: Boolean get() = pausedLeft != null && !ringing
}

data class StopwatchState(val startedAt: Long?, val accumulated: Long) {
    fun elapsed(now: Long): Long = accumulated + (startedAt?.let { now - it } ?: 0)
    val running: Boolean get() = startedAt != null
}

enum class ChargingSpeed {
    SLOW,
    STANDARD,
    FAST;

    val label: String
        get() = when (this) {
            SLOW -> "Slow charging"
            STANDARD -> "Charging"
            FAST -> "Super fast charging"
        }
}

data class BatteryInfo(
    val level: Int = -1,
    val charging: Boolean = false,
    val speed: ChargingSpeed = ChargingSpeed.STANDARD,
    /** Measured power into the battery in watts; null when the phone doesn't report current. */
    val watts: Float? = null,
    val milliamps: Int? = null,
    val voltageMv: Int? = null,
    val tempC: Float? = null,
)

object ChargingTints {
    val Slow = Color(0xFF9ECE10) // Yellowish green
    val SlowBrush = Brush.linearGradient(listOf(Color(0xFFBEF264), Color(0xFF84CC16)))

    val Standard = Color(0xFF34C759) // Standard green
    val StandardBrush = Brush.linearGradient(listOf(Color(0xFF30D158), Color(0xFF10B981))) // Green gradient

    val Fast = Color(0xFF00D9C0) // Bluish-green: cyan/teal-green
    val FastBrush = Brush.linearGradient(listOf(Color(0xFF00E8C8), Color(0xFF00B8D9)))

    fun colorFor(speed: ChargingSpeed): Color = when (speed) {
        ChargingSpeed.SLOW -> Slow
        ChargingSpeed.STANDARD -> Standard
        ChargingSpeed.FAST -> Fast
    }

    fun brushFor(speed: ChargingSpeed): Brush = when (speed) {
        ChargingSpeed.SLOW -> SlowBrush
        ChargingSpeed.STANDARD -> StandardBrush
        ChargingSpeed.FAST -> FastBrush
    }
}

enum class NoticeStyle {
    WIDE,
    PILL,
    MESSAGE,
    PAY_DONE,
    LIVE,
    ALARM,
    EGG,
    SENT,
}

data class PayReady(val pkg: String, val icon: ImageBitmap?)

data class AlarmInfo(val triggerAt: Long, val pkg: String?, val show: PendingIntent?)

class MsgAction(
    val title: String,
    val intent: PendingIntent,
    val inputs: List<RemoteInput> = emptyList(),
) {
    val isReply: Boolean get() = inputs.isNotEmpty()
}

class ReplyState(val noticeId: Long, val action: MsgAction)

data class AppMessage(
    val key: String,
    val pkg: String,
    val appName: String,
    val text: String,
    val avatar: ImageBitmap?,
    val appIcon: ImageBitmap?,
    val intent: PendingIntent?,
    val actions: List<MsgAction> = emptyList(),
    val accent: Color? = null,
    val history: List<String> = emptyList(),
    val sensitive: Boolean = false,
)

data class Notice(
    val title: String,
    val state: String? = null,
    val icon: ImageVector,
    val tint: Color,
    val brush: Brush? = null,
    val battery: Int? = null,
    val ring: Float? = null,
    val caption: String? = null,
    val style: NoticeStyle = NoticeStyle.WIDE,
    val message: AppMessage? = null,
    val extraMs: Long = 0,
    val appIcon: ImageBitmap? = null,
    val live: LiveInfo? = null,
    val openPkg: String? = null,
    val alarm: AlarmInfo? = null,
    val id: Long = ids.incrementAndGet(),
) {
    private companion object {
        val ids = AtomicLong()
    }
}

object Tints {
    val Green = Color(0xFF34C759)
    val Red = Color(0xFFFF453A)
    val Blue = Color(0xFF0A84FF)
    val Orange = Color(0xFFFF9F0A)
    val Yellow = Color(0xFFFFD60A)
    val Purple = Color(0xFFBF5AF2)
    val Teal = Color(0xFF64D2FF)
    val Gray = Color(0xFF8E8E93)
    val Lavender = Color(0xFFC6C3F7)
}

fun formatDuration(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
