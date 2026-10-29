package com.qi.player.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qi.player.ui.theme.QiColors
import com.qi.player.ui.theme.QiTheme

/**
 * 拟物（Neumorphism）构件库。
 *
 * 实现取舍说明：
 * 真正的拟物需要「同色背景 + 双向模糊阴影」。Compose 的 Modifier.shadow 只能画
 * 单向外阴影，且设置彩色阴影的 ambientColor/spotColor 在 API 28 以下会被忽略。
 * 因此这里用三层拼出效果：
 *   1. shadow() 画暗侧外阴影（API 28+ 是有色阴影，以下退化为黑色，仍成立）；
 *   2. 一层线性渐变描边画亮侧高光（左上亮 → 右下透明），跨 API 稳定；
 *   3. 内凹构件反过来：不画外阴影，改在四边内侧贴渐变条模拟内阴影。
 * 这套做法没有任何模糊滤镜依赖，也就没有硬件加速下的失效问题。
 */

/** 亮侧高光描边：左上角最亮，向右下淡出 */
private fun raisedBorderBrush(colors: QiColors): Brush = Brush.linearGradient(
    colors = listOf(
        colors.shadowLight.copy(alpha = if (colors.isLight) 1f else 0.55f),
        Color.Transparent,
        Color.Transparent,
    ),
    start = Offset.Zero,
    end = Offset.Infinite,
)

/** 外凸：暗侧投影 + 亮侧高光描边 */
fun Modifier.neuRaised(
    shape: Shape,
    colors: QiColors,
    elevation: Dp,
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = colors.shadowDark,
        spotColor = colors.shadowDark,
    )
    .background(colors.surface, shape)
    .border(1.dp, raisedBorderBrush(colors), shape)

/** 内凹：四边内侧贴渐变条，模拟光被「压」进去 */
fun Modifier.neuInset(colors: QiColors): Modifier = this.drawWithContent {
    drawContent()
    val edge = 14.dp.toPx().coerceAtMost(size.minDimension * 0.5f)
    val dark = colors.shadowDark.copy(alpha = 0.85f)
    val light = colors.shadowLight.copy(alpha = if (colors.isLight) 0.9f else 0.4f)

    // 顶部内阴影
    drawRect(
        brush = Brush.verticalGradient(0f to dark, 1f to Color.Transparent, endY = edge),
        size = Size(size.width, edge),
    )
    // 左侧内阴影
    drawRect(
        brush = Brush.horizontalGradient(0f to dark, 1f to Color.Transparent, endX = edge),
        size = Size(edge, size.height),
    )
    // 底部高光
    drawRect(
        brush = Brush.verticalGradient(0f to Color.Transparent, 1f to light, startY = size.height - edge, endY = size.height),
        topLeft = Offset(0f, size.height - edge),
        size = Size(size.width, edge),
    )
    // 右侧高光
    drawRect(
        brush = Brush.horizontalGradient(0f to Color.Transparent, 1f to light, startX = size.width - edge, endX = size.width),
        topLeft = Offset(size.width - edge, 0f),
        size = Size(edge, size.height),
    )
}

/** 一块外凸的拟物面板 */
@Composable
fun NeuSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    color: Color = QiTheme.colors.surface,
    elevation: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = QiTheme.colors
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = colors.shadowDark,
                spotColor = colors.shadowDark,
            )
            .background(color, shape)
            .border(1.dp, raisedBorderBrush(colors), shape),
        content = content,
    )
}

/** 一块内凹的槽（进度条轨道、搜索框、推子轨道、设置行的底） */
@Composable
fun NeuInsetBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    color: Color = QiTheme.colors.inset,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = QiTheme.colors
    Box(
        modifier = modifier
            .clip(shape)
            .background(color, shape)
            .neuInset(colors),
        content = content,
    )
}

/**
 * 圆形拟物按钮。
 * 按下时不再缩外阴影，而是翻成内凹——这是拟物拟真的核心反馈：
 * 光从左上打来，按下去的东西应该是「陷进去」而不是「变小」。
 */
@Composable
fun NeuCircleButton(
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = QiTheme.colors.textMain,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = QiTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val elevation by animateDpAsState(
        targetValue = if (pressed) 0.dp else 7.dp,
        animationSpec = tween(90),
        label = "neuButtonElevation",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = tween(90),
        label = "neuButtonScale",
    )

    val base = Modifier
        .size(size)
        .scale(scale)
        .shadow(
            elevation = elevation,
            shape = CircleShape,
            clip = false,
            ambientColor = colors.shadowDark,
            spotColor = colors.shadowDark,
        )
        .clip(CircleShape)
        .background(colors.surface)

    Box(
        modifier = modifier
            .then(base)
            .then(if (pressed) Modifier.neuInset(colors) else Modifier.border(1.dp, raisedBorderBrush(colors), CircleShape))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * 拟物开关。
 * 轨道内凹、滑块外凸，滑动用 300ms 的弹性曲线——和草图里的手感一致。
 */
@Composable
fun NeuToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 46.dp,
    height: Dp = 24.dp,
) {
    val colors = QiTheme.colors
    val knobSize = height - 6.dp
    val travel = width - knobSize - 6.dp
    val offsetX by animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        animationSpec = tween(280),
        label = "neuToggle",
    )

    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(height / 2))
            .background(colors.inset)
            .neuInset(colors)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .offset(x = 3.dp + offsetX, y = 3.dp)
                .size(knobSize)
                .shadow(4.dp, CircleShape, clip = false, ambientColor = colors.shadowDark, spotColor = colors.shadowDark)
                .clip(CircleShape)
                .background(if (checked) colors.accent.copy(alpha = 0.85f) else colors.surface)
                .border(1.dp, raisedBorderBrush(colors), CircleShape),
        )
    }
}
