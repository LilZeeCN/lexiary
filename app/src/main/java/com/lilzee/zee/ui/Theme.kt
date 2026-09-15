package com.lilzee.zee.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/** 「暖纸词典」色系：卡片封面按单词哈希轮换的 6 个暖调色 */
data class WordTint(val light: Color, val dark: Color)

private val tints = listOf(
    WordTint(Color(0xFFF5E6C4), Color(0xFF4A422E)), // 奶油
    WordTint(Color(0xFFDCE5D3), Color(0xFF39423A)), // 鼠尾草
    WordTint(Color(0xFFF0D9CB), Color(0xFF4A3A32)), // 陶土
    WordTint(Color(0xFFD7E1EA), Color(0xFF33404A)), // 青灰
    WordTint(Color(0xFFF2DAD8), Color(0xFF46383A)), // 豆沙
    WordTint(Color(0xFFE6DEEA), Color(0xFF3C3844)), // 薰衣草
)

fun tintFor(word: String, dark: Boolean): Color {
    val t = tints[abs(word.hashCode()) % tints.size]
    return if (dark) t.dark else t.light
}

val HeroInkLight = Color(0xFF3A342B)
val HeroInkDark = Color(0xFFEDE6DA)

private val LightScheme = lightColorScheme(
    background = Color(0xFFFAF6EF),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF332E28),
    onSurface = Color(0xFF332E28),
    primary = Color(0xFFB4632A),
    onPrimary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0E8DB),
    onSecondaryContainer = Color(0xFF5A5142),
    surfaceVariant = Color(0xFFF4EEE3),
    onSurfaceVariant = Color(0xFF6B6255),
    outline = Color(0xFFB9AE9C),
)

private val DarkScheme = darkColorScheme(
    background = Color(0xFF151210),
    surface = Color(0xFF1E1A16),
    onBackground = Color(0xFFE8E1D5),
    onSurface = Color(0xFFE8E1D5),
    primary = Color(0xFFE0A060),
    onPrimary = Color(0xFF2A2014),
    secondaryContainer = Color(0xFF2C2721),
    onSecondaryContainer = Color(0xFFCFC5B4),
    surfaceVariant = Color(0xFF272219),
    onSurfaceVariant = Color(0xFFB0A695),
    outline = Color(0xFF6B6153),
)

@Composable
fun ZeeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
