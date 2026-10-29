package com.qi.player.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.qi.player.data.LibraryRepository
import com.qi.player.data.Song

/**
 * 均衡器实例的跨层引用。
 *
 * 为什么允许一个「全局单例」：均衡器活在 PlaybackService 的音频线程里，
 * UI 必须能直接改它的系数。同进程内直接持有引用，比走
 * MediaSession 的自定义命令 + Bundle 序列化简单得多，也没有延迟。
 * 代价是 UI 与 Service 必须同进程——本应用本来就没有分进程的理由。
 */
object EqualizerBridge {
    @Volatile
    var processor: TenBandEqualizer? = null
        private set

    fun attach(eq: TenBandEqualizer) {
        processor = eq
    }

    fun detach(eq: TenBandEqualizer) {
        if (processor === eq) processor = null
    }
}

/** 均衡器增益的持久化（10 个 float，用逗号串存最省事） */
object EqualizerPrefs {

    private const val KEY = "gains"
    private const val PREFS = "qi_eq"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): FloatArray {
        val raw = prefs(context).getString(KEY, null) ?: return FloatArray(10)
        val parts = raw.split(',')
        return FloatArray(10) { i -> parts.getOrNull(i)?.toFloatOrNull() ?: 0f }
    }

    fun save(context: Context, gains: FloatArray) {
        prefs(context).edit().putString(KEY, gains.joinToString(",")).apply()
    }
}

/** Song -> Media3 的播放条目。封面写进 metadata，通知栏才会显示专辑图 */
fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(uri)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(displayTitle)
            .setArtist(displayArtist)
            .setAlbumTitle(album)
            .setArtworkUri(LibraryRepository.albumArtUri(albumId))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
    )
    .build()
