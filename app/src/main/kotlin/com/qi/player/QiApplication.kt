package com.qi.player

import android.app.Application
import com.qi.player.data.LibraryRepository
import com.qi.player.data.LibraryStore
import com.qi.player.data.LyricsRepository
import com.qi.player.data.SettingsStore

/**
 * 极简的依赖容器。
 *
 * 只有一个 Application 作用域，成员都是无状态服务或轻量存储，
 * 因此不做 DI 框架——引入 Hilt 只会多一层编译期注解处理。
 */
class QiApplication : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var libraryStore: LibraryStore
        private set
    lateinit var library: LibraryRepository
        private set
    lateinit var lyrics: LyricsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        libraryStore = LibraryStore(this)
        library = LibraryRepository(this)
        lyrics = LyricsRepository(this, library)
    }
}
