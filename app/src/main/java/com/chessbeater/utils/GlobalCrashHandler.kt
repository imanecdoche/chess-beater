package com.chessbeater.utils

import android.util.Log

class GlobalCrashHandler(
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val stackTraceString = Log.getStackTraceString(throwable)
        AppLogger.log("FATAL_CRASH", "💥 Crash Terjadi di Thread [${thread.name}]:\n$stackTraceString")

        // Berikan jeda 300ms agar thread IO selesai menulis log ke file
        try { Thread.sleep(300) } catch (_: InterruptedException) {}

        defaultHandler?.uncaughtException(thread, throwable)
    }

    companion object {
        fun install() {
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (currentHandler !is GlobalCrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(GlobalCrashHandler(currentHandler))
            }
        }
    }
}
