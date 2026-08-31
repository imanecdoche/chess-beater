package com.chessbeater.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

data class SessionLogInfo(
    val file: File,
    val fileName: String,
    val formattedDate: String,
    val sizeText: String,
    val moveCount: Int
)

object SessionLogger {
    private const val TAG = "SessionLogger"
    private const val PREFS_NAME = "chessbeater_logging_prefs"
    private const val KEY_ENABLE_LOGS = "save_session_logs"

    private var currentSessionFile: File? = null
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", Locale("id", "ID"))
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun isLoggingEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLE_LOGS, false)
    }

    fun setLoggingEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLE_LOGS, enabled)
            .apply()
        if (enabled && currentSessionFile == null) {
            startNewSession(context)
        }
    }

    @Synchronized
    fun startNewSession(context: Context): File? {
        try {
            val logsDir = File(context.filesDir, "logs").apply { mkdirs() }
            val timeStamp = fileDateFormat.format(Date())
            val file = File(logsDir, "session_$timeStamp.log")
            currentSessionFile = file

            FileWriter(file, true).use { writer ->
                writer.append("=== CHESS BEATER SESSION LOG ===\n")
                writer.append("Waktu Mulai: ${dateFormat.format(Date())}\n")
                writer.append("Perangkat: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})\n")
                writer.append("-------------------------------------------\n\n")
            }
            Log.i(TAG, "New session log created: ${file.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session log file", e)
            return null
        }
    }

    @Synchronized
    fun logEvent(context: Context, tag: String, message: String) {
        if (!isLoggingEnabled(context)) return
        try {
            val file = currentSessionFile ?: startNewSession(context) ?: return
            val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            FileWriter(file, true).use { writer ->
                writer.append("[$timeStr] [$tag] $message\n")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error writing event log", e)
        }
    }

    @Synchronized
    fun logMove(
        context: Context,
        moveNumber: Int,
        fromTo: String,
        player: String,
        fen: String,
        bestMove: String?,
        eval: String?
    ) {
        if (!isLoggingEnabled(context)) return
        try {
            val file = currentSessionFile ?: startNewSession(context) ?: return
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            FileWriter(file, true).use { writer ->
                writer.append("-------------------------------------------\n")
                writer.append("Langkah #$moveNumber | $player | $fromTo | $timeStr\n")
                writer.append("FEN: $fen\n")
                if (!bestMove.isNullOrBlank()) writer.append("Engine BestMove: $bestMove\n")
                if (!eval.isNullOrBlank()) writer.append("Eval: $eval\n")
                writer.append("\n")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error logging chess move", e)
        }
    }

    fun getAllLogs(context: Context): List<SessionLogInfo> {
        val logsDir = File(context.filesDir, "logs")
        if (!logsDir.exists()) return emptyList()

        return logsDir.listFiles { f -> f.extension == "log" || f.extension == "txt" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val sizeKb = (file.length() + 1023) / 1024
                val formattedDate = dateFormat.format(Date(file.lastModified()))
                var moveCount = 0
                try {
                    file.forEachLine { line ->
                        if (line.startsWith("Langkah #")) moveCount++
                    }
                } catch (ignored: Exception) {}

                SessionLogInfo(
                    file = file,
                    fileName = file.name,
                    formattedDate = formattedDate,
                    sizeText = "$sizeKb KB",
                    moveCount = moveCount
                )
            } ?: emptyList()
    }

    fun readLogContent(file: File): String {
        return try {
            if (file.exists()) file.readText() else "File log tidak ditemukan."
        } catch (e: Exception) {
            "Gagal membaca log: ${e.localizedMessage}"
        }
    }

    fun deleteLog(file: File): Boolean {
        return try {
            if (currentSessionFile?.absolutePath == file.absolutePath) {
                currentSessionFile = null
            }
            file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete log file", e)
            false
        }
    }

    fun clearAllLogs(context: Context): Boolean {
        val logsDir = File(context.filesDir, "logs")
        currentSessionFile = null
        return logsDir.deleteRecursively().also { logsDir.mkdirs() }
    }
}
