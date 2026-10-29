package com.qi.player.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 界面与播放引擎的全部可持久化设置 */
data class AppSettings(
    val lightTheme: Boolean = false,
    /** 强调色 ARGB，默认墨绿 #4A7C59 */
    val accentArgb: Int = 0xFF4A7C59.toInt(),
    /** 切歌时的淡出/淡入过渡 */
    val crossfade: Boolean = false,
    /** 曲目之间连续播放，不留空隙 */
    val gapless: Boolean = true,
    /** 联网补全歌词（默认关闭，完全离线可用） */
    val onlineLyrics: Boolean = false,
    /** 已导入的 SAF 目录 */
    val treeUri: String? = null,
)

/**
 * 设置存储。
 *
 * 用 SharedPreferences 而非 DataStore：这里只是十几个标量，
 * 引入 DataStore 要多一个依赖与一套 Flow 转换，收益不成正比。
 * 对外仍然暴露 StateFlow，Compose 侧只需 collectAsState。
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("qi_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        lightTheme = prefs.getBoolean(KEY_LIGHT, false),
        accentArgb = prefs.getInt(KEY_ACCENT, 0xFF4A7C59.toInt()),
        crossfade = prefs.getBoolean(KEY_CROSSFADE, false),
        gapless = prefs.getBoolean(KEY_GAPLESS, true),
        onlineLyrics = prefs.getBoolean(KEY_ONLINE_LYRICS, false),
        treeUri = prefs.getString(KEY_TREE_URI, null),
    )

    fun setLightTheme(value: Boolean) {
        prefs.edit().putBoolean(KEY_LIGHT, value).apply()
        _state.value = _state.value.copy(lightTheme = value)
    }

    fun setAccent(argb: Int) {
        prefs.edit().putInt(KEY_ACCENT, argb).apply()
        _state.value = _state.value.copy(accentArgb = argb)
    }

    fun setCrossfade(value: Boolean) {
        prefs.edit().putBoolean(KEY_CROSSFADE, value).apply()
        _state.value = _state.value.copy(crossfade = value)
    }

    fun setGapless(value: Boolean) {
        prefs.edit().putBoolean(KEY_GAPLESS, value).apply()
        _state.value = _state.value.copy(gapless = value)
    }

    fun setOnlineLyrics(value: Boolean) {
        prefs.edit().putBoolean(KEY_ONLINE_LYRICS, value).apply()
        _state.value = _state.value.copy(onlineLyrics = value)
    }

    fun setTreeUri(uri: String?) {
        prefs.edit().apply {
            if (uri == null) remove(KEY_TREE_URI) else putString(KEY_TREE_URI, uri)
        }.apply()
        _state.value = _state.value.copy(treeUri = uri)
    }

    // ------------------------------------------------------------ 断点续播

    fun saveLastSession(songUri: String?, positionMs: Long) {
        prefs.edit().putString(KEY_LAST_URI, songUri).putLong(KEY_LAST_POS, positionMs).apply()
    }

    fun lastSession(): Pair<String?, Long> =
        prefs.getString(KEY_LAST_URI, null) to prefs.getLong(KEY_LAST_POS, 0L)

    /** 整份队列一起存，冷启动才能把「上一首/下一首」也还原回来 */
    fun saveQueue(uris: List<String>, index: Int, positionMs: Long) {
        val arr = org.json.JSONArray(uris)
        prefs.edit()
            .putString(KEY_QUEUE, arr.toString())
            .putInt(KEY_QUEUE_INDEX, index)
            .putLong(KEY_QUEUE_POS, positionMs)
            .apply()
    }

    fun savedQueue(): Triple<List<String>, Int, Long> {
        val uris = runCatching {
            val arr = org.json.JSONArray(prefs.getString(KEY_QUEUE, "[]") ?: "[]")
            (0 until arr.length()).map { arr.optString(it) }
        }.getOrDefault(emptyList())
        return Triple(
            uris,
            prefs.getInt(KEY_QUEUE_INDEX, 0),
            prefs.getLong(KEY_QUEUE_POS, 0L),
        )
    }

    private companion object {
        const val KEY_LIGHT = "light_theme"
        const val KEY_ACCENT = "accent"
        const val KEY_CROSSFADE = "crossfade"
        const val KEY_GAPLESS = "gapless"
        const val KEY_ONLINE_LYRICS = "online_lyrics"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_LAST_URI = "last_uri"
        const val KEY_LAST_POS = "last_pos"
        const val KEY_QUEUE = "queue"
        const val KEY_QUEUE_INDEX = "queue_index"
        const val KEY_QUEUE_POS = "queue_pos"
    }
}
