package com.akira.island.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.provider.AlarmClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.akira.island.core.ChronoInfo
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

object ClockApps {
    private val known = setOf(
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.oneplus.deskclock",
        "com.coloros.alarmclock",
        "com.oplus.alarmclock",
        "com.huawei.deskclock",
        "com.motorola.timeweatherwidget",
    )
    private var found: Set<String>? = null

    fun packages(context: Context): Set<String> = found ?: (known + runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentActivities(Intent(AlarmClock.ACTION_SET_TIMER), 0).map { it.activityInfo.packageName }
    }.getOrDefault(emptyList()) - context.packageName).also { found = it }

    fun isClock(context: Context, pkg: String) = pkg in packages(context)

    private var alarmApps: Set<String>? = null

    fun isAlarmApp(context: Context, pkg: String): Boolean = pkg in (alarmApps ?: (packages(context) +
        listOf(AlarmClock.ACTION_SET_ALARM, AlarmClock.ACTION_SHOW_ALARMS).flatMap { action ->
            runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(Intent(action), 0).map { it.activityInfo.packageName }
            }.getOrDefault(emptyList())
        }).also { alarmApps = it })
}

enum class ClockAction { START, PAUSE, RESET }

object ClockWords {
    private val start = listOf("start", "begin", "resume")
    private val pause = setOf("pause", "stop")
    private val reset = listOf("reset", "delete", "cancel", "clear")
    private val nonLetters = Regex("[^\\p{L}]+")

    val labels = mapOf(
        ClockAction.START to listOf("Start", "Resume"),
        ClockAction.PAUSE to listOf("Pause", "Stop"),
        ClockAction.RESET to listOf("Reset", "Delete"),
    )

    fun action(label: CharSequence?): ClockAction? {
        val words = label?.toString()?.lowercase()?.split(nonLetters)?.filter { it.isNotEmpty() } ?: return null
        return when {
            words.any { w -> reset.any { w.startsWith(it) } } -> ClockAction.RESET
            words.any { w -> w in pause } -> ClockAction.PAUSE
            words.any { w -> start.any { w.startsWith(it) } } -> ClockAction.START
            else -> null
        }
    }

    fun buttons(actions: Array<android.app.Notification.Action>?): Map<ClockAction, android.app.PendingIntent> {
        val out = HashMap<ClockAction, android.app.PendingIntent>()
        actions?.forEach { a ->
            val intent = a.actionIntent ?: return@forEach
            val kind = action(a.title) ?: return@forEach
            if (kind !in out) out[kind] = intent
        }
        return out
    }
}

object ClockScreenParser {
    private val time = Regex("""^(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?:[.,](\d{1,3}))?$""")
    private val anywhere = Regex("""(?:\d{1,2}:)?\d{1,2}:\d{2}(?:[.,]\d{1,3})?""")

    fun find(text: CharSequence?): Long? = text?.let { anywhere.find(it)?.value }?.let { parse(it) }

    fun parse(text: String): Long? {
        val m = time.matchEntire(text.filterNot { it.isWhitespace() }) ?: return null
        val h = m.groupValues[1].toLongOrNull() ?: 0L
        val min = m.groupValues[2].toLong()
        val sec = m.groupValues[3].toLong()
        if (sec >= 60 || (m.groupValues[1].isNotEmpty() && min >= 60)) return null
        val frac = m.groupValues[4].takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.toLong() ?: 0L
        return ((h * 60 + min) * 60 + sec) * 1000 + frac
    }
}

class ClockScreenReader(private val service: AccessibilityService, private val scope: CoroutineScope) {
    private enum class Kind { TIMER, STOPWATCH }

    private class Display(val key: String, val node: AccessibilityNodeInfo, val height: Int)

    private class Seen(var value: Long, var changedAt: Long, var kind: Kind? = null, var streak: Int = 0, var fine: Boolean = false)

