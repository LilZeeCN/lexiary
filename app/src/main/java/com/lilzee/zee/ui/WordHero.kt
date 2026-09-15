package com.lilzee.zee.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lilzee.zee.R
import com.lilzee.zee.speech.Speaker

/** 单词展示：先尝试单行自适应缩小；缩到下限仍放不下（极端长词）则切换为多行换行兜底，永不溢出 */
@Composable
fun AutoSizeWord(word: String, color: androidx.compose.ui.graphics.Color, maxFontSize: TextUnit = 26.sp) {
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 在显示前确定字号，缓存到文字或可用宽度变化；不在排版回调里逐帧缩小。
        val (fontSize, wrap) = remember(word, maxFontSize, constraints.maxWidth, textMeasurer) {
            var size = maxFontSize * minOf(1f, 16f / word.length).coerceAtLeast(0.45f)
            var multiline = false
            while (true) {
                val result = textMeasurer.measure(
                    text = word,
                    style = TextStyle(
                        fontSize = size,
                        lineHeight = size * 1.15,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = if (multiline) 3 else 1,
                    softWrap = multiline,
                    constraints = Constraints(maxWidth = constraints.maxWidth),
                )
                if (!result.hasVisualOverflow) break
                if (!multiline && size <= 14.sp) {
                    multiline = true
                } else if (size > 10.sp) {
                    size = (size.value * 0.9f).coerceAtLeast(10f).sp
                } else {
                    break
                }
            }
            size to multiline
        }
        BasicText(
            text = word,
            style = TextStyle(
                fontSize = fontSize,
                lineHeight = fontSize * 1.15,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = color,
            ),
            maxLines = if (wrap) 3 else 1,
            softWrap = wrap,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 封面右下角出血裁切的幽灵首字母水印 */
@Composable
fun GhostLetter(word: String, heroInk: androidx.compose.ui.graphics.Color, fontSize: TextUnit, modifier: Modifier = Modifier) {
    Text(
        word.take(1).uppercase(),
        fontSize = fontSize,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        color = heroInk.copy(alpha = 0.08f),
        modifier = modifier,
    )
}

/** 与主页卡片同源的「暖纸词典」封面：色块 + 衬线大字 + 音标 + 幽灵水印。
 *  弹窗查词时用它，所见即所藏——弹窗里就是它入库后的样子。 */
@Composable
fun WordHeroBlock(
    word: String,
    phonetic: String?,
    loading: Boolean = false,
    big: Boolean = false,
) {
    val dark = isSystemInDarkTheme()
    val tint = tintFor(word, dark)
    val heroInk = if (dark) HeroInkDark else HeroInkLight
    val context = LocalContext.current

    // 与主页详情卡同款交互：点封面听发音，引擎提前预热
    LaunchedEffect(Unit) { Speaker.warmup(context) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (big) 150.dp else 112.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tint)
            .pointerInput(word) { detectTapGestures { Speaker.speak(word) } }
    ) {
        GhostLetter(
            word = word,
            heroInk = heroInk,
            fontSize = if (big) 170.sp else 120.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = if (big) 44.dp else 30.dp),
        )
        if (Speaker.ready && !loading) {
            Image(
                painterResource(R.drawable.ic_speaker),
                contentDescription = "朗读单词",
                colorFilter = ColorFilter.tint(heroInk.copy(alpha = 0.45f)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .size(14.dp),
            )
        }
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            AutoSizeWord(word = word, color = heroInk, maxFontSize = if (big) 44.sp else 32.sp)
            phonetic?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.size(4.dp))
                Text(
                    it,
                    fontSize = if (big) 15.sp else 13.sp,
                    lineHeight = if (big) 20.sp else 17.sp,
                    maxLines = 2,
                    color = heroInk.copy(alpha = 0.85f),
                )
            }
            if (loading) {
                Spacer(Modifier.size(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "DeepSeek 查询中…",
                        fontSize = 12.sp,
                        color = heroInk.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
