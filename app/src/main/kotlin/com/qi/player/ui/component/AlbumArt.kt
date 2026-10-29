package com.qi.player.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qi.player.data.LibraryRepository
import com.qi.player.data.Song
import com.qi.player.ui.theme.QiTheme

/**
 * 专辑封面。
 *
 * 取图顺序：MediaStore 的 albumart -> 音频条目本身 -> 占位符。
 * 占位符不是灰块，而是画一圈内凹的「唱片孔」加一个极淡的「沏」，
 * 既呼应命名，也避免列表里出现大片死白或死黑。
 */
@Composable
fun AlbumArt(
    song: Song?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    showPlaceholderGlyph: Boolean = true,
    elevation: Dp = 0.dp,
) {
    val colors = QiTheme.colors
    val artUri = song?.let { LibraryRepository.albumArtUri(it.albumId) ?: it.uriObj }

    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false, ambientColor = colors.shadowDark, spotColor = colors.shadowDark)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        if (colors.isLight) Color(0xFFD8DCD7) else Color(0xFF232323),
                        if (colors.isLight) Color(0xFFE9ECE8) else Color(0xFF181818),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (showPlaceholderGlyph) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.bg.copy(alpha = 0.5f))
                    .neuInset(colors),
            )
            Text(
                text = "沏",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = colors.textSub.copy(alpha = 0.3f),
            )
        }

        if (artUri != null) {
            // 加载失败时 AsyncImage 不绘制任何内容，底下的占位符自然露出
            AsyncImage(
                model = artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Hi-Res 金色徽章 */
@Composable
fun HrBadge(modifier: Modifier = Modifier) {
    val colors = QiTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(colors.gold)
            .padding(horizontal = 3.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "HR",
            color = if (colors.isLight) Color.White else Color.Black,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}
