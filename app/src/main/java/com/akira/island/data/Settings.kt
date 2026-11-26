package com.akira.island.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class FxRate(val fps: Int, val title: String) {
    ECO(30, "30 fps — saves power"),
    SMOOTH(60, "60 fps — smooth"),
    MAX(0, "Screen rate — up to 120"),
}

@Serializable
enum class ExpandStyle(val title: String) {
    FRUIT("Like a Fruit"),
    SPRING("Spring"),
    SMOOTH("Smooth"),
    INSTANT("No animation"),
}

@Serializable
enum class Elasticity(val title: String, val damping: Float) {
    NONE("No bounce", 1f),
    LOW("Light bounce", 0.8f),
    MEDIUM("Medium bounce", 0.62f),
    HIGH("Strong bounce", 0.48f),
}

@Serializable
enum class TapAction(val title: String) {
    EXPAND("Expand the capsule"),
    OPEN_APP("Open the app"),
    PLAY_PAUSE("Play / pause"),
    NEXT_TRACK("Next track"),
    SCREENSHOT("Screenshot without the island"),
    NOTHING("Nothing"),
}

@Serializable
enum class QuickAction(val title: String, val short: String) {
    TIMER("Timer", "Timer"),
    STOPWATCH("Stopwatch", "Stopwatch"),
    FLASHLIGHT("Flashlight", "Flashlight"),
    SCREENSHOT("Screenshot without the island", "Screenshot"),
    CAMERA("Camera", "Camera"),
    LOCK("Lock the screen", "Lock"),
}

@Serializable
enum class AccentMode(val title: String, val hint: String) {
    DEFAULT("Lavender", "The app's color"),
    SYSTEM("System", "From your Android 12+ wallpaper (Material You)"),
    ADAPTIVE("From the avatar", "Matched to the avatar or app icon"),
    CUSTOM("Custom", "Any color from the palette"),
}

@Serializable
data class IslandSettings(
    val enabled: Boolean = true,

    val cardMedia: Boolean = true,
    val cardTorch: Boolean = true,
    val cardProgress: Boolean = true,
    val cardTimer: Boolean = true,
    val cardCharging: Boolean = true,
    val cardCall: Boolean = true,
    val cardMessages: Boolean = true,
    val msgActions: Boolean = true,
    val cardLive: Boolean = true,
    val cardSports: Boolean = true,
    val cardAlarm: Boolean = true,
    val clockAutoTap: Boolean = true,
    val timerFromAssistant: Boolean = true,
    val wakeForAlerts: Boolean = true,
    val easterEggs: Boolean = true,

    val evChargeStart: Boolean = true,
    val evChargeStop: Boolean = true,
    val evLowBattery: Boolean = true,
    val evWifiOn: Boolean = true,
    val evWifiOff: Boolean = true,
    val evHeadsetOn: Boolean = true,
    val evHeadsetOff: Boolean = true,
    val evAirplaneOn: Boolean = true,
    val evAirplaneOff: Boolean = true,
    val evRinger: Boolean = true,
    val evBrightness: Boolean = true,
    val evUsbOn: Boolean = true,
    val evUsbOff: Boolean = true,
    val evUnlock: Boolean = false,
    val evLock: Boolean = true,
    val evDnd: Boolean = true,
    val evPayReady: Boolean = true,
    val payHintOnOpen: Boolean = false,
    val evPaySuccess: Boolean = true,
    val evAlarmSet: Boolean = true,

    val width: Int = 104,
    val height: Int = 31,
    val offsetX: Int = 0,
    val offsetY: Int = 11,
    val notch: Boolean = false,
    val cameraGap: Int = 34,
    val activeExtra: Int = 6,
    val activeWidth: Int = 40,
    val layoutVersion: Int = 1,

    val color: Long = 0xFF000000,
    val outline: Boolean = false,
    val outlineColor: Long = 0xFF3A3A40,
    val outlineWidth: Float = 1f,
    val outlineFromArt: Boolean = true,
    val shadow: Boolean = false,

    val accentMode: AccentMode = AccentMode.DEFAULT,
    val accentColor: Long = 0xFF0A84FF,

    val liquidGlass: Boolean = false,
    val glassOpacity: Int = 68,

    val expandStyle: ExpandStyle = ExpandStyle.FRUIT,
    val durationPercent: Int = 35,
    val liquidExpand: Boolean = false,
    val elasticity: Elasticity = Elasticity.LOW,
    val reduceEffects: Boolean = false,
    val haptics: Boolean = true,

    val fxBlur: Boolean = true,
    val fxEqualizer: Boolean = true,
    val fxRate: FxRate = FxRate.SMOOTH,
    val fxGlow: Boolean = false,
    val fxPay: Boolean = true,
    val fxShake: Boolean = true,
    val batterySaver: Boolean = false,

    val tapAction: TapAction = TapAction.OPEN_APP,
    val doubleTapAction: TapAction = TapAction.NOTHING,
    val longPressAction: TapAction = TapAction.EXPAND,
    val swipeTracks: Boolean = true,
    val swipeExpand: Boolean = true,

    val lyricsEnabled: Boolean = true,

    val hideIdle: Boolean = false,
    val hideInLandscape: Boolean = true,
    val hideWhenPlayerOpen: Boolean = true,
    val scheduleOn: Boolean = false,
    val scheduleFrom: Int = 10,
    val scheduleTo: Int = 23,
    val noticeDurationMs: Int = 2500,
    val timerMinutes: Int = 5,
    val autoCollapseSec: Int = 0,
    val bubble: Boolean = true,
    val idleExpand: Boolean = true,
    val quickActions: List<QuickAction> = listOf(QuickAction.TIMER, QuickAction.STOPWATCH, QuickAction.FLASHLIGHT),
    val keepAlive: Boolean = false,
    val msgLines: Int = 3,
    val msgInApp: Boolean = false,
    val msgChannels: Map<String, String> = emptyMap(),
    val bannerHintHidden: Boolean = false,
) {
    fun forPowerSave(on: Boolean): IslandSettings = if (on && batterySaver && !reduceEffects) copy(reduceEffects = true) else this

    fun inSchedule(hour: Int): Boolean = when {
        !scheduleOn || scheduleFrom == scheduleTo -> true
        scheduleFrom < scheduleTo -> hour in scheduleFrom until scheduleTo
        else -> hour >= scheduleFrom || hour < scheduleTo
    }

    val topOffset: Int get() = if (notch) 0 else offsetY

    val liveEqualizer: Boolean get() = fxEqualizer && !reduceEffects
}

