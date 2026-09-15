package com.lilzee.zee

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon as DrawableIcon
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.ScaleToBounds
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lilzee.zee.R
import com.lilzee.zee.data.WordEntry
import com.lilzee.zee.data.WordStore
import com.lilzee.zee.speech.Speaker
import com.lilzee.zee.ui.AutoSizeWord
import com.lilzee.zee.ui.GhostLetter
import com.lilzee.zee.ui.HeroInkDark
import com.lilzee.zee.ui.HeroInkLight
import com.lilzee.zee.ui.ZeeTheme
import com.lilzee.zee.ui.tintFor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private val words = mutableStateOf<List<WordEntry>>(emptyList())
    private val daily = mutableStateOf<Map<String, Int>>(emptyMap())

    /** 会话内稳定的乱序排名：进程活着期间同一词的位置不变，重开应用重新洗牌 */
    private val sessionRank = mutableMapOf<Long, Long>()

    private fun shuffled(list: List<WordEntry>): List<WordEntry> {
        list.forEach { sessionRank.getOrPut(it.id) { Random.nextLong() } }
        sessionRank.keys.retainAll(list.asSequence().map { it.id }.toSet())
        return list.sortedBy { sessionRank[it.id] }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeeTheme {
                WordListScreen(
                    words = words.value,
                    daily = daily.value,
                    onDelete = ::deleteWord,
                    onAddTile = ::addLookupTile,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // TTS 引擎在此提前预热：绑定引擎要 1–5 秒，放在列表页空闲期完成，
        // 避免首次打开详情卡点发音时才连接造成掉帧。
        Speaker.warmup(this)
        val store = WordStore.get(this)
        words.value = shuffled(store.all())
        daily.value = store.dailyCounts()
    }

    private fun deleteWord(id: Long) {
        WordStore.get(this).delete(id)
        words.value = WordStore.get(this).all()
    }

    private fun addLookupTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, LookupTileService::class.java),
                getString(R.string.lookup_tile_label),
                DrawableIcon.createWithResource(this, R.drawable.ic_lookup_tile),
                mainExecutor,
            ) { result ->
                val message = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                        "已添加。复制英文后，在控制中心点「Zee 查词」。"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                        "已在控制中心，复制英文后点「Zee 查词」即可。"
                    else -> "也可以在控制中心的「编辑」中添加「Zee 查词」。"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "下拉快捷设置，在「编辑」中添加「Zee 查词」。", Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun WordListScreen(
    words: List<WordEntry>,
    daily: Map<String, Int>,
    onDelete: (Long) -> Unit,
    onAddTile: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<WordEntry?>(null) }
    var selected by remember { mutableStateOf<WordEntry?>(null) }
    var detailEntry by remember { mutableStateOf<WordEntry?>(null) }
    var filter by rememberSaveable { mutableStateOf(TimeFilter.ALL) }
    val gridState = rememberLazyStaggeredGridState()
    val shown = remember(words, filter) { words.filter { filter.contains(it.createdAt) } }

    BackHandler(enabled = selected != null) { selected = null }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SharedTransitionLayout {
            // 列表保留在布局树中，收起详情时无需重新创建、排版所有卡片。
            val listTransition = updateTransition(
                if (selected == null) EnterExitState.Visible else EnterExitState.PostExit,
                label = "wordList",
            )
            val listScope = remember(listTransition) {
                object : AnimatedVisibilityScope {
                    override val transition = listTransition
                }
            }
            val listAlpha = listTransition.animateFloat(
                transitionSpec = { tween(200) }, label = "listAlpha",
            ) { if (it == EnterExitState.Visible) 1f else 0f }
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .graphicsLayer { alpha = listAlpha.value }
                        .then(if (selected != null) Modifier.clearAndSetSemantics { } else Modifier)
                ) {
                    Header(count = words.size, onAddTile = onAddTile)
                    if (words.isNotEmpty()) {
                        FilterRow(selected = filter, onSelect = { filter = it })
                        HeatmapCard(daily)
                    }
                    LazyVerticalStaggeredGrid(
                        state = gridState,
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalItemSpacing = 12.dp,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (words.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) { EmptyState() }
                        }
                        items(shown, key = { it.id }) { entry ->
                            WordCard(
                                entry = entry,
                                sts = this@SharedTransitionLayout,
                                avScope = listScope,
                                onRequestDelete = { pendingDelete = entry },
                                onOpen = {
                                    detailEntry = entry
                                    selected = entry
                                },
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = selected != null,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .pointerInput(Unit) { detectTapGestures { selected = null } },
                        contentAlignment = Alignment.Center,
                    ) {
                        WordDetailCard(
                            entry = checkNotNull(detailEntry),
                            sts = this@SharedTransitionLayout,
                            avScope = this@AnimatedVisibility,
                            onClose = { selected = null },
                            onRequestDelete = { pendingDelete = detailEntry },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${entry.word}」？") },
            text = { Text("词条和例句会一起删除，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(entry.id)
                    if (entry.id == selected?.id) selected = null
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/** 时间筛选：按入库距今的天数划档，纯内存过滤，不查库 */
private enum class TimeFilter(val label: String, val minAgeDays: Int, val maxAgeDays: Int) {
    ALL("全部", 0, Int.MAX_VALUE),
    WEEK("近7天", 0, 7),
    MONTH("近30天", 0, 30),
    OLDER("更早", 30, Int.MAX_VALUE);

    fun contains(createdAt: Long, now: Long = System.currentTimeMillis()): Boolean {
        val ageDays = ((now - createdAt) / DAY_MS).toInt()
        return ageDays in minAgeDays until maxAgeDays
    }

    companion object {
        private const val DAY_MS = 86_400_000L
    }
}

/** 词典页边注式的筛选标签：纯文字 + 选中词下的赤陶色墨线，无底色不抢卡片 */
@Composable
private fun FilterRow(selected: TimeFilter, onSelect: (TimeFilter) -> Unit) {
    val lineColor = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TimeFilter.entries.forEach { f ->
            val sel = f == selected
            Text(
                f.label,
                fontSize = 11.sp,
                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                letterSpacing = 0.5.sp,
                color = if (sel) lineColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(f) }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .drawBehind {
                        // 选中项：文字正下方一条 2dp 墨线，宽度即文字宽
                        if (sel) {
                            val stroke = 2.dp.toPx()
                            drawRoundRect(
                                color = lineColor,
                                topLeft = Offset(0f, size.height - stroke),
                                size = Size(size.width, stroke),
                                cornerRadius = CornerRadius(stroke / 2f),
                            )
                        }
                    },
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

private val heatInkLight = Color(0xFFB4632A)
private val heatInkDark = Color(0xFFE0A060)

/** 热力图格子色阶：空 = 纸上淡墨，1/2/3–4/5+ 四档赤陶墨 */
private fun heatCellColor(level: Int, dark: Boolean): Color = when {
    level <= 0 -> if (dark) Color.White.copy(alpha = 0.07f) else Color(0xFFB9AE9C).copy(alpha = 0.18f)
    else -> (if (dark) heatInkDark else heatInkLight).copy(
        alpha = floatArrayOf(0f, 0.28f, 0.55f, 0.78f, 1f)[level.coerceIn(1, 4)]
    )
}

private const val HEAT_WEEKS = 16

private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

private fun startOfToday(): Calendar = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}

/** 以今天收尾、周一开行的 16×7 日格；level: -1 未发生(未来) 0 空 1–4 强度 */
private fun buildDayGrid(counts: Map<String, Int>): List<IntArray> {
    val today = startOfToday()
    val todayRow = (today.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val start = (today.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -(HEAT_WEEKS - 1) * 7 - todayRow)
    }
    val grid = List(HEAT_WEEKS) { IntArray(7) { -1 } }
    val cursor = start.clone() as Calendar
    for (i in 0 until HEAT_WEEKS * 7) {
        if (cursor.after(today)) break
        val n = counts[dayFormat.format(cursor.time)] ?: 0
        grid[i / 7][i % 7] = when {
            n == 0 -> 0; n == 1 -> 1; n == 2 -> 2; n <= 4 -> 3; else -> 4
        }
        cursor.add(Calendar.DAY_OF_YEAR, 1)
    }
    return grid
}

/** 连续收录天数；今天还没收则从昨天起算，避免白天数字归零 */
private fun calcStreak(counts: Map<String, Int>): Int {
    val cursor = startOfToday()
    if ((counts[dayFormat.format(cursor.time)] ?: 0) == 0) cursor.add(Calendar.DAY_OF_YEAR, -1)
    var streak = 0
    while ((counts[dayFormat.format(cursor.time)] ?: 0) > 0) {
        streak++
        cursor.add(Calendar.DAY_OF_YEAR, -1)
    }
    return streak
}

/** 收录活动：折叠成一行小字，展开是 16 周赤陶墨热力格。整块一个 Canvas 节点，零逐格组合开销 */
@Composable
private fun HeatmapCard(counts: Map<String, Int>) {
    var open by rememberSaveable { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    val total = remember(counts) { counts.values.sum() }
    val streak = remember(counts) { calcStreak(counts) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "活动",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    if (total == 0) "最近 16 周的收录热力"
                    else "近 16 周 $total 词 · 连续 $streak 天",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (open) "收起" else "展开",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (open) {
                val grid = remember(counts, dark) { buildDayGrid(counts) }
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .height((7 * 13 - 3).dp)
                ) {
                    val cell = 10.dp.toPx()
                    val gap = 3.dp.toPx()
                    val gridWidth = HEAT_WEEKS * (cell + gap) - gap
                    val x0 = (size.width - gridWidth) / 2f
                    grid.forEachIndexed { col, week ->
                        week.forEachIndexed { row, level ->
                            if (level >= 0) {
                                drawRoundRect(
                                    color = heatCellColor(level, dark),
                                    topLeft = Offset(x0 + col * (cell + gap), row * (cell + gap)),
                                    size = Size(cell, cell),
                                    cornerRadius = CornerRadius(2.5.dp.toPx()),
                                )
                            }
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "少",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    repeat(5) { level ->
                        Box(
                            Modifier
                                .padding(start = 3.dp)
                                .size(7.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(heatCellColor(level, dark))
                        )
                    }
                    Text(
                        "多",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(count: Int, onAddTile: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            "Zee",
            fontSize = 32.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
        )
        Spacer(Modifier.size(10.dp))
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                "$count 个词",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (Build.VERSION.SDK_INT >= 24) {
            TextButton(onClick = onAddTile) { Text("添加快捷入口", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Zz",
                fontSize = 72.sp,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "还没有收录单词",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (Build.VERSION.SDK_INT >= 24) "复制英文 → 控制中心点「Zee 查词」"
                else "在支持选词菜单的应用中选中英文 → Zee",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun WordCard(
    entry: WordEntry,
    sts: androidx.compose.animation.SharedTransitionScope,
    avScope: androidx.compose.animation.AnimatedVisibilityScope,
    onRequestDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val tint = tintFor(entry.word, dark)
    val heroInk = if (dark) HeroInkDark else HeroInkLight

    with(sts) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            onClick = onOpen,
            modifier = Modifier
                .fillMaxWidth()
                // 固定文字排版，再缩放整张卡片，避免动画每一帧重新测量文本。
                .sharedBounds(
                    rememberSharedContentState("word-${entry.id}"), avScope,
                    resizeMode = ScaleToBounds(ContentScale.FillBounds),
                ),
        ) {
            Column {
                // 封面：色块 + 幽灵首字母 + 衬线大字单词
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 92.dp)
                        .background(tint)
                ) {
                    GhostLetter(
                        word = entry.word,
                        heroInk = heroInk,
                        fontSize = 92.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 6.dp, y = 22.dp),
                    )
                    Column(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 14.dp, top = 16.dp, end = 40.dp, bottom = 14.dp)
                    ) {
                        AutoSizeWord(entry.word, color = heroInk)
                        entry.phonetic?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                it,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                maxLines = 2,
                                color = heroInk.copy(alpha = 0.85f),
                            )
                        }
                    }
                    IconButton(
                        onClick = onRequestDelete,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(30.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            modifier = Modifier.size(15.dp),
                            tint = heroInk.copy(alpha = 0.55f),
                        )
                    }
                }

                // 释义 + 例句
                Column(Modifier.padding(12.dp)) {
                    Text(
                        entry.meaning,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val exampleEn = entry.exampleEn
                    val exampleZh = entry.exampleZh
                    if (exampleEn != null || exampleZh != null) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(Modifier.padding(9.dp)) {
                                exampleEn?.let {
                                    Text(
                                        it,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                exampleZh?.let {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        it,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                .format(Date(entry.createdAt)),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (dark) tint.copy(alpha = 0.55f) else tint,
                        ) {
                            Text(
                                if (entry.sourceSentence == null) "AI 例句" else "原句",
                                fontSize = 9.sp,
                                color = heroInk,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 点卡片放大到屏幕中央的详情视图 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun WordDetailCard(
    entry: WordEntry,
    sts: androidx.compose.animation.SharedTransitionScope,
    avScope: androidx.compose.animation.AnimatedVisibilityScope,
    onClose: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val tint = tintFor(entry.word, dark)
    val heroInk = if (dark) HeroInkDark else HeroInkLight
    val context = LocalContext.current

    // 引擎提前预热（初始化要 1–5 秒）；离开详情即停，不把声音带回去处
    LaunchedEffect(Unit) { Speaker.warmup(context) }
    DisposableEffect(entry.id) { onDispose { Speaker.stop() } }

    with(sts) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(26.dp)
                .sharedBounds(
                    rememberSharedContentState("word-${entry.id}"), avScope,
                    resizeMode = ScaleToBounds(ContentScale.FillBounds),
                )
                // 消费卡片点击，避免冒泡到遮罩关闭
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(tint)
                        // 点封面听发音；引擎不可用时 speak 是空操作
                        .pointerInput(entry.word) {
                            detectTapGestures { Speaker.speak(entry.word) }
                        }
                ) {
                    GhostLetter(
                        word = entry.word,
                        heroInk = heroInk,
                        fontSize = 150.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 10.dp, y = 38.dp),
                    )
                    if (Speaker.ready) {
                        Image(
                            painterResource(R.drawable.ic_speaker),
                            contentDescription = "朗读单词",
                            colorFilter = ColorFilter.tint(heroInk.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                                .size(15.dp),
                        )
                    }
                    Column(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(horizontal = 18.dp, vertical = 20.dp)
                    ) {
                        AutoSizeWord(entry.word, color = heroInk, maxFontSize = 44.sp)
                        entry.phonetic?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                it,
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                maxLines = 2,
                                color = heroInk.copy(alpha = 0.85f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    entry.meaning,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (entry.exampleEn != null || entry.exampleZh != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            entry.exampleEn?.let {
                                Text(
                                    it,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            entry.exampleZh?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    it,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                            .format(Date(entry.createdAt)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (dark) tint.copy(alpha = 0.55f) else tint,
                    ) {
                        Text(
                            if (entry.sourceSentence == null) "AI 例句" else "原句",
                            fontSize = 10.sp,
                            color = heroInk,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRequestDelete) {
                        Text("删除词条", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        onClick = onClose,
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            "关闭",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}
