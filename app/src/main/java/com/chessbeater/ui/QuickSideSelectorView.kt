package com.chessbeater.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class QuickSideSelectorView(
    context: Context,
    private val onSideSelected: (isOpponentWhite: Boolean) -> Unit,
    private val onDismissKeepLast: () -> Unit
) : FrameLayout(context) {

    init {
        val density = resources.displayMetrics.density
        val pad12 = (12 * density).toInt()
        val pad8 = (8 * density).toInt()

        // Container Card
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad12, pad12, pad12, pad12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1E222B")) // 93% solid dark
                cornerRadius = 12 * density
                setStroke((1 * density).toInt(), Color.parseColor("#334155"))
            }
        }

        // Header Title
        val tvTitle = TextView(context).apply {
            text = "⚔️ Tentukan Sisi Lawan"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad8)
        }
        container.addView(tvTitle)

        val tvSub = TextView(context).apply {
            text = "Anda selalu di atas papan • Komputer di bawah papan"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 9.5f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad12)
        }
        container.addView(tvSub)

        // Row Tombol Pilihan Sisi
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }

        // 1. Tombol Lawan Putih (Anda Putih di Atas, Mesin Hitam di Bawah -> Board Flipped)
        val btnOpponentWhite = Button(context).apply {
            text = "⚪ Lawan Putih\n(Anda: Putih Atas)"
            textSize = 10f
            setTextColor(Color.parseColor("#0F172A"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F8FAFC"))
                cornerRadius = 8 * density
            }
            setOnClickListener { onSideSelected(true) }
        }
        val p1 = LinearLayout.LayoutParams(0, (44 * density).toInt(), 1f).apply {
            marginEnd = (4 * density).toInt()
        }
        buttonRow.addView(btnOpponentWhite, p1)

        // 2. Tombol Lawan Hitam (Anda Hitam di Atas, Mesin Putih di Bawah -> Board Normal)
        val btnOpponentBlack = Button(context).apply {
            text = "⚫ Lawan Hitam\n(Anda: Hitam Atas)"
            textSize = 10f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = 8 * density
            }
            setOnClickListener { onSideSelected(false) }
        }
        val p2 = LinearLayout.LayoutParams(0, (44 * density).toInt(), 1f).apply {
            marginStart = (4 * density).toInt()
        }
        buttonRow.addView(btnOpponentBlack, p2)
        container.addView(buttonRow)

        // 3. Tombol Tutup / Pakai Pengaturan Terakhir
        val btnDismiss = TextView(context).apply {
            text = "✖️ Lewati (Gunakan Setelan Terakhir)"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(0, pad12, 0, (4 * density).toInt())
            setOnClickListener { onDismissKeepLast() }
        }
        container.addView(btnDismiss)

        val lp = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
            val margin = (24 * density).toInt()
            setMargins(margin, 0, margin, 0)
        }
        addView(container, lp)
    }
}
