package com.qi.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.qi.player.MainActivity
import com.qi.player.QiApplication

/**
 * 后台播放服务。
 *
 * 用 [MediaSessionService] 而不是自己写前台通知，好处是：
 * 通知栏控制、蓝牙耳机按键、车机、系统媒体面板全部由 Media3 接管，
 * 不用手写 MediaButtonReceiver，也不会漏掉 Android 13 的通知权限细节。
 *
 * 音频链在这里组装：ExoPlayer -> 十段均衡器 -> AudioSink。
 * 均衡器必须挂在 RenderersFactory 上而不是事后 attach，
 * 否则要等下一次音频格式切换才会生效，用户会觉得「拖了没反应」。
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var equalizer: TenBandEqualizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()

        val app = application as QiApplication

        // ---- 均衡器：从持久化恢复增益，挂进音频链 ----
        val eq = TenBandEqualizer()
        eq.setGains(EqualizerPrefs.load(this))
        EqualizerBridge.attach(eq)
        equalizer = eq

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(eq))
                .build()
        }

        val player = ExoPlayer.Builder(this, renderersFactory).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // 拔耳机自动暂停，避免外放突然响起来
            setHandleAudioBecomingNoisy(true)
        }

        player.addListener(object : Player.Listener {
            /**
             * 「无缝播放」关闭时，在两首之间插入一段刻意留出的空隙。
             *
             * ExoPlayer 默认就是无缝的（同格式连播不插静音），所以这个开关
             * 只能反过来实现：关闭时主动 pause 一下再 play。
             * 这样做出来的「有空隙」是用户按开关能直接听出来的，不是装饰性设置。
             */
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                    !app.settings.state.value.gapless
                ) {
                    player.pause()
                    mainHandler.postDelayed({ if (player.playbackState != Player.STATE_IDLE) player.play() }, GAP_MS)
                }
            }
        })

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** 用户从最近任务里划掉应用：没在放歌就顺手结束服务，不要在后台白占一个前台通知 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        equalizer?.let {
            EqualizerPrefs.save(this, it.gainsSnapshot())
            EqualizerBridge.detach(it)
        }
        equalizer = null
        session?.run {
            player.release()
            release()
        }
        session = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val GAP_MS = 400L
    }
}
