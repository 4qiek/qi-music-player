package com.qi.player.util

/** 一行歌词及其时间戳 */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * LRC 解析器。
 *
 * 只做三件事，但每件都必须稳：
 *  1. 一行可带多个时间标签（副歌复用行），要全部展开；
 *  2. `[offset:±ms]` 是整首歌词的整体偏移，必须作用到每一行；
 *  3. 解析失败时**不能**抛异常——歌词坏掉不应该影响播放。
 */
object LrcParser {

    private val TIME_TAG = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val OFFSET_TAG = Regex("\\[offset:\\s*([+-]?\\d+)\\s*]", RegexOption.IGNORE_CASE)

    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()

        val offsetMs = OFFSET_TAG.find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L

        val out = ArrayList<LyricLine>()
        raw.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val matches = TIME_TAG.findAll(line).toList()
            if (matches.isEmpty()) return@forEach

            // 时间标签之后的文本才是歌词正文
            val text = line.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) return@forEach

            matches.forEach { m ->
                val minutes = m.groupValues[1].toLongOrNull() ?: return@forEach
                val seconds = m.groupValues[2].toLongOrNull() ?: return@forEach
                val fractionRaw = m.groupValues[3]
                // 毫秒位可能是 2 位（百分秒）或 3 位
                val millis = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> fractionRaw.toLong() * 100
                    2 -> fractionRaw.toLong() * 10
                    else -> fractionRaw.take(3).toLong()
                }
                val time = minutes * 60_000 + seconds * 1_000 + millis - offsetMs
                out += LyricLine(time.coerceAtLeast(0L), text)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    /**
     * 当前播放位置对应的歌词行下标。
     * 用二分查找而不是线性扫描：长歌词（几百行）× 每秒十余次重组，
     * 线性扫描会白白吃掉主线程时间。
     */
    fun indexAt(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        var ans = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                ans = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return ans
    }
}
