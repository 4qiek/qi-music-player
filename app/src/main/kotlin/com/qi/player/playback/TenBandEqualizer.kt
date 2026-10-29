package com.qi.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 十段图示均衡器（真实 DSP，不依赖系统 Equalizer）。
 *
 * 为什么不用 android.media.audiofx.Equalizer：
 * 系统均衡器的频段数由厂商实现决定，绝大多数机型只有 5 段，
 * 蓝牙输出下还有相当比例直接不可用。草图要的是 10 段调音台，
 * 靠系统接口做不到「10 个推子都真的有效」，因此自己实现。
 *
 * 结构：每声道 10 个串接的 peaking biquad（RBJ Audio EQ Cookbook），
 * 中心频率按倍频程分布 31Hz ~ 16kHz，Q 取 1.41（约一个倍频程带宽），
 * 增益范围 ±12dB。全 0dB 时系数为单位冲激，直接走直通分支。
 *
 * 线程模型：
 *  - [setGain] / [setGains] 在 UI 线程调用，重建系数数组后**整体替换引用**；
 *  - 音频线程每处理一个 buffer 只读一次 `coeffs` 引用，全程不加锁。
 *    实时音频路径上任何锁竞争都会变成可听见的爆音，这一点不能让。
 *  - 系数与滤波状态是两张独立的表：换系数不会清状态，所以拖推子不会咔哒。
 */
class TenBandEqualizer : BaseAudioProcessor() {

    /** 频段中心频率（Hz），与 UI 刻度一一对应 */
    val centerFrequencies: IntArray get() = CENTER_FREQUENCIES

    private val bandCount = centerFrequencies.size

    private val gainsDb = FloatArray(bandCount)

    @Volatile
    private var coeffs: Array<Coeff> = Array(bandCount) { Coeff.IDENTITY }

    @Volatile
    private var channels = 2

    @Volatile
    private var sampleRate = 44_100

    @Volatile
    private var encoding = C.ENCODING_PCM_16BIT

    // 滤波状态：[band][channel]
    private var x1 = Array(bandCount) { FloatArray(2) }
    private var x2 = Array(bandCount) { FloatArray(2) }
    private var y1 = Array(bandCount) { FloatArray(2) }
    private var y2 = Array(bandCount) { FloatArray(2) }

    // ------------------------------------------------------------ 对外控制

    fun gainsSnapshot(): FloatArray = gainsDb.copyOf()

    fun setGain(band: Int, gainDb: Float) {
        if (band !in 0 until bandCount) return
        val clamped = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        if (gainsDb[band] == clamped) return
        gainsDb[band] = clamped
        rebuildCoefficients()
    }

    fun setGains(values: FloatArray) {
        for (i in 0 until bandCount) {
            gainsDb[i] = (values.getOrNull(i) ?: 0f).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }
        rebuildCoefficients()
    }

    fun resetGains() = setGains(FloatArray(bandCount))

    private fun isFlat(): Boolean = gainsDb.all { it == 0f }

    // ------------------------------------------------------------ AudioProcessor

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            // 不认识的编码一律拒绝，交给上层直通，绝不冒险处理
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channels = inputAudioFormat.channelCount.coerceIn(1, 8)
        sampleRate = inputAudioFormat.sampleRate
        encoding = inputAudioFormat.encoding
        allocateState()
        rebuildCoefficients()
        // 输出格式与输入严格一致：不改采样率、不改声道数，避免与 AudioTrack 协商失败
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        val output = replaceOutputBuffer(remaining)
        if (isFlat()) {
            output.put(inputBuffer)
        } else {
            when (encoding) {
                C.ENCODING_PCM_FLOAT -> processFloat(inputBuffer, output, remaining)
                else -> processShort(inputBuffer, output, remaining)
            }
        }
        output.flip()
    }

    // BaseAudioProcessor 把 flush()/reset() 声明为 final，只留 onFlush()/onReset() 钩子
    override fun onFlush() {
        super.onFlush()
        clearState()
    }

    override fun onReset() {
        super.onReset()
        clearState()
    }

    // ------------------------------------------------------------ DSP

    private fun allocateState() {
        x1 = Array(bandCount) { FloatArray(channels) }
        x2 = Array(bandCount) { FloatArray(channels) }
        y1 = Array(bandCount) { FloatArray(channels) }
        y2 = Array(bandCount) { FloatArray(channels) }
    }

    /** seek / 切歌会触发 flush，必须清掉历史样本，否则有残留拖尾 */
    private fun clearState() {
        for (b in 0 until bandCount) {
            x1[b].fill(0f); x2[b].fill(0f); y1[b].fill(0f); y2[b].fill(0f)
        }
    }

    private fun processShort(input: ByteBuffer, output: ByteBuffer, bytes: Int) {
        val c = coeffs
        val frames = bytes / 2 / channels
        for (frame in 0 until frames) {
            for (ch in 0 until channels) {
                var sample = input.short.toFloat() / 32768f
                for (b in 0 until bandCount) {
                    sample = filter(c[b], b, ch, sample)
                }
                output.putShort((sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            }
        }
    }

    private fun processFloat(input: ByteBuffer, output: ByteBuffer, bytes: Int) {
        val c = coeffs
        val frames = bytes / 4 / channels
        for (frame in 0 until frames) {
            for (ch in 0 until channels) {
                var sample = input.float
                for (b in 0 until bandCount) {
                    sample = filter(c[b], b, ch, sample)
                }
                output.putFloat(sample.coerceIn(-1f, 1f))
            }
        }
    }

    private fun filter(k: Coeff, band: Int, ch: Int, input: Float): Float {
        val out = k.b0 * input + k.b1 * x1[band][ch] + k.b2 * x2[band][ch] -
            k.a1 * y1[band][ch] - k.a2 * y2[band][ch]
        x2[band][ch] = x1[band][ch]
        x1[band][ch] = input
        y2[band][ch] = y1[band][ch]
        y1[band][ch] = out
        return out
    }

    private fun rebuildCoefficients() {
        val nyquist = sampleRate / 2f
        coeffs = Array(bandCount) { i ->
            val freq = centerFrequencies[i].toFloat().coerceAtMost(nyquist * 0.95f)
            Coeff.peaking(freq, sampleRate.toFloat(), Q, gainsDb[i])
        }
    }

    /** 归一化后的 biquad 系数 */
    private data class Coeff(
        val b0: Float,
        val b1: Float,
        val b2: Float,
        val a1: Float,
        val a2: Float,
    ) {
        companion object {
            val IDENTITY = Coeff(1f, 0f, 0f, 0f, 0f)

            fun peaking(freq: Float, sampleRate: Float, q: Float, gainDb: Float): Coeff {
                if (gainDb == 0f) return IDENTITY
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2.0 * PI * freq / sampleRate
                val cosW = cos(w0)
                val alpha = sin(w0) / (2.0 * q)
                val a0 = 1.0 + alpha / a
                return Coeff(
                    b0 = ((1.0 + alpha * a) / a0).toFloat(),
                    b1 = ((-2.0 * cosW) / a0).toFloat(),
                    b2 = ((1.0 - alpha * a) / a0).toFloat(),
                    a1 = ((-2.0 * cosW) / a0).toFloat(),
                    a2 = ((1.0 - alpha / a) / a0).toFloat(),
                )
            }
        }
    }

    companion object {
        const val MIN_GAIN_DB = -12f
        const val MAX_GAIN_DB = 12f
        private const val Q = 1.41f

        /** UI 也要读这份频率表，做成常量避免为了取刻度去 new 一个处理器 */
        val CENTER_FREQUENCIES = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    }
}