    private class Track(val key: String, val base: Long, val pausedMs: Long?, val pausedAt: Long = 0L)

    private fun pausedTrack(t: Track, value: Long) = Track(t.key, t.base, value, SystemClock.elapsedRealtime())

    val active: Boolean get() = current != null

    private var current: String? = null
    private var loop: Job? = null
    private var endJob: Job? = null
    private var displays: List<Display> = emptyList()
    private var tab: Kind? = null
    private var scannedAt = 0L
    private var lastTick = 0L
    private var needRescan = true
    private val seen = HashMap<String, Seen>()
    private var hint: ClockAction? = null
    private var hintKind: Kind? = null
    private var hintAt = 0L
    private var timer: Track? = null
    private var stopwatch: Track? = null
    private var timerMisses = 0
    private var stopwatchMisses = 0
    private val labels = HashMap<String, String>()

    private class Pending(val action: ClockAction, val timer: Boolean, val until: Long, val opened: Boolean)
    private var pending: Pending? = null
    private var openedAt = 0L
    private var ownClickAt = 0L
    private var cleanupJob: Job? = null

    fun onForeground(pkg: String) {
        if (!ClockApps.isClock(service, pkg)) {
            val left = current
            if (left != null && SettingsRepo.current.cardTimer) {
                runCatching { tick() }
                if (tab == Kind.TIMER && displays.none { it.key == timer?.key }) timer = null
                if (tab == Kind.STOPWATCH && displays.none { it.key == stopwatch?.key }) stopwatch = null
                publish(left)
            }
            stopReading()
            return
        }
        if (pkg == current) return
        stopReading()
        current = pkg
        openedAt = SystemClock.elapsedRealtime()
        loop = scope.launch {
            while (isActive) {
                if (!IslandBus.screenOn.value) IslandBus.screenOn.first { it }
                if (SettingsRepo.current.cardTimer) runCatching { tick() }
                delay(250)
            }
        }
    }

    fun onContentChanged(event: AccessibilityEvent) {
        if (event.packageName?.toString() != current) return
        if (event.contentChangeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE != 0) needRescan = true
        if (SystemClock.elapsedRealtime() - lastTick >= 80) runCatching { tick() }
    }

    fun onClicked(event: AccessibilityEvent) {
        if (event.packageName?.toString() != current) return
        if (SystemClock.elapsedRealtime() - ownClickAt < 1_500) return
        val label = event.text.joinToString(" ") + " " + (event.contentDescription ?: "")
        val id = runCatching { event.source?.viewIdResourceName }.getOrNull().orEmpty().lowercase()
        val running = if (tab == Kind.STOPWATCH) stopwatch?.pausedMs == null && stopwatch != null else timer?.pausedMs == null && timer != null
        val action = ClockWords.action(label) ?: when {
            "reset" in id || "delete" in id -> ClockAction.RESET
            "pause" in id || ("stop" in id && "stopwatch" !in id) -> ClockAction.PAUSE
            "start" in id || "resume" in id || "play" in id -> ClockAction.START
            "fab" in id -> if (running) ClockAction.PAUSE else ClockAction.START
            else -> null
        }
        if (action != null) {
            hintKind = tab
            hint = action
            hintAt = SystemClock.elapsedRealtime()
        }
        needRescan = true
        scannedAt = 0L
        scope.launch {
            for (wait in longArrayOf(60, 140, 250, 350)) {
                delay(wait)
                runCatching { tick() }
            }
        }
    }

