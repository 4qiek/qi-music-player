package com.qi.player.playback

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.qi.player.QiApplication
import com.qi.player.data.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 播放界面需要的最小状态集合 */
data class PlayerUiState(
    val ready: Boolean = false,
    val current: Song? = null,
    val queue: List<Song> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * 播放控制器（UI 侧）。
 *
 * 真正的 ExoPlayer 在 [PlaybackService] 里，这里通过 MediaController 远程控制。
 * 这样做的好处是 UI 被销毁时播放不受影响；代价是所有操作都变成异步，
 * 因此需要一个 200ms 的轮询来同步进度——Media3 不提供位置回调流。
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val qi = app as QiApplication

    val settings = qi.settings
    val libraryStore = qi.libraryStore

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var lastVolume = 1f

    /** VM 自己持有队列实体：MediaController 只认识 MediaItem，还原成 Song 需要这份映射 */
    private var queue: List<Song> = emptyList()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _ui.value = _ui.value.copy(isPlaying = isPlaying)
            if (isPlaying) startTicker() else stopTicker()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val c = controller ?: return
            val idx = c.currentMediaItemIndex
            val song = queue.getOrNull(idx)
            _ui.value = _ui.value.copy(
                index = idx,
                current = song,
                positionMs = 0L,
                durationMs = c.duration.coerceAtLeast(0L),
            )
            song?.let { qi.libraryStore.markPlayed(it.uri) }
            if (settings.state.value.crossfade) fadeIn(c)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val c = controller ?: return
            _ui.value = _ui.value.copy(
                durationMs = c.duration.coerceAtLeast(0L),
                current = _ui.value.current ?: queue.getOrNull(c.currentMediaItemIndex),
            )
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            // 单曲损坏不应该让整个队列卡死：直接跳下一首
            controller?.seekToNextMediaItem()
        }
    }

    init {
        connect()
    }

    private fun connect() {
        val token = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java),
        )
        val future = MediaController.Builder(getApplication(), token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { c ->
                controller = c
                c.addListener(listener)
                _ui.value = _ui.value.copy(
                    ready = true,
                    isPlaying = c.isPlaying,
                    durationMs = c.duration.coerceAtLeast(0L),
                )
                if (c.isPlaying) startTicker()
            }
        }, ContextCompat.getMainExecutor(getApplication()))
    }

    // ---------------------------------------------------------------- 队列控制

    fun playSongs(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        val index = startIndex.coerceIn(0, songs.lastIndex)
        queue = songs
        _ui.value = _ui.value.copy(
            queue = songs,
            index = index,
            current = songs[index],
            positionMs = 0L,
            durationMs = songs[index].durationMs,
        )
        val c = controller ?: return
        c.setMediaItems(songs.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
        qi.libraryStore.markPlayed(songs[index].uri)
    }

    /** 把一首歌插到当前曲目之后（列表长按菜单用） */
    fun playNext(song: Song) {
        val c = controller ?: return
        c.addMediaItem(song.toMediaItem())
        val newIndex = c.currentMediaItemIndex + 1
        if (newIndex < c.mediaItemCount) c.moveMediaItem(c.mediaItemCount - 1, newIndex)
        queue = queue.toMutableList().apply { add(newIndex.coerceAtMost(size), song) }
        _ui.value = _ui.value.copy(queue = queue)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem()

    fun previous() {
        val c = controller ?: return
        // 播放超过 3 秒时，「上一首」先回到本曲开头——这是播放器的通行手感
        if (c.currentPosition > 3_000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekToFraction(fraction: Float) {
        val c = controller ?: return
        val duration = c.duration
        if (duration <= 0) return
        c.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        _ui.value = _ui.value.copy(positionMs = (duration * fraction).toLong())
    }

    /** 歌词点击跳转 */
    fun seekToMs(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0L))
    }

    // ---------------------------------------------------------------- 进度轮询

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                val c = controller ?: break
                if (c.isPlaying) {
                    _ui.value = _ui.value.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0L),
                        durationMs = c.duration.coerceAtLeast(0L),
                    )
                    applyCrossfade(c)
                }
                // 交叉渐入被关掉时，必须把可能停在淡出中途的音量拉回满值，
                // 否则用户会莫名其妙觉得「声音变小了」且找不到原因
                if (!settings.state.value.crossfade) setVolume(c, 1f)
                // 交叉淡入淡出期间需要更细的采样，平时 250ms 足够，省电
                val remaining = (c.duration - c.currentPosition)
                val fine = settings.state.value.crossfade && remaining in 0..FADE_OUT_MS
                delay(if (fine) 60L else 250L)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
        saveSession()
    }

    // ---------------------------------------------------------------- 交叉淡入淡出

    /**
     * 单播放器实现的是「淡出 + 淡入」，不是真正的双流交叉叠加。
     * 真正的交叉渐入需要两个播放器实例同时出声，代价是资源翻倍，
     * 而在手机上听感差异极小——这里选择轻量方案，并在设置项文案里如实标注。
     */
    private fun applyCrossfade(c: MediaController) {
        if (!settings.state.value.crossfade) return
        val duration = c.duration
        if (duration <= 0) return
        val remaining = duration - c.currentPosition
        if (remaining in 1..FADE_OUT_MS) {
            setVolume(c, (remaining.toFloat() / FADE_OUT_MS).coerceIn(0f, 1f))
        }
    }

    private fun fadeIn(c: MediaController) {
        viewModelScope.launch {
            val steps = 12
            for (i in 1..steps) {
                setVolume(c, i.toFloat() / steps)
                delay(FADE_IN_MS / steps)
            }
            setVolume(c, 1f)
        }
    }

    /**
     * 只在音量真的变化时才写回 Player。
     * MediaController.setVolume 每次都要跨进程同步到 Service，
     * 250ms 一轮的无脑写入会造成不必要的主线程阻塞。
     */
    private fun setVolume(c: MediaController, volume: Float) {
        if (kotlin.math.abs(volume - lastVolume) < 0.01f) return
        lastVolume = volume
        c.volume = volume
    }

    // ---------------------------------------------------------------- 会话恢复

    fun saveSession() {
        val song = _ui.value.current
        qi.settings.saveQueue(
            uris = queue.map { it.uri },
            index = _ui.value.index,
            positionMs = _ui.value.positionMs,
        )
        qi.settings.saveLastSession(song?.uri, _ui.value.positionMs)
    }

    /**
     * 冷启动恢复：只把队列和播放位置摆回去，**不自动播放**。
     * 手机一打开就外放是很讨嫌的行为。
     */
    fun restoreSession(allSongs: List<Song>) {
        if (_ui.value.current != null) return
        val byUri = allSongs.associateBy { it.uri }
        val (uris, index, position) = qi.settings.savedQueue()
        if (uris.isEmpty()) return
        val restored = uris.mapNotNull { byUri[it] }
        if (restored.isEmpty()) return

        queue = restored
        val idx = index.coerceIn(0, restored.lastIndex)
        _ui.value = _ui.value.copy(
            queue = restored,
            index = idx,
            current = restored[idx],
            durationMs = restored[idx].durationMs,
            positionMs = position,
        )
        controller?.let { c ->
            c.setMediaItems(restored.map { it.toMediaItem() }, idx, position)
            c.prepare()
            // 不调用 play()，只把进度摆好
        }
    }

    // ---------------------------------------------------------------- 均衡器

    fun equalizer(): TenBandEqualizer? = EqualizerBridge.processor

    fun saveEqualizer() {
        EqualizerBridge.processor?.let { EqualizerPrefs.save(getApplication(), it.gainsSnapshot()) }
    }

    /** 歌词查询：本地外挂优先，联网仅在用户开启时进行 */
    suspend fun lyricsFor(song: Song): com.qi.player.data.LyricsResult =
        qi.lyrics.lyricsFor(song, settings.state.value.onlineLyrics)

    override fun onCleared() {
        saveSession()
        controller?.let {
            it.removeListener(listener)
            it.release()
        }
        controller = null
        super.onCleared()
    }

    private companion object {
        const val FADE_OUT_MS = 1_500L
        const val FADE_IN_MS = 700L
    }
}
