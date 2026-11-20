package com.offlinemusicpro.player

import android.app.Application
import com.offlinemusicpro.player.di.AppContainer

class OfflineMusicApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
