package com.chessbeater.data

import android.content.Context
import android.graphics.Rect
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Geometric board calibration coordinates on screen.
 */
data class BoardCalibration(
    val x: Int,
    val y: Int,
    val size: Int
) {
    fun toRect(): Rect = Rect(x, y, x + size, y + size)
}

/**
 * DataStore repository for persistent board calibration coordinates per target app package.
 */
class CalibrationPreferencesRepository(context: Context) {

    private val dataStore = context.dataStore

    private fun keyForX(pkg: String) = intPreferencesKey("calib_x_${sanitizeKey(pkg)}")
    private fun keyForY(pkg: String) = intPreferencesKey("calib_y_${sanitizeKey(pkg)}")
    private fun keyForSize(pkg: String) = intPreferencesKey("calib_size_${sanitizeKey(pkg)}")

    private fun sanitizeKey(pkg: String): String =
        if (pkg.isBlank()) "default_target" else pkg.replace(".", "_")

    fun getCalibrationFlow(packageName: String): Flow<BoardCalibration?> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val x = preferences[keyForX(packageName)] ?: return@map null
                val y = preferences[keyForY(packageName)] ?: return@map null
                val size = preferences[keyForSize(packageName)] ?: return@map null
                if (size > 0) BoardCalibration(x, y, size) else null
            }

    suspend fun saveCalibration(packageName: String, x: Int, y: Int, size: Int) {
        dataStore.edit { preferences ->
            preferences[keyForX(packageName)] = x
            preferences[keyForY(packageName)] = y
            preferences[keyForSize(packageName)] = size
        }
    }

    suspend fun clearCalibration(packageName: String) {
        dataStore.edit { preferences ->
            preferences.remove(keyForX(packageName))
            preferences.remove(keyForY(packageName))
            preferences.remove(keyForSize(packageName))
        }
    }
}
