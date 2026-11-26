package com.akira.island.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.os.Build
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.akira.island.core.CallInfo
import com.akira.island.core.CallState
import com.akira.island.core.ChronoInfo
import com.akira.island.core.isScreenRecording
import com.akira.island.core.IslandBus
import com.akira.island.core.LiveKind
import com.akira.island.core.ProgressInfo
import com.akira.island.core.SportInfo
import com.akira.island.data.SettingsRepo

/**
 * Доступ к уведомлениям даёт две вещи: медиасессии всех плееров
 * и уведомления с прогрессом или секундомером (загрузки, Часы, звонки).
 */
class NotifListener : NotificationListenerService() {
    private var media: MediaMonitor? = null

    override fun onListenerConnected() {
        SettingsRepo.init(this)
        media = MediaMonitor(applicationContext).also { it.start() }
        // Уже висящие уведомления разбираем без показа сообщений — они не новые
        runCatching { activeNotifications?.forEach { NotificationParser.onPosted(this, it, initial = true) } }
    }

    override fun onListenerDisconnected() {
        media?.stop()
        media = null
        IslandBus.progress.value = emptyMap()
        IslandBus.chrono.value = emptyMap()
        IslandBus.calls.value = emptyMap()
        IslandBus.live.value = emptyMap()
        IslandBus.sports.value = emptyMap()
        IslandBus.ringingAlarms.value = emptySet()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationParser.onPosted(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationParser.onRemoved(sbn.key)
    }
}

object NotificationParser {
    private val icons = HashMap<String, ImageBitmap?>()

    fun onPosted(context: Context, sbn: StatusBarNotification, initial: Boolean = false) {
        if (sbn.packageName == context.packageName) return
        val n = sbn.notification ?: return
        val ex = n.extras ?: return
        val isMedia = n.category == Notification.CATEGORY_TRANSPORT || ex.containsKey(Notification.EXTRA_MEDIA_SESSION)
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: appLabel(context, sbn.packageName)

        val max = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val value = ex.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        val hasProgress = !isMedia && ((max > 0 && value < max) || (indeterminate && ongoing))
        IslandBus.progress.value = IslandBus.progress.value.toMutableMap().apply {
            if (hasProgress) {
                put(sbn.key, ProgressInfo(sbn.key, sbn.packageName, title, icon(context, sbn.packageName), value, max, indeterminate, sbn.postTime))
            } else {
                remove(sbn.key)
            }
        }

        val call = if (n.category == Notification.CATEGORY_CALL && !isMedia) parseCall(context, sbn, title) else null
        // Какой канал присылает всплывающую плашку звонка или будильника — для экрана «Системные плашки»
        SystemBanners.onPosted(sbn, incomingCall = call?.incoming == true)
        // Звонящий будильник или таймер «Часов» — у него полноэкранный сигнал; остров включит экран сам,
        // даже если всплывающие плашки «Часов» выключены
        val ringing = n.category == Notification.CATEGORY_ALARM && n.fullScreenIntent != null
        if (ringing != (sbn.key in IslandBus.ringingAlarms.value)) {
            IslandBus.ringingAlarms.value = if (ringing) IslandBus.ringingAlarms.value + sbn.key else IslandBus.ringingAlarms.value - sbn.key
        }
        IslandBus.calls.value = IslandBus.calls.value.toMutableMap().apply {
            if (call != null) put(sbn.key, call) else remove(sbn.key)
        }

        val textStr = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigStr = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val isRec = isScreenRecording(sbn.packageName, title, textStr, bigStr)
        val ticking = (ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) || isRec) && (n.`when` > 0 || sbn.postTime > 0)
        val baseTime = if (n.`when` > 0) n.`when` else sbn.postTime
        // Кнопки «Часов» (пауза, продолжить, сброс) — их нажимают кнопки острова
        val clock = ClockApps.isClock(context, sbn.packageName)
        val buttons = if (clock) ClockWords.buttons(n.actions) else emptyMap()
        // Таймер или секундомер «Часов» на паузе: счёт стоит, время только в тексте, а в кнопках — «Продолжить»
        val pausedMs = if (clock && !ticking && call == null && !isMedia && ClockAction.START in buttons) {
            listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT)
                .firstNotNullOfOrNull { ClockScreenParser.find(ex.getCharSequence(it)) }
        } else {
            null
        }
        // Неактивный (на паузе) таймер, который остров уже убрал, снова не показываем
        val chrono = call == null && !isMedia && (ticking || isRec || pausedMs != null) && !ClockControl.wasDismissed(sbn.key, pausedMs)
        if (clock && chrono) ClockControl.reader?.onNotificationChrono(sbn.packageName)
        if (pausedMs != null && chrono) ClockControl.dropPausedLater(sbn.key)
        IslandBus.chrono.value = IslandBus.chrono.value.toMutableMap().apply {
            if (chrono) {
                val down = if (pausedMs != null) looksLikeTimer(n, title) else ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
                val stop = if (isRec) {
                    n.actions?.firstOrNull { a ->
                        val t = a.title?.toString()?.lowercase().orEmpty()
                        listOf("стоп", "останов", "заверш", "stop", "end", "finish", "tap", "нажми", "косн").any { it in t }
                    }?.actionIntent ?: n.actions?.firstOrNull()?.actionIntent ?: n.contentIntent
                } else {
                    n.actions?.firstOrNull { a ->
                        val t = a.title?.toString()?.lowercase().orEmpty()
                        listOf("стоп", "останов", "заверш", "stop", "finish").any { it in t }
                    }?.actionIntent
                }
                put(sbn.key, ChronoInfo(sbn.key, sbn.packageName, title, icon(context, sbn.packageName), baseTime, down, sbn.postTime, stop,
                    pausedMs = pausedMs, pause = buttons[ClockAction.PAUSE], resume = buttons[ClockAction.START],
                    reset = buttons[ClockAction.RESET]))
            } else {
                remove(sbn.key)
            }
        }

