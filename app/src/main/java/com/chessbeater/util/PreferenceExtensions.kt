package com.chessbeater.util

import android.content.SharedPreferences

fun SharedPreferences.getSafeFloat(key: String, defaultValue: Float): Float {
    return try {
        this.getFloat(key, defaultValue)
    } catch (e: Exception) {
        try {
            this.getInt(key, defaultValue.toInt()).toFloat()
        } catch (e2: Exception) {
            try {
                this.getString(key, null)?.toFloatOrNull() ?: defaultValue
            } catch (e3: Exception) {
                defaultValue
            }
        }
    }
}

fun SharedPreferences.getSafeInt(key: String, defaultValue: Int): Int {
    return try {
        this.getInt(key, defaultValue)
    } catch (e: Exception) {
        try {
            this.getFloat(key, defaultValue.toFloat()).toInt()
        } catch (e2: Exception) {
            try {
                this.getString(key, null)?.toIntOrNull() ?: defaultValue
            } catch (e3: Exception) {
                defaultValue
            }
        }
    }
}

fun SharedPreferences.getSafeBoolean(key: String, defaultValue: Boolean): Boolean {
    return try {
        this.getBoolean(key, defaultValue)
    } catch (e: Exception) {
        try {
            this.getString(key, null)?.toBooleanStrictOrNull() ?: defaultValue
        } catch (e2: Exception) {
            defaultValue
        }
    }
}

fun SharedPreferences.getSafeString(key: String, defaultValue: String): String {
    return try {
        this.getString(key, defaultValue) ?: defaultValue
    } catch (e: Exception) {
        defaultValue
    }
}
