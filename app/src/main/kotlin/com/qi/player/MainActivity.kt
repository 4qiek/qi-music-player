package com.qi.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.player.playback.PlayerViewModel
import com.qi.player.ui.LibraryViewModel
import com.qi.player.ui.QiApp
import com.qi.player.ui.theme.QiTheme

class MainActivity : ComponentActivity() {

    private val playerVm: PlayerViewModel by viewModels()
    private val libraryVm: LibraryViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // 无论授没授权都扫一次：被拒时会得到空列表，界面里的
        // 「选择音乐文件夹」就是给这种情况留的备用通道。
        libraryVm.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by playerVm.settings.state.collectAsStateWithLifecycle()
            val songs by libraryVm.songs.collectAsStateWithLifecycle()

            // 首次进入：权限 -> 扫描
            LaunchedEffect(Unit) { ensurePermissionThenScan() }

            // 曲库到位后恢复上次的队列与进度（不自动播放）
            LaunchedEffect(songs) {
                if (songs.isNotEmpty()) playerVm.restoreSession(songs)
            }

            // 状态栏图标明暗必须跟随主题，否则浅色主题下状态栏一片白
            LaunchedEffect(settings.lightTheme) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = settings.lightTheme
                    isAppearanceLightNavigationBars = settings.lightTheme
                }
                @Suppress("DEPRECATION")
                run {
                    window.statusBarColor = Color.Transparent.toArgb()
                    window.navigationBarColor = Color.Transparent.toArgb()
                }
            }

            QiTheme(light = settings.lightTheme, accent = Color(settings.accentArgb)) {
                QiApp(player = playerVm, library = libraryVm)
            }
        }
    }

    private fun ensurePermissionThenScan() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) libraryVm.refresh() else permissionLauncher.launch(missing.toTypedArray())
    }
}