        val sport = if (call == null && !isMedia) parseSport(context, sbn, title) else null
        IslandBus.sports.value = IslandBus.sports.value.toMutableMap().apply {
            if (sport != null) put(sbn.key, sport) else remove(sbn.key)
        }

        val live = if (call == null && !isMedia && sport == null) LiveParser.parse(context, sbn, title) else null
        val liveWasKnown = sbn.key in IslandBus.live.value
        IslandBus.live.value = IslandBus.live.value.toMutableMap().apply {
            if (live != null) put(sbn.key, live) else remove(sbn.key)
        }
        // Новая «живая активность» сначала показывается широкой плашкой, потом сворачивается в капсулу
        if (live != null && !liveWasKnown && !initial && SettingsRepo.current.cardLive) {
            LiveParser.postBannerLater(context, sbn.key)
        }
        // Навигатор и такси показываются своей карточкой, а не полоской прогресса или таймером
        if (live != null || sport != null) {
            IslandBus.progress.value = IslandBus.progress.value - sbn.key
            IslandBus.chrono.value = IslandBus.chrono.value - sbn.key
            if (sport != null) IslandBus.live.value = IslandBus.live.value - sbn.key
        }

        val service = context as? NotificationListenerService
        if (!initial && service != null && !isMedia && call == null && !chrono && !hasProgress && live == null && sport == null) {
            // Уведомление об оплате из банка — «Оплачено» с галочкой, а не карточка сообщения
            if (PayMonitor.onNotification(context, sbn)) return
            Messages.maybeShow(service, sbn, appLabel(context, sbn.packageName))
        }
    }

    private fun parseCall(context: Context, sbn: StatusBarNotification, title: String): CallInfo? {
        val n = sbn.notification
        val ex = n.extras
        val ongoing = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) && n.`when` > 0
        // CallStyle (Android 12+) кладёт кнопки в extras, у остальных звонилок ищем по подписи на языке телефона
        var answer = pending(ex, "android.answerIntent")
        var decline = pending(ex, "android.declineIntent")
        var hangUp = pending(ex, "android.hangUpIntent")
        n.actions?.forEach { a ->
            val t = a.title?.toString()?.lowercase().orEmpty()
            when {
                answer == null && AnswerWords.any { it in t } -> answer = a.actionIntent
                decline == null && DeclineWords.any { it in t } -> decline = a.actionIntent
                hangUp == null && HangUpWords.any { it in t } -> hangUp = a.actionIntent
            }
        }
        val incoming = !ongoing && answer != null
        val text = listOf(Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT)
            .firstNotNullOfOrNull { ex.getCharSequence(it)?.toString()?.lowercase().orEmpty() } ?: ""
        val combined = "${title.lowercase()} $text"
        val isCallActivity = CallActivityWords.any { it in combined } || n.category == Notification.CATEGORY_CALL
        if (!incoming && !ongoing && !isCallActivity) return null

        val callState = when {
            incoming -> CallState.RINGING
            ongoing -> CallState.CONNECTED
            CallingWords.any { it in combined } -> CallState.CALLING
            RingingWords.any { it in combined } -> CallState.RINGING
            else -> if (incoming) CallState.RINGING else CallState.CONNECTED
        }

        val person = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            runCatching { ex.getParcelable<Person>(Notification.EXTRA_CALL_PERSON) }.getOrNull()
        } else {
            null
        }
        // person is only non-null when SDK >= S (31), which also covers P (28) for Person API
        @Suppress("NewApi")
        val name = person?.name?.toString()?.takeIf { it.isNotBlank() } ?: title
        val avatar = runCatching {
            @Suppress("NewApi")
            val icon = person?.icon ?: n.getLargeIcon()
            icon?.loadDrawable(context)?.toBitmap(128, 128)?.asImageBitmap()
        }.getOrNull()
        val baseTime = if (n.`when` > 0) n.`when` else System.currentTimeMillis()
        return CallInfo(
            key = sbn.key,
            pkg = sbn.packageName,
            name = name,
            avatar = avatar,
            incoming = incoming,
            state = callState,
            base = baseTime,
            postTime = sbn.postTime,
            answer = answer,
            decline = decline ?: hangUp,
            hangUp = hangUp ?: decline,
            open = n.contentIntent,
        )
    }

    /** Таймер на паузе или секундомер — по заголовку и каналу; по умолчанию таймер. */
    private fun looksLikeTimer(n: Notification, title: String): Boolean {
        val t = (title + " " + n.channelId.orEmpty()).lowercase()
        return when {
            "секундомер" in t || "stopwatch" in t -> false
            else -> true
        }
    }

    @Suppress("DEPRECATION")
    private fun pending(ex: android.os.Bundle, key: String): PendingIntent? =
        runCatching { ex.getParcelable<PendingIntent>(key) }.getOrNull()

    fun onRemoved(key: String) {
        Messages.forget(key)
        LiveParser.forget(key)
        ClockControl.forget(key)
        LiveDebug.forget(key)
        if (key in IslandBus.calls.value) IslandBus.calls.value = IslandBus.calls.value - key
        if (key in IslandBus.progress.value) IslandBus.progress.value = IslandBus.progress.value - key
        if (key in IslandBus.chrono.value) IslandBus.chrono.value = IslandBus.chrono.value - key
        if (key in IslandBus.live.value) IslandBus.live.value = IslandBus.live.value - key
        if (key in IslandBus.sports.value) IslandBus.sports.value = IslandBus.sports.value - key
        if (key in IslandBus.ringingAlarms.value) IslandBus.ringingAlarms.value = IslandBus.ringingAlarms.value - key
    }

    private val SportsPackages = setOf(
        "com.mobilefootie.fotmob",
        "com.sofascore.results",
        "eu.livesport.FlashScore",
        "de.motain.iliga",
        "com.espn.score_center",
        "com.espn.uk",
        "com.livescore",
        "com.thescore",
        "com.scores365",
        "com.besoccer.android",
        "com.protrade.sportshd",
        "com.bleacherreport.android.teamstream",
        "com.handmark.sportcaster",
        "uk.co.bbc.sport",
        "ru.sports.tribuna",
        "ru.sports.main",
        "ru.championat.app",
        "se.footballaddicts.livescore",
        "com.pl.premierleague",
        "com.nbaimd.gametime.nba2011",
        "com.uefa.ucl",
        "com.fifa.fifaapp.android",
        "com.cricbuzz.android",
    )

    private fun parseSport(context: Context, sbn: StatusBarNotification, title: String): SportInfo? {
        val pkg = sbn.packageName.lowercase()

        val n = sbn.notification
        val ex = n.extras ?: return null
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val sub = (ex.getCharSequence(Notification.EXTRA_SUB_TEXT) ?: ex.getCharSequence(Notification.EXTRA_INFO_TEXT))
            ?.toString()?.trim().orEmpty()
        val isKnownSportsPkg = SportsPackages.any { pkg == it.lowercase() }
        val isSportNotification = SportDetector.isSport(
            pkg = sbn.packageName,
            category = n.category,
            title = title,
            text = text,
            big = big,
            sub = sub,
        )
        if (!isKnownSportsPkg && !isSportNotification) return null

        val combined = "$title $text $big $sub"
        val scoreRegex = Regex("""(?:^|[^\d])(\d{1,3})\s*[-–:]\s*(\d{1,3})(?:[^\d]|$)""")
        val scoreMatch = scoreRegex.find(title) ?: scoreRegex.find(text) ?: scoreRegex.find(combined)

        val eventPrefixMatch = Regex(
            """^(\d{1,3}['’]\s*)?(?:goal|gol|гол|red card|красная карточка|yellow card|var|penalty)!?:?\s*""",
            RegexOption.IGNORE_CASE,
        ).find(title)
        val cleanTitle = if (eventPrefixMatch != null) title.substring(eventPrefixMatch.range.last + 1).trim() else title

        var homeTeam = ""
        var awayTeam = ""
        var homeScore = "0"
        var awayScore = "0"

        val scoreInMiddleRegex = Regex("""^([A-Za-zА-Яа-я0-9\s.]{2,30}?)\s+(\d{1,3})\s*[-–:]\s*(\d{1,3})\s+([A-Za-zА-Яа-я0-9\s.]{2,30}?)$""")
        val middleMatch = scoreInMiddleRegex.find(cleanTitle)

        val scoreAtEndRegex = Regex("""^([A-Za-zА-Яа-я0-9\s.]{2,30}?)\s+(?:vs|v|[-–])\s+([A-Za-zА-Яа-я0-9\s.]{2,30}?)\s+(\d{1,3})\s*[-–:]\s*(\d{1,3})$""", RegexOption.IGNORE_CASE)
        val endMatch = scoreAtEndRegex.find(cleanTitle)

        val commaScoreRegex = Regex("""^([A-Za-zА-Яа-я0-9\s.]{2,30}?)\s+(\d{1,3})\s*,\s*([A-Za-zА-Яа-я0-9\s.]{2,30}?)\s+(\d{1,3})$""")
        val commaMatch = commaScoreRegex.find(cleanTitle)

        when {
            middleMatch != null -> {
                homeTeam = middleMatch.groupValues[1].trim()
                homeScore = middleMatch.groupValues[2]
                awayScore = middleMatch.groupValues[3]
                awayTeam = middleMatch.groupValues[4].trim()
            }
            endMatch != null -> {
                homeTeam = endMatch.groupValues[1].trim()
                awayTeam = endMatch.groupValues[2].trim()
                homeScore = endMatch.groupValues[3]
                awayScore = endMatch.groupValues[4]
            }
            commaMatch != null -> {
                homeTeam = commaMatch.groupValues[1].trim()
                homeScore = commaMatch.groupValues[2]
                awayTeam = commaMatch.groupValues[3].trim()
                awayScore = commaMatch.groupValues[4]
            }
            else -> {
                val parts = cleanTitle.split(Regex("""\s*[-–:]\s*"""))
                if (parts.size >= 2) {
                    val left = parts[0].trim()
                    val right = parts[1].trim()
                    val leftScoreMatch = Regex("""\d+""").find(left)
                    val rightScoreMatch = Regex("""\d+""").find(right)
                    if (leftScoreMatch != null && rightScoreMatch != null) {
                        homeScore = leftScoreMatch.value
                        awayScore = rightScoreMatch.value
                        homeTeam = left.replace(leftScoreMatch.value, "").trim()
                        awayTeam = right.replace(rightScoreMatch.value, "").trim()
                    } else {
                        homeTeam = left
                        awayTeam = right
                    }
                } else {
                    val vsSplit = cleanTitle.split(Regex("""\s+(?:vs|v|[-–])\s+""", RegexOption.IGNORE_CASE))
                    if (vsSplit.size >= 2) {
                        homeTeam = vsSplit[0].trim()
                        awayTeam = vsSplit[1].trim()
                    } else {
                        homeTeam = cleanTitle
                        awayTeam = ""
                    }
                }

                if (homeScore == "0" && awayScore == "0") {
                    scoreMatch?.let {
                        if (it.groupValues.size >= 3) {
                            homeScore = it.groupValues[1]
                            awayScore = it.groupValues[2]
                        }
                    }
                }
            }
        }

        if (homeTeam.isBlank() || awayTeam.isBlank()) {
            val vsSplit = cleanTitle.split(Regex("""\s+(?:vs|v|[-–])\s+""", RegexOption.IGNORE_CASE))
            if (vsSplit.size >= 2) {
                homeTeam = vsSplit[0].trim()
                awayTeam = vsSplit[1].trim()
            }
        }

        homeTeam = homeTeam.replace(Regex("""^\(\d+\)\s*|\s*\(\d+\)$"""), "").trim().ifEmpty { "Home" }
        awayTeam = awayTeam.replace(Regex("""^\(\d+\)\s*|\s*\(\d+\)$"""), "").trim().ifEmpty { "Away" }

        val timeRegex = Regex("""\b(\d{1,3}(?:\+\d{1,2})?['’]|\d{1,2}:\d{2})\b""")
        val periodRegex = Regex("""\b(HT|FT|OT|Q[1-4]|ET|Half[- ]?Time|Full[- ]?Time|Final|Live|Перерыв|Матч окончен)\b""", RegexOption.IGNORE_CASE)

        val matchTime = timeRegex.find(text)?.value
            ?: timeRegex.find(sub)?.value
            ?: timeRegex.find(big)?.value
            ?: timeRegex.find(title)?.value
            ?: periodRegex.find(text)?.value
            ?: periodRegex.find(sub)?.value
            ?: periodRegex.find(title)?.value

        val league = sub.ifEmpty {
            when {
                pkg.contains("premierleague") -> "Premier League"
                pkg.contains("nba") -> "NBA"
                pkg.contains("ucl") -> "Champions League"
                else -> ""
            }
        }

        val competition = when {
            league.isNotBlank() && matchTime != null -> "$league • $matchTime"
            league.isNotBlank() -> league
            matchTime != null -> "Live • $matchTime"
            else -> "Live Match"
        }

        val latestEvent = when {
            eventPrefixMatch != null && eventPrefixMatch.value.isNotBlank() -> {
                val prefix = eventPrefixMatch.value.trim()
                if (text.isNotBlank() && text != title && scoreRegex.find(text) == null) "$prefix: $text" else prefix
            }
            big.isNotBlank() && big != title && big != text -> big
            text.isNotBlank() && text != title && scoreRegex.matchEntire(text) == null -> text
            else -> null
        }

        val (resolvedHome, resolvedAway) = SportBadges.resolve(context, sbn.key, n, homeTeam, awayTeam)
        val homeBadge = resolvedHome ?: runCatching {
            n.getLargeIcon()?.loadDrawable(context)?.toBitmap(128, 128)?.asImageBitmap()
        }.getOrNull()
        val awayBadge = resolvedAway

        return SportInfo(
            key = sbn.key,
            pkg = sbn.packageName,
            competition = competition,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            homeScore = homeScore,
            awayScore = awayScore,
            homeBadge = homeBadge,
            awayBadge = awayBadge,
            latestEvent = latestEvent,
            postTime = sbn.postTime,
            open = n.contentIntent,
        )
    }

    fun icon(context: Context, pkg: String): ImageBitmap? = icons.getOrPut(pkg) {
        runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }

    fun appLabel(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}

