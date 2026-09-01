package com.chessbeater.engine

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Low-level JNI interface bridging Kotlin with the native C++ Stockfish engine.
 */
object StockfishNativeBridge {
    private const val TAG = "StockfishNativeBridge"
    private var isLibraryLoaded = false
    private val isEngineInitialized = AtomicBoolean(false)
    private val initLock = Any()

    init {
        try {
            System.loadLibrary("chessbeater_engine")
            isLibraryLoaded = true
            Log.i(TAG, "chessbeater_engine native library loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library 'chessbeater_engine' not found (running in JVM test or unsupported ABI): ${e.message}")
            isLibraryLoaded = false
        }
    }

    fun isNativeLoaded(): Boolean = isLibraryLoaded

    fun isInitialized(): Boolean = isEngineInitialized.get()

    fun initializeEngineSafely(): Boolean {
        if (!isLibraryLoaded) return false
        synchronized(initLock) {
            if (isEngineInitialized.get()) {
                Log.d(TAG, "ℹ️ Engine sudah terinisialisasi sebelumnya. Melewati nativeInitEngine.")
                return true
            }
            return try {
                val success = nativeInitEngine()
                if (success) {
                    isEngineInitialized.set(true)
                    Log.d(TAG, "✅ Stockfish Engine berhasil diinisialisasi untuk pertama kali.")
                }
                success
            } catch (e: Throwable) {
                Log.e(TAG, "❌ Gagal inisialisasi native Stockfish: ${e.message}")
                false
            }
        }
    }

    fun resetGameStateOnly() {
        if (!isEngineInitialized.get()) return
        try {
            nativeSendUciCommand("stop")
            nativeSendUciCommand("ucinewgame")
            nativeSendUciCommand("isready")
            Log.d(TAG, "🔄 State permainan di-reset via UCI ucinewgame (Tanpa re-init native).")
        } catch (e: Throwable) {
            Log.e(TAG, "Error saat resetGameStateOnly: ${e.message}")
        }
    }

    /**
     * Inisialisasi engine C++ dan memulai worker thread UCI.
     */
    external fun nativeInitEngine(): Boolean

    /**
     * Mengirim perintah UCI string ke pipa stdin engine C++.
     */
    external fun nativeSendUciCommand(command: String): Boolean

    /**
     * Membaca satu baris output dari antrean stdout engine C++ (non-blocking).
     * Mengembalikan null jika antrean kosong.
     */
    external fun nativeReadEngineOutput(): String?

    /**
     * Menghentikan kalkulasi pencarian engine yang sedang berjalan ("stop").
     */
    external fun nativeStopEvaluation()

    /**
     * Membersihkan resource dan mematikan thread engine C++.
     */
    external fun nativeDestroyEngine()
}
