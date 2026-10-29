package com.qi.player.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 曲库来源。
 *
 * 两条独立通道：
 *  1. MediaStore —— 系统媒体库，覆盖绝大多数用户的音乐，扫描快；
 *  2. SAF 目录 —— 用户手动「导入文件夹」，用于系统未收录的目录（含 .lrc 同目录歌词）。
 *
 * 两者结果合并去重（按内容 URI），并过滤掉短于 30 秒的音频
 * （提示音、录音片段混进歌单会非常碍事）。
 */
class LibraryRepository(private val context: Context) {

    private val minDurationMs = 30_000L

    /** 扫描结果里顺带收集到的外挂歌词：小写基名 -> .lrc 文本 */
    private val lrcByBaseName = HashMap<String, String>()

    fun localLyricsFor(song: Song): String? {
        val base = song.title.substringBeforeLast('.').lowercase(Locale.ROOT)
        return lrcByBaseName[base]
    }

    suspend fun scan(treeUri: String? = null): List<Song> = withContext(Dispatchers.IO) {
        val result = LinkedHashMap<String, Song>()

        runCatching { scanMediaStore() }
            .onFailure { Log.w(TAG, "MediaStore 扫描失败: ${it.message}") }
            .getOrNull()
            ?.forEach { result[it.uri] = it }

        if (!treeUri.isNullOrBlank()) {
            runCatching { scanTree(Uri.parse(treeUri)) }
                .onFailure { Log.w(TAG, "文件夹扫描失败: ${it.message}") }
                .getOrNull()
                ?.forEach { if (!result.containsKey(it.uri)) result[it.uri] = it }
        }

        result.values.sortedWith(compareBy({ it.title.lowercase(Locale.ROOT) }, { it.artist }))
    }

    // ---------------------------------------------------------------- MediaStore

    private fun scanMediaStore(): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        // 注意：RELATIVE_PATH 是 API 29 才有的列，低版本查询会直接抛异常，必须按版本分支。
        val columns = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf(minDurationMs.toString())
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val songs = ArrayList<Song>()
        context.contentResolver.query(collection, columns.toTypedArray(), selection, args, order)
            ?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dataIdx = c.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val uri = ContentUris.withAppendedId(collection, id).toString()
                    val fileName = c.getStringOrEmpty(nameIdx)
                    val path = if (dataIdx >= 0) c.getStringOrEmpty(dataIdx) else ""
                    songs += Song(
                        id = id,
                        uri = uri,
                        title = cleanTitle(c.getStringOrEmpty(titleIdx), fileName),
                        artist = cleanArtist(c.getStringOrEmpty(artistIdx)),
                        album = c.getStringOrEmpty(albumIdx),
                        albumId = c.getLong(albumIdIdx),
                        durationMs = c.getLong(durationIdx),
                        folder = path.substringBeforeLast('/', "").substringAfterLast('/'),
                        mimeType = c.getStringOrEmpty(mimeIdx),
                        fromFolder = false,
                    )
                }
            }
        return songs
    }

    // ---------------------------------------------------------------- SAF 目录

    private fun scanTree(rootUri: Uri): List<Song> {
        val resolver = context.contentResolver
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()
        val out = ArrayList<Song>()
        var visited = 0

        // 显式栈替代递归：目录层级过深时递归容易爆栈，且便于加总量上限。
        val stack = ArrayDeque<DocumentFile>()
        stack.addLast(root)
        while (stack.isNotEmpty() && visited < MAX_FILES) {
            val dir = stack.removeLast()
            val children = dir.listFiles()
            for (child in children) {
                visited++
                if (visited > MAX_FILES) break
                if (child.isDirectory) {
                    stack.addLast(child)
                    continue
                }
                val name = child.name ?: continue
                val lower = name.lowercase(Locale.ROOT)
                if (lower.endsWith(".lrc")) {
                    runCatching {
                        resolver.openInputStream(child.uri)?.bufferedReader()?.use { r ->
                            lrcByBaseName[name.substringBeforeLast('.').lowercase(Locale.ROOT)] = r.readText()
                        }
                    }
                    continue
                }
                if (AUDIO_EXTENSIONS.none { lower.endsWith(it) }) continue

                out += Song(
                    id = (child.uri.toString().hashCode().toLong() and 0x7fffffff),
                    uri = child.uri.toString(),
                    title = name.substringBeforeLast('.'),
                    artist = "",
                    album = dir.name ?: "",
                    albumId = 0,
                    durationMs = 0,
                    folder = dir.name ?: "",
                    mimeType = guessMime(lower),
                    fromFolder = true,
                )
            }
        }
        return out
    }

    // ---------------------------------------------------------------- 工具

    private fun cleanTitle(raw: String, fileName: String): String {
        if (raw.isNotBlank() && raw != "<unknown>") return raw
        return fileName.substringBeforeLast('.').ifBlank { "未知曲目" }
    }

    private fun cleanArtist(raw: String): String =
        if (raw.isBlank() || raw == "<unknown>") "" else raw

    private fun Cursor.getStringOrEmpty(index: Int): String =
        if (index < 0 || isNull(index)) "" else getString(index) ?: ""

    private fun guessMime(lower: String): String = when {
        lower.endsWith(".flac") -> "audio/flac"
        lower.endsWith(".wav") -> "audio/x-wav"
        lower.endsWith(".dsf") -> "audio/dsf"
        lower.endsWith(".m4a") || lower.endsWith(".alac") -> "audio/mp4"
        lower.endsWith(".ogg") -> "audio/ogg"
        lower.endsWith(".ape") -> "audio/x-ape"
        else -> "audio/mpeg"
    }

    companion object {
        private const val TAG = "LibraryRepository"
        private const val MAX_FILES = 5000

        private val AUDIO_EXTENSIONS = listOf(
            ".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".opus", ".dsf", ".dff", ".ape", ".alac", ".wma",
        )

        /** 封面 URI。取不到时 Coil 会走占位图，不会崩 */
        fun albumArtUri(albumId: Long): Uri? =
            if (albumId <= 0) null
            else ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

        /** 传入与机型无关的原始 Uri，便于 SAF 曲目用 document 缩略图兜底 */
        fun isSafUri(uri: String): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && uri.startsWith("content://com.android.externalstorage")
    }
}
