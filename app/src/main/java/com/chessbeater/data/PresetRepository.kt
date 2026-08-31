package com.chessbeater.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * DataStore repository untuk menyimpan dan mengelola Multi-Preset Kalibrasi Papan Catur.
 */
class PresetRepository(private val context: Context) {

    private val dataStore = context.dataStore

    companion object {
        val KEY_PRESETS_JSON = stringPreferencesKey("key_calibration_presets_json")
        val KEY_ACTIVE_PRESET_ID = stringPreferencesKey("key_active_calibration_preset_id")
    }

    /**
     * Mengambil daftar semua preset kalibrasi yang tersimpan.
     */
    fun getAllPresets(): Flow<List<CalibrationPreset>> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val jsonStr = preferences[KEY_PRESETS_JSON] ?: return@map emptyList()
                parsePresets(jsonStr)
            }

    /**
     * Menyimpan atau memperbarui preset kalibrasi.
     */
    suspend fun savePreset(preset: CalibrationPreset) {
        dataStore.edit { preferences ->
            val currentJson = preferences[KEY_PRESETS_JSON] ?: "[]"
            val list = parsePresets(currentJson).toMutableList()

            val index = list.indexOfFirst { it.id == preset.id }
            if (index >= 0) {
                list[index] = preset
            } else {
                list.add(preset)
            }

            preferences[KEY_PRESETS_JSON] = serializePresets(list)
            preferences[KEY_ACTIVE_PRESET_ID] = preset.id
        }
    }

    /**
     * Menghapus preset berdasarkan ID.
     */
    suspend fun deletePreset(id: String) {
        dataStore.edit { preferences ->
            val currentJson = preferences[KEY_PRESETS_JSON] ?: "[]"
            val list = parsePresets(currentJson).filter { it.id != id }
            preferences[KEY_PRESETS_JSON] = serializePresets(list)
            if (preferences[KEY_ACTIVE_PRESET_ID] == id) {
                preferences.remove(KEY_ACTIVE_PRESET_ID)
            }
        }
    }

    /**
     * Mencari preset yang tertaut dengan nama package aplikasi tertentu.
     */
    suspend fun getPresetByPackage(packageName: String): CalibrationPreset? {
        val presets = getAllPresets().first()
        return presets.find { it.packageName.equals(packageName, ignoreCase = true) }
    }

    /**
     * Mengambil ID preset yang sedang aktif.
     */
    fun getActivePresetId(): Flow<String?> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_ACTIVE_PRESET_ID]
            }

    /**
     * Memperbarui koordinat preset aktif atau membuat preset default baru.
     */
    suspend fun updateActivePresetCoordinates(x: Float, y: Float, size: Float) {
        dataStore.edit { preferences ->
            val activeId = preferences[KEY_ACTIVE_PRESET_ID]
            val currentJson = preferences[KEY_PRESETS_JSON] ?: "[]"
            val list = parsePresets(currentJson).toMutableList()

            val index = if (activeId != null) list.indexOfFirst { it.id == activeId } else 0
            if (index in list.indices) {
                val current = list[index]
                list[index] = current.copy(x = x, y = y, width = size, height = size)
                preferences[KEY_PRESETS_JSON] = serializePresets(list)
                preferences[KEY_ACTIVE_PRESET_ID] = list[index].id
            } else {
                val newPreset = CalibrationPreset(
                    id = "preset_default",
                    name = "Preset Utama",
                    packageName = null,
                    x = x,
                    y = y,
                    width = size,
                    height = size
                )
                list.add(newPreset)
                preferences[KEY_PRESETS_JSON] = serializePresets(list)
                preferences[KEY_ACTIVE_PRESET_ID] = newPreset.id
            }
        }
    }

    /**
     * Menandai preset tertentu sebagai aktif.
     */
    suspend fun setActivePresetId(id: String?) {
        dataStore.edit { preferences ->
            if (id != null) {
                preferences[KEY_ACTIVE_PRESET_ID] = id
            } else {
                preferences.remove(KEY_ACTIVE_PRESET_ID)
            }
        }
    }

    private fun parsePresets(jsonStr: String): List<CalibrationPreset> {
        val result = mutableListOf<CalibrationPreset>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val preset = CalibrationPreset(
                    id = obj.optString("id"),
                    name = obj.optString("name", "Preset #${i + 1}"),
                    packageName = if (obj.has("packageName") && !obj.isNull("packageName")) obj.getString("packageName") else null,
                    x = obj.optDouble("x", 0.0).toFloat(),
                    y = obj.optDouble("y", 0.0).toFloat(),
                    width = obj.optDouble("width", 600.0).toFloat(),
                    height = obj.optDouble("height", 600.0).toFloat(),
                    isFlipped = obj.optBoolean("isFlipped", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
                result.add(preset)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun serializePresets(list: List<CalibrationPreset>): String {
        val array = JSONArray()
        for (p in list) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            if (p.packageName != null) {
                obj.put("packageName", p.packageName)
            } else {
                obj.put("packageName", JSONObject.NULL)
            }
            obj.put("x", p.x.toDouble())
            obj.put("y", p.y.toDouble())
            obj.put("width", p.width.toDouble())
            obj.put("height", p.height.toDouble())
            obj.put("isFlipped", p.isFlipped)
            obj.put("createdAt", p.createdAt)
            array.put(obj)
        }
        return array.toString()
    }
}
