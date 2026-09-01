package com.chessbeater.playground

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.chessbeater.R
import com.chessbeater.engine.ChessFenUtils
import com.chessbeater.engine.StockfishBridge
import com.chessbeater.utils.EloInfoFormatter

class PlaygroundActivity : AppCompatActivity() {

    private lateinit var stockfishBridge: StockfishBridge
    private lateinit var controller: PlaygroundController

    private lateinit var boardView: PlaygroundBoardView
    private lateinit var tvEvalStatus: TextView
    private lateinit var etFen: EditText

    // Tab buttons
    private lateinit var tabUserVsEngine: Button
    private lateinit var tabDualBot: Button
    private lateinit var tabEditor: Button

    // Panels
    private lateinit var panelUserVsEngine: LinearLayout
    private lateinit var panelDualBot: LinearLayout
    private lateinit var panelEditor: LinearLayout

    // User vs Engine controls
    private lateinit var btnSideWhite: Button
    private lateinit var btnSideBlack: Button
    private lateinit var tvUserVsBotEloTitle: TextView
    private lateinit var tvUserVsBotEloDesc: TextView
    private lateinit var sbUserVsBotElo: SeekBar
    private lateinit var btnEngineStep: Button

    // Dual Bot controls
    private lateinit var tvWhiteBotEloTitle: TextView
    private lateinit var sbWhiteBotElo: SeekBar
    private lateinit var tvBlackBotEloTitle: TextView
    private lateinit var sbBlackBotElo: SeekBar
    private lateinit var tvMatchSpeedTitle: TextView
    private lateinit var sbMatchSpeed: SeekBar
    private lateinit var btnPlayPauseDuel: Button
    private lateinit var btnStepDuel: Button