object SettingsRepo {
    private const val KEY = "settings_v1"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private var prefs: SharedPreferences? = null

    var hadSavedSettings = false
        private set
    private val _state = MutableStateFlow(IslandSettings())
    val state: StateFlow<IslandSettings> = _state.asStateFlow()
    val current: IslandSettings get() = _state.value

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("island", Context.MODE_PRIVATE)
        prefs = p
        hadSavedSettings = p.contains(KEY)
        p.getString(KEY, null)?.let { raw ->
            runCatching { json.decodeFromString<IslandSettings>(raw) }.onSuccess { _state.value = it }
        }
        val cur = _state.value
        if (cur.layoutVersion in 2..2) update { copy(cameraGap = IslandSettings().cameraGap, layoutVersion = 3) }
        if (cur.layoutVersion < 2) {
            update {
                copy(
                    width = if (width == 96) 126 else width,
                    height = if (height == 34) 37 else height,
                    offsetY = if (offsetY == 8) 11 else offsetY,
                    layoutVersion = 3,
                )
            }
        }
        if (current.layoutVersion < 4) {
            val d = IslandSettings()
            update {
                val old = width == 126 && height == 37
                copy(
                    width = if (old) d.width else width,
                    height = if (old) d.height else height,
                    activeWidth = if (old && activeWidth == 48) d.activeWidth else activeWidth,
                    elasticity = if (elasticity == Elasticity.HIGH) d.elasticity else elasticity,
                    layoutVersion = 4,
                )
            }
        }
    }

    fun update(block: IslandSettings.() -> IslandSettings) {
        val next = _state.value.block()
        if (next == _state.value) return
        _state.value = next
        prefs?.edit()?.putString(KEY, json.encodeToString(next))?.apply()
    }

    fun reset() = update { IslandSettings() }

    fun resetAnimations() = update {
        val d = IslandSettings()
        copy(
            expandStyle = d.expandStyle,
            durationPercent = d.durationPercent,
            liquidExpand = d.liquidExpand,
            elasticity = d.elasticity,
            reduceEffects = d.reduceEffects,
            noticeDurationMs = d.noticeDurationMs,
        )
    }

    fun resetEffects() = update {
        val d = IslandSettings()
        copy(
            fxBlur = d.fxBlur, fxEqualizer = d.fxEqualizer, fxRate = d.fxRate, fxGlow = d.fxGlow, fxPay = d.fxPay,
            fxShake = d.fxShake, batterySaver = d.batterySaver,
        )
    }
}
