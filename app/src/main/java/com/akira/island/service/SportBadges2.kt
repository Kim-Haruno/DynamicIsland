package com.akira.island.service

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.LinkedHashMap

object SportBadges {
    data class Result(val home: ImageBitmap?, val away: ImageBitmap?)

    private val cache = object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 64
    }

    private data class Candidate(
        val bitmap: Bitmap,
        val description: String,
        val width: Int,
        val height: Int,
    )

    fun resolve(context: Context, key: String, n: Notification, homeTeam: String, awayTeam: String): Result {
        @Suppress("DEPRECATION")
        val layoutType = when {
            n.bigContentView != null -> "bigContentView"
            n.contentView != null -> "contentView"
            n.headsUpContentView != null -> "headsUpContentView"
            else -> "none"
        }

        @Suppress("DEPRECATION")
        val rv = n.bigContentView ?: n.contentView ?: n.headsUpContentView
        val candidates = mutableListOf<Candidate>()

        if (rv != null) {
            runCatching {
                val root = rv.apply(context, FrameLayout(context))
                collectImageViews(root, candidates)
            }
        }

        val largeIconExists = n.getLargeIcon() != null

        val imageDetails = StringBuilder()
        imageDetails.append("Layout: $layoutType, CandidateImages: ${candidates.size}, LargeIconExists: $largeIconExists\n")

        val uniqueCandidates = mutableListOf<Candidate>()
        for ((idx, cand) in candidates.withIndex()) {
            imageDetails.append("[$idx] size=${cand.width}x${cand.height}, desc='${cand.description}'\n")
            val isDup = uniqueCandidates.any { existing ->
                runCatching { existing.bitmap.sameAs(cand.bitmap) }.getOrDefault(false)
            }
            if (!isDup) {
                uniqueCandidates.add(cand)
            }
        }

        val normHome = normalize(homeTeam)
        val normAway = normalize(awayTeam)

        var homeBitmap: Bitmap? = null
        var awayBitmap: Bitmap? = null

        val assignmentLog = mutableListOf<String>()

        if (normHome.length >= 3 && cache.containsKey(normHome)) {
            homeBitmap = cache[normHome]
            assignmentLog.add("Home loaded from cache")
        }
        if (normAway.length >= 3 && cache.containsKey(normAway)) {
            awayBitmap = cache[normAway]
            assignmentLog.add("Away loaded from cache")
        }

        val assignedIndices = mutableSetOf<Int>()

        for ((idx, cand) in uniqueCandidates.withIndex()) {
            val normDesc = normalize(cand.description)
            if (normDesc.length >= 3) {
                if (normHome.length >= 3 && (normDesc.contains(normHome) || normHome.contains(normDesc))) {
                    if (homeBitmap == null) {
                        homeBitmap = cand.bitmap
                        assignedIndices.add(idx)
                        assignmentLog.add("Home assigned by description match ($normDesc <=> $normHome) at index $idx")
                    }
                }
                if (normAway.length >= 3 && (normDesc.contains(normAway) || normAway.contains(normDesc))) {
                    if (awayBitmap == null) {
                        awayBitmap = cand.bitmap
                        assignedIndices.add(idx)
                        assignmentLog.add("Away assigned by description match ($normDesc <=> $normAway) at index $idx")
                    }
                }
            }
        }

        val unassigned = uniqueCandidates.withIndex().filter { it.index !in assignedIndices }
        if (unassigned.size == 1) {
            val remainingCand = unassigned[0].value
            val remIdx = unassigned[0].index
            if (homeBitmap != null && awayBitmap == null) {
                awayBitmap = remainingCand.bitmap
                assignedIndices.add(remIdx)
                assignmentLog.add("Away assigned by elimination (only 1 remaining image at index $remIdx)")
            } else if (awayBitmap != null && homeBitmap == null) {
                homeBitmap = remainingCand.bitmap
                assignedIndices.add(remIdx)
                assignmentLog.add("Home assigned by elimination (only 1 remaining image at index $remIdx)")
            }
        }

        if (homeBitmap == null && awayBitmap == null && uniqueCandidates.size >= 2) {
            val unass = uniqueCandidates.withIndex().filter { it.index !in assignedIndices }
            if (unass.size >= 2) {
                homeBitmap = unass[0].value.bitmap
                awayBitmap = unass[1].value.bitmap
                assignedIndices.add(unass[0].index)
                assignedIndices.add(unass[1].index)
                assignmentLog.add("Assigned by layout order: first (index ${unass[0].index}) = home, second (index ${unass[1].index}) = away")
            }
        }

        if (normHome.length >= 3 && homeBitmap != null) {
            cache[normHome] = homeBitmap
        }
        if (normAway.length >= 3 && awayBitmap != null) {
            cache[normAway] = awayBitmap
        }

        val finalHomeImg = homeBitmap?.asImageBitmap()
        val finalAwayImg = awayBitmap?.asImageBitmap()

        val extrasKeys = n.extras?.keySet()?.sorted()?.joinToString(", ") ?: ""

        val report = buildString {
            append(imageDetails)
            append("Resolved Home: ${homeBitmap != null}, Away: ${awayBitmap != null}\n")
            append("Assignments: ${assignmentLog.joinToString("; ")}\n")
            append("Extras keySet: [$extrasKeys]")
        }

        LiveDebug.recordRaw("sport:$key", report)

        return Result(finalHomeImg, finalAwayImg)
    }

    private fun collectImageViews(view: View, out: MutableList<Candidate>) {
        if (view is ImageView) {
            runCatching {
                val drawable = view.drawable
                val desc = view.contentDescription?.toString().orEmpty()
                if (drawable != null) {
                    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: view.width
                    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: view.height
                    if (w in 8..512 && h in 8..512) {
                        val ratio = w.toFloat() / h.toFloat()
                        if (ratio in 0.6f..1.7f) {
                            val bmp = drawable.toBitmap(w.coerceIn(8, 128), h.coerceIn(8, 128), Bitmap.Config.ARGB_8888)
                            out.add(Candidate(bmp, desc, w, h))
                        }
                    }
                }
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectImageViews(view.getChildAt(i), out)
            }
        }
    }

    private fun normalize(str: String): String {
        return str.lowercase().replace(Regex("[^a-z0-9]"), "")
    }
}