    // Editor controls
    private lateinit var btnInitialBoard: Button
    private lateinit var btnClearBoard: Button
    private lateinit var btnToggleTurn: Button
    private lateinit var btnLoadFen: Button
    private val paletteButtons = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playground)

        stockfishBridge = StockfishBridge.getInstance(this)
        stockfishBridge.startEngine()

        initViews()
        setupController()
        setupListeners()
        selectTab(PlayMode.USER_VS_ENGINE)
    }

    private fun initViews() {
        boardView = findViewById(R.id.boardView)
        tvEvalStatus = findViewById(R.id.tvEvalStatus)
        etFen = findViewById(R.id.etFen)

        tabUserVsEngine = findViewById(R.id.tabUserVsEngine)
        tabDualBot = findViewById(R.id.tabDualBot)
        tabEditor = findViewById(R.id.tabEditor)

        panelUserVsEngine = findViewById(R.id.panelUserVsEngine)
        panelDualBot = findViewById(R.id.panelDualBot)
        panelEditor = findViewById(R.id.panelEditor)

        btnSideWhite = findViewById(R.id.btnSideWhite)
        btnSideBlack = findViewById(R.id.btnSideBlack)
        tvUserVsBotEloTitle = findViewById(R.id.tvUserVsBotEloTitle)
        tvUserVsBotEloDesc = findViewById(R.id.tvUserVsBotEloDesc)
        sbUserVsBotElo = findViewById(R.id.sbUserVsBotElo)
        btnEngineStep = findViewById(R.id.btnEngineStep)

        tvWhiteBotEloTitle = findViewById(R.id.tvWhiteBotEloTitle)
        sbWhiteBotElo = findViewById(R.id.sbWhiteBotElo)
        tvBlackBotEloTitle = findViewById(R.id.tvBlackBotEloTitle)
        sbBlackBotElo = findViewById(R.id.sbBlackBotElo)
        tvMatchSpeedTitle = findViewById(R.id.tvMatchSpeedTitle)
        sbMatchSpeed = findViewById(R.id.sbMatchSpeed)
        btnPlayPauseDuel = findViewById(R.id.btnPlayPauseDuel)
        btnStepDuel = findViewById(R.id.btnStepDuel)

        btnInitialBoard = findViewById(R.id.btnInitialBoard)
        btnClearBoard = findViewById(R.id.btnClearBoard)
        btnToggleTurn = findViewById(R.id.btnToggleTurn)
        btnLoadFen = findViewById(R.id.btnLoadFen)

        paletteButtons.addAll(
            listOf(
                findViewById(R.id.btnPalWP), findViewById(R.id.btnPalWN),
                findViewById(R.id.btnPalWB), findViewById(R.id.btnPalWR),
                findViewById(R.id.btnPalWQ), findViewById(R.id.btnPalWK),
                findViewById(R.id.btnPalErase),
                findViewById(R.id.btnPalBP), findViewById(R.id.btnPalBN),
                findViewById(R.id.btnPalBB), findViewById(R.id.btnPalBR),
                findViewById(R.id.btnPalBQ), findViewById(R.id.btnPalBK)
            )
        )
    }

    private fun setupController() {
        controller = PlaygroundController(
            stockfishBridge = stockfishBridge,
            onStateUpdated = { fen, turn, isFinished ->
                runOnUiThread {
                    boardView.setFen(fen)
                    etFen.setText(fen)
                    val turnLabel = if (turn == "WHITE") "⚪ Putih" else "⚫ Hitam"
                    val finishedStr = if (isFinished) " | 🏁 Permainan Selesai" else ""
                    tvEvalStatus.text = "Giliran: $turnLabel$finishedStr"
                    btnToggleTurn.text = if (fen.contains(" w ")) "Giliran: W" else "Giliran: B"
                }
            },
            onEvalUpdated = { _, bestMove ->
                runOnUiThread {
                    val arrowStr = if (bestMove.isNotBlank()) " | Rekomendasi: 🎯 $bestMove" else ""
                    val currentText = tvEvalStatus.text.toString().substringBefore(" | Rekomendasi:")
                    tvEvalStatus.text = "$currentText$arrowStr"
                    boardView.setBestMoveArrow(bestMove)
                }
            }
        )

        boardView.onMoveAttempted = { fromUci, toUci ->
            controller.tryUserMove(fromUci, toUci)
        }

        boardView.onEditorSquareClicked = { sqIdx, piece ->
            controller.setEditorPiece(sqIdx, piece)
        }

        boardView.setFen(controller.currentFen)
        etFen.setText(controller.currentFen)
        controller.evaluateCurrentPosition()
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<ImageButton>(R.id.btnFlipBoard).setOnClickListener {
            boardView.isFlipped = !boardView.isFlipped
        }

        findViewById<ImageButton>(R.id.btnCopyFen).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("FEN", controller.currentFen))
            Toast.makeText(this, "📋 FEN disalin ke Clipboard", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btnResetMatch).setOnClickListener {
            controller.resetMatch()
            btnPlayPauseDuel.text = "▶️ Mulai Duel Bot"
            btnPlayPauseDuel.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
            Toast.makeText(this, "🔄 Pertandingan di-reset", Toast.LENGTH_SHORT).show()
        }

        // Mode Tabs
        tabUserVsEngine.setOnClickListener { selectTab(PlayMode.USER_VS_ENGINE) }
        tabDualBot.setOnClickListener { selectTab(PlayMode.ENGINE_VS_ENGINE) }
        tabEditor.setOnClickListener { selectTab(PlayMode.BOARD_EDITOR) }

        // User vs Engine Panel
        btnSideWhite.setOnClickListener {
            controller.userSide = "WHITE"
            boardView.isFlipped = false
            btnSideWhite.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
            btnSideWhite.setTextColor(Color.BLACK)
            btnSideBlack.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#334155"))
            btnSideBlack.setTextColor(Color.WHITE)
        }

        btnSideBlack.setOnClickListener {
            controller.userSide = "BLACK"
            boardView.isFlipped = true
            btnSideBlack.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
            btnSideBlack.setTextColor(Color.BLACK)
            btnSideWhite.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#334155"))
            btnSideWhite.setTextColor(Color.WHITE)
            if (controller.currentFen.contains(" w ")) {
                controller.triggerEngineResponse()
            }
        }

        sbUserVsBotElo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val elo = 800 + (progress * 27)
                if (controller.userSide == "WHITE") {
                    controller.blackElo = elo
                } else {
                    controller.whiteElo = elo
                }
                tvUserVsBotEloTitle.text = "🎯 Target Engine ELO: [ $elo ELO ]"
                tvUserVsBotEloDesc.text = EloInfoFormatter.formatEloDetails(elo, false)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnEngineStep.setOnClickListener {
            controller.triggerEngineResponse()
        }

        // Dual Bot Panel
        sbWhiteBotElo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val elo = 800 + (progress * 27)
                controller.whiteElo = elo
                tvWhiteBotEloTitle.text = "⚪ Bot Putih: [ $elo ELO ]"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbBlackBotElo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val elo = 800 + (progress * 27)
                controller.blackElo = elo
                tvBlackBotEloTitle.text = "⚫ Bot Hitam: [ $elo ELO ]"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbMatchSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val delayMs = 200L + (progress * 100L)
                controller.botVsBotDelayMs = delayMs
                tvMatchSpeedTitle.text = "⏱️ Jeda Langkah Bot: [ $delayMs ms ]"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnPlayPauseDuel.setOnClickListener {
            if (controller.isBotMatchRunning) {
                controller.pauseBotVsBotMatch()
                btnPlayPauseDuel.text = "▶️ Lanjutkan Duel"
                btnPlayPauseDuel.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
            } else {
                controller.startBotVsBotMatch()
                btnPlayPauseDuel.text = "⏸️ Jeda Duel"
                btnPlayPauseDuel.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
            }
        }

        btnStepDuel.setOnClickListener {
            controller.pauseBotVsBotMatch()
            btnPlayPauseDuel.text = "▶️ Mulai Duel Bot"
            btnPlayPauseDuel.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
            controller.triggerEngineResponse()
        }

        // Editor Panel
        setupPalette()

        btnInitialBoard.setOnClickListener {
            controller.loadFen(ChessFenUtils.INITIAL_FEN)
        }

        btnClearBoard.setOnClickListener {
            controller.clearBoard()
        }

        btnToggleTurn.setOnClickListener {
            val fen = controller.currentFen
            val newFen = if (fen.contains(" w ")) {
                fen.replace(" w ", " b ")
            } else if (fen.contains(" b ")) {
                fen.replace(" b ", " w ")
            } else {
                "$fen w"
            }
            controller.loadFen(newFen)
        }

        btnLoadFen.setOnClickListener {
            val text = etFen.text.toString().trim()
            if (text.isNotBlank()) {
                controller.loadFen(text)
                Toast.makeText(this, "FEN berhasil dimuat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPalette() {
        val map = mapOf(
            R.id.btnPalWP to 'P', R.id.btnPalWN to 'N', R.id.btnPalWB to 'B',
            R.id.btnPalWR to 'R', R.id.btnPalWQ to 'Q', R.id.btnPalWK to 'K',
            R.id.btnPalErase to '.',
            R.id.btnPalBP to 'p', R.id.btnPalBN to 'n', R.id.btnPalBB to 'b',
            R.id.btnPalBR to 'r', R.id.btnPalBQ to 'q', R.id.btnPalBK to 'k'
        )

        for ((id, pieceChar) in map) {
            val btn = findViewById<Button>(id)
            btn.setOnClickListener {
                boardView.selectedEditorPiece = pieceChar
                for (other in paletteButtons) {
                    other.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#334155"))
                    other.setTextColor(Color.WHITE)
                }
                btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
                btn.setTextColor(Color.BLACK)
            }
        }
    }

    private fun selectTab(mode: PlayMode) {
        controller.playMode = mode
        controller.pauseBotVsBotMatch()
        btnPlayPauseDuel.text = "▶️ Mulai Duel Bot"
        btnPlayPauseDuel.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))

        // Reset Tab Colors
        tabUserVsEngine.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E293B"))
        tabUserVsEngine.setTextColor(Color.parseColor("#94A3B8"))
        tabDualBot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E293B"))
        tabDualBot.setTextColor(Color.parseColor("#94A3B8"))
        tabEditor.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E293B"))
        tabEditor.setTextColor(Color.parseColor("#94A3B8"))

        panelUserVsEngine.visibility = View.GONE
        panelDualBot.visibility = View.GONE
        panelEditor.visibility = View.GONE
        boardView.isEditorMode = false

        when (mode) {
            PlayMode.USER_VS_ENGINE -> {
                tabUserVsEngine.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
                tabUserVsEngine.setTextColor(Color.BLACK)
                panelUserVsEngine.visibility = View.VISIBLE
            }
            PlayMode.ENGINE_VS_ENGINE -> {
                tabDualBot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
                tabDualBot.setTextColor(Color.BLACK)
                panelDualBot.visibility = View.VISIBLE
            }
            PlayMode.BOARD_EDITOR -> {
                tabEditor.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
                tabEditor.setTextColor(Color.BLACK)
                panelEditor.visibility = View.VISIBLE
                boardView.isEditorMode = true
            }
        }
    }

    override fun onDestroy() {
        controller.pauseBotVsBotMatch()
        super.onDestroy()
    }
}