    fun control(pkg: String, isTimer: Boolean, action: ClockAction, own: Boolean) {
        if (own) {
            val wall = System.currentTimeMillis()
            val next = (if (isTimer) timer else stopwatch)?.let { apply(it, isTimer, action, wall) }
            if (isTimer) timer = next else stopwatch = next
            publish(pkg)
        }
        val opened = current != pkg
        pending = Pending(action, isTimer, SystemClock.elapsedRealtime() + 4_000, opened)
        if (!opened) {
            scope.launch {
                delay(60)
                runCatching { tick() }
            }
            return
        }
        val intents = listOfNotNull(
            if (isTimer) Intent(AlarmClock.ACTION_SHOW_TIMERS).setPackage(pkg) else null,
            service.packageManager.getLaunchIntentForPackage(pkg),
        )
        val started = intents.any { intent ->
            runCatching { service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
        if (!started) pending = null
    }

    private fun apply(t: Track, isTimer: Boolean, action: ClockAction, wall: Long): Track? {
        val value = t.pausedMs ?: if (isTimer) (t.base - wall).coerceAtLeast(0) else wall - t.base
        return when (action) {
            ClockAction.PAUSE -> pausedTrack(t, value)
            ClockAction.START -> Track(t.key, if (isTimer) wall + value else wall - value, null)
            ClockAction.RESET -> null
        }
    }

    private fun performPending() {
        val p = pending ?: return
        val now = SystemClock.elapsedRealtime()
        if (now > p.until) {
            pending = null
            return
        }
        val root = service.rootInActiveWindow ?: return
        if (root.packageName?.toString() != current) return
        val want = if (p.timer) Kind.TIMER else Kind.STOPWATCH
        if (tab != null && tab != want) {
            if (clickTab(root, if (p.timer) TIMER_TABS else STOPWATCH_TABS)) needRescan = true
            return
        }
        val main = displays.maxByOrNull { it.height }
        val s = main?.let { seen[it.key] }
        val running = s != null && s.kind != null && now - s.changedAt < 1_200
        val known = running || now - openedAt > 1_300
        when (p.action) {
            ClockAction.PAUSE, ClockAction.START -> {
                val button = findButton(root, p.action) ?: return
                if (button.toggle) {
                    if (!known) return
                    val needed = if (p.action == ClockAction.PAUSE) running else !running
                    if (needed && !click(button.node)) return
                } else if (!click(button.node)) {
                    return
                }
            }
            ClockAction.RESET -> {
                val button = findButton(root, ClockAction.RESET)
                if (button == null) {
                    if (known && running) findButton(root, ClockAction.PAUSE)?.let { click(it.node) }
                    return
                }
                if (!click(button.node)) return
            }
        }
        hint = p.action
        hintKind = want
        hintAt = now
        ownClickAt = now
        pending = null
        if (p.opened) {
            scope.launch {
                delay(600)
                runCatching { tick() }
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
        }
    }

    private class Button(val node: AccessibilityNodeInfo, val toggle: Boolean)

    private fun findButton(root: AccessibilityNodeInfo, action: ClockAction): Button? {
        var byLabel: AccessibilityNodeInfo? = null
        var byId: AccessibilityNodeInfo? = null
        var byIdToggle = false
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (byLabel != null || depth > 40) return
            if (n.isVisibleToUser && !n.isSelected) {
                val label = listOfNotNull(n.text, n.contentDescription).joinToString(" ").trim()
                if (label.isNotEmpty() && label.length <= 40 && ClockScreenParser.parse(label) == null &&
                    ClockWords.action(label) == action
                ) {
                    byLabel = n
                    return
                }
                val id = n.viewIdResourceName.orEmpty().lowercase()
                if (byId == null && id.isNotEmpty()) {
                    val toggle = "fab" in id || ("play" in id && "pause" in id) || ("start" in id && "stop" in id)
                    val match = when (action) {
                        ClockAction.PAUSE -> toggle || "pause" in id || ("stop" in id && "stopwatch" !in id)
                        ClockAction.START -> toggle || "start" in id || "resume" in id || "play" in id
                        ClockAction.RESET -> "reset" in id || "delete" in id || "clear" in id
                    }
                    if (match) {
                        byId = n
                        byIdToggle = toggle
                    }
                }
            }
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                walk(child, depth + 1)
            }
        }
        walk(root, 0)
        byLabel?.let { return Button(it, false) }
        return byId?.let { Button(it, byIdToggle) }
    }

    private fun clickTab(root: AccessibilityNodeInfo, labels: List<String>): Boolean {
        var found: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (found != null || depth > 40) return
            val label = (n.text ?: n.contentDescription)?.toString()?.trim()
            if (n.isVisibleToUser && labels.any { it.equals(label, ignoreCase = true) }) {
                found = n
                return
            }
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                walk(child, depth + 1)
            }
        }
        walk(root, 0)
        return found?.let { click(it) } ?: false
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        var up = 0
        while (target != null && !target.isClickable && up < 4) {
            target = target.parent
            up++
        }
        return target != null && target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun dropInactive(now: Long) {
        if (timer?.let { it.pausedMs != null && now - it.pausedAt > INACTIVE_MS } == true) timer = null
        if (stopwatch?.let { it.pausedMs != null && now - it.pausedAt > INACTIVE_MS } == true) stopwatch = null
    }

