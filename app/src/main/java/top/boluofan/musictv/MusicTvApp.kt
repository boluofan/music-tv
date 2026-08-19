package top.boluofan.musictv

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import top.boluofan.musictv.data.api.TlsCompat

@HiltAndroidApp
class MusicTvApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Android < 7.1.1 内置 ISRG Root X1，兼容 Let's Encrypt 服务器
        TlsCompat.initialize(this)
    }

    companion object {
        private lateinit var instance: MusicTvApp

        @JvmStatic
        fun getInstance(): MusicTvApp = instance

        @JvmStatic
        fun getAppContext(): Context = instance
    }
}
