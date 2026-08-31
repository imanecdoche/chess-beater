package com.chessbeater.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.chessbeater.data.PresetRepository
import com.chessbeater.overlay.MiniBoardOverlayService
import kotlinx.coroutines.*

/**
 * Sprint 30 & 36: Accessibility Service for Touch-Forwarding and Foreground App Auto-Switching.
 * - Dispatches swipe / tap gestures to target chess apps underneath the transparent overlay.
 * - Detects foreground window package changes and automatically applies linked calibration presets.
 */
class ChessAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var presetRepository: PresetRepository? = null

    companion object {
        private const val TAG = "ChessAccessibility"
        var instance: ChessAccessibilityService? = null
            private set

        var currentForegroundPackage: String? = null

        val isServiceRunning: Boolean
            get() = instance != null

        fun forwardClick(rawX: Float, rawY: Float, durationMs: Long = 100L): Boolean {
            val service = instance
            if (service == null) {
                Log.w(TAG, "Accessibility Service is not enabled/running")
                return false
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

            return try {
                // Samsung One UI & Android Input Pipeline requires minimal path delta to trigger motion events reliably
                val path = Path().apply {
                    moveTo(rawX, rawY)
                    lineTo(rawX + 0.1f, rawY + 0.1f)
                }
                val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(50L, 200L))
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                val dispatched = service.dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.d(TAG, "Touch forward click completed: ($rawX, $rawY)")
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.w(TAG, "Touch forward click cancelled")
                    }
                }, null)
                dispatched
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch touch forward click", e)
                false
            }
        }

        fun forwardDrag(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            durationMs: Long = 150L
        ): Boolean {
            val service = instance
            if (service == null) {
                Log.w(TAG, "Accessibility Service is not enabled/running")
                return false
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

            return try {
                val path = Path().apply {
                    moveTo(startX, startY)
                    lineTo(endX, endY)
                }
                val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(50L, 500L))
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                val dispatched = service.dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.d(TAG, "Touch forward drag completed: ($startX, $startY) -> ($endX, $endY)")
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.w(TAG, "Touch forward drag cancelled")
                    }
                }, null)
                dispatched
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch touch forward drag", e)
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        presetRepository = PresetRepository(applicationContext)
        Log.i(TAG, "ChessAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val pkg = event.packageName?.toString() ?: return

            if (pkg.isNotBlank() &&
                pkg != packageName &&
                pkg != currentForegroundPackage &&
                !pkg.contains("android.inputmethod") &&
                !pkg.contains("systemui")
            ) {
                currentForegroundPackage = pkg
                Log.d(TAG, "Foreground package changed to: $pkg")

                val repo = presetRepository ?: PresetRepository(applicationContext).also { presetRepository = it }
                serviceScope.launch(Dispatchers.IO) {
                    val matchedPreset = repo.getPresetByPackage(pkg)
                    if (matchedPreset != null) {
                        withContext(Dispatchers.Main) {
                            Log.i(TAG, "Auto-switching preset '${matchedPreset.name}' for foreground package: $pkg")
                            MiniBoardOverlayService.instance?.applyPreset(matchedPreset)
                        }
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "ChessAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "ChessAccessibilityService destroyed")
    }
}
