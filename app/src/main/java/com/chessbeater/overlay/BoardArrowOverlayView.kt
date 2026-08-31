package com.chessbeater.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.chessbeater.vision.models.PlayerColor
import kotlin.math.*

/**
 * Transparent Overlay Canvas rendered directly over the chess board coordinates.
 * Renders smooth dynamic vector arrows (straight or curved for Knight moves)
 * with adaptive color theming according to PRD Section 5.3.
 */
@SuppressLint("ViewConstructor")
class BoardArrowOverlayView(context: Context) : View(context) {

    private var currentMove: String? = null // e.g. "e2e4" or "g1f3"
    private var boardRect: Rect? = null
    private var playerOrientation: PlayerColor = PlayerColor.WHITE
    private var arrowColor: Int = ArrowColorTheme.COLOR_BEST_MOVE

    private var animAlpha: Float = 1.0f
    private var alphaAnimator: ValueAnimator? = null

    // Preallocated drawing objects for 60fps rendering without allocations in onDraw
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val originDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(180, 0, 0, 0)
    }

    private val arrowPath = Path()
    private val headPath = Path()

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    /**
     * Updates the active arrow with evaluation color and triggers smooth fade-in animation
     */
    fun updateArrow(
        bestMove: String?,
        evalCentipawns: Int?,
        mateInMoves: Int?,
        boundingRect: Rect?,
        orientation: PlayerColor = PlayerColor.WHITE,
        isAlternative: Boolean = false
    ) {
        if (bestMove == null || bestMove.length < 4 || boundingRect == null) {
            clearArrow()
            return
        }

        val color = ArrowColorTheme.getColorForEvaluation(evalCentipawns, mateInMoves, isAlternative)
        val isNewMove = (this.currentMove != bestMove || this.boardRect != boundingRect)

        this.currentMove = bestMove
        this.boardRect = boundingRect
        this.playerOrientation = orientation
        this.arrowColor = color

        if (isNewMove) {
            alphaAnimator?.cancel()
            alphaAnimator = ValueAnimator.ofFloat(0.3f, 1.0f).apply {
                duration = 180L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    animAlpha = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            invalidate()
        }
    }

    fun clearArrow() {
        currentMove = null
        boardRect = null
        alphaAnimator?.cancel()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val move = currentMove ?: return
        val rect = boardRect ?: return
        if (move.length < 4 || rect.width() <= 0 || rect.height() <= 0) return

        val fromSquare = move.substring(0, 2)
        val toSquare = move.substring(2, 4)

        val fromPoint = squareToCoordinates(fromSquare, rect, playerOrientation) ?: return
        val toPoint = squareToCoordinates(toSquare, rect, playerOrientation) ?: return

        val squareSize = rect.width() / 8.0f
        val strokeWidth = squareSize * 0.16f
        val headSize = squareSize * 0.42f
        val originRadius = strokeWidth * 0.9f

        val alphaInt = (animAlpha * 230).toInt().coerceIn(0, 255)
        arrowPaint.color = arrowColor
        arrowPaint.alpha = alphaInt
        arrowPaint.strokeWidth = strokeWidth

        headPaint.color = arrowColor
        headPaint.alpha = alphaInt

        originDotPaint.color = arrowColor
        originDotPaint.alpha = alphaInt

        outlinePaint.strokeWidth = strokeWidth + 4.0f
        outlinePaint.alpha = (animAlpha * 140).toInt().coerceIn(0, 255)

        val isKnightMove = isKnightJump(fromSquare, toSquare)

        // Draw Origin Circle
        canvas.drawCircle(fromPoint.x, fromPoint.y, originRadius + 2f, outlinePaint)
        canvas.drawCircle(fromPoint.x, fromPoint.y, originRadius, originDotPaint)

        arrowPath.reset()
        headPath.reset()

        if (isKnightMove) {
            drawKnightArrow(canvas, fromPoint, toPoint, strokeWidth, headSize, squareSize)
        } else {
            drawStraightArrow(canvas, fromPoint, toPoint, strokeWidth, headSize)
        }
    }

    private fun drawStraightArrow(
        canvas: Canvas,
        from: PointF,
        to: PointF,
        strokeWidth: Float,
        headSize: Float
    ) {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        if (distance < headSize) return

        val angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
        // Pull back endpoint so arrowhead tip touches center of destination square
        val shaftEndX = to.x - cos(angle) * (headSize * 0.65f)
        val shaftEndY = to.y - sin(angle) * (headSize * 0.65f)

        // Draw Outline
        canvas.drawLine(from.x, from.y, shaftEndX, shaftEndY, outlinePaint)
        // Draw Shaft
        canvas.drawLine(from.x, from.y, shaftEndX, shaftEndY, arrowPaint)

        // Construct Arrowhead polygon
        val headAngle = Math.toRadians(32.0).toFloat()
        val leftX = to.x - headSize * cos(angle - headAngle)
        val leftY = to.y - headSize * sin(angle - headAngle)
        val rightX = to.x - headSize * cos(angle + headAngle)
        val rightY = to.y - headSize * sin(angle + headAngle)

        headPath.moveTo(to.x, to.y)
        headPath.lineTo(leftX, leftY)
        headPath.lineTo(to.x - headSize * 0.35f * cos(angle), to.y - headSize * 0.35f * sin(angle))
        headPath.lineTo(rightX, rightY)
        headPath.close()

        canvas.drawPath(headPath, outlinePaint)
        canvas.drawPath(headPath, headPaint)
    }

    private fun drawKnightArrow(
        canvas: Canvas,
        from: PointF,
        to: PointF,
        strokeWidth: Float,
        headSize: Float,
        squareSize: Float
    ) {
        // Smooth curved bezier path for Knight moves
        val midX = (from.x + to.x) / 2f
        val midY = (from.y + to.y) / 2f

        // Perpendicular offset for organic knight arc
        val dx = to.x - from.x
        val dy = to.y - from.y
        val controlOffsetX = -dy * 0.28f
        val controlOffsetY = dx * 0.28f

        val ctrlX = midX + controlOffsetX
        val ctrlY = midY + controlOffsetY

        // Angle at the destination for the arrowhead
        val endAngle = atan2((to.y - ctrlY).toDouble(), (to.x - ctrlX).toDouble()).toFloat()

        arrowPath.moveTo(from.x, from.y)
        arrowPath.quadTo(ctrlX, ctrlY, to.x - cos(endAngle) * (headSize * 0.65f), to.y - sin(endAngle) * (headSize * 0.65f))

        canvas.drawPath(arrowPath, outlinePaint)
        canvas.drawPath(arrowPath, arrowPaint)

        // Draw Arrowhead
        val headAngle = Math.toRadians(32.0).toFloat()
        val leftX = to.x - headSize * cos(endAngle - headAngle)
        val leftY = to.y - headSize * sin(endAngle - headAngle)
        val rightX = to.x - headSize * cos(endAngle + headAngle)
        val rightY = to.y - headSize * sin(endAngle + headAngle)

        headPath.moveTo(to.x, to.y)
        headPath.lineTo(leftX, leftY)
        headPath.lineTo(to.x - headSize * 0.35f * cos(endAngle), to.y - headSize * 0.35f * sin(endAngle))
        headPath.lineTo(rightX, rightY)
        headPath.close()

        canvas.drawPath(headPath, outlinePaint)
        canvas.drawPath(headPath, headPaint)
    }

    private fun isKnightJump(fromSquare: String, toSquare: String): Boolean {
        val fileDiff = abs(fromSquare[0] - toSquare[0])
        val rankDiff = abs(fromSquare[1] - toSquare[1])
        return (fileDiff == 1 && rankDiff == 2) || (fileDiff == 2 && rankDiff == 1)
    }

    companion object {
        /**
         * Transforms algebraic square notation (e.g. "e4") to canvas pixel coordinate (center of square)
         */
        fun notationToScreenPoint(pos: String, boardRect: Rect, isFlipped: Boolean): PointF? {
            if (pos.length < 2) return null
            val fileChar = pos[0].lowercaseChar()
            val rankChar = pos[1]
            if (fileChar !in 'a'..'h' || rankChar !in '1'..'8') return null

            val col = fileChar - 'a'      // 0..7
            val rank = rankChar - '1'     // 0..7 (0=rank 1, 7=rank 8)

            val sqW = boardRect.width() / 8f
            val sqH = boardRect.height() / 8f

            val displayCol = if (isFlipped) 7 - col else col
            val displayRow = if (isFlipped) rank else 7 - rank // Normal: Rank 1 berada di displayRow 7 (bawah)

            val x = boardRect.left + (displayCol + 0.5f) * sqW
            val y = boardRect.top + (displayRow + 0.5f) * sqH
            return PointF(x, y)
        }

        fun squareToCoordinates(
            square: String,
            boardRect: Rect,
            orientation: PlayerColor
        ): PointF? {
            return notationToScreenPoint(square, boardRect, isFlipped = (orientation == PlayerColor.BLACK))
        }
    }
}

