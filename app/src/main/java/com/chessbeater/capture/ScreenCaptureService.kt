package com.chessbeater.capture

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import java.nio.ByteBuffer



/**
 * Android Foreground Service managing MediaProjection screen ingestion.
 * Operates at downscaled resolution (e.g. 720p) and 15-30 FPS for high performance & power efficiency.
 */
class ScreenCaptureService : Service() {

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var captureConfig = ScreenCaptureConfig()
    private var isCapturing = false
    private var lastFrameTimestampMs = 0L

    // Reusable Bitmap buffers to prevent GC thrashing & memory leaks
    private var reusableBuffer: ByteBuffer? = null
    private var reusableBitmap: Bitmap? = null
    private val bufferLock = Any()

    // Foreground orchestrator & overlay managers
    private var overlayManager: com.chessbeater.overlay.OverlayManager? = null
    private var hapticManager: com.chessbeater.haptics.HapticFeedbackManager? = null
    private var orchestrator: com.chessbeater.orchestrator.GameOrchestrator? = null
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())


    companion object {
        private const val TAG = "ScreenCaptureService"
        const val NOTIFICATION_CHANNEL_ID = "chess_beater_capture_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_CAPTURE = "com.chessbeater.action.START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.chessbeater.action.STOP_CAPTURE"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_CONFIG = "extra_config"

        // Token fallbacks to prevent IPC intent parceling dropouts
        private var cachedResultCode: Int = Activity.RESULT_CANCELED
        private var cachedResultData: Intent? = null

        // Global SharedFlow for real-time frame distribution
        private val _frameFlow = MutableSharedFlow<Bitmap>(extraBufferCapacity = 4)
        val frameFlow: SharedFlow<Bitmap> = _frameFlow.asSharedFlow()

        @Volatile
        var latestFrame: Bitmap? = null
            internal set

        private var frameListener: ((Bitmap) -> Unit)? = null

        var activeOrchestrator: com.chessbeater.orchestrator.GameOrchestrator? = null
            internal set

        var activeOverlayManager: com.chessbeater.overlay.OverlayManager? = null
            internal set

        fun triggerCalibration() {
            activeOverlayManager?.onCalibrationRequested?.invoke()
        }

        fun setFrameListener(listener: ((Bitmap) -> Unit)?) {

            frameListener = listener
        }

        fun start(context: Context, resultCode: Int, data: Intent, config: ScreenCaptureConfig = ScreenCaptureConfig()) {
            cachedResultCode = resultCode
            cachedResultData = data

            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START_CAPTURE
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                putExtra(EXTRA_CONFIG, config)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            cachedResultData = null
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP_CAPTURE
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        createNotificationChannel()
        acquireWakeLock()
        startBackgroundThread()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                val resultCode = if (intent.hasExtra(EXTRA_RESULT_CODE)) {
                    intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                } else {
                    cachedResultCode
                }

                @Suppress("DEPRECATION")
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA) ?: cachedResultData
                val config = (intent.getSerializableExtra(EXTRA_CONFIG) as? ScreenCaptureConfig) ?: ScreenCaptureConfig()

                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    startForegroundWithNotification()
                    startScreenCapture(resultCode, resultData, config)
                } else {
                    Log.e(TAG, "Invalid MediaProjection token or user denied permission (resultCode=$resultCode, data=$resultData)")
                    stopSelf()
                }
            }
            ACTION_STOP_CAPTURE -> {
                stopScreenCapture()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }


    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Chess Beater Active")
            .setContentText("Screen ingestion & real-time board analysis running")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("WrongConstant")
    private fun startScreenCapture(resultCode: Int, data: Intent, config: ScreenCaptureConfig) {
        if (isCapturing) return
        this.captureConfig = config

        val projection = mediaProjectionManager?.getMediaProjection(resultCode, data)
        if (projection == null) {
            Log.e(TAG, "Failed to obtain MediaProjection instance")
            stopSelf()
            return
        }
        this.mediaProjection = projection

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped by system")
                stopScreenCapture()
            }
        }, backgroundHandler)

        // Setup ImageReader with RGBA_8888 for high-efficiency pixel reading
        val width = config.targetWidth
        val height = config.targetHeight
        val density = config.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, config.maxBufferImages).apply {
            setOnImageAvailableListener({ reader ->
                handleImageAvailable(reader)
            }, backgroundHandler)
        }

        virtualDisplay = projection.createVirtualDisplay(
            "ChessBeaterVirtualDisplay",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            backgroundHandler
        )

        isCapturing = true
        Log.i(TAG, "MediaProjection VirtualDisplay active: ${width}x${height}@${density}dpi")

        // 3. Initialize and Start Overlays & GameOrchestrator within Foreground Service Context
        if (overlayManager == null) {
            overlayManager = com.chessbeater.overlay.OverlayManager(this)
        }
        if (hapticManager == null) {
            hapticManager = com.chessbeater.haptics.HapticFeedbackManager(this)
        }
        if (orchestrator == null) {
            orchestrator = com.chessbeater.orchestrator.GameOrchestrator(
                overlayManager = overlayManager,
                hapticManager = hapticManager
            )
        }
        activeOrchestrator = orchestrator
        activeOverlayManager = overlayManager

        // Show overlays on main UI thread
        Handler(Looper.getMainLooper()).post {
            overlayManager?.showOverlays()
        }

        val calibRepo = com.chessbeater.data.CalibrationPreferencesRepository(this)
        val prefRepo = com.chessbeater.data.EnginePreferencesRepository(this)

        overlayManager?.onCalibrationRequested = {
            serviceScope.launch {
                val currentPrefs = prefRepo.userPreferencesFlow.first()
                val pkg = currentPrefs.selectedAppPackage
                val existingCalib = calibRepo.getCalibrationFlow(pkg).first()
                val initialRect = existingCalib?.toRect()

                withContext(Dispatchers.Main) {
                    overlayManager?.showCalibrationOverlay(
                        initialRect = initialRect,
                        onSave = { savedRect ->
                            serviceScope.launch {
                                calibRepo.saveCalibration(pkg, savedRect.left, savedRect.top, savedRect.width())
                                val profile = com.chessbeater.vision.models.ChessAppProfile
                                    .forTarget(currentPrefs.targetApp)
                                    .copy(customCalibratedRect = savedRect)
                                orchestrator?.updateAppProfile(profile)
                            }
                        }
                    )
                }
            }
        }

        overlayManager?.onPlayerColorToggleRequested = {
            orchestrator?.toggleManualPlayerColor()
        }

        overlayManager?.onMiniBoardEvaluationRequested = { customFen ->
            serviceScope.launch {
                val result = orchestrator?.evaluateCustomFen(customFen)
                if (result != null) {
                    withContext(Dispatchers.Main) {
                        overlayManager?.updateInteractiveBoardEngineResult(
                            bestMove = result.bestMove,
                            evalCp = result.evaluationCentipawns,
                            mate = result.mateInMoves
                        )
                    }
                }
            }
        }

        overlayManager?.onMiniBoardPositionChanged = { x, y ->
            serviceScope.launch {
                prefRepo.updateMiniBoardPosition(x, y)
            }
        }


        serviceScope.launch {
            try {
                val prefs = prefRepo.userPreferencesFlow.first()
                val pkg = prefs.selectedAppPackage
                val savedCalib = calibRepo.getCalibrationFlow(pkg).first()
                if (savedCalib != null) {
                    val profile = com.chessbeater.vision.models.ChessAppProfile
                        .forTarget(prefs.targetApp)
                        .copy(customCalibratedRect = savedCalib.toRect())
                    orchestrator?.updateAppProfile(profile)
                }
                orchestrator?.start()

                // Auto-launch target chess app if enabled
                if (prefs.autoLaunchTargetApp) {
                    val pkg = prefs.selectedAppPackage
                    if (pkg.isNotBlank()) {
                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        }
                        if (launchIntent != null) {
                            try {
                                startActivity(launchIntent)
                                Log.i(TAG, "Auto-launched target app: $pkg")
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to auto-launch package: $pkg", e)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ScreenCaptureService, "Silakan buka aplikasi catur Anda secara manual", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting GameOrchestrator or auto-launching", e)
            }
        }
    }




    /**
     * Extracts Bitmap frame from ImageReader in a thread-safe manner without memory leaks or buffer freeze.
     */
    private fun handleImageAvailable(reader: ImageReader) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage() ?: return
            val now = SystemClock.uptimeMillis()

            // Throttling: discard frame if interval has not elapsed to save battery
            if (now - lastFrameTimestampMs < captureConfig.frameIntervalMs) {
                return
            }

            val planes = image.planes
            if (planes.isEmpty()) return

            val buffer = planes[0].buffer ?: return
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val width = image.width
            val height = image.height

            lastFrameTimestampMs = now

            synchronized(bufferLock) {
                val rowPadding = rowStride - pixelStride * width
                val fullWidth = width + (rowPadding / pixelStride)

                if (reusableBitmap == null || reusableBitmap?.width != fullWidth || reusableBitmap?.height != height) {
                    reusableBitmap?.recycle()
                    reusableBitmap = Bitmap.createBitmap(fullWidth, height, Bitmap.Config.ARGB_8888)
                }

                val currentBitmap = reusableBitmap ?: return@synchronized
                currentBitmap.copyPixelsFromBuffer(buffer)

                // If row padding exists, crop to exact clean width; otherwise use directly
                val finalBitmap = if (rowPadding == 0) {
                    currentBitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    Bitmap.createBitmap(currentBitmap, 0, 0, width, height)
                }

                finalBitmap?.let { bmp ->
                    latestFrame = bmp
                    _frameFlow.tryEmit(bmp)
                    frameListener?.invoke(bmp)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting screen frame Bitmap", e)
        } finally {
            try {
                image?.close()
            } catch (e: Exception) {
                // Ignore already closed
            }
        }
    }


    private fun stopScreenCapture() {
        if (!isCapturing) return
        isCapturing = false
        latestFrame = null

        try {
            virtualDisplay?.release()
            virtualDisplay = null

            imageReader?.close()
            imageReader = null

            mediaProjection?.stop()
            mediaProjection = null

            synchronized(bufferLock) {
                reusableBitmap?.recycle()
                reusableBitmap = null
            }

            orchestrator?.stop()
            orchestrator?.release()
            orchestrator = null
            activeOrchestrator = null

            overlayManager?.hideOverlays()
            overlayManager = null

            hapticManager?.cancel()
            hapticManager = null

        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up screen capture resources", e)
        }
        Log.i(TAG, "Screen capture service stopped successfully")
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Chess Beater Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground screen capture for real-time chess move assistance"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChessBeater:ScreenCaptureWakeLock")
        wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            wakeLock = null
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("ScreenCaptureHandlerThread").apply {
            start()
            backgroundHandler = Handler(looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join(500)
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted stopping handler thread", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopScreenCapture()
        releaseWakeLock()
        stopBackgroundThread()
        serviceScope.cancel()
        super.onDestroy()
    }
}

