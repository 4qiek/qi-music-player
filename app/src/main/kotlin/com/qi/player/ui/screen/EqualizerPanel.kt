package com.qi.player.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qi.player.playback.TenBandEqualizer
import com.qi.player.ui.component.neuInset
import com.qi.player.ui.theme.QiTheme
import com.qi.player.ui.theme.QiType
import kotlin.math.roundToInt

/**
 * 十段调音台。
 *
 * 推子按「绝对位置」而非「相对位移」映射：手指落在哪，增益就是哪。
 * 混音台的老手感都是这样——相对拖动会让用户不停来回找零点。
 * 每一段都真的作用在音频链上（见 TenBandEqualizer），不是装饰。
 */
@Composable
fun EqualizerPanel(
    gains: FloatArray,
    onGainChange: (band: Int, gainDb: Float) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QiTheme.colors
    val frequencies = TenBandEqualizer.CENTER_FREQUENCIES

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(18.dp))

        // ---------------- 头部 ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("EQUALIZER", style = QiType.sectionLabel, color = colors.textMain)
                Text("十段 · ±12dB", style = QiType.monoSmall, color = colors.textSub)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onReset() },
                ) {
                    Text("归零", style = QiType.mono, color = colors.accent, modifier = Modifier.padding(6.dp))
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "收起均衡器", tint = colors.textSub, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------------- 推子组 ----------------
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 10.dp),
        ) {
            val trackHeight = (maxHeight - 20.dp).coerceAtLeast(60.dp)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (band in gains.indices) {
                    BandFader(
                        gainDb = gains[band],
                        label = freqLabel(frequencies[band]),
                        trackHeight = trackHeight,
                        onChange = { onGainChange(band, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BandFader(
    gainDb: Float,
    label: String,
    trackHeight: Dp,
    onChange: (Float) -> Unit,
) {
    val colors = QiTheme.colors
    val normalized = ((gainDb - TenBandEqualizer.MIN_GAIN_DB) /
        (TenBandEqualizer.MAX_GAIN_DB - TenBandEqualizer.MIN_GAIN_DB)).coerceIn(0f, 1f)

    var heightPx by remember { mutableStateOf(0f) }
    val knobHeight = 20.dp

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(trackHeight)
                .onSizeChanged { heightPx = it.height.toFloat() }
                .pointerInput(heightPx) {
                    if (heightPx <= 0f) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onChange(gainFromY(down.position.y, heightPx))
                        down.consume()
                        drag(down.id) { change ->
                            onChange(gainFromY(change.position.y, heightPx))
                            change.consume()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // 轨道（内凹）
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.inset)
                    .neuInset(colors),
            )

            // 0dB 刻度线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.textSub.copy(alpha = 0.18f)),
            )

            // 推子帽
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (trackHeight - knobHeight) * (1f - normalized))
                    .width(24.dp)
                    .height(knobHeight)
                    .shadow(4.dp, RoundedCornerShape(4.dp), clip = false, ambientColor = colors.shadowDark, spotColor = colors.shadowDark)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surface),
            ) {
                // 帽身中缝，模拟实体推子的防滑纹
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.textSub.copy(alpha = 0.35f)),
                )
                // 被调整过的推子，帽顶亮起强调色，便于一眼看出哪些段非零
                if (gainDb != 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(colors.accent),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(text = label, style = QiType.monoSmall, fontSize = 8.sp, color = colors.textSub)
    }
}

private fun gainFromY(y: Float, heightPx: Float): Float {
    val normalized = (1f - (y / heightPx)).coerceIn(0f, 1f)
    val gain = TenBandEqualizer.MIN_GAIN_DB +
        normalized * (TenBandEqualizer.MAX_GAIN_DB - TenBandEqualizer.MIN_GAIN_DB)
    // 吸附到 0.5dB 台阶：连续浮点会让推子看起来「抖」，半格更稳
    return (gain * 2f).roundToInt() / 2f
}

private fun freqLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000}K" else "$hz"
