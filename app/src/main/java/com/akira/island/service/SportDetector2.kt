package com.akira.island.service

import android.app.Notification

/**
 * Accepts only Google Search sports updates with both an explicit sport/competition clue and match-like text.
 * Generic Google notifications (including messages, calendar events, and search suggestions) are rejected.
 */
object SportDetector {
    const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    const val GOOGLE_SEARCH_LITE = "com.google.android.apps.searchlite"

    private val googleSportsSources = setOf(GOOGLE_APP, GOOGLE_SEARCH_LITE)

    private val blockedCategories = setOf(
        Notification.CATEGORY_MESSAGE, Notification.CATEGORY_EMAIL, Notification.CATEGORY_CALL,
        Notification.CATEGORY_REMINDER, Notification.CATEGORY_EVENT, Notification.CATEGORY_ALARM,
        Notification.CATEGORY_SOCIAL, Notification.CATEGORY_PROMO,
    )

    /** 2-1 or 2 – 1, not 10:30, 3.5-1.2 or 12/3-4. */
    private val score = Regex("""(?<![\d:.,/])(\d{1,2})\s*[-–]\s*(\d{1,2})(?![\d:.,/])""")

    private val versus = Regex("""\S\s+(?:vs\.?|v)\s+\S""", RegexOption.IGNORE_CASE)

    private val sportTerms = listOf(
        "football", "soccer", "basketball", "cricket", "tennis", "rugby", "hockey", "baseball",
        "goal", "goals", "half time", "half-time", "halftime", "full time", "full-time", "fulltime",
        "kick off", "kick-off", "yellow card", "red card", "substitution", "penalty", "var", "offside",
        "premier league", "champions league", "europa league", "la liga", "serie a", "bundesliga",
        "ligue 1", "mls", "nba", "nfl", "nhl", "mlb", "ipl", "uefa", "fifa", "матч", "лига", "гол",
    )
    private val sportsContext = Regex(
        sportTerms.joinToString("|", prefix = "(?<!\\p{L})(?:", postfix = ")(?!\\p{L})") { Regex.escape(it) },
        RegexOption.IGNORE_CASE,
    )

    fun isSport(pkg: String, category: String?, title: String, text: String, big: String, sub: String): Boolean {
        if (pkg.lowercase() !in googleSportsSources || category in blockedCategories) return false

        val combined = "$title $text $big $sub"
        val hasMatchText = score.containsMatchIn(combined) || versus.containsMatchIn(combined)
        val hasSportsContext = sportsContext.containsMatchIn(combined)
        return hasSportsContext && hasMatchText
    }
}
