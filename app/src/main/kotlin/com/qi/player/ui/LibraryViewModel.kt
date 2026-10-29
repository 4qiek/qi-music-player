package com.qi.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qi.player.QiApplication
import com.qi.player.data.AudioProbe
import com.qi.player.data.LibraryStore
import com.qi.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class LibraryTab(val label: String) {
    Songs("歌曲"), Artists("歌手"), Albums("专辑"), Playlists("歌单")
}

/** 专辑 / 歌手 / 文件夹的聚合单元 */
data class Group(
    val key: String,
    val title: String,
    val subtitle: String,
    val artUri: String?,
    val songs: List<Song>,
)

/**
 * 曲库视图模型。
 *
 * 关键设计：扫描（快）与参数探测（慢）分离。
 * 扫描先出结果让列表立刻可交互，随后逐首探测采样率/位深并**分批**回写，
 * 用户会看到 Hi-Res 金标一个个亮起来，而不是对着转圈等十秒。
 */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val qi = app as QiApplication

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _scanning = MutableStateFlow(true)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _probing = MutableStateFlow(false)
    val probing: StateFlow<Boolean> = _probing.asStateFlow()

    val query = MutableStateFlow("")
    val tab = MutableStateFlow(LibraryTab.Songs)

    private var scanJob: Job? = null
    private var probeJob: Job? = null

    /** 当前列表（受搜索词过滤后） */
    val visibleSongs: StateFlow<List<Song>> = combine(_songs, query) { list, q ->
        if (q.isBlank()) list
        else {
            val needle = q.trim().lowercase(Locale.ROOT)
            list.filter {
                it.title.lowercase(Locale.ROOT).contains(needle) ||
                    it.artist.lowercase(Locale.ROOT).contains(needle) ||
                    it.album.lowercase(Locale.ROOT).contains(needle)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        scanJob?.cancel()
        probeJob?.cancel()
        scanJob = viewModelScope.launch {
            _scanning.value = true
            val result = qi.library.scan(qi.settings.state.value.treeUri)
            _songs.value = result
            _scanning.value = false
            startProbing(result)
        }
    }

    private fun startProbing(songs: List<Song>) {
        if (songs.isEmpty()) return
        probeJob = viewModelScope.launch {
            _probing.value = true
            val working = songs.toMutableList()
            var sincePublish = 0
            songs.forEachIndexed { index, song ->
                val facts = withContext(Dispatchers.IO) { AudioProbe.probe(getApplication(), song) }
                working[index] = song.copy(
                    sampleRateHz = facts.sampleRateHz,
                    bitsPerSample = facts.bitsPerSample,
                    bitrate = if (facts.bitrate > 0) facts.bitrate else song.bitrate,
                    channels = facts.channels,
                    durationMs = if (song.durationMs > 0) song.durationMs else facts.durationMs,
                    mimeType = song.mimeType.ifBlank { facts.mimeType },
                )
                sincePublish++
                // 每 8 首回写一次：太频繁会引发列表整段重组，太稀疏则金标亮得太慢
                if (sincePublish >= 8) {
                    _songs.value = working.toList()
                    sincePublish = 0
                }
            }
            _songs.value = working.toList()
            _probing.value = false
        }
    }

    // ---------------------------------------------------------------- 聚合

    fun albums(): List<Group> = _songs.value
        .groupBy { "${it.album}\u0000${it.artist}" }
        .map { (key, list) ->
            val first = list.first()
            Group(
                key = key,
                title = first.album.ifBlank { "未知专辑" },
                subtitle = "${first.displayArtist} · ${list.size} 首",
                artUri = null,
                songs = list,
            )
        }
        .sortedBy { it.title.lowercase(Locale.ROOT) }

    fun artists(): List<Group> = _songs.value
        .groupBy { it.displayArtist }
        .map { (artist, list) ->
            Group(
                key = artist,
                title = artist,
                subtitle = "${list.size} 首",
                artUri = null,
                songs = list,
            )
        }
        .sortedBy { it.title.lowercase(Locale.ROOT) }

    fun playlists(store: LibraryStore.Data): List<Group> {
        val byUri = _songs.value.associateBy { it.uri }
        val out = ArrayList<Group>()
        val favorites = store.favorites.mapNotNull { byUri[it] }
        if (favorites.isNotEmpty()) {
            out += Group("fav", "我喜欢的音乐", "${favorites.size} 首", null, favorites)
        }
        val recents = store.recents.mapNotNull { byUri[it] }
        if (recents.isNotEmpty()) {
            out += Group("recent", "最近播放", "${recents.size} 首", null, recents)
        }
        store.playlists.forEach { (name, uris) ->
            val list = uris.mapNotNull { byUri[it] }
            out += Group("pl:$name", name, "${list.size} 首", null, list)
        }
        return out
    }
}
