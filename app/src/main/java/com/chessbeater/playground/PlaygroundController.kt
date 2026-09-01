package com.chessbeater.playground

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.chessbeater.engine.ChessFenUtils
import com.chessbeater.engine.ChessLogic
import com.chessbeater.engine.ChessMoveValidator
import com.chessbeater.engine.StockfishBridge

enum class PlayMode { USER_VS_ENGINE, ENGINE_VS_ENGINE, BOARD_EDITOR }

class PlaygroundController(
    private val stockfishBridge: StockfishBridge,
    private val onStateUpdated: (fen: String, turn: String, isFinished: Boolean) -> Unit,
    private val onEvalUpdated: (evalCp: Int, bestMove: String) -> Unit
) {
    companion object {
        private const val TAG = "PlaygroundController"
    }

    var playMode = PlayMode.USER_VS_ENGINE
    var currentFen = ChessFenUtils.INITIAL_FEN
    var whiteElo = 2000
    var blackElo = 2800
    var userSide = "WHITE"
    var botVsBotDelayMs = 1000L
    var isBotMatchRunning = false
    var lastBestMove = ""

    private val loopHandler = Handler(Looper.getMainLooper())

    fun loadFen(fen: String) {
        val trimmed = fen.trim()
        if (trimmed.isNotBlank()) {
            currentFen = trimmed
            lastBestMove = ""
            notifyState()
            evaluateCurrentPosition()
        }
    }

    fun resetMatch() {
        pauseBotVsBotMatch()
        currentFen = ChessFenUtils.INITIAL_FEN
        lastBestMove = ""
        notifyState()
        evaluateCurrentPosition()
    }

    fun setEditorPiece(squareIndex: Int, pieceChar: Char) {
        val board = ChessFenUtils.fenToBoardArray(currentFen)
        if (squareIndex in 0..63) {
            board[squareIndex] = pieceChar
            val turn = if (currentFen.contains(" w ")) "WHITE" else "BLACK"
            currentFen = ChessFenUtils.boardArrayToFen(board, turn)
            notifyState()
        }
    }

    fun clearBoard() {
        val emptyBoard = CharArray(64) { '.' }
        currentFen = ChessFenUtils.boardArrayToFen(emptyBoard, "WHITE")
        notifyState()
    }

    fun tryUserMove(from: String, to: String): Boolean {
        if (playMode != PlayMode.USER_VS_ENGINE) return false
        val turn = if (currentFen.contains(" w ")) "WHITE" else "BLACK"
        if (turn != userSide) return false

        if (!ChessMoveValidator.isLegal(currentFen, from, to, turn)) {
            Log.w(TAG, "Langkah ilegal dari $from ke $to untuk giliran $turn")
            return false
        }

        currentFen = ChessFenUtils.updateFenAfterMove(currentFen, from, to)
        notifyState()
        triggerEngineResponse()
        return true
    }

    fun triggerEngineResponse() {
        val turn = if (currentFen.contains(" w ")) "WHITE" else "BLACK"
        val targetElo = if (turn == "WHITE") whiteElo else blackElo

        stockfishBridge.triggerEvaluation(currentFen, targetElo) { bestMove ->
            lastBestMove = bestMove
            onEvalUpdated(0, bestMove)
            if (bestMove.length >= 4) {
                val from = bestMove.substring(0, 2)
                val to = bestMove.substring(2, 4)
                currentFen = ChessFenUtils.updateFenAfterMove(currentFen, from, to)
                notifyState()
                evaluateCurrentPosition()
            }
        }
    }

    fun evaluateCurrentPosition() {
        val turn = if (currentFen.contains(" w ")) "WHITE" else "BLACK"
        val targetElo = if (turn == "WHITE") whiteElo else blackElo
        stockfishBridge.triggerEvaluation(currentFen, targetElo) { bestMove ->
            lastBestMove = bestMove
            onEvalUpdated(0, bestMove)
        }
    }

    fun startBotVsBotMatch() {
        if (isBotMatchRunning) return
        isBotMatchRunning = true
        runBotVsBotStep()
    }

    fun pauseBotVsBotMatch() {
        isBotMatchRunning = false
        loopHandler.removeCallbacksAndMessages(null)
    }

    private fun runBotVsBotStep() {
        if (!isBotMatchRunning) return

        val turn = if (currentFen.contains(" w ")) "WHITE" else "BLACK"
        val targetElo = if (turn == "WHITE") whiteElo else blackElo

        stockfishBridge.triggerEvaluation(currentFen, targetElo) { bestMove ->
            if (!isBotMatchRunning) return@triggerEvaluation
            lastBestMove = bestMove
            onEvalUpdated(0, bestMove)

            if (bestMove.length >= 4) {
                val from = bestMove.substring(0, 2)
                val to = bestMove.substring(2, 4)
                currentFen = ChessFenUtils.updateFenAfterMove(currentFen, from, to)
                notifyState()

                loopHandler.postDelayed({
                    runBotVsBotStep()
                }, botVsBotDelayMs)
            } else {
                // Game Over / No moves
                isBotMatchRunning = false
                notifyState(isFinished = true)
            }
        }
    }

    private fun notifyState(isFinished: Boolean = false) {
        val turn = if (currentFen.contains(" w ")) "WHITE" else "BLACK"
        onStateUpdated(currentFen, turn, isFinished)
    }
}
