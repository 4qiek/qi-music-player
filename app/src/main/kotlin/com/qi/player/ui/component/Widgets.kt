package com.qi.player.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.qi.player.data.LyricsResult
import com.qi.player.data.Song
import com.qi.player.ui.theme.QiTheme
import com.qi.player.ui.theme.QiType
import com.qi.player.util.LrcParser
import kotlin.math.roundToInt

/** 小标题：等宽字体 + 字距，模仿草图中 SETTINGS / LIBRARY 的分节标签 */
@Composable
fun SectionLabel(text: String, color: Color = QiTheme.colors.textSub) {
    Text(
        text = text,
        style = QiType.sectionLabel,
        color = color.copy(alpha = 0.75f),
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/**
 * 可拖动进度条。
 *
 * 用一个 24dp 高的透明热区包住 6dp 的细轨道：草图上那条线太细，
 * 手指点不准。视觉细、热区大，是这类控件必须做的手感补偿。
 *
 * 手势用单个 awaitEachGesture 统一处理「按下即定位 + 拖动跟手」。
 * 早期写法是 tap 与 drag 各挂一个 pointerInput，两者都会等待/消费
 * 同一串指针事件，实测会互相吞——点一下没反应、或拖到一半断掉。
 */
@Composable
fun QiProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    onSeek: (Float) -> Unit,
) {
    val colors = QiTheme.colors
    var widthPx = remember { 0f }
    val fraction = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (widthPx > 0f) onSeek((down.position.x / widthPx).coerceIn(0f, 1f))
                    down.consume()
                    drag(down.id) { change ->
                        if (widthPx > 0f) onSeek((change.position.x / widthPx).coerceIn(0f, 1f))
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        NeuInsetBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            shape = RoundedCornerShape(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.accent),
            )
        }
    }
}

/**
 * 曲库列表的一行。
 *
 * 刻意**不在内部挂 clickable**：点击与长按的语义由调用方决定
 *（播放 / 收藏），把修饰符从外部传进来，避免内外两层手势互相抢事件。
 */
@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = QiTheme.colors
    val titleColor by animateColorAsState(
        targetValue = when {
            isCurrent -> colors.accent
            song.isHiRes -> colors.gold
            else -> colors.textMain
        },
        animationSpec = tween(220),
        label = "songTitleColor",
    )

    NeuSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArt(
                song = song,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                showPlaceholderGlyph = false,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.displayTitle,
                        style = QiType.listTitle,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (song.isHiRes) {
                        Spacer(Modifier.width(6.dp))
                        HrBadge()
                    }
                }
                Text(
                    text = song.displayArtist,
                    fontSize = 11.sp,
                    color = colors.textSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = song.listTrailingText,
                style = QiType.monoSmall,
                color = if (song.isHiRes) colors.gold.copy(alpha = 0.9f) else colors.textSub,
            )
        }
    }
}

/** 歌手 / 专辑 / 歌单的聚合行 */
@Composable
fun GroupRow(
    title: String,
    subtitle: String,
    leading: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = QiTheme.colors
    NeuSurface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.inset)
                    .neuInset(colors),
                contentAlignment = Alignment.Center,
            ) {
                Icon(leading, contentDescription = null, tint = colors.textSub, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = QiType.listTitle, color = colors.textMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = colors.textSub)
            }
        }
    }
}

/** 设置页里的整行动作按钮 */
@Composable
fun NeuActionButton(
    text: String,
    leading: ImageVector? = null,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = QiTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(90), label = "actionScale")
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .then(
                if (pressed) {
                    Modifier
                        .clip(shape)
                        .background(colors.inset)
                        .neuInset(colors)
                } else {
                    Modifier.neuRaised(shape, colors, 6.dp)
                },
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Icon(
                    leading,
                    contentDescription = null,
                    tint = if (accent) colors.accent else colors.textSub,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = QiType.body,
                color = if (accent) colors.accent else colors.textMain,
            )
        }
    }
}

/**
 * 歌词视图。
 *
 * 当前行用衬线体放大并加一层强调色辉光；其余行压到 0.45 透明度。
 * 点击任意行跳转到该行——这是看歌词时最自然的手势。
 */
@Composable
fun LyricView(
    result: LyricsResult,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QiTheme.colors

    when (result) {
        is LyricsResult.Empty -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("尚无歌词", style = QiType.lyric, color = colors.textSub)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "把同名 .lrc 放在歌曲目录，或在设置中开启联网补全",
                        fontSize = 10.sp,
                        color = colors.textSub.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        is LyricsResult.Plain -> {
            LazyColumn(modifier.fillMaxSize(), state = rememberLazyListState()) {
                item {
                    Text(
                        text = result.text,
                        style = QiType.lyric,
                        color = colors.textMain.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp, horizontal = 8.dp),
                    )
                }
            }
        }

        is LyricsResult.Synced -> {
            val lines = result.lines
            val activeIndex = LrcParser.indexAt(lines, positionMs)
            val listState = rememberLazyListState()

            LaunchedEffect(activeIndex) {
                if (activeIndex >= 0) {
                    // 让当前行停在偏上的位置，给后续歌词留出视线余量
                    listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
                }
            }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item { Spacer(Modifier.height(60.dp)) }
                itemsIndexed(lines) { index, line ->
                    val active = index == activeIndex
                    val scale by animateFloatAsState(if (active) 1.06f else 0.94f, tween(240), label = "lyricScale")
                    Text(
                        text = line.text,
                        style = if (active) {
                            QiType.lyric.copy(
                                fontSize = 17.sp,
                                shadow = Shadow(colors.accent, Offset.Zero, 14f),
                            )
                        } else {
                            QiType.lyric
                        },
                        color = if (active) colors.textMain else colors.textSub.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(scale)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSeek(line.timeMs) }
                            .padding(vertical = 6.dp, horizontal = 12.dp),
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

/** 把 0..1 的浮点转成百分比文案，供调音台与进度提示复用 */
fun Float.asPercent(): String = "${(this * 100).roundToInt()}%"

/**
 * 点击 + 长按，且**不带水波纹**。
 *
 * 用 detectTapGestures 而不是 combinedClickable：
 *  1. combinedClickable 在 Compose 1.5 有多个重载，带 interactionSource 的那个
 *     参数表不稳定，容易踩到解析歧义；
 *  2. 更重要的一点——拟物界面上飘一层圆形水波纹会立刻把「实体材质」的错觉打碎。
 *     手感交给按压缩放与阴影翻转，不要 Ripple。
 */
fun Modifier.tapGestures(
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
): Modifier = this.pointerInput(onTap, onLongPress) {
    detectTapGestures(
        onTap = { onTap() },
        onLongPress = if (onLongPress == null) null else { _: Offset -> onLongPress() },
    )
}
