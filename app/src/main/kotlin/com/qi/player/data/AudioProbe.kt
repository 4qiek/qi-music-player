package com.qi.player.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * 用 MediaExtractor 读取音频轨的真实参数。
 *
 * 单独成类的原因：这一步比 MediaStore 查询慢一个数量级（每首 5~30ms），
 * 必须放到后台线程逐首跑，并缓存结果，不能阻塞列表首屏。
 */
object AudioProbe {

    private const val TAG = "AudioProbe"
    private val cache = ConcurrentHashMap<String, AudioFacts>()

    fun cachedFacts(uri: String): AudioFacts? = cache[uri]

    fun probe(context: Context, song: Song): AudioFacts {
        cache[song.uri]?.let { return it }

        val facts = readWithExtractor(context, song.uriObj)
            ?: readWithRetriever(context, song.uriObj)
            ?: AudioFacts(0, 0, 0, 0, song.mimeType, 0)

        cache[song.uri] = facts
        return facts
    }

    private fun readWithExtractor(context: Context, uri: Uri): AudioFacts? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue

                val sampleRate = format.optInt(MediaFormat.KEY_SAMPLE_RATE)
                val channels = format.optInt(MediaFormat.KEY_CHANNEL_COUNT)
                val bitrate = format.optInt(MediaFormat.KEY_BIT_RATE)
                // KEY_BITS_PER_SAMPLE 只有 PCM 类格式才会给出，其余格式按 0 处理
                val bits = format.optInt("bits-per-sample")
                return AudioFacts(
                    sampleRateHz = sampleRate,
                    bitsPerSample = bits,
                    bitrate = bitrate,
                    channels = channels,
                    mimeType = mime,
                )
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "extractor failed for $uri: ${e.message}")
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    /** 兜底：拿不到音轨格式时，至少把时长与码率补齐 */
    private fun readWithRetriever(context: Context, uri: Uri): AudioFacts? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull() ?: 0
            AudioFacts(
                sampleRateHz = 0,
                bitsPerSample = 0,
                bitrate = bitrate,
                channels = 0,
                mimeType = "",
                durationMs = duration,
            )
        } catch (e: Exception) {
            Log.w(TAG, "retriever failed for $uri: ${e.message}")
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun MediaFormat.optInt(key: String): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0
}
