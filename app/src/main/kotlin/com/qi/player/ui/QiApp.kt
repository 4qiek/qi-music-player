package com.qi.player.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.player.data.LyricsResult
import com.qi.player.playback.EqualizerPrefs
import com.qi.player.playback.PlayerViewModel
import com.qi.player.ui.screen.EqualizerPanel
import com.qi.player.ui.screen.LibraryPanel
import com.qi.player.ui.screen.PlayerScreen
import com.qi.player.ui.screen.SettingsDrawer
import com.qi.player.ui.screen.WheelPanel
import com.qi.player.ui.theme.QiTheme
import kotlinx.coroutines.delay

private enum class Panel { None, Library, Equalizer, Settings }

/**
 * 应用外壳。
 *
 * 所有面板都叠在同一个 Box 里靠 translation 做进出，而不是用 Navigation 或
 * 多个 Activity —— 草图的交互本质是「同一张唱片的四个抽屉」，
 * 用导航栈会把状态与转场拆碎，反而更难还原。
 */
@Composable
fun QiApp(
    player: PlayerViewModel,
    library: LibraryViewModel,
) {
    val context = LocalContext.current
    val settings by player.settings.state.collectAsStateWithLifecycle()
    val ui by player.ui.collectAsStateWithLifecycle()
    val songs by library.songs.collectAsStateWithLifecycle()
    val visibleSongs by library.visibleSongs.collectAsStateWithLifecycle()
    val scanning by library.scanning.collectAsStateWithLifecycle()
    val probing by library.probing.collectAsStateWithLifecycle()
    val query by library.query.collectAsStateWithLifecycle()
    val tab by library.tab.collectAsStateWithLifecycle()
    val libraryData by player.libraryStore.state.collectAsStateWithLifecycle()

    var panel by remember { mutableStateOf(Panel.None) }
    var wheelVisible by remember { mutableStateOf(false) }
    var wheelTick by remember { mutableIntStateOf(0) }

    var gains by remember { mutableStateOf(EqualizerPrefs.load(context)) }
    var lyrics by remember { mutableStateOf<LyricsResult>(LyricsResult.Empty) }

    // 换歌时重新取歌词；联网开关变化也应重新取
    LaunchedEffect(ui.current?.uri, settings.onlineLyrics) {
        val song = ui.current
        lyrics = if (song == null) LyricsResult.Empty else player.lyricsFor(song)
    }

    // 滚轮 2.5 秒无操作自动收起，避免长期霸占右侧手势区
    LaunchedEffect(wheelVisible, wheelTick) {
        if (wheelVisible) {
            delay(2500)
            wheelVisible = false
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            // 必须持久化授权，否则下次启动就失去该目录的读取权限
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            player.settings.setTreeUri(uri.toString())
            library.refresh()
        }
    }

    val libraryProgress by animateFloatAsState(
        targetValue = if (panel == Panel.Library) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "libraryPanel",
    )
    val eqProgress by animateFloatAsState(
        targetValue = if (panel == Panel.Equalizer) 1f else 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "eqPanel",
    )
    val settingsProgress by animateFloatAsState(
        targetValue = if (panel == Panel.Settings) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "settingsPanel",
    )
    val wheelProgress by animateFloatAsState(
        targetValue = if (wheelVisible) 1f else 0f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "wheelPanel",
    )

    val colors = QiTheme.colors

    // 分组只在曲库或收藏变化时重算，避免每次重组都重新分组全量歌曲
    val albums = remember(songs) { library.albums() }
    val artists = remember(songs) { library.artists() }
    val playlists = remember(songs, libraryData) { library.playlists(libraryData) }

    Box(Modifier.fillMaxSize().background(colors.bg)) {

        // ---------------- 主界面 ----------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = libraryProgress
                    scaleX = 1f - 0.08f * p
                    scaleY = 1f - 0.08f * p
                    translationY = 48.dp.toPx() * p
                    alpha = 1f - 0.9f * p
                }
                .systemBarsPadding(),
        ) {
            PlayerScreen(
                ui = ui,
                lyrics = lyrics,
                lightTheme = settings.lightTheme,
                onOpenSettings = { panel = Panel.Settings },
                onToggleTheme = { player.settings.setLightTheme(!settings.lightTheme) },
                onPlayPause = {
                    // 冷启动时队列可能还没建立（用户没从曲库点过歌），
                    // 直接按播放键就顺手从当前列表第一首开始，不要让它没反应
                    if (ui.current == null && visibleSongs.isNotEmpty()) {
                        player.playSongs(visibleSongs, 0)
                    } else {
                        player.togglePlay()
                    }
                },
                onNext = player::next,
                onPrevious = player::previous,
                onSeek = player::seekToFraction,
                onSeekToMs = player::seekToMs,
            )
        }

        // ---------------- 手势热区 ----------------
        // 顶部下拉：拉手本身很细，热区做到 26dp 高，否则几乎点不到
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .width(140.dp)
                .height(26.dp)
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > 24f) panel = Panel.Library },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            total += amount
                        },
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textSub.copy(alpha = 0.28f)),
            )
        }

        // 底部上滑：呼出调音台
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .width(140.dp)
                .height(24.dp)
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -24f) panel = Panel.Equalizer },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            total += amount
                        },
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textSub.copy(alpha = 0.28f)),
            )
        }

        // 右侧：点一下呼出滚轮选曲
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.5f)
                .width(30.dp)
                .pointerInput(Unit) {
                    detectTapGestures {
                        wheelTick++
                        wheelVisible = true
                    }
                },
        )

        // ---------------- 遮罩 ----------------
        val scrim = maxOf(libraryProgress, eqProgress, settingsProgress) * 0.62f
        if (scrim > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrim))
                    .pointerInput(Unit) {
                        detectTapGestures { panel = Panel.None }
                    },
            )
        }

        // ---------------- 曲库（顶部下拉） ----------------
        if (libraryProgress > 0.002f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = -size.height * (1f - libraryProgress) },
            ) {
                LibraryPanel(
                    songs = visibleSongs,
                    totalCount = songs.size,
                    scanning = scanning,
                    probing = probing,
                    query = query,
                    onQueryChange = { library.query.value = it },
                    tab = tab,
                    onTabChange = { library.tab.value = it },
                    albums = albums,
                    artists = artists,
                    playlists = playlists,
                    favorites = libraryData.favorites,
                    currentUri = ui.current?.uri,
                    onPlay = { list, index ->
                        player.playSongs(list, index)
                        panel = Panel.None
                    },
                    onToggleFavorite = { player.libraryStore.toggleFavorite(it.uri) },
                    onImportFolder = { folderLauncher.launch(null) },
                    onClose = { panel = Panel.None },
                    modifier = Modifier,
                )
            }
        }

        // ---------------- 调音台（底部上滑） ----------------
        if (eqProgress > 0.002f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .graphicsLayer { translationY = size.height * (1f - eqProgress) }
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(colors.surface)
                    .navigationBarsPadding(),
            ) {
                EqualizerPanel(
                    gains = gains,
                    onGainChange = { band, value ->
                        gains = gains.copyOf().also { it[band] = value }
                        player.equalizer()?.setGain(band, value)
                    },
                    onReset = {
                        gains = FloatArray(10)
                        player.equalizer()?.resetGains()
                    },
                    onClose = {
                        // 均衡器活在 Service 里，UI 关闭时把当前曲线落盘，
                        // 下次服务重建（冷启动播放）能原样恢复
                        EqualizerPrefs.save(context, gains)
                        player.saveEqualizer()
                        panel = Panel.None
                    },
                )
            }
        }

        // ---------------- 滚轮选曲（右侧） ----------------
        if (wheelProgress > 0.002f && ui.queue.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .graphicsLayer { translationX = size.width * (1f - wheelProgress) },
            ) {
                WheelPanel(
                    queue = ui.queue,
                    currentIndex = ui.index,
                    onSelect = { index ->
                        wheelTick++
                        player.playSongs(ui.queue, index)
                    },
                    onInteract = { wheelTick++ },
                )
            }
        }

        // ---------------- 设置抽屉（左侧） ----------------
        if (settingsProgress > 0.002f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.86f)
                    .graphicsLayer { translationX = -size.width * (1f - settingsProgress) },
            ) {
                SettingsDrawer(
                    lightTheme = settings.lightTheme,
                    accentArgb = settings.accentArgb,
                    crossfade = settings.crossfade,
                    gapless = settings.gapless,
                    onlineLyrics = settings.onlineLyrics,
                    treeUri = settings.treeUri,
                    onToggleTheme = { player.settings.setLightTheme(it) },
                    onAccentChange = { player.settings.setAccent(it) },
                    onCrossfadeChange = { player.settings.setCrossfade(it) },
                    onGaplessChange = { player.settings.setGapless(it) },
                    onOnlineLyricsChange = { player.settings.setOnlineLyrics(it) },
                    onImportFolder = { folderLauncher.launch(null) },
                    onRescan = { library.refresh() },
                    onClose = { panel = Panel.None },
                    modifier = Modifier,
                )
            }
        }
    }
}
