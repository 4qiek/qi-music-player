package com.qi.player.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qi.player.data.Song
import com.qi.player.ui.theme.QiTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 右侧半圆滚轮：滑动选曲。
 *
 * 交互仿「物理转盘」：拖动时列表跟手，松手按位移量吸附到最近一格。
 * 面板只在按住右侧触发区时出现，松手 1 秒后自动收回——
 * 这样它不会长期占住屏幕右侧那条本来可以放歌词的手势区。
 */
@Composable
fun WheelPanel(
    queue: List<Song>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onInteract: () -> Unit = {},
    panelWidth: Dp = 118.dp,
    panelHeight: Dp = 260.dp,
) {
    val colors = QiTheme.colors
    val itemHeight = 40.dp
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }

    var dragPx by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    // 让当前曲目停在滚轮正中：中心偏移 - 当前序号 * 行高
    val baseOffset = (panelHeight - itemHeight) / 2 - itemHeight * currentIndex

    Box(
        modifier = modifier
            .width(panelWidth)
            .height(panelHeight)
            .clip(RoundedCornerShape(panelHeight / 2, 0.dp, 0.dp, panelHeight / 2))
            .background(colors.surface)
            .pointerInput(itemHeightPx, queue.size) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragging = true
                        onInteract()
                    },
                    onDragEnd = {
                        val delta = -(dragPx / itemHeightPx).roundToInt()
                        if (abs(delta) >= 1) {
                            val target = (currentIndex + delta).coerceIn(0, queue.lastIndex)
                            if (target != currentIndex) onSelect(target)
                        }
                        dragPx = 0f
                        dragging = false
                        onInteract()
                    },
                    onDragCancel = {
                        dragPx = 0f
                        dragging = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // 单次手势最多推两格：再放大位移会让人觉得「一滑就飞过头」
                        dragPx = (dragPx + dragAmount).coerceIn(-itemHeightPx * 2, itemHeightPx * 2)
                    },
                )
            },
    ) {
        // 列表本体：用 px 位移，避免 Dp 换算误差累积成「越滑越偏」
        val offsetPx = with(density) { baseOffset.toPx() } + dragPx

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = with(density) { offsetPx.toDp() }),
        ) {
            queue.forEachIndexed { index, song ->
                val active = index == currentIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = song.displayTitle,
                        fontSize = if (active) 13.sp else 11.sp,
                        color = when {
                            active && song.isHiRes -> colors.gold
                            active -> colors.textMain
                            else -> colors.textSub.copy(alpha = 0.35f)
                        },
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.scale(if (active) 1.02f else 0.94f),
                    )
                }
            }
        }

        // 上下渐隐遮罩，让滚轮看起来是「转」进去而不是被硬切
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    Brush.verticalGradient(listOf(colors.surface, Color.Transparent)),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, colors.surface)),
                ),
        )

        // 右侧中心的选中刻度
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(14.dp)
                .height(2.dp)
                .background(colors.accent),
        )
    }
}
