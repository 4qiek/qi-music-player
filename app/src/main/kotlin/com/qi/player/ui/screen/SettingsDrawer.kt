package com.qi.player.ui.screen

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qi.player.ui.component.NeuActionButton
import com.qi.player.ui.component.NeuInsetBox
import com.qi.player.ui.component.NeuToggle
import com.qi.player.ui.component.SectionLabel
import com.qi.player.ui.component.neuInset
import com.qi.player.ui.theme.QiTheme
import com.qi.player.ui.theme.QiType

/**
 * 设置抽屉。
 *
 * 强调色没有做成自由取色轮，而是「色相条 + 低饱和锁定」：
 * 饱和度与明度固定，只让用户选色相。理由很实在——
 * 拟物界面靠同色系的亮暗关系撑起立体感，随手选一个高饱和的粉色
 * 会让整套阴影关系立刻崩掉。限定范围内给自由，是这类界面的必要约束。
 */
@Composable
fun SettingsDrawer(
    lightTheme: Boolean,
    accentArgb: Int,
    crossfade: Boolean,
    gapless: Boolean,
    onlineLyrics: Boolean,
    treeUri: String?,
    onToggleTheme: (Boolean) -> Unit,
    onAccentChange: (Int) -> Unit,
    onCrossfadeChange: (Boolean) -> Unit,
    onGaplessChange: (Boolean) -> Unit,
    onOnlineLyricsChange: (Boolean) -> Unit,
    onImportFolder: () -> Unit,
    onRescan: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QiTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(46.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("沏", style = QiType.brand, color = colors.textMain)
                Text("一壶一盏，慢慢听", style = QiType.mono, color = colors.textSub)
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭设置", tint = colors.textSub, modifier = Modifier.size(17.dp))
            }
        }

        Spacer(Modifier.height(26.dp))

        // ---------------- 曲库与网络 ----------------
        SectionLabel("LIBRARY & NETWORK")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NeuActionButton(
                text = if (treeUri == null) "导入本地歌曲文件夹" else "已指定文件夹 · 点击更换",
                leading = Icons.Rounded.FolderOpen,
                onClick = onImportFolder,
            )
            NeuActionButton(
                text = "重新扫描曲库",
                leading = Icons.Rounded.Refresh,
                onClick = onRescan,
            )
            SettingRow(
                title = "联网补全歌词",
                subtitle = "开启后按歌名向 LRCLIB 查询，默认关闭",
                checked = onlineLyrics,
                onCheckedChange = onOnlineLyricsChange,
            )
        }

        Spacer(Modifier.height(26.dp))

        // ---------------- 界面 ----------------
        SectionLabel("INTERFACE")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingRow(
                title = "浅色主题",
                subtitle = "宣纸灰绿 · 深色为茶室",
                checked = lightTheme,
                onCheckedChange = onToggleTheme,
            )
            AccentPicker(
                accentArgb = accentArgb,
                lightTheme = lightTheme,
                onChange = onAccentChange,
            )
        }

        Spacer(Modifier.height(26.dp))

        // ---------------- 音频引擎 ----------------
        SectionLabel("AUDIO ENGINE")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingRow(
                title = "交叉渐入",
                subtitle = "切歌时淡出淡入，非双流叠加",
                checked = crossfade,
                onCheckedChange = onCrossfadeChange,
            )
            SettingRow(
                title = "无缝播放",
                subtitle = "关闭后曲目之间留 0.4 秒空隙",
                checked = gapless,
                onCheckedChange = onGaplessChange,
            )
        }

        Spacer(Modifier.height(26.dp))

        // ---------------- 关于 ----------------
        SectionLabel("ABOUT")
        NeuInsetBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("沏 · 本地音乐播放器 1.0.0", style = QiType.mono, color = colors.textMain)
                Text(
                    "全本地曲库，不依赖任何账号与云服务；均衡器为自研十段 DSP，" +
                        "不经过系统 Equalizer，蓝牙与有线输出表现一致。",
                    fontSize = 11.sp,
                    color = colors.textSub,
                    lineHeight = 17.sp,
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = QiTheme.colors
    NeuInsetBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = QiType.body, color = colors.textMain)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 10.sp, color = colors.textSub.copy(alpha = 0.8f))
            }
            Spacer(Modifier.width(12.dp))
            NeuToggle(checked = checked, onCheckedChange = onCheckedChange, width = 42.dp, height = 22.dp)
        }
    }
}

/**
 * 色相条取色器。
 *
 * 饱和度/明度由当前主题决定，用户只能横向选色相——
 * 见文件头注释：拟物界面撑不住高饱和强调色。
 */
@Composable
private fun AccentPicker(
    accentArgb: Int,
    lightTheme: Boolean,
    onChange: (Int) -> Unit,
) {
    val colors = QiTheme.colors
    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(0f) }

    val sat = 0.42f
    val value = if (lightTheme) 0.45f else 0.58f

    val hsv = remember(accentArgb) {
        FloatArray(3).also { AndroidColor.colorToHSV(accentArgb, it) }
    }
    val hue = hsv[0]

    val stops = remember(lightTheme) {
        (0..12).map { i ->
            val h = i * 30f
            Color(AndroidColor.HSVToColor(floatArrayOf(h.coerceAtMost(359.9f), sat, value)))
        }
    }

    Column(Modifier.padding(top = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("强调色", style = QiType.body, color = colors.textMain, modifier = Modifier.weight(1f))
            Text(
                text = "#%06X".format(0xFFFFFF and accentArgb),
                style = QiType.monoSmall,
                color = colors.textSub,
            )
        }
        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(widthPx, lightTheme) {
                    if (widthPx <= 0f) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pickHue(down.position.x, widthPx, sat, value, onChange)
                        down.consume()
                        drag(down.id) { change ->
                            pickHue(change.position.x, widthPx, sat, value, onChange)
                            change.consume()
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.horizontalGradient(stops)),
            )
            // 选中位置的手柄
            Box(
                modifier = Modifier
                    .offset(x = with(density) { ((widthPx * (hue / 360f)) - 9.dp.toPx()).toDp() })
                    .size(18.dp)
                    .shadow(4.dp, CircleShape, clip = false, ambientColor = colors.shadowDark, spotColor = colors.shadowDark)
                    .clip(CircleShape)
                    .background(Color(accentArgb)),
            )
        }
    }
}

private fun pickHue(x: Float, widthPx: Float, sat: Float, value: Float, onChange: (Int) -> Unit) {
    val h = (x / widthPx).coerceIn(0f, 1f) * 359.4f
    onChange(AndroidColor.HSVToColor(floatArrayOf(h, sat, value)))
}
