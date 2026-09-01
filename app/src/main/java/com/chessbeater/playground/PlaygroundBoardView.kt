package com.chessbeater.playground

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.chessbeater.R
import com.chessbeater.engine.ChessFenUtils
import com.chessbeater.engine.ChessLogic
import kotlin.math.min

class PlaygroundBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var isFlipped: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isEditorMode: Boolean = false
        set(value) {
            field = value
            selectedSquare = -1
            invalidate()
        }

    var selectedEditorPiece: Char = 'P'

    var onMoveAttempted: ((fromUci: String, toUci: String) -> Boolean)? = null
    var onEditorSquareClicked: ((squareIndex: Int, piece: Char) -> Unit)? = null

    private var boardArray: CharArray = ChessFenUtils.fenToBoardArray(ChessFenUtils.INITIAL_FEN)
    private var selectedSquare: Int = -1
    private var lastFromSquare: Int = -1
    private var lastToSquare: Int = -1
    private var bestMoveUci: String = ""

    // Dragging support
    private var isDragging = false
    private var dragSquare: Int = -1
    private var dragX = 0f
    private var dragY = 0f

    // Paints
    private val lightSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#334155") }
    private val darkSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E293B") }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6610B981")
        style = Paint.Style.FILL
    }
    private val selectedSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7738BDF8")
        style = Paint.Style.FILL
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E000E676")
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val arrowHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E000E676")
        style = Paint.Style.FILL
    }
    private val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val pieceBitmaps = HashMap<Char, Bitmap>()
    private var currentSquareSize = 0f
    private var boardRect = RectF()

    private val pieceDrawableMap = mapOf(
        'P' to R.drawable.piece_neo_wp,
        'N' to R.drawable.piece_neo_wn,
        'B' to R.drawable.piece_neo_wb,
        'R' to R.drawable.piece_neo_wr,
        'Q' to R.drawable.piece_neo_wq,
        'K' to R.drawable.piece_neo_wk,
        'p' to R.drawable.piece_neo_bp,
        'n' to R.drawable.piece_neo_bn,
        'b' to R.drawable.piece_neo_bb,
        'r' to R.drawable.piece_neo_br,
        'q' to R.drawable.piece_neo_bq,
        'k' to R.drawable.piece_neo_bk
    )

    fun setFen(fen: String) {
        boardArray = ChessFenUtils.fenToBoardArray(fen)
        invalidate()
    }

    fun setLastMove(fromUci: String, toUci: String) {
        lastFromSquare = ChessFenUtils.uciSquareToIndex(fromUci)
        lastToSquare = ChessFenUtils.uciSquareToIndex(toUci)
        invalidate()
    }

    fun setBestMoveArrow(uci: String) {
        bestMoveUci = uci
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = min(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        val left = (w - size) / 2f
        val top = (h - size) / 2f
        boardRect.set(left, top, left + size, top + size)
        currentSquareSize = size / 8f
        loadPieceBitmaps(currentSquareSize.toInt())
    }

    private fun loadPieceBitmaps(squareSize: Int) {
        if (squareSize <= 0) return
        pieceBitmaps.clear()
        for ((char, resId) in pieceDrawableMap) {
            val drawable = ContextCompat.getDrawable(context, resId) ?: continue
            val bmp = Bitmap.createBitmap(squareSize, squareSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, squareSize, squareSize)
            drawable.draw(canvas)
            pieceBitmaps[char] = bmp
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sqSize = currentSquareSize
        if (sqSize <= 0) return

        // 1. Draw Squares & Highlights
        for (r in 0..7) {
            for (c in 0..7) {
                val actualRow = if (isFlipped) 7 - r else r
                val actualCol = if (isFlipped) 7 - c else c
                val sqIdx = actualRow * 8 + actualCol

                val left = boardRect.left + c * sqSize
                val top = boardRect.top + r * sqSize
                val right = left + sqSize
                val bottom = top + sqSize

                val isLight = (r + c) % 2 == 0
                canvas.drawRect(left, top, right, bottom, if (isLight) lightSquarePaint else darkSquarePaint)

                // Highlight Last Move
                if (sqIdx == lastFromSquare || sqIdx == lastToSquare) {
                    canvas.drawRect(left, top, right, bottom, highlightPaint)
                }

                // Highlight Selected
                if (sqIdx == selectedSquare) {
                    canvas.drawRect(left, top, right, bottom, selectedSquarePaint)
                }

                // Coordinates
                if (c == 0) {
                    val rankText = (8 - actualRow).toString()
                    canvas.drawText(rankText, left + 6f, top + 30f, coordPaint)
                }
                if (r == 7) {
                    val fileText = ('a'.code + actualCol).toChar().toString()
                    canvas.drawText(fileText, right - 24f, bottom - 8f, coordPaint)
                }
            }
        }

        // 2. Draw Pieces (except the one being dragged)
        for (r in 0..7) {
            for (c in 0..7) {
                val actualRow = if (isFlipped) 7 - r else r
                val actualCol = if (isFlipped) 7 - c else c
                val sqIdx = actualRow * 8 + actualCol

                if (isDragging && sqIdx == dragSquare) continue

                val piece = boardArray[sqIdx]
                if (piece != '.' && piece != ' ') {
                    pieceBitmaps[piece]?.let { bmp ->
                        val left = boardRect.left + c * sqSize
                        val top = boardRect.top + r * sqSize
                        canvas.drawBitmap(bmp, left, top, null)
                    }
                }
            }
        }

        // 3. Draw Dragged Piece
        if (isDragging && dragSquare in 0..63) {
            val piece = boardArray[dragSquare]
            if (piece != '.' && piece != ' ') {
                pieceBitmaps[piece]?.let { bmp ->
                    val half = sqSize / 2f
                    canvas.drawBitmap(bmp, dragX - half, dragY - half, null)
                }
            }
        }

        // 4. Draw Best Move Arrow
        if (bestMoveUci.length >= 4) {
            val fromIdx = ChessFenUtils.uciSquareToIndex(bestMoveUci.substring(0, 2))
            val toIdx = ChessFenUtils.uciSquareToIndex(bestMoveUci.substring(2, 4))
            if (fromIdx != -1 && toIdx != -1) {
                drawArrow(canvas, fromIdx, toIdx)
            }
        }
    }

    private fun drawArrow(canvas: Canvas, fromIdx: Int, toIdx: Int) {
        val sqSize = currentSquareSize
        val fromRow = fromIdx / 8
        val fromCol = fromIdx % 8
        val toRow = toIdx / 8
        val toCol = toIdx % 8

        val startDisplayCol = if (isFlipped) 7 - fromCol else fromCol
        val startDisplayRow = if (isFlipped) 7 - fromRow else fromRow
        val endDisplayCol = if (isFlipped) 7 - toCol else toCol
        val endDisplayRow = if (isFlipped) 7 - toRow else toRow

        val startX = boardRect.left + startDisplayCol * sqSize + sqSize / 2f
        val startY = boardRect.top + startDisplayRow * sqSize + sqSize / 2f
        val endX = boardRect.left + endDisplayCol * sqSize + sqSize / 2f
        val endY = boardRect.top + endDisplayRow * sqSize + sqSize / 2f

        canvas.drawLine(startX, startY, endX, endY, arrowPaint)

        // Draw Arrowhead
        val angle = Math.atan2((endY - startY).toDouble(), (endX - startX).toDouble())
        val headLength = sqSize * 0.35f
        val headAngle = Math.PI / 6

        val x1 = endX - headLength * Math.cos(angle - headAngle).toFloat()
        val y1 = endY - headLength * Math.sin(angle - headAngle).toFloat()
        val x2 = endX - headLength * Math.cos(angle + headAngle).toFloat()
        val y2 = endY - headLength * Math.sin(angle + headAngle).toFloat()

        val path = Path().apply {
            moveTo(endX, endY)
            lineTo(x1, y1)
            lineTo(x2, y2)
            close()
        }
        canvas.drawPath(path, arrowHeadPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val sqSize = currentSquareSize
        if (sqSize <= 0) return super.onTouchEvent(event)

        val x = event.x
        val y = event.y

        val col = ((x - boardRect.left) / sqSize).toInt().coerceIn(0, 7)
        val row = ((y - boardRect.top) / sqSize).toInt().coerceIn(0, 7)
        val actualCol = if (isFlipped) 7 - col else col
        val actualRow = if (isFlipped) 7 - row else row
        val touchedSquare = actualRow * 8 + actualCol

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isEditorMode) {
                    onEditorSquareClicked?.invoke(touchedSquare, selectedEditorPiece)
                    invalidate()
                    return true
                }

                val piece = boardArray[touchedSquare]
                if (selectedSquare == -1) {
                    if (piece != '.' && piece != ' ') {
                        selectedSquare = touchedSquare
                        isDragging = true
                        dragSquare = touchedSquare
                        dragX = x
                        dragY = y
                        invalidate()
                    }
                } else {
                    if (selectedSquare == touchedSquare) {
                        isDragging = true
                        dragSquare = touchedSquare
                        dragX = x
                        dragY = y
                    } else {
                        val fromUci = ChessFenUtils.indexToUciSquare(selectedSquare)
                        val toUci = ChessFenUtils.indexToUciSquare(touchedSquare)
                        val success = onMoveAttempted?.invoke(fromUci, toUci) ?: false
                        if (success) {
                            lastFromSquare = selectedSquare
                            lastToSquare = touchedSquare
                            selectedSquare = -1
                        } else {
                            if (piece != '.' && piece != ' ') {
                                selectedSquare = touchedSquare
                                isDragging = true
                                dragSquare = touchedSquare
                                dragX = x
                                dragY = y
                            } else {
                                selectedSquare = -1
                            }
                        }
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    dragX = x
                    dragY = y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    if (dragSquare != touchedSquare) {
                        val fromUci = ChessFenUtils.indexToUciSquare(dragSquare)
                        val toUci = ChessFenUtils.indexToUciSquare(touchedSquare)
                        val success = onMoveAttempted?.invoke(fromUci, toUci) ?: false
                        if (success) {
                            lastFromSquare = dragSquare
                            lastToSquare = touchedSquare
                            selectedSquare = -1
                        }
                    }
                    dragSquare = -1
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                dragSquare = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