    fun onNotificationChrono(pkg: String) {
        if (timer == null && stopwatch == null) return
        timer = null
        stopwatch = null
        publish(pkg)
    }

    fun stopReading() {
        loop?.cancel()
        loop = null
        current = null
        displays = emptyList()
        seen.clear()
        hint = null
        needRescan = true
        scannedAt = 0L
    }

    fun stop() {
        stopReading()
        endJob?.cancel()
        timer = null
        stopwatch = null
        IslandBus.chrono.value = IslandBus.chrono.value - TIMER_KEY - STOPWATCH_KEY
    }

    private fun tick() {
        val pkg = current ?: return
        val now = SystemClock.elapsedRealtime()
        lastTick = now
        if ((needRescan && now - scannedAt > 500) || now - scannedAt > 3_000) rescan(pkg, now)
        val wall = System.currentTimeMillis()
        val hinted = hint?.takeIf { now - hintAt < HINT_MS }

        var bestTimer: Pair<Display, Long>? = null
        var bestStopwatch: Pair<Display, Long>? = null
        for (d in displays) {
            val alive = runCatching { d.node.refresh() && d.node.isVisibleToUser }.getOrDefault(false)
            if (!alive) {
                needRescan = true
                continue
            }
            val text = (d.node.text ?: d.node.contentDescription)?.toString() ?: continue
            if (isTimeOfDay(text, wall)) continue
            val v = ClockScreenParser.parse(text) ?: continue
            seen[d.key]?.fine = text.contains('.') || text.contains(',')
            when (observe(d.key, v, now, hinted)) {
                Kind.TIMER -> if (d.height > (bestTimer?.first?.height ?: -1)) bestTimer = d to v
                Kind.STOPWATCH -> if (d.height > (bestStopwatch?.first?.height ?: -1)) bestStopwatch = d to v
                null -> Unit
            }
        }
        bestTimer?.let { (d, v) -> timer = running(timer, d.key, wall + v) }
        bestStopwatch?.let { (d, v) -> stopwatch = running(stopwatch, d.key, wall - v) }

        val main = displays.maxByOrNull { it.height }
        val mainValue = main?.let { seen[it.key]?.value }
        if (hinted == ClockAction.START && main != null && mainValue != null) {
            val t = timer
            val w = stopwatch
            if (hintKind == Kind.TIMER && bestTimer == null && mainValue > 0 && (t == null || t.pausedMs != null)) {
                timer = Track(main.key, wall + mainValue, null)
                seen[main.key]?.changedAt = now
            }
            if (hintKind == Kind.STOPWATCH && bestStopwatch == null && (w == null || w.pausedMs != null)) {
                stopwatch = Track(main.key, wall - mainValue, null)
                seen[main.key]?.changedAt = now
            }
        }
        if (hinted == ClockAction.PAUSE && main != null && mainValue != null) {
            if (hintKind == Kind.TIMER) timer?.let { timer = Track(it.key, it.base, mainValue, it.pausedAt.takeIf { at -> at > 0 } ?: now) }
            if (hintKind == Kind.STOPWATCH) stopwatch?.let { stopwatch = Track(it.key, it.base, mainValue, it.pausedAt.takeIf { at -> at > 0 } ?: now) }
        }
        if (hinted == ClockAction.RESET) {
            if (hintKind == Kind.TIMER) timer = null
            if (hintKind == Kind.STOPWATCH) stopwatch = null
        }

        if (hinted != ClockAction.START) {
            timer = pauseIfStill(timer, now)
            stopwatch = pauseIfStill(stopwatch, now)
        }
        dropInactive(now)
        publish(pkg)
        performPending()
    }

