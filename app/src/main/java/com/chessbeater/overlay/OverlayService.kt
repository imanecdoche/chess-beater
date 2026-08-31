package com.chessbeater.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Service wrapper for managing the life-cycle of Chess Beater Floating Overlays.
 */
class OverlayService : Service() {

    private var overlayManager: OverlayManager? = null

    companion object {
        const val ACTION_SHOW_OVERLAY = "com.chessbeater.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.chessbeater.action.HIDE_OVERLAY"

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_SHOW_OVERLAY
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_HIDE_OVERLAY
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        overlayManager = OverlayManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> overlayManager?.showOverlays()
            ACTION_HIDE_OVERLAY -> {
                overlayManager?.hideOverlays()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        overlayManager?.hideOverlays()
        overlayManager = null
        super.onDestroy()
    }
}
