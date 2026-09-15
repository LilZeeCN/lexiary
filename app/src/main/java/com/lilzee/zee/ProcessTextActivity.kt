package com.lilzee.zee

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lilzee.zee.data.WordStore
import com.lilzee.zee.net.DeepSeekClient
import com.lilzee.zee.net.LookupResult
import com.lilzee.zee.speech.Speaker
import com.lilzee.zee.ui.WordHeroBlock
import com.lilzee.zee.ui.ZeeTheme
import kotlinx.coroutines.launch

/** 去掉单词首尾的标点，保留撇号和连字符（don't、well-known） */
internal fun normalizeToken(token: String): String =
    token.trim { c -> !(c.isLetter() || c == '\'' || c == '’' || c == '-') }

/**
 * 系统文本选择菜单 "Zee" 的落地页。
 * 句子 -> 译文置顶 + 点词入库（原句作例句）；单词 -> 直接入库（AI 补例句）。
 * singleTop：连续查词时走 onNewIntent，重置弹窗内容。
 */
class ProcessTextActivity : ComponentActivity() {

    private val selection = mutableStateOf<Pair<String, Boolean>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            ZeeTheme {
                val current = selection.value
                if (current == null) {
                    finish()
                } else {
                    key(current) {
                        PopupScreen(selected = current.first, singleWord = current.second, onClose = ::finish)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val selected = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        val singleWord = selected.isNotEmpty() && !selected.any { it.isWhitespace() }
        selection.value = when {
            selected.isEmpty() -> null
            singleWord && normalizeToken(selected).isEmpty() -> null
            else -> selected to singleWord
        }
    }
}

private sealed interface PopupState {
    data object Picking : PopupState
    data object Loading : PopupState
    data class Done(val token: String, val result: LookupResult, val newlySaved: Boolean) : PopupState
    data class Failed(val message: String) : PopupState
}

@Composable
private fun PopupScreen(selected: String, singleWord: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 弹窗关闭（含点词完成跳回原应用）时停止朗读，不把声音带回去处
    DisposableEffect(Unit) { onDispose { Speaker.stop() } }
    var state by remember {
        mutableStateOf<PopupState>(if (singleWord) PopupState.Loading else PopupState.Picking)
    }
    var pendingWord by remember { mutableStateOf(if (singleWord) normalizeToken(selected) else "") }

    // 整句翻译：句子模式一进来就发请求（与选词互不阻塞），读懂第一优先
    var sentenceZh by remember { mutableStateOf<String?>(null) }
    var sentenceError by remember { mutableStateOf<String?>(null) }
    var sentenceAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(sentenceAttempt) {
        if (singleWord) return@LaunchedEffect
        sentenceZh = null
        sentenceError = null
        try {
            sentenceZh = DeepSeekClient.translateSentence(selected)
        } catch (e: Exception) {
            sentenceError = e.message ?: "网络错误"
        }
    }

    fun lookup(token: String) {
        pendingWord = token
        state = PopupState.Loading
        scope.launch {
            try {
                val result = if (singleWord) DeepSeekClient.lookupWord(token)
                else DeepSeekClient.lookupInSentence(token, selected)
                val saved = WordStore.get(context).insert(
                    word = result.word.ifBlank { token },
                    norm = token.lowercase(),
                    phonetic = result.phonetic,
                    meaning = result.meaning,
                    exampleEn = result.exampleEn,
                    exampleZh = result.exampleZh,
                    sourceSentence = if (singleWord) null else selected,
                )
                state = PopupState.Done(token, result, saved)
            } catch (e: Exception) {
                state = PopupState.Failed(e.message ?: "请求失败")
            }
        }
    }

    if (singleWord) {
        LaunchedEffect(Unit) { lookup(normalizeToken(selected)) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
                // 消费卡片上的点击，避免冒泡到背景关闭弹窗
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(
                Modifier
                    .padding(18.dp)
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 字标 + 保存状态徽章
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Zee",
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    val done = state as? PopupState.Done
                    if (done != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (done.newlySaved) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                if (done.newlySaved) "✓ 已入库" else "已在生词本",
                                fontSize = 11.sp,
                                color = if (done.newlySaved) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                if (!singleWord) {
                    Spacer(Modifier.height(10.dp))
                    SentenceTranslationBlock(
                        source = selected,
                        zh = sentenceZh,
                        error = sentenceError,
                        onRetry = { sentenceAttempt++ },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    )
                }

                when (val s = state) {
                    PopupState.Picking -> PickingContent(selected) { lookup(it) }
                    PopupState.Loading -> {
                        Spacer(Modifier.height(4.dp))
                        WordHeroBlock(word = pendingWord, phonetic = null, loading = true)
                    }
                    is PopupState.Done -> DoneContent(
                        singleWord = singleWord,
                        info = s,
                        onPickMore = { state = PopupState.Picking },
                        onClose = onClose,
                    )
                    is PopupState.Failed -> FailedContent(
                        message = s.message,
                        onRetry = { lookup(pendingWord) },
                        onClose = onClose,
                    )
                }
            }
        }
    }
}

/** 置顶整句译文：原句小字回显 + 中文译文，读完即走，不入库 */
@Composable
private fun SentenceTranslationBlock(source: String, zh: String?, error: String?, onRetry: () -> Unit) {
    Text(
        source,
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "译文",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    when {
        zh != null -> Text(
            zh,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "翻译失败：$error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
            PillButton("重试") { onRetry() }
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "翻译中…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickingContent(selected: String, onPick: (String) -> Unit) {
    Text("点击要记的单词：", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        selected.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { token ->
            val norm = normalizeToken(token)
            val enabled = norm.any { it.isLetter() }
            Surface(
                onClick = { if (enabled) onPick(norm) },
                enabled = enabled,
                shape = RoundedCornerShape(50),
                color = if (enabled) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    norm.ifEmpty { token },
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "选好即自动保存，例句就是这句话",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun DoneContent(
    singleWord: Boolean,
    info: PopupState.Done,
    onPickMore: () -> Unit,
    onClose: () -> Unit,
) {
    val r = info.result
    Spacer(Modifier.height(4.dp))
    WordHeroBlock(word = r.word.ifBlank { info.token }, phonetic = r.phonetic)
    Spacer(Modifier.height(12.dp))
    Text(r.meaning, fontSize = 15.sp, lineHeight = 22.sp)
    // 句中选词时例句就是原句+译文，顶部翻译块已展示，不再重复；仅单词模式显示 AI 例句
    if (singleWord && (r.exampleEn != null || r.exampleZh != null)) {
        Spacer(Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(12.dp)) {
                r.exampleEn?.let {
                    Text(it, fontSize = 12.sp, lineHeight = 17.sp, fontStyle = FontStyle.Italic)
                }
                r.exampleZh?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        it,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        if (!singleWord) {
            PillButton("继续选词") { onPickMore() }
            Spacer(Modifier.width(8.dp))
        }
        PillButton("完成", filled = true) { onClose() }
    }
}

@Composable
private fun FailedContent(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Text("查询失败", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        PillButton("重试") { onRetry() }
        Spacer(Modifier.width(8.dp))
        PillButton("关闭", filled = true) { onClose() }
    }
}

@Composable
private fun PillButton(text: String, filled: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (filled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (filled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
