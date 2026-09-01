package com.chessbeater.utils

object EloInfoFormatter {
    fun formatEloDetails(elo: Int, isBullet: Boolean): String {
        val (level, depth, ply, normalTime) = when {
            elo < 1200 -> Quad("🟢 Pemula", "4-6", "8-12", 200)
            elo < 1800 -> Quad("🟡 Menengah", "8-11", "16-22", 400)
            elo < 2400 -> Quad("🟠 Mahir", "12-15", "24-30", 700)
            elo < 2800 -> Quad("🔴 Master", "16-19", "32-38", 1000)
            else -> Quad("👑 Grandmaster (Max)", "20-25+", "40+", 1500)
        }
        val thinkingTime = if (isBullet) normalTime.coerceAtMost(1200) else normalTime
        return "$level | Depth: $depth | Ply: $ply | Max Think: ${thinkingTime}ms"
    }
}

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