/** Приложения, чьи постоянные уведомления остров показывает как «живые активности». */
object LiveApps {
    private val nav = setOf(
        "com.google.android.apps.maps",
        "ru.yandex.yandexmaps",
        "ru.yandex.yandexnavi",
        "ru.dublgis.dgismobile",
        "com.waze",
        "net.osmand",
        "net.osmand.plus",
        "com.here.app.maps",
        "cz.seznam.mapy",
        "app.organicmaps",
        "com.mapswithme.maps.pro",
    )
    private val ride = setOf(
        "ru.yandex.taxi",
        "com.yandex.go",
        "com.ubercab",
        "ee.mtakso.client",
        "ru.citymobil",
        "sinet.startup.indriver",
        "com.lyft.android",
    )
    private val delivery = setOf(
        "ru.yandex.eda",
        "ru.foodfox.client",
        "ru.samokat.app",
        "ru.instamart",
        "ru.sbermarket",
        "com.deliveryclub",
        "com.ubercab.eats",
        "ru.ozon.app.android",
        "com.wildberries.ru",
        "ru.beru.android",
    )

    fun kind(pkg: String, category: String?): LiveKind? = when {
        pkg in nav || category == Notification.CATEGORY_NAVIGATION -> LiveKind.NAV
        pkg in ride -> LiveKind.RIDE
        pkg in delivery -> LiveKind.DELIVERY
        else -> null
    }
}

