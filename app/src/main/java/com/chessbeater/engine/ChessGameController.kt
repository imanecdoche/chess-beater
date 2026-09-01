package com.chessbeater.engine

import android.content.Context
import android.util.Log

/**
 * Controller managing chess game state, target ELO power synchronization, and engine calculations.
 */
class ChessGameController(
    private val context: Context,
    private val stockfishBridge: StockfishBridge = StockfishBridge.getInstance(context)
) {
    interface EngineTurnListener {
        fun onEngineTurnCompleted()
        fun onEngineMoveExecuted(from: String, to: String, promotion: String?) {}
        fun onGameStateChanged(newFen: String, isUserTurn: Boolean, isFlipped: Boolean) {}
    }

    var serviceCallback: EngineTurnListener? = null

    var targetElo: Int = EngineSettingsManager.getTargetElo(context)
        private set

    var isBulletMode: Boolean = EngineSettingsManager.isBulletMode(context)
        private set

    var currentFen: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    var currentTurn: String = "WHITE"
    var userSide: String = "WHITE"
    var engineSide: String = "BLACK"

    init {
        syncTargetEloFromPreferences()
    }

    fun syncTargetEloFromPreferences(): Int {
        targetElo = EngineSettingsManager.getTargetElo(context)
        isBulletMode = EngineSettingsManager.isBulletMode(context)
        Log.d("ChessGameController", "🎯 Synced targetElo from SharedPreferences: $targetElo | isBullet: $isBulletMode")
        stockfishBridge.applyEloConfiguration(targetElo)
        return targetElo
    }

    fun setTargetElo(newElo: Int) {
        val clamped = newElo.coerceIn(800, 3500)
        targetElo = clamped
        EngineSettingsManager.saveTargetElo(context, clamped)
        stockfishBridge.applyEloConfiguration(clamped)
    }

    fun setBulletMode(enabled: Boolean) {
        isBulletMode = enabled
        EngineSettingsManager.saveBulletMode(context, enabled)
    }

    fun triggerEvaluation(fen: String, onResult: (String) -> Unit) {
        currentFen = fen
        stockfishBridge.triggerEvaluation(fen, targetElo, isBulletMode) { bestMove ->
            onResult(bestMove)
        }
    }

    fun handleEngineMove(from: String, to: String, promotion: String? = null) {
        if (currentTurn != engineSide) {
            Log.w("ChessController", "⚠️ Abaikan langkah mesin: bukan giliran engine (Turn: $currentTurn, Engine:$engineSide)")
            return
        }

        Log.d("StockfishNative", "Move executed: 🤖 $from➔$to | Castling & FEN updating...")

        // 1. Gambar panah petunjuk langkah mesin
        serviceCallback?.onEngineMoveExecuted(from, to, promotion)

        // 2. Perbarui FEN catur (geser bidak dari -> ke)
        currentFen = ChessFenUtils.updateFenAfterMove(currentFen, from, to, promotion)
        com.chessbeater.utils.AppLogger.log("GAME_FLOW", "🤖 Engine Move: $from➔$to (Promo: $promotion) | FEN: $currentFen")

        // 3. Alihkan giliran ke User
        currentTurn = userSide
        serviceCallback?.onGameStateChanged(currentFen, isUserTurn = true, isFlipped = (userSide == "BLACK"))

        // 4. Picu timer Auto-Hide (jika aktif)
        onEngineMoveFinished()
    }

    // Panggil listener onEngineTurnCompleted HANYA setelah Stockfish bestMove dijalankan di FEN
    fun onEngineMoveFinished() {
        serviceCallback?.onEngineTurnCompleted()
    }
}
