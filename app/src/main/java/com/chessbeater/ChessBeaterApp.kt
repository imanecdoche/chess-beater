package com.chessbeater

import android.app.Application
import com.chessbeater.utils.AppLogger
import com.chessbeater.utils.GlobalCrashHandler

class ChessBeaterApp : Application() {
    companion object {
        lateinit var instance: ChessBeaterApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        AppLogger.init(this)
        GlobalCrashHandler.install()
    }
}
