package com.chessbeater.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.chessbeater.engine.HumanizationEngine
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class SettingsOverlayView(
    context: Context,
    private val onBackToMainMenu: (() -> Unit)? = null,
    private val onVisualSettingsChanged: (() -> Unit)? = null,
    private val onClose: () -> Unit
) : FrameLayout(context) {

    private val prefs = context.getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE)
    private val density = resources.displayMetrics.density

    private enum class Tab { DISPLAY_ENGINE, ANTI_CHEAT }
    private var currentTab = Tab.DISPLAY_ENGINE

    private val tabDisplayBtn: Button
    private val tabAntiCheatBtn: Button
    private val contentContainer: LinearLayout

    init {
        isClickable = true
        isFocusable = true

        val pad12 = (12 * density).toInt()
        val pad8 = (8 * density).toInt()

        // Root Dialog Container
        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad12, pad12, pad12, pad12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1E222B")) // 93% solid dark slate
                cornerRadius = 14 * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#334155"))
            }
        }

        // 1. Header Bar: Title + Close Button
        val headerBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, pad8)
        }

        val tvTitle = TextView(context).apply {
            text = "⚙️ Pengaturan & Kustomisasi"
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

        // 2. Tab Navigation Bar
        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, pad8)
        }

        tabDisplayBtn = Button(context).apply {
            text = "🎮 Tampilan & Engine"
            textSize = 10f
            setOnClickListener {
                currentTab = Tab.DISPLAY_ENGINE
                updateTabSelection()
            }
        }
        tabRow.addView(tabDisplayBtn, LinearLayout.LayoutParams(0, (38 * density).toInt(), 1f).apply {
            marginEnd = (3 * density).toInt()
        })

        tabAntiCheatBtn = Button(context).apply {
            text = "🛡️ Anti-Cheat"
            textSize = 10f
            setOnClickListener {
                currentTab = Tab.ANTI_CHEAT
                updateTabSelection()
            }
        }
        tabRow.addView(tabAntiCheatBtn, LinearLayout.LayoutParams(0, (38 * density).toInt(), 1f).apply {
            marginStart = (3 * density).toInt()
        })
        rootCard.addView(tabRow)

        // 3. Scrollable Content Area
        val scrollView = ScrollView(context).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (4 * density).toInt(), 0, pad8)
        }
        scrollView.addView(contentContainer)
        rootCard.addView(scrollView)

        // 4. Bottom Button: Back to Main Menu
        val btnBackToBoard = Button(context).apply {
            text = "⬅️ Kembali ke Menu Kontrol"
            textSize = 11.5f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0284C7")) // Primary Sky Blue
                cornerRadius = 8 * density
            }
            setOnClickListener {
                if (onBackToMainMenu != null) {
                    onBackToMainMenu.invoke()
                } else {
                    onClose()
                }
            }
        }
        val pBottom = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (42 * density).toInt()
        ).apply {
            topMargin = (6 * density).toInt()
        }
        rootCard.addView(btnBackToBoard, pBottom)

        val rootLp = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        addView(rootCard, rootLp)

        updateTabSelection()
    }

    private fun updateTabSelection() {
        if (currentTab == Tab.DISPLAY_ENGINE) {
            tabDisplayBtn.apply {
                setTextColor(Color.parseColor("#0F172A"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#00E5FF")) // Active Neon Cyan
                    cornerRadius = 6 * density
                }
            }
            tabAntiCheatBtn.apply {
                setTextColor(Color.parseColor("#94A3B8"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = 6 * density
                }
            }
            renderDisplayEngineTab()
        } else {
            tabAntiCheatBtn.apply {
                setTextColor(Color.parseColor("#0F172A"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#10B981")) // Active Emerald Green
                    cornerRadius = 6 * density
                }
            }
            tabDisplayBtn.apply {
                setTextColor(Color.parseColor("#94A3B8"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = 6 * density
                }
            }
            renderAntiCheatTab()
        }
    }

    private fun renderDisplayEngineTab() {
        contentContainer.removeAllViews()

        // --- SECTION: KEAMANAN & GHOST ---
        addSectionHeader("🔒 Keamanan & Kontrol")

        val isLocked = prefs.getBoolean("board_is_locked", true)
        addSwitchItem("Kunci Posisi Papan", "Cegah geser saat bermain", isLocked) { checked ->
            prefs.edit().putBoolean("board_is_locked", checked).apply()
        }

        val isGhost = prefs.getBoolean("ghost_controls_enabled", false)
        addSwitchItem("Ghost Controls", "Header & footer 100% transparan", isGhost) { checked ->
            prefs.edit().putBoolean("ghost_controls_enabled", checked).apply()
        }

        val isHighlightFilled = prefs.getBoolean("highlight_is_filled", true)
        addSwitchItem("Gaya Highlight Kotak", if (isHighlightFilled) "Blok Penuh (Filled)" else "Garis Tepi (Outlined)", isHighlightFilled) { checked ->
            prefs.edit().putBoolean("highlight_is_filled", checked).apply()
        }

        // --- SECTION: ENGINE ELO ---
        addSectionHeader("⚡ Target Engine ELO")
        val currentElo = prefs.getSafeInt("max_elo_rating", 3500)
        addSliderItem("Kekuatan Engine", "${currentElo} ELO", (currentElo - 800) / 27, 100) { progress ->
            val elo = 800 + (progress * 27)
            prefs.edit().putInt("max_elo_rating", elo).apply()
            "${elo} ELO"
        }

        // --- SECTION: SKALA TOMBOL ---
        addSectionHeader("👁️ Ukuran Tombol Kontrol")
        val eyeSize = prefs.getSafeInt("size_floating_eye_dp", 44)
        addSliderItem("Ukuran Floating Eye", "${eyeSize} dp", ((eyeSize - 28) / 36f * 100).toInt(), 100) { progress ->
            val sz = 28 + (progress * 36 / 100)
            prefs.edit().putInt("size_floating_eye_dp", sz).apply()
            "${sz} dp"
        }

        val hideSize = prefs.getSafeInt("size_header_eye_dp", 34)
        addSliderItem("Tombol Sembunyi (Mata)", "${hideSize} dp", ((hideSize - 24) / 28f * 100).toInt(), 100) { progress ->
            val sz = 24 + (progress * 28 / 100)
            prefs.edit().putInt("size_header_eye_dp", sz).apply()
            "${sz} dp"
        }

        val menuSize = prefs.getSafeInt("size_header_menu_dp", 34)
        addSliderItem("Tombol Menu (Titik 3)", "${menuSize} dp", ((menuSize - 24) / 28f * 100).toInt(), 100) { progress ->
            val sz = 24 + (progress * 28 / 100)
            prefs.edit().putInt("size_header_menu_dp", sz).apply()
            "${sz} dp"
        }

        // --- SECTION: OTOMASI VISIBILITAS (AUTO HIDE & SHOW) ---
        addSectionHeader("⏱️ Otomasi Sembunyi / Tampil")

        val isAutoHideEnabled = prefs.getBoolean("auto_hide_enabled", false)
        val autoHideDelay = prefs.getSafeFloat("auto_hide_delay_sec", 5.0f).coerceIn(1.0f, 30.0f)
        addSwitchItem("⏳ Auto-Hide Papan", "Sembunyi otomatis setelah rekomendasi muncul", isAutoHideEnabled) { checked ->
            prefs.edit().putBoolean("auto_hide_enabled", checked)
                .putInt("auto_hide_delay_sec", if (checked) autoHideDelay.toInt() else -1)
                .apply()
            renderDisplayEngineTab()
        }

        if (isAutoHideEnabled) {
            val progressAutoHide = (((autoHideDelay - 1.0f) / 0.5f).toInt()).coerceIn(0, 58)
            addSliderItem("Durasi Auto-Hide", "${String.format("%.1f", autoHideDelay)} dtk", progressAutoHide, 58) { progress ->
                val delaySec = 1.0f + (progress * 0.5f)
                prefs.edit().putFloat("auto_hide_delay_sec", delaySec)
                    .putInt("auto_hide_delay_sec", delaySec.toInt())
                    .apply()
                "${String.format("%.1f", delaySec)} dtk"
            }
        }

        val isAutoShowEnabled = prefs.getBoolean("auto_show_enabled", false)
        val autoShowDelay = prefs.getSafeFloat("auto_show_delay_sec", 3.0f).coerceIn(1.0f, 30.0f)
        addSwitchItem("✨ Auto-Show Papan", "Muncul kembali otomatis dari Floating Eye", isAutoShowEnabled) { checked ->
            prefs.edit().putBoolean("auto_show_enabled", checked)
                .putBoolean("is_auto_show_enabled", checked)
                .apply()
            renderDisplayEngineTab()
        }

        if (isAutoShowEnabled) {
            val progressAutoShow = (((autoShowDelay - 1.0f) / 0.5f).toInt()).coerceIn(0, 58)
            addSliderItem("Durasi Auto-Show", "${String.format("%.1f", autoShowDelay)} dtk", progressAutoShow, 58) { progress ->
                val delaySec = 1.0f + (progress * 0.5f)
                prefs.edit().putFloat("auto_show_delay_sec", delaySec)
                    .putInt("auto_show_delay_sec", delaySec.toInt())
                    .apply()
                "${String.format("%.1f", delaySec)} dtk"
            }
        }

        // --- SECTION: TRANSPARANSI & VISUAL ---
        addSectionHeader("🎨 Transparansi Visual")

        // 1. Bidak Catur (alpha_pieces)
        val piecesAlpha = prefs.getSafeFloat("alpha_pieces", prefs.getSafeFloat("piece_alpha", prefs.getSafeFloat("pieces_alpha", 1.0f))).coerceIn(0.05f, 1f)
        addSliderItem("Bidak Catur", "${(piecesAlpha * 100).toInt()}%", (piecesAlpha * 100).toInt(), 100) { p ->
            val a = (p / 100f).coerceIn(0.05f, 1f)
            prefs.edit()
                .putFloat("alpha_pieces", a)
                .putFloat("piece_alpha", a)
                .putFloat("pieces_alpha", a)
                .apply()
            onVisualSettingsChanged?.invoke()
            "${p}%"
        }

        // 2. Transparansi Papan / Grid (board_alpha)
        val boardAlpha = prefs.getSafeFloat("board_alpha", prefs.getSafeFloat("grid_alpha", 0.85f)).coerceIn(0f, 1f)
        addSliderItem("Transparansi Papan / Grid", "${(boardAlpha * 100).toInt()}%", (boardAlpha * 100).toInt(), 100) { p ->
            val a = (p / 100f).coerceIn(0f, 1f)
            prefs.edit()
                .putFloat("board_alpha", a)
                .putFloat("grid_alpha", a)
                .apply()
            onVisualSettingsChanged?.invoke()
            "${p}%"
        }

        // 3. Panah Langkah (alpha_arrows)
        val arrowAlpha = prefs.getSafeFloat("alpha_arrows", prefs.getSafeFloat("arrow_alpha", 0.95f)).coerceIn(0.05f, 1f)
        addSliderItem("Panah Langkah", "${(arrowAlpha * 100).toInt()}%", (arrowAlpha * 100).toInt(), 100) { p ->
            val a = (p / 100f).coerceIn(0.05f, 1f)
            prefs.edit()
                .putFloat("alpha_arrows", a)
                .putFloat("arrow_alpha", a)
                .apply()
            onVisualSettingsChanged?.invoke()
            "${p}%"
        }

        // 4. Highlight Petak (alpha_highlights)
        val hlAlpha = prefs.getSafeFloat("alpha_highlights", prefs.getSafeFloat("highlight_alpha", 0.50f)).coerceIn(0.05f, 1f)
        addSliderItem("Highlight Petak", "${(hlAlpha * 100).toInt()}%", (hlAlpha * 100).toInt(), 100) { p ->
            val a = (p / 100f).coerceIn(0.05f, 1f)
            prefs.edit()
                .putFloat("alpha_highlights", a)
                .putFloat("highlight_alpha", a)
                .apply()
            onVisualSettingsChanged?.invoke()
            "${p}%"
        }

        // 5. Move Guide Dots (alpha_dots)
        val dotsAlpha = prefs.getSafeFloat("alpha_dots", prefs.getSafeFloat("guide_dots_alpha", prefs.getSafeFloat("move_guide_alpha", 0.80f))).coerceIn(0.05f, 1f)
        addSliderItem("Move Guide Dots", "${(dotsAlpha * 100).toInt()}%", (dotsAlpha * 100).toInt(), 100) { p ->
            val a = (p / 100f).coerceIn(0.05f, 1f)
            prefs.edit()
                .putFloat("alpha_dots", a)
                .putFloat("guide_dots_alpha", a)
                .putFloat("move_guide_alpha", a)
                .apply()
            onVisualSettingsChanged?.invoke()
            "${p}%"
        }

        // 6. Floating Eye (alpha_floating_eye)
        val eyeAlpha = prefs.getSafeFloat("alpha_floating_eye", prefs.getSafeFloat("floating_eye_alpha", 0.85f)).coerceIn(0.05f, 1f)
        addSliderItem("Tombol Floating Eye", "${(eyeAlpha * 100).toInt()}%", (eyeAlpha * 100).toInt(), 100) { p ->
            val a = (p / 100f).coerceIn(0.05f, 1f)
            prefs.edit()
                .putFloat("alpha_floating_eye", a)
                .putFloat("floating_eye_alpha", a)
                .apply()
            onVisualSettingsChanged?.invoke()
            "${p}%"
        }
    }

    private fun renderAntiCheatTab() {
        contentContainer.removeAllViews()

        addSectionHeader("🛡️ Humanisasi & Anti-Cheat")

        addSwitchItem("Humanize Move Engine", "Gerakan natural manusia", HumanizationEngine.isHumanizeEnabled) { checked ->
            HumanizationEngine.isHumanizeEnabled = checked
            HumanizationEngine.saveSettings(context)
        }

        val currentLevel = HumanizationEngine.humanizeLevel
        addSliderItem("Level Humanis", "Level $currentLevel", currentLevel * 10, 100) { progress ->
            val lvl = (progress / 10).coerceIn(0, 10)
            HumanizationEngine.humanizeLevel = lvl
            HumanizationEngine.saveSettings(context)
            "Level $lvl"
        }

        addSwitchItem("Blunder Guard", "Cegah blunder fatal", HumanizationEngine.isBlunderGuardEnabled) { checked ->
            HumanizationEngine.isBlunderGuardEnabled = checked
            HumanizationEngine.saveSettings(context)
        }

        addSwitchItem("Natural Move Delay", "Jeda waktu langkah variatif", HumanizationEngine.isNaturalDelayEnabled) { checked ->
            HumanizationEngine.isNaturalDelayEnabled = checked
            HumanizationEngine.saveSettings(context)
        }
    }

    private fun addSectionHeader(title: String) {
        val tv = TextView(context).apply {
            text = title
            setTextColor(Color.parseColor("#38BDF8")) // Sky blue
            textSize = 11f
            paint.isFakeBoldText = true
            setPadding(0, (8 * density).toInt(), 0, (4 * density).toInt())
        }
        contentContainer.addView(tv)
    }

    private fun addSwitchItem(title: String, subtitle: String, isChecked: Boolean, onToggle: (Boolean) -> Unit) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 8 * density
            }
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
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 9f
        }
        textCol.addView(tvSub)
        row.addView(textCol)

        val sw = Switch(context).apply {
            this.isChecked = isChecked
            setOnCheckedChangeListener { _, checked ->
                onToggle(checked)
            }
        }
        row.addView(sw)

        val p = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (4 * density).toInt()
        }
        contentContainer.addView(row, p)
    }

    private fun addSliderItem(
        title: String,
        initialValueText: String,
        initialProgress: Int,
        maxProgress: Int,
        onProgressChanged: (Int) -> String
    ) {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 8 * density
            }
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvTitle = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 10.5f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(tvTitle)

        val tvVal = TextView(context).apply {
            text = initialValueText
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 10f
            paint.isFakeBoldText = true
        }
        topRow.addView(tvVal)
        card.addView(topRow)

        val seekBar = TapOnReleaseSeekBar(context).apply {
            max = maxProgress
            progress = initialProgress
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        tvVal.text = onProgressChanged(progress)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    sb?.let {
                        tvVal.text = onProgressChanged(it.progress)
                    }
                }
            })
        }
        val pSeek = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (2 * density).toInt()
        }
        card.addView(seekBar, pSeek)

        val p = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (4 * density).toInt()
        }
        contentContainer.addView(card, p)
    }
}

private fun android.content.SharedPreferences.getSafeFloat(key: String, defaultValue: Float): Float {
    return try {
        this.getFloat(key, defaultValue)
    } catch (e: Exception) {
        try {
            this.getInt(key, defaultValue.toInt()).toFloat()
        } catch (e2: Exception) {
            defaultValue
        }
    }
}

private fun android.content.SharedPreferences.getSafeInt(key: String, defaultValue: Int): Int {
    return try {
        this.getInt(key, defaultValue)
    } catch (e: Exception) {
        try {
            this.getFloat(key, defaultValue.toFloat()).toInt()
        } catch (e2: Exception) {
            defaultValue
        }
    }
}
