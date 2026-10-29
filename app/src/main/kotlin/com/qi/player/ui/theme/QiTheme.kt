package com.qi.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 「沏」的配色。
 *
 * 深色是主场景：茶室的暗，墨绿作强调，Hi-Res 用一点金。
 * 浅色不是简单反相，而是换了一套「宣纸灰绿」——
 * 阴影色变亮、文字变深，这样拟物的凸起感才成立。
 */
data class QiColors(
    val bg: Color,
    val surface: Color,
    val textMain: Color,
    val textSub: Color,
    val shadowDark: Color,
    val shadowLight: Color,
    val accent: Color,
    val gold: Color,
    val isLight: Boolean,
) {
    /** 内凹槽的底色：比背景再深一档，才压得住 */
    val inset: Color get() = if (isLight) bg else Color(0xFF101010)

    companion object {
        fun dark(accent: Color) = QiColors(
            bg = Color(0xFF141414),
            surface = Color(0xFF1C1C1C),
            textMain = Color(0xFFE8E8E8),
            textSub = Color(0xFF7A7A7A),
            shadowDark = Color(0xFF0C0C0C),
            shadowLight = Color(0xFF242424),
            accent = accent,
            gold = Color(0xFFD4AF37),
            isLight = false,
        )

        fun light(accent: Color) = QiColors(
            bg = Color(0xFFE2E5E0),
            surface = Color(0xFFE2E5E0),
            textMain = Color(0xFF2C3530),
            textSub = Color(0xFF6B7A71),
            shadowDark = Color(0xFFB8C0BA),
            shadowLight = Color(0xFFFFFFFF),
            accent = accent,
            gold = Color(0xFFB8860B),
            isLight = true,
        )
    }
}

/**
 * 字体策略：
 *  - 标题与歌词走 Serif（中文落到系统的衬线体，多数国产 ROM 具备 Noto Serif CJK）；
 *  - 数值、标签、时长走 Monospace，和草图里调音台的复古仪表感一致；
 *  - 正文用系统默认无衬线，保证长文本可读性。
 */
object QiType {
    val brand = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = 2.sp,
    )

    val songTitle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
    )

    val artist = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
    )

    val lyric = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 15.sp,
        lineHeight = 26.sp,
    )

    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp,
    )

    val monoSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        letterSpacing = 0.5.sp,
    )

    val sectionLabel = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
    )

    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
    )

    val listTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    )
}

private val LocalQiColors = staticCompositionLocalOf { QiColors.dark(Color(0xFF4A7C59)) }

object QiTheme {
    val colors: QiColors
        @Composable @ReadOnlyComposable get() = LocalQiColors.current
}

@Composable
fun QiTheme(
    light: Boolean = isSystemInDarkTheme().not(),
    accent: Color = Color(0xFF4A7C59),
    content: @Composable () -> Unit,
) {
    val colors = if (light) QiColors.light(accent) else QiColors.dark(accent)

    // 只把配色灌进 Material 的槽位，保证 Text/Icon 的默认前景色跟着走；
    // 具体界面刻意不用 Material 组件，全部走自绘的拟物件。
    val scheme = if (light) {
        lightColorScheme(
            primary = accent,
            background = colors.bg,
            surface = colors.surface,
            onBackground = colors.textMain,
            onSurface = colors.textMain,
        )
    } else {
        darkColorScheme(
            primary = accent,
            background = colors.bg,
            surface = colors.surface,
            onBackground = colors.textMain,
            onSurface = colors.textMain,
        )
    }

    CompositionLocalProvider(LocalQiColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = Typography()) {
            content()
        }
    }
}
