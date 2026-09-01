package com.chessbeater.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object AppLogger {
    private val executor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private var logFile: File? = null

    fun init(context: Context) {
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()
        logFile = File(logDir, "chessbeater_master.log")
        log("SYSTEM", "🚀 AppLogger diinisialisasi. Perangkat: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
    }

    fun log(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedLog = "[$timestamp] [$tag] $message"

        // Cetak juga ke Android Logcat
        Log.d("ChessBeaterLog", formattedLog)

        executor.execute {
            try {
                logFile?.let { file ->
                    FileWriter(file, true).use { writer ->
                        writer.appendLine(formattedLog)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChessBeaterLog", "Gagal menulis log ke file: ${e.message}")
            }
        }
    }

    fun exportLogToUri(context: Context, destinationUri: Uri): Boolean {
        return try {
            val contentResolver = context.contentResolver
            val outputStream = contentResolver.openOutputStream(destinationUri) ?: return false

            outputStream.bufferedWriter().use { writer ->
                writer.write("=====================================================\n")
                writer.write("♟️ CHESS BEATER - COMPLETE AUDIT ACTIVITY LOG\n")
                writer.write("📅 Waktu Ekspor : ${dateFormat.format(Date())}\n")
                writer.write("📱 Perangkat    : ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})\n")
                writer.write("=====================================================\n\n")

                logFile?.let { file ->
                    if (file.exists()) {
                        file.forEachLine { line ->
                            writer.write(line)
                            writer.newLine()
                        }
                    }
                }
            }
            log("STORAGE", "💾 Seluruh log berhasil diekspor ke URI: $destinationUri")
            true
        } catch (e: Exception) {
            log("STORAGE", "❌ Gagal mengekspor file log: ${e.message}")
            false
        }
    }

    fun clearLogs() {
        executor.execute {
            try {
                logFile?.writeText("")
                log("STORAGE", "🧹 File log lokal berhasil dibersihkan.")
            } catch (e: Exception) {
                Log.e("ChessBeaterLog", "Gagal membersihkan log: ${e.message}")
            }
        }
    }
}
