package com.qi.player.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** 歌词查询结果 */
sealed interface LyricsResult {
    data class Synced(val lines: List<com.qi.player.util.LyricLine>, val source: String) : LyricsResult
    data class Plain(val text: String, val source: String) : LyricsResult
    data object Empty : LyricsResult
}

/**
 * 歌词来源，按优先级：
 *  1. 导入文件夹内的同名 .lrc（离线、最准）
 *  2. 联网 LRCLIB（用户显式开启才请求）
 *
 * LRCLIB 是开放歌词库，无需密钥；请求失败一律静默降级为「暂无歌词」，
 * 不允许因为网络问题打断播放。
 */
class LyricsRepository(
    private val context: Context,
    private val library: LibraryRepository,
) {

    private val cache = HashMap<String, LyricsResult>()

    suspend fun lyricsFor(song: Song, onlineEnabled: Boolean): LyricsResult =
        withContext(Dispatchers.IO) {
            cache[song.uri]?.let { return@withContext it }

            library.localLyricsFor(song)?.let { raw ->
                val lines = com.qi.player.util.LrcParser.parse(raw)
                val result = if (lines.isNotEmpty()) {
                    LyricsResult.Synced(lines, "本地歌词")
                } else {
                    LyricsResult.Plain(raw.trim(), "本地歌词")
                }
                cache[song.uri] = result
                return@withContext result
            }

            if (onlineEnabled) {
                fetchOnline(song)?.let {
                    cache[song.uri] = it
                    return@withContext it
                }
            }

            LyricsResult.Empty.also { cache[song.uri] = it }
        }

    private fun fetchOnline(song: Song): LyricsResult? {
        val connection = runCatching {
            val params = buildString {
                append("track_name=").append(URLEncoder.encode(song.displayTitle, "UTF-8"))
                if (song.artist.isNotBlank()) {
                    append("&artist_name=").append(URLEncoder.encode(song.artist, "UTF-8"))
                }
                if (song.durationMs > 0) {
                    append("&duration=").append(String.format(Locale.US, "%.0f", song.durationMs / 1000f))
                }
            }
            val url = URL("https://lrclib.net/api/get?$params")
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "QiPlayer/1.0 (Android)")
            }
        }.getOrNull() ?: return null

        return try {
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val synced = json.optString("syncedLyrics")
            if (synced.isNotBlank()) {
                val lines = com.qi.player.util.LrcParser.parse(synced)
                if (lines.isNotEmpty()) return LyricsResult.Synced(lines, "LRCLIB")
            }
            val plain = json.optString("plainLyrics")
            if (plain.isNotBlank()) LyricsResult.Plain(plain.trim(), "LRCLIB") else null
        } catch (e: Exception) {
            Log.w(TAG, "在线歌词获取失败: ${e.message}")
            null
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private companion object {
        const val TAG = "LyricsRepository"
    }
}

/**
 * 歌单与收藏。
 *
 * 数据量小（最多几百条 URI），直接以 JSON 存 SharedPreferences；
 * 为此引入 Room 需要额外的 KSP 与迁移成本，不划算。
 */
class LibraryStore(context: Context) {

    data class Data(
        val favorites: Set<String> = emptySet(),
        val recents: List<String> = emptyList(),
        val playlists: Map<String, List<String>> = emptyMap(),
    )

    private val prefs = context.getSharedPreferences("qi_library", Context.MODE_PRIVATE)
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(load())
    val state: kotlinx.coroutines.flow.StateFlow<Data> = _state

    private fun load(): Data {
        val favorites = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()
        val recents = readArray(KEY_RECENTS)
        val playlists = LinkedHashMap<String, List<String>>()
        runCatching {
            val obj = JSONObject(prefs.getString(KEY_PLAYLISTS, "{}") ?: "{}")
            obj.keys().forEach { name ->
                val arr = obj.optJSONArray(name) ?: JSONArray()
                playlists[name] = (0 until arr.length()).map { arr.optString(it) }
            }
        }
        return Data(favorites, recents, playlists)
    }

    private fun readArray(key: String): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        (0 until arr.length()).map { arr.optString(it) }
    }.getOrDefault(emptyList())

    fun isFavorite(uri: String): Boolean = _state.value.favorites.contains(uri)

    fun toggleFavorite(uri: String) {
        val next = _state.value.favorites.toMutableSet()
        if (!next.add(uri)) next.remove(uri)
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
        _state.value = _state.value.copy(favorites = next)
    }

    fun markPlayed(uri: String) {
        val next = (listOf(uri) + _state.value.recents.filter { it != uri }).take(MAX_RECENTS)
        prefs.edit().putString(KEY_RECENTS, JSONArray(next).toString()).apply()
        _state.value = _state.value.copy(recents = next)
    }

    fun createPlaylist(name: String) {
        if (name.isBlank() || _state.value.playlists.containsKey(name)) return
        persistPlaylists(_state.value.playlists + (name to emptyList()))
    }

    fun addToPlaylist(name: String, uri: String) {
        val current = _state.value.playlists[name] ?: return
        if (current.contains(uri)) return
        persistPlaylists(_state.value.playlists + (name to (current + uri)))
    }

    private fun persistPlaylists(map: Map<String, List<String>>) {
        val obj = JSONObject()
        map.forEach { (name, uris) -> obj.put(name, JSONArray(uris)) }
        prefs.edit().putString(KEY_PLAYLISTS, obj.toString()).apply()
        _state.value = _state.value.copy(playlists = map)
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENTS = "recents"
        const val KEY_PLAYLISTS = "playlists"
        const val MAX_RECENTS = 100
    }
}
