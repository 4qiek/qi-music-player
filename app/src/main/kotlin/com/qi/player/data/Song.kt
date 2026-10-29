package com.qi.player.data

import android.net.Uri
import java.util.Locale

/**
 * 一首曲目。
 *
 * 曲库扫描阶段只填 MediaStore 能直接给出的字段（快），
 * 音频真实参数（采样率 / 位深 / 声道）由 [AudioProbe] 逐首异步补齐，
 * 补齐之前 [sampleRateHz] 等字段为 0，UI 必须对 0 做降级处理。
 */
data class Song(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val folder: String,
    val mimeType: String,
    /** 采样率，Hz。0 表示尚未探测 */
    val sampleRateHz: Int = 0,
    /** 位深，bit。0 表示未知（多数有损格式拿不到该字段） */
    val bitsPerSample: Int = 0,
    /** 码率，bps */
    val bitrate: Int = 0,
    val channels: Int = 0,
    /** 是否为外挂文件夹（SAF 导入）中的曲目 */
    val fromFolder: Boolean = false,
) {
    val uriObj: Uri get() = Uri.parse(uri)

    /**
     * Hi-Res 判定。
     *
     * 采用业界通行的门槛（与索尼 Hi-Res 认证的最低档一致）：
     * 采样率高于 48kHz，或位深达到 24bit，或码率超过 CD 无损的两倍。
     * 拿不到任何参数时一律**不**标 Hi-Res——宁可漏标，不可虚标。
     */
    val isHiRes: Boolean
        get() = when {
            sampleRateHz > 48_000 -> true
            bitsPerSample >= 24 -> true
            bitrate >= 2_822_400 -> true
            else -> false
        }

    /** 形如 "24bit/96kHz"，参数不全时自动降级为 "96kHz" 或空串 */
    val qualityLabel: String
        get() {
            if (sampleRateHz <= 0) return ""
            val khz = if (sampleRateHz % 1000 == 0) {
                "${sampleRateHz / 1000}kHz"
            } else {
                String.format(Locale.US, "%.1fkHz", sampleRateHz / 1000f)
            }
            return if (bitsPerSample > 0) "${bitsPerSample}bit/$khz" else khz
        }

    /** 列表右侧的副标注：Hi-Res 显示规格，普通曲目显示时长 */
    val listTrailingText: String
        get() = if (isHiRes && qualityLabel.isNotEmpty()) qualityLabel else formatDuration(durationMs)

    /** 无标题的曲目用文件名兜底，避免列表出现空白行 */
    val displayTitle: String
        get() = title.ifBlank { "未知曲目" }

    val displayArtist: String
        get() = artist.ifBlank { "未知艺术家" }

    companion object {
        fun formatDuration(ms: Long): String {
            if (ms <= 0) return "--:--"
            val totalSec = ms / 1000
            val m = totalSec / 60
            val s = totalSec % 60
            return String.format(Locale.US, "%02d:%02d", m, s)
        }
    }
}

/** 音频文件真实参数，由 MediaExtractor 读出 */
data class AudioFacts(
    val sampleRateHz: Int,
    val bitsPerSample: Int,
    val bitrate: Int,
    val channels: Int,
    val mimeType: String,
    /** 探测得到的时长；MediaStore 已有值时为 0，不覆盖 */
    val durationMs: Long = 0,
)
