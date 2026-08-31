package com.chessbeater.engine.models

/**
 * Engine types supported by Chess Beater
 */
enum class EngineType {
    STOCKFISH,
    LC0_ALPHAZERO,
    DEEP_BLUE_CLASSIC
}

/**
 * Dynamic Engine Configuration & Power Calibration
 * Mapped directly from PRD Section 4.2
 */
data class EngineConfig(
    val engineType: EngineType = EngineType.STOCKFISH,
    val powerPercentage: Int = 100 // 0 to 100%
) {
    init {
        require(powerPercentage in 0..100) { "powerPercentage must be between 0 and 100" }
    }

    /**
     * Converts the power level into specific UCI parameter configuration commands.
     */
    fun toUciCommands(): List<String> {
        val uciCommands = mutableListOf<String>()

        when (engineType) {
            EngineType.STOCKFISH -> {
                // Skala Elo: 800 (Power 0%) s/d 3500 (Power 100%)
                val targetElo = 800 + ((powerPercentage / 100.0) * 2700).toInt()
                val skillLevel = ((powerPercentage / 100.0) * 20).toInt() // 0 - 20
                val searchDepth = 1 + ((powerPercentage / 100.0) * 24).toInt() // Depth 1 - 25
                val moveTimeMs = 50 + ((powerPercentage / 100.0) * 1950).toInt() // 50ms - 2000ms

                uciCommands.add("setoption name UCI_LimitStrength value true")
                uciCommands.add("setoption name UCI_Elo value $targetElo")
                uciCommands.add("setoption name Skill Level value $skillLevel")
                uciCommands.add("setoption name Threads value 2")
                uciCommands.add("setoption name Hash value 32")

                // Error Injection untuk Elo rendah (< 1500 / power < 30%)
                if (powerPercentage < 30) {
                    val maxError = 200 - (powerPercentage * 5)
                    uciCommands.add("setoption name Skill Level Maximum Error value $maxError")
                }
            }
            EngineType.LC0_ALPHAZERO -> {
                val playouts = (10 + (powerPercentage * 15)) // 10 to 1510 playouts
                uciCommands.add("setoption name NNCacheSize value 200000")
                uciCommands.add("setoption name MiniBatchSize value 16")
                uciCommands.add("setoption name Nodes value $playouts")
            }
            EngineType.DEEP_BLUE_CLASSIC -> {
                val depth = 1 + ((powerPercentage / 100.0) * 12).toInt() // Depth 1 - 13
                uciCommands.add("setoption name ClassicDepth value $depth")
            }
        }
        return uciCommands
    }

    /**
     * Calculates the 'go' command search constraints (e.g. depth / movetime)
     */
    fun toGoCommand(): String {
        return when (engineType) {
            EngineType.STOCKFISH -> {
                val searchDepth = 1 + ((powerPercentage / 100.0) * 24).toInt()
                val moveTimeMs = 50 + ((powerPercentage / 100.0) * 1950).toInt()
                "go depth $searchDepth movetime $moveTimeMs"
            }
            EngineType.LC0_ALPHAZERO -> {
                val nodes = (10 + (powerPercentage * 15))
                "go nodes $nodes"
            }
            EngineType.DEEP_BLUE_CLASSIC -> {
                val depth = 1 + ((powerPercentage / 100.0) * 12).toInt()
                "go depth $depth"
            }
        }
    }
}

/**
 * Result returned after engine completes position evaluation
 */
data class EngineResult(
    val bestMove: String,                // e.g., "e2e4" or "d1h5"
    val ponderMove: String? = null,      // e.g., "e7e5"
    val evaluationCentipawns: Int? = null, // +65 means +0.65 advantage for White
    val mateInMoves: Int? = null,        // positive for winning mate, negative for losing mate
    val depth: Int = 0,
    val calculationTimeMs: Long = 0L
)

/**
 * Core interface for Chess Engine Bridges
 */
interface ChessEngineBridge {
    suspend fun initializeEngine(): Boolean
    suspend fun setStrength(config: EngineConfig)
    suspend fun evaluatePosition(fen: String): EngineResult
    suspend fun stopEvaluation()
    fun release()
}
