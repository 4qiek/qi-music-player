package com.qi.player.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qi.player.data.LyricsResult
import com.qi.player.data.Song
import com.qi.player.playback.PlayerUiState
import com.qi.player.ui.component.AlbumArt
import com.qi.player.ui.component.HrBadge
import com.qi.player.ui.component.LyricView
import com.qi.player.ui.component.NeuCircleButton
import com.qi.player.ui.component.NeuToggle
import com.qi.player.ui.component.QiProgressBar
import com.qi.player.ui.theme.QiTheme
import com.qi.player.ui.theme.QiType

/**
 * 主播放界面。
 *
 * 版式完全对齐草图：顶部两枚控件、中部方形封面（可翻转到歌词）、
 * 底部歌名 / 进度 / 三键。所有尺寸都按屏幕宽度做比例换算，
 * 草图是 375×812 的定宽画板，直接照抄 dp 会在小屏溢出、大屏过空。
 */
@Composable
fun PlayerScreen(
    ui: PlayerUiState,
    lyrics: LyricsResult,
    lightTheme: Boolean,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekToMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QiTheme.colors
    var showLyrics by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {

        // ---------------- 顶部：设置 + 明暗开关 ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeuCircleButton(size = 32.dp, onClick = onOpenSettings) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "设置",
                    tint = colors.textSub,
                    modifier = Modifier.size(15.dp),
                )
            }
            NeuToggle(checked = lightTheme, onCheckedChange = { onToggleTheme() })
        }

        // ---------------- 中部：封面 / 歌词 ----------------
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            // 封面取「宽度 74%」与「可用高度 92%」的较小值，保证任何比例都不裁切
            val stageSize = minOf(maxWidth * 0.74f, maxHeight * 0.94f)

            FlipStage(
                size = stageSize,
                showLyrics = showLyrics,
                onFlipToLyrics = { showLyrics = true },
                onFlipBack = { showLyrics = false },
                song = ui.current,
                lyrics = lyrics,
                positionMs = ui.positionMs,
                onSeekToMs = onSeekToMs,
            )
        }

        // ---------------- 底部：信息 + 进度 + 控制 ----------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 22.dp),
        ) {
            SongInfo(ui.current)

            Spacer(Modifier.height(22.dp))

            val duration = ui.durationMs
            val progress = if (duration > 0) ui.positionMs.toFloat() / duration else 0f
            QiProgressBar(progress = progress, onSeek = onSeek)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(Song.formatDuration(ui.positionMs), style = QiType.monoSmall, color = colors.textSub)
                Text(Song.formatDuration(duration), style = QiType.monoSmall, color = colors.textSub)
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NeuCircleButton(size = 54.dp, onClick = onPrevious) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "上一首",
                        tint = colors.textMain,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(28.dp))
                NeuCircleButton(size = 74.dp, onClick = onPlayPause) {
                    Icon(
                        imageVector = if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (ui.isPlaying) "暂停" else "播放",
                        tint = colors.accent,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.width(28.dp))
                NeuCircleButton(size = 54.dp, onClick = onNext) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        tint = colors.textMain,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SongInfo(song: Song?) {
    val colors = QiTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = song?.displayTitle ?: "未在播放",
                style = QiType.songTitle,
                color = when {
                    song == null -> colors.textSub
                    song.isHiRes -> colors.gold
                    else -> colors.textMain
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (song != null && song.isHiRes) {
                Spacer(Modifier.width(8.dp))
                HrBadge()
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = song?.displayArtist ?: "从下方曲库选一首",
            style = QiType.artist,
            color = colors.textSub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 封面与歌词的翻转舞台。
 *
 * 用 rotationY 做真正的 3D 翻转（cameraDistance 模拟透视），
 * 而不是简单淡入淡出——草图里那一圈转轴是这套界面的记忆点。
 * 转到 90° 之后把正面 alpha 归零，否则会看到镜像的背面内容。
 */
@Composable
private fun FlipStage(
    size: androidx.compose.ui.unit.Dp,
    showLyrics: Boolean,
    onFlipToLyrics: () -> Unit,
    onFlipBack: () -> Unit,
    song: Song?,
    lyrics: LyricsResult,
    positionMs: Long,
    onSeekToMs: (Long) -> Unit,
) {
    val colors = QiTheme.colors
    val rotation by animateFloatAsState(
        targetValue = if (showLyrics) 180f else 0f,
        animationSpec = tween(durationMillis = 620),
        label = "flip",
    )

    Box(contentAlignment = Alignment.Center) {
        // 正面：封面
        AlbumArt(
            song = song,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .alpha(if (rotation <= 90f) 1f - (rotation / 90f) else 0f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onFlipToLyrics() },
            shape = RoundedCornerShape(16.dp),
            elevation = 14.dp,
        )

        // 背面：歌词
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    rotationY = rotation - 180f
                    cameraDistance = 12f * density
                }
                .alpha(if (rotation > 90f) ((rotation - 90f) / 90f) else 0f),
            contentAlignment = Alignment.Center,
        ) {
            LyricView(
                result = lyrics,
                positionMs = positionMs,
                onSeek = onSeekToMs,
                modifier = Modifier.fillMaxSize(),
            )

            // 歌词模式下给一个明确的返回口，避免用户困在这一页
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onFlipBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Subject,
                    contentDescription = "返回封面",
                    tint = colors.textSub.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