    private fun observe(key: String, v: Long, now: Long, hinted: ClockAction?): Kind? {
        val s = seen[key]
        if (s == null) {
            seen[key] = Seen(v, now)
            return null
        }
        if (v == s.value) return null
        val dv = v - s.value
        val since = now - s.changedAt
        val paused = listOfNotNull(timer, stopwatch).firstOrNull { it.key == key && it.pausedMs != null }
        val kind = when {
            dv < 0 && -dv <= since + 1_100 && since <= -dv + 1_300 -> Kind.TIMER
            dv > 0 && dv <= since + 1_100 && since <= dv + 1_300 -> Kind.STOPWATCH
            paused != null && abs(dv) <= 1_500 -> if (dv < 0) Kind.TIMER else Kind.STOPWATCH
            else -> null
        }
        s.streak = if (kind != null && kind == s.kind) s.streak + 1 else if (kind != null) 1 else 0
        s.kind = kind
        s.value = v
        s.changedAt = now
        if (hinted == ClockAction.PAUSE) return null
        if (kind == null) {
            if (timer?.key == key && dv > 1_500) timer = null
            if (v == 0L && stopwatch?.key == key) stopwatch = null
            return null
        }
        if (v == 0L) {
            if (timer?.key == key) timer = null
            return null
        }
        val sure = s.streak >= 2 || tab == kind || hinted == ClockAction.START || paused != null
        return if (sure) kind else null
    }

    private fun pauseIfStill(track: Track?, now: Long): Track? {
        track ?: return null
        val s = seen[track.key] ?: return track
        if (track.pausedMs != null) return track
        val wait = if (s.fine) 800L else 1_700L
        val still = now - s.changedAt > wait
        return if (still) pausedTrack(track, s.value) else track
    }

    private fun running(existing: Track?, key: String, base: Long): Track =
        if (existing?.key == key && existing.pausedMs == null && abs(existing.base - base) < 1_500) existing
        else Track(key, base, null)

    private fun publish(pkg: String) {
        val t = timer
        val w = stopwatch
        val icon = NotificationParser.icon(service, pkg)
        val label = labels.getOrPut(pkg) { NotificationParser.appLabel(service, pkg) }
        val map = IslandBus.chrono.value.toMutableMap()
        if (t != null) {
            map[TIMER_KEY] = ChronoInfo(TIMER_KEY, pkg, tr("Timer · $label"), icon, t.base, true,
                System.currentTimeMillis(), pausedMs = t.pausedMs)
        } else {
            map.remove(TIMER_KEY)
        }
        if (w != null) {
            map[STOPWATCH_KEY] = ChronoInfo(STOPWATCH_KEY, pkg, tr("Stopwatch · $label"), icon, w.base, false,
                System.currentTimeMillis(), pausedMs = w.pausedMs)
        } else {
            map.remove(STOPWATCH_KEY)
        }
        IslandBus.chrono.value = map
    }

    private fun rescan(pkg: String, now: Long) {
        scannedAt = now
        needRescan = false
        val root = service.rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        tab = detectTab(root)
        val list = ArrayList<Display>()
        collect(root, list, 0)

        displays = list
    }

