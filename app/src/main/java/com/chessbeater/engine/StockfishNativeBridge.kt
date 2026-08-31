package com.chessbeater.engine

import android.util.Log

/**
 * Low-level JNI interface bridging Kotlin with the native C++ Stockfish engine.
 */
object StockfishNativeBridge {
    private const val TAG = "StockfishNativeBridge"
    private var isLibraryLoaded = false

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
