package com.akira.island.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

object Lang {
    val en: Boolean get() = true
    val locale: Locale get() = Locale.ENGLISH
}

fun tr(text: String): String = text
fun tr(text: String, en: String): String = en

@Composable
fun LangScope(content: @Composable () -> Unit) {
    LocalConfiguration.current
    content()
}
