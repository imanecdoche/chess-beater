package com.chessbeater.data

import java.util.UUID

/**
 * Model Data untuk Multi-Preset Kalibrasi Papan Catur dengan Penautan Paket Game.
 */
data class CalibrationPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val packageName: String? = null, // Contoh: "com.chess", "org.lichess.mobileapp"
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val isFlipped: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