/**
 * Подписи кнопок звонка на разных языках: остров смотрят во Вьетнаме, Индонезии, Таиланде, Бразилии,
 * Латинской Америке и не только — без них ответить и сбросить из острова там не получалось.
 */
private val AnswerWords = listOf(
    "ответ", "принять", "answer", "accept", "trả lời", "nghe", "jawab", "terima", "angkat", "contestar", "responder",
    "atender", "aceptar", "aceitar", "รับสาย", "cevapla", "yanıtla", "kabul", "annehmen", "répondre", "rispondi", "accetta",
    "odbierz", "відповісти", "прийняти", "sagutin", "接听", "接聽", "応答", "응답", "받기",
)
private val DeclineWords = listOf(
    "отклон", "сброс", "decline", "reject", "dismiss", "từ chối", "tolak", "rechazar", "recusar", "rejeitar", "ปฏิเสธ",
    "reddet", "ablehnen", "refuser", "rifiuta", "odrzuć", "відхилити", "tanggihan", "拒接", "拒絕", "拒否", "거절",
)
private val HangUpWords = listOf(
    "заверш", "положить", "hang", "end call", "kết thúc", "gác máy", "akhiri", "colgar", "finalizar", "desligar",
    "encerrar", "วางสาย", "kapat", "auflegen", "beenden", "raccrocher", "riaggancia", "zakończ", "завершити", "ibaba",
    "挂断", "掛斷", "終了", "종료",
)

private val CallingWords = listOf("calling", "dialing", "вызов", "набор", "соединение", "calling…", "dialing…", "идет набор", "идёт набор")
private val RingingWords = listOf("ringing", "идёт вызов", "идет вызов", "гудки", "ringing…", "перезвон")
private val CallActivityWords = listOf("call", "звонок", "вызов", "audio", "video", "whatsapp", "telegram", "viber")
