package com.chessbeater.utils

object PrefKeys {
    const val PREF_NAME = "chess_beater_prefs"

    // Engine Keys
    const val KEY_ENGINE_ELO = "engine_target_elo"       // Int (800 - 3500)
    const val KEY_BULLET_MODE = "engine_bullet_mode"     // Boolean

    // Timer Keys
    const val KEY_AUTO_HIDE_ENABLED = "auto_hide_enabled" // Boolean
    const val KEY_AUTO_HIDE_SEC = "auto_hide_delay_sec"  // Float (0.5f - 30.0f)
    const val KEY_AUTO_SHOW_ENABLED = "auto_show_enabled" // Boolean
    const val KEY_AUTO_SHOW_SEC = "auto_show_delay_sec"  // Float (0.5f - 30.0f)

    // Visual Alpha Keys (Float 0.0f - 1.0f)
    const val KEY_ALPHA_PIECES = "alpha_pieces"
    const val KEY_ALPHA_BOARD = "board_alpha"
    const val KEY_ALPHA_ARROWS = "alpha_arrows"
    const val KEY_ALPHA_HIGHLIGHTS = "alpha_highlights"
    const val KEY_ALPHA_DOTS = "alpha_dots"
}