    private fun detectTab(root: AccessibilityNodeInfo): Kind? {
        var found: Kind? = null
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (found != null || depth > 40) return
            val label = (n.text ?: n.contentDescription)?.toString()?.trim()
            if (n.isVisibleToUser && label != null) {
                if (TIMER_TABS.any { it.equals(label, ignoreCase = true) } && (n.isSelected || n.isFocused)) found = Kind.TIMER
                if (STOPWATCH_TABS.any { it.equals(label, ignoreCase = true) } && (n.isSelected || n.isFocused)) found = Kind.STOPWATCH
            }
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                walk(child, depth + 1)
            }
        }
        walk(root, 0)
        return found
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<Display>, depth: Int) {
        if (out.size > 20 || depth > 40) return
        val text = (node.text ?: node.contentDescription)?.toString()?.trim()
        if (node.isVisibleToUser && text != null && ClockScreenParser.find(text) != null) {
            val b = Rect()
            node.getBoundsInScreen(b)
            if (b.width() > 0 && b.height() > 0) {
                val key = node.viewIdResourceName?.takeIf { it.isNotBlank() } ?: "${b.left},${b.top}"
                out += Display(key, node, b.height())
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1)
        }
    }

    private fun isTimeOfDay(text: String, wallNow: Long): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = wallNow }
        val h = c.get(Calendar.HOUR)
        val h24 = c.get(Calendar.HOUR_OF_DAY)
        val m = c.get(Calendar.MINUTE)
        val candidates = setOf(
            "%d:%02d".format(h24, m),
            "%d:%02d".format(if (h == 0) 12 else h, m),
            "%02d:%02d".format(h24, m),
        )
        return text in candidates
    }

    companion object {
        const val KEY_PREFIX = "screen_clock:"
        const val TIMER_KEY = "${KEY_PREFIX}timer"
        const val STOPWATCH_KEY = "${KEY_PREFIX}stopwatch"
        private const val HINT_MS = 2_500L
        private const val INACTIVE_MS = 2 * 60_000L

        private val TIMER_TABS = listOf("Timer", "Timers")
        private val STOPWATCH_TABS = listOf("Stopwatch", "Stopwatches")
    }
}

object ClockControl {
    @Volatile
    var reader: ClockScreenReader? = null

    private val dismissed = HashMap<String, Long>()

    fun toggle(context: Context, c: ChronoInfo) {
        val r = reader
        val isTimer = c.key == ClockScreenReader.TIMER_KEY
        val action = if (c.pausedMs != null) ClockAction.START else ClockAction.PAUSE
        if (r != null && r.active) {
            r.control(c.pkg, isTimer, action, own = true)
            return
        }
        val target = if (c.pausedMs != null) c.resume else c.pause
        if (target != null && Controls.send(context, target)) return
        r?.control(c.pkg, isTimer, action, own = false)
    }

    fun reset(context: Context, c: ChronoInfo) {
        dismissed[c.key] = c.pausedMs ?: -1L
        val r = reader
        val isTimer = c.key == ClockScreenReader.TIMER_KEY
        if (r != null && r.active) {
            r.control(c.pkg, isTimer, ClockAction.RESET, own = true)
            return
        }
        if (c.reset != null && Controls.send(context, c.reset)) {
            IslandBus.chrono.value = IslandBus.chrono.value - c.key
            return
        }
        r?.control(c.pkg, isTimer, ClockAction.RESET, own = false)
    }

    fun wasDismissed(key: String, pausedMs: Long?): Boolean {
        val last = dismissed[key] ?: return false
        return last == (pausedMs ?: -1L)
    }

    fun forget(key: String) {
        dismissed.remove(key)
    }

    fun dropPausedLater(key: String) {
        IslandBus.scope.launch {
            delay(120_000)
            val cur = IslandBus.chrono.value[key]
            if (cur != null && cur.pausedMs != null) IslandBus.chrono.value = IslandBus.chrono.value - key
        }
    }
}
