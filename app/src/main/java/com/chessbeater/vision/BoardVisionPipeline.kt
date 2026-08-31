package com.chessbeater.vision

import android.graphics.Bitmap
import com.chessbeater.vision.models.PieceClass
import com.chessbeater.vision.models.PlayerColor
import com.chessbeater.vision.models.VisionResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance Vision Orchestration Pipeline.
 * Converts raw screen capture Bitmaps into valid Chess FEN strings in under 50ms.
 */
class BoardVisionPipeline(
    private val boardDetector: OpenCvBoardDetector = OpenCvBoardDetector(),
    private val squareExtractor: SquareExtractor = SquareExtractor(),
    private val pieceClassifier: TfLitePieceClassifier = TfLitePieceClassifier(),
    private val fenAssembler: FenAssembler = FenAssembler(),
    private val edgeCaseHandler: com.chessbeater.vision.edgecase.BoardEdgeCaseHandler = com.chessbeater.vision.edgecase.BoardEdgeCaseHandler(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private var lastValidFen: String = ""
    private var lastPieceMatrix: Array<Array<PieceClass>> = Array(8) { Array(8) { PieceClass.EMPTY } }
    private var hasInitialInferenceRun: Boolean = false
    private var frameCount: Long = 0L

    /**
     * Executes the complete Vision Pipeline:
     * Raw Frame Bitmap -> Board Detection -> Slicing -> Frame Diff -> Edge-Case Filter -> Inference -> FEN
     */
    suspend fun processFrame(
        frameBitmap: Bitmap,
        appProfile: com.chessbeater.vision.models.ChessAppProfile = com.chessbeater.vision.models.ChessAppProfile(),
        overridePlayerOrientation: PlayerColor? = null
    ): VisionResult = withContext(dispatcher) {

        val startTime = System.currentTimeMillis()
        frameCount++

        try {
            // Step 1: Detect Board & Warp Perspective (with physical screen coordinate mapping)
            val localization = boardDetector.locateAndExtractBoard(frameBitmap, appProfile)
            if (localization == null) {
                val latency = System.currentTimeMillis() - startTime
                return@withContext VisionResult(
                    fen = if (lastValidFen.isNotBlank()) lastValidFen else "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                    boardBoundingRect = null,
                    playerOrientation = PlayerColor.WHITE,
                    changedSquares = emptyList(),
                    pieceMatrix = lastPieceMatrix,
                    latencyMs = latency,
                    isPositionChanged = false,
                    isBoardDetected = false
                )
            }

            // Step 2: Slicing into 64 sub-petak
            val rawSquareBitmaps = squareExtractor.slice64Squares(
                localization.warpedBoardBitmap,
                localization.squareGrid
            )

            // Step 3: Frame Difference Change Detection
            val changedSquares = squareExtractor.detectChangedSquares(
                rawSquareBitmaps,
                localization.playerOrientation
            )

            // Run neural classification on first frame, whenever any square changes, or periodically every 4 frames as sync watchdog
            val shouldRunInference = !hasInitialInferenceRun ||
                    changedSquares.isNotEmpty() ||
                    (frameCount % 4L == 0L) ||
                    lastValidFen.isEmpty()

            val pieceMatrix = if (shouldRunInference) {
                // Apply Edge-Case Filters: Highlight stripping & Adaptive Contrast Normalization
                val cleanSquareBitmaps = rawSquareBitmaps.map { squareBmp ->
                    val desaturated = edgeCaseHandler.filterPlatformHighlights(squareBmp)
                    val normalized = edgeCaseHandler.normalizeContrastAdaptive(desaturated)
                    if (desaturated != squareBmp && desaturated != normalized && !desaturated.isRecycled) {
                        desaturated.recycle()
                    }
                    normalized
                }

                // Step 4: Batch TFLite Piece Classification (64 squares)
                val matrix = pieceClassifier.classify64Squares(cleanSquareBitmaps)
                cleanSquareBitmaps.forEach { if (!it.isRecycled) it.recycle() }
                matrix
            } else {
                lastPieceMatrix
            }

            rawSquareBitmaps.forEach { if (!it.isRecycled) it.recycle() }
            if (!localization.warpedBoardBitmap.isRecycled) {
                localization.warpedBoardBitmap.recycle()
            }


            val pieceBasedOrientation = run {
                var whiteBottom = 0
                var blackBottom = 0
                for (r in 6..7) {
                    for (c in 0 until 8) {
                        val p = pieceMatrix[r][c]
                        if (p.isWhite) whiteBottom++
                        else if (p.isBlack) blackBottom++
                    }
                }
                if (whiteBottom == 0 && blackBottom == 0) localization.playerOrientation
                else if (whiteBottom >= blackBottom) PlayerColor.WHITE else PlayerColor.BLACK
            }

            val effectiveOrientation = overridePlayerOrientation ?: pieceBasedOrientation

            // Step 5: Assemble Valid FEN String
            val fen = if (shouldRunInference) {
                fenAssembler.assembleFen(pieceMatrix, effectiveOrientation)
            } else {
                lastValidFen
            }


            val isFenActuallyChanged = (lastValidFen.isNotEmpty() && fen != lastValidFen) || !hasInitialInferenceRun

            hasInitialInferenceRun = true
            lastValidFen = fen
            lastPieceMatrix = pieceMatrix

            val totalLatency = System.currentTimeMillis() - startTime

            VisionResult(
                fen = fen,
                boardBoundingRect = localization.boardBoundingRect, // Physical Screen Rect
                playerOrientation = effectiveOrientation,
                changedSquares = changedSquares,
                pieceMatrix = pieceMatrix,
                latencyMs = totalLatency,
                isPositionChanged = isFenActuallyChanged,
                isBoardDetected = true
            )

        } catch (e: Exception) {
            android.util.Log.e("BoardVisionPipeline", "Exception in processFrame", e)
            val totalLatency = System.currentTimeMillis() - startTime
            VisionResult(
                fen = if (lastValidFen.isNotBlank()) lastValidFen else "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                boardBoundingRect = null,
                playerOrientation = PlayerColor.WHITE,
                changedSquares = emptyList(),
                pieceMatrix = lastPieceMatrix,
                latencyMs = totalLatency,
                isPositionChanged = false,
                isBoardDetected = false
            )
        }
    }

    /**
     * Resets internal pipeline state and caches
     */
    fun reset() {
        squareExtractor.resetCache()
        fenAssembler.resetState()
        hasInitialInferenceRun = false
        lastValidFen = ""
        frameCount = 0L
        lastPieceMatrix = Array(8) { Array(8) { PieceClass.EMPTY } }
    }
}
