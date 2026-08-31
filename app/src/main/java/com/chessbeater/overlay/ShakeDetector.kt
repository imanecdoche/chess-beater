package com.chessbeater.overlay

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * Sprint 33: ShakeDetector
 * Menggunakan Sensor.TYPE_ACCELEROMETER untuk mendeteksi guncangan fisik pada perangkat.
 * Saat gForce > 2.5F terdeteksi, memicu onShakeDetected() untuk memunculkan kembali overlay.
 */
class ShakeDetector(
    private val onShakeListener: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "ShakeDetector"
        private const val SHAKE_THRESHOLD_GRAVITY = 2.5f
        private const val SHAKE_SLOP_TIME_MS = 500L
    }

    private var shakeTimestamp: Long = 0L
    private var isListening = false

    fun start(sensorManager: SensorManager?): Boolean {
        if (sensorManager == null) return false
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: run {
            Log.w(TAG, "Accelerometer sensor not available on this device")
            return false
        }
        val registered = sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )
        isListening = registered
        Log.i(TAG, "ShakeDetector registered: $registered")
        return registered
    }

    fun stop(sensorManager: SensorManager?) {
        if (sensorManager != null && isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
            Log.i(TAG, "ShakeDetector unregistered")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()
            if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                return
            }
            shakeTimestamp = now
            Log.d(TAG, "Shake event detected! gForce: $gForce")
            onShakeListener()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
