package com.chessbeater.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

@SuppressLint("ViewConstructor")
class MainControlMenuView(
    context: Context,
    private val isAutoDetectActive: Boolean,
    private val isFlipped: Boolean,
    private val onSelectOpponentWhite: () -> Unit,
    private val onSelectOpponentBlack: () -> Unit,
    private val onToggleAutoDetect: () -> Unit,
    private val onUndoMove: () -> Unit,
    private val onCorrectionMode: () -> Unit,
    private val onSavePreset: () -> Unit,
    private val onNewCalibration: () -> Unit,
    private val onFlipBoard: () -> Unit,
    private val onResetGame: () -> Unit,
    private val onOpenAdvancedSettings: () -> Unit,
    private val onHideOverlay: () -> Unit,
    private val onExitService: () -> Unit,
    private val onClose: () -> Unit
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density

    init {
        isClickable = true
        isFocusable = true

        val pad12 = (12 * density).toInt()
        val pad8 = (8 * density).toInt()

        // Root Card Container
        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad12, pad12, pad12, pad12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1E222B")) // 93% solid dark slate
                cornerRadius = 14 * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#334155"))
            }
        }

        // Header Bar
        val headerBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, pad8)
        }

        val tvTitle = TextView(context).apply {
            text = "♟️ Menu Kontrol Papan"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerBar.addView(tvTitle)

        val btnClose = TextView(context).apply {
            text = "✖"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(pad8, (4 * density).toInt(), pad8, (4 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = 6 * density
            }
            setOnClickListener { onClose() }
        }
        headerBar.addView(btnClose)
        rootCard.addView(headerBar)

        // Scrollable Menu List
        val scrollView = ScrollView(context).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val menuList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
        }

        // Row 1: Sisi Lawan (Putih / Hitam)
        val sideRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        val btnWhite = createButton("⚪ Lawan Putih\n(Anda: Putih Atas)", "#F8FAFC", "#0F172A") {
            onSelectOpponentWhite()
            onClose()
        }
        val btnBlack = createButton("⚫ Lawan Hitam\n(Anda: Hitam Atas)", "#334155", "#FFFFFF") {
            onSelectOpponentBlack()
            onClose()
        }
        sideRow.addView(btnWhite, LinearLayout.LayoutParams(0, (42 * density).toInt(), 1f).apply { marginEnd = (3 * density).toInt() })
        sideRow.addView(btnBlack, LinearLayout.LayoutParams(0, (42 * density).toInt(), 1f).apply { marginStart = (3 * density).toInt() })
        menuList.addView(sideRow)

        // Menu Items
        addMenuItem(menuList, "🤖 Deteksi Otomatis Lawan", if (isAutoDetectActive) "Status: AKTIF ✅" else "Status: NONAKTIF ⏸️", "#00E5FF") {
            onToggleAutoDetect()
        }

        addMenuItem(menuList, "↩️ Urungkan Langkah Terakhir", "Undo langkah bidak terakhir", "#94A3B8") {
            onUndoMove()
            onClose()
        }

        addMenuItem(menuList, "✏️ Koreksi Posisi Papan", "Buka editor bidak interaktif", "#FBBF24") {
            onCorrectionMode()
            onClose()
        }

        addMenuItem(menuList, "💾 Simpan Posisi ke Preset", "Simpan koordinat papan saat ini", "#34D399") {
            onSavePreset()
            onClose()
        }

        addMenuItem(menuList, "📐 Kalibrasi Baru Papan", "Buka panduan alignment HUD", "#38BDF8") {
            onNewCalibration()
            onClose()
        }

        addMenuItem(menuList, "🔄 Putar Papan (Flip)", if (isFlipped) "Posisi: TERBALIK (Flipped)" else "Posisi: NORMAL", "#A78BFA") {
            onFlipBoard()
            onClose()
        }

        addMenuItem(menuList, "🗑️ Reset Game", "Mulai ulang permainan ke posisi awal", "#F87171") {
            onResetGame()
            onClose()
        }

        // Highlight Item: Pengaturan Lengkap
        val btnSettings = Button(context).apply {
            text = "⚙️ Pengaturan & Anti-Cheat ➔"
            textSize = 11.5f
            paint.isFakeBoldText = true
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0284C7")) // Primary Sky Blue
                cornerRadius = 8 * density
            }
            setOnClickListener {
                onOpenAdvancedSettings()
            }
        }
        val pSet = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (44 * density).toInt()
        ).apply {
            topMargin = (4 * density).toInt()
            bottomMargin = (4 * density).toInt()
        }
        menuList.addView(btnSettings, pSet)

        // Bottom Actions: Sembunyikan & Matikan
        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, (4 * density).toInt(), 0, 0)
        }
        val btnHide = createButton("👁️ Sembunyikan", "#1E293B", "#38BDF8") {
            onHideOverlay()
            onClose()
        }
        val btnExit = createButton("🚪 Matikan", "#7F1D1D", "#FCA5A5") {
            onExitService()
            onClose()
        }
        bottomRow.addView(btnHide, LinearLayout.LayoutParams(0, (38 * density).toInt(), 1f).apply { marginEnd = (3 * density).toInt() })
        bottomRow.addView(btnExit, LinearLayout.LayoutParams(0, (38 * density).toInt(), 1f).apply { marginStart = (3 * density).toInt() })
        menuList.addView(bottomRow)

        scrollView.addView(menuList)
        rootCard.addView(scrollView)

        val rootLp = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        addView(rootCard, rootLp)
    }

    private fun addMenuItem(parent: LinearLayout, title: String, subtitle: String, accentColorHex: String, onClick: () -> Unit) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * density).toInt(), (7 * density).toInt(), (10 * density).toInt(), (7 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 8 * density
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 11f
            paint.isFakeBoldText = true
        }
        textCol.addView(tvTitle)

        val tvSub = TextView(context).apply {
            text = subtitle
            setTextColor(Color.parseColor(accentColorHex))
            textSize = 9f
        }
        textCol.addView(tvSub)
        row.addView(textCol)

        val arrow = TextView(context).apply {
            text = "›"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 14f
        }
        row.addView(arrow)

        val p = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (4 * density).toInt()
        }
        parent.addView(row, p)
    }

    private fun createButton(text: String, bgHex: String, textHex: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            textSize = 9.5f
            setTextColor(Color.parseColor(textHex))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = 8 * density
            }
            setOnClickListener { onClick() }
        }
    }
}
