package com.chessbeater.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessbeater.data.BoardPreferencesRepository
import com.chessbeater.data.BoardVisualPreferences
import com.chessbeater.data.EnginePreferencesRepository
import com.chessbeater.utils.AppLogger
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {

    private val createLogDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val success = AppLogger.exportLogToUri(this, uri)
            if (success) {
                Toast.makeText(this, "✅ File log berhasil disimpan!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "❌ Gagal menyimpan file log.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SettingsScreen(
                onBack = { finish() },
                onExportLogs = {
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val defaultFileName = "ChessBeater_Log_$timeStamp.txt"
                    createLogDocumentLauncher.launch(defaultFileName)
                },
                onClearLogs = {
                    AppLogger.clearLogs()
                    Toast.makeText(this, "🧹 Log aktivitas telah dikosongkan.", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

enum class SettingsActivityTab {
    DISPLAY_ENGINE,
    ANTI_CHEAT,
    SYSTEM_LOGS
}

data class ColorOption(val name: String, val hexInt: Int, val color: Color)

val FROM_COLOR_OPTIONS = listOf(
    ColorOption("Cyan", AndroidColor.parseColor("#00E5FF"), Color(0xFF00E5FF)),
    ColorOption("Kuning", AndroidColor.parseColor("#FACC15"), Color(0xFFFACC15)),
    ColorOption("Orange", AndroidColor.parseColor("#FB923C"), Color(0xFFFB923C)),
    ColorOption("Putih", AndroidColor.parseColor("#FFFFFF"), Color(0xFFFFFFFF))
)

val TO_COLOR_OPTIONS = listOf(
    ColorOption("Hijau", AndroidColor.parseColor("#10B981"), Color(0xFF10B981)),
    ColorOption("Lime", AndroidColor.parseColor("#84CC16"), Color(0xFF84CC16)),
    ColorOption("Magenta", AndroidColor.parseColor("#EC4899"), Color(0xFFEC4899)),
    ColorOption("Biru", AndroidColor.parseColor("#38BDF8"), Color(0xFF38BDF8))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onExportLogs: () -> Unit = {},
    onClearLogs: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences(com.chessbeater.utils.PrefKeys.PREF_NAME, Context.MODE_PRIVATE) }
    val visualPrefs = remember { context.getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE) }
    val boardRepo = remember { BoardPreferencesRepository(context) }

    var selectedTab by remember { mutableStateOf(SettingsActivityTab.DISPLAY_ENGINE) }

    // State Visual Preferences
    var eloRating by remember { mutableStateOf(com.chessbeater.engine.EngineSettingsManager.getTargetElo(context)) }
    var isBulletMode by remember { mutableStateOf(com.chessbeater.engine.EngineSettingsManager.isBulletMode(context)) }
    var isHighlightFilled by remember { mutableStateOf(visualPrefs.getBoolean("highlight_is_filled", true)) }
    var fromColor by remember { mutableStateOf(visualPrefs.getSafeInt("color_highlight_from", AndroidColor.parseColor("#00E5FF"))) }
    var toColor by remember { mutableStateOf(visualPrefs.getSafeInt("color_highlight_to", AndroidColor.parseColor("#10B981"))) }

    // Button Scaling States
    var floatingEyeSizeDp by remember { mutableStateOf(visualPrefs.getSafeInt("size_floating_eye_dp", 44).coerceIn(28, 64)) }
    var headerEyeSizeDp by remember { mutableStateOf(visualPrefs.getSafeInt("size_header_eye_dp", 34).coerceIn(24, 52)) }
    var headerMenuSizeDp by remember { mutableStateOf(visualPrefs.getSafeInt("size_header_menu_dp", 34).coerceIn(24, 52)) }

    var pieceAlpha by remember { mutableStateOf(visualPrefs.getSafeFloat("alpha_pieces", visualPrefs.getSafeFloat("piece_alpha", 1.0f)).coerceIn(0.05f, 1.0f)) }
    var gridAlpha by remember { mutableStateOf(visualPrefs.getSafeFloat("board_alpha", visualPrefs.getSafeFloat("grid_alpha", 0.85f)).coerceIn(0.0f, 1.0f)) }
    var arrowAlpha by remember { mutableStateOf(visualPrefs.getSafeFloat("alpha_arrows", visualPrefs.getSafeFloat("arrow_alpha", 0.90f)).coerceIn(0.05f, 1.0f)) }
    var highlightAlpha by remember { mutableStateOf(visualPrefs.getSafeFloat("alpha_highlights", visualPrefs.getSafeFloat("highlight_alpha", 0.50f)).coerceIn(0.05f, 1.0f)) }
    var moveGuideAlpha by remember { mutableStateOf(visualPrefs.getSafeFloat("alpha_dots", visualPrefs.getSafeFloat("move_guide_alpha", 0.80f)).coerceIn(0.05f, 1.0f)) }
    var floatingEyeAlpha by remember { mutableStateOf(visualPrefs.getSafeFloat("alpha_floating_eye", visualPrefs.getSafeFloat("floating_eye_alpha", 0.85f)).coerceIn(0.05f, 1.0f)) }

    var isBoardLocked by remember { mutableStateOf(visualPrefs.getBoolean("board_is_locked", true)) }
    var isGhostControlsEnabled by remember { mutableStateOf(visualPrefs.getBoolean("ghost_controls_enabled", false)) }
    var isAutoHideEnabled by remember { mutableStateOf(com.chessbeater.engine.EngineSettingsManager.isAutoHideEnabled(context)) }
    var autoHideDelayFloat by remember { mutableStateOf(com.chessbeater.engine.EngineSettingsManager.getAutoHideDelaySec(context)) }
    var autoHideDelaySec by remember { mutableStateOf(if (isAutoHideEnabled) autoHideDelayFloat.toInt() else -1) }
    var isAutoShowEnabled by remember { mutableStateOf(com.chessbeater.engine.EngineSettingsManager.isAutoShowEnabled(context)) }
    var autoShowDelayFloat by remember { mutableStateOf(com.chessbeater.engine.EngineSettingsManager.getAutoShowDelaySec(context)) }
    var autoShowDelaySec by remember { mutableStateOf(autoShowDelayFloat.toInt()) }

    // State Anti-Cheat Preferences
    var humanizeLevel by remember { mutableStateOf(visualPrefs.getSafeInt("humanize_level", 3).coerceIn(0, 10)) }
    var isHumanizeEnabled by remember { mutableStateOf(visualPrefs.getBoolean("humanize_enabled", true)) }
    var isBlunderGuardEnabled by remember { mutableStateOf(visualPrefs.getBoolean("blunder_guard_enabled", true)) }
    var isNaturalDelayEnabled by remember { mutableStateOf(visualPrefs.getBoolean("natural_delay_enabled", true)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                eloRating = com.chessbeater.engine.EngineSettingsManager.getTargetElo(context)
                isBulletMode = com.chessbeater.engine.EngineSettingsManager.isBulletMode(context)
                isAutoHideEnabled = com.chessbeater.engine.EngineSettingsManager.isAutoHideEnabled(context)
                autoHideDelayFloat = com.chessbeater.engine.EngineSettingsManager.getAutoHideDelaySec(context)
                autoHideDelaySec = if (isAutoHideEnabled) autoHideDelayFloat.toInt() else -1
                isAutoShowEnabled = com.chessbeater.engine.EngineSettingsManager.isAutoShowEnabled(context)
                autoShowDelayFloat = com.chessbeater.engine.EngineSettingsManager.getAutoShowDelaySec(context)
                autoShowDelaySec = autoShowDelayFloat.toInt()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun saveVisualPrefs() {
        com.chessbeater.engine.EngineSettingsManager.saveTargetElo(context, eloRating)
        com.chessbeater.engine.EngineSettingsManager.saveBulletMode(context, isBulletMode)
        com.chessbeater.engine.EngineSettingsManager.saveAutoHideEnabled(context, isAutoHideEnabled)
        com.chessbeater.engine.EngineSettingsManager.saveAutoHideDelaySec(context, autoHideDelayFloat)
        com.chessbeater.engine.EngineSettingsManager.saveAutoShowEnabled(context, isAutoShowEnabled)
        com.chessbeater.engine.EngineSettingsManager.saveAutoShowDelaySec(context, autoShowDelayFloat)
        com.chessbeater.engine.StockfishBridge.getInstance(context).applyEloConfiguration(eloRating)
        sharedPrefs.edit()
            .putInt("engine_target_elo", eloRating)
            .putBoolean("engine_bullet_mode", isBulletMode)
            .putInt("target_elo", eloRating)
            .putInt("max_elo_rating", eloRating)
            .putInt("elo_rating", eloRating)
            .putBoolean("highlight_is_filled", isHighlightFilled)
            .putInt("color_highlight_from", fromColor)
            .putInt("color_highlight_to", toColor)
            .putInt("size_floating_eye_dp", floatingEyeSizeDp)
            .putInt("size_header_eye_dp", headerEyeSizeDp)
            .putInt("size_header_menu_dp", headerMenuSizeDp)
            .putFloat("alpha_pieces", pieceAlpha)
            .putFloat("piece_alpha", pieceAlpha)
            .putFloat("pieces_alpha", pieceAlpha)
            .putFloat("board_alpha", gridAlpha)
            .putFloat("grid_alpha", gridAlpha)
            .putFloat("alpha_arrows", arrowAlpha)
            .putFloat("arrow_alpha", arrowAlpha)
            .putFloat("alpha_highlights", highlightAlpha)
            .putFloat("highlight_alpha", highlightAlpha)
            .putFloat("alpha_dots", moveGuideAlpha)
            .putFloat("move_guide_alpha", moveGuideAlpha)
            .putFloat("guide_dots_alpha", moveGuideAlpha)
            .putFloat("alpha_floating_eye", floatingEyeAlpha)
            .putFloat("floating_eye_alpha", floatingEyeAlpha)
            .putBoolean("board_is_locked", isBoardLocked)
            .putBoolean("ghost_controls_enabled", isGhostControlsEnabled)
            .putBoolean("auto_hide_enabled", isAutoHideEnabled)
            .putFloat("auto_hide_delay_sec", autoHideDelayFloat)
            .putInt("auto_hide_delay_sec", if (isAutoHideEnabled) autoHideDelayFloat.toInt() else -1)
            .putBoolean("auto_show_enabled", isAutoShowEnabled)
            .putBoolean("is_auto_show_enabled", isAutoShowEnabled)
            .putFloat("auto_show_delay_sec", autoShowDelayFloat)
            .putInt("auto_show_delay_sec", autoShowDelayFloat.toInt())
            .putInt("humanize_level", humanizeLevel)
            .putBoolean("humanize_enabled", isHumanizeEnabled)
            .putBoolean("blunder_guard_enabled", isBlunderGuardEnabled)
            .putBoolean("natural_delay_enabled", isNaturalDelayEnabled)
            .apply()

        coroutineScope.launch {
            val visual = BoardVisualPreferences(
                eloRating = eloRating,
                gridAlpha = gridAlpha,
                pieceAlpha = pieceAlpha,
                highlightAlpha = highlightAlpha,
                arrowAlpha = arrowAlpha,
                floatingEyeAlpha = floatingEyeAlpha,
                eyeSizeDp = floatingEyeSizeDp,
                autoHideDelaySec = autoHideDelaySec,
                isAutoShowEnabled = isAutoShowEnabled,
                autoShowDelaySec = autoShowDelaySec
            )
            boardRepo.saveVisualPreferences(visual)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "⚙️ Pengaturan & Kustomisasi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF131923))
            )
        },
        containerColor = Color(0xFF0B0E14)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .background(Color(0xFF161E2E), RoundedCornerShape(10.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedTab == SettingsActivityTab.DISPLAY_ENGINE) Color(0xFF2563EB) else Color.Transparent)
                        .clickable { selectedTab = SettingsActivityTab.DISPLAY_ENGINE }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎮 Tampilan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (selectedTab == SettingsActivityTab.DISPLAY_ENGINE) Color.White else Color(0xFF94A3B8)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedTab == SettingsActivityTab.ANTI_CHEAT) Color(0xFF059669) else Color.Transparent)
                        .clickable { selectedTab = SettingsActivityTab.ANTI_CHEAT }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🛡️ Anti-Cheat",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (selectedTab == SettingsActivityTab.ANTI_CHEAT) Color.White else Color(0xFF94A3B8)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedTab == SettingsActivityTab.SYSTEM_LOGS) Color(0xFFD97706) else Color.Transparent)
                        .clickable { selectedTab = SettingsActivityTab.SYSTEM_LOGS }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📁 Audit Log",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (selectedTab == SettingsActivityTab.SYSTEM_LOGS) Color.White else Color(0xFF94A3B8)
                    )
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (selectedTab == SettingsActivityTab.DISPLAY_ENGINE) {
                    // --- TAB 1 CONTENT ---

                    // Section: LIVE PREVIEW & TOMBOL KONTROL UKURAN
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161E2E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("👁️ Ukuran & Tombol Kontrol (Live Preview)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF00E676))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Live Preview Box Container
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(115.dp)
                                    .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Floating Eye Preview
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(floatingEyeSizeDp.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF121A26).copy(alpha = floatingEyeAlpha))
                                            .border(2.dp, Color(0xFF00E676), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("👁", fontSize = (floatingEyeSizeDp * 0.45f).sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Floating Eye", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                                }

                                // 2. Header Eye Button Preview
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(headerEyeSizeDp.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E293B))
                                            .border(1.5.dp, Color(0xFF00E5FF), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("👁", fontSize = (headerEyeSizeDp * 0.50f).sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Tombol Sembunyi", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                                }

                                // 3. Header Menu Button Preview
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(headerMenuSizeDp.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E293B))
                                            .border(1.5.dp, Color(0xFF10B981), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("⋮", fontSize = (headerMenuSizeDp * 0.55f).sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Tombol Menu", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 1. Slider Ukuran Floating Eye
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Ukuran Floating Eye:", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                                    Text("[ $floatingEyeSizeDp dp ]", fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = floatingEyeSizeDp.toFloat(),
                                    onValueChange = { floatingEyeSizeDp = it.roundToInt() },
                                    onValueChangeFinished = { saveVisualPrefs() },
                                    valueRange = 28f..64f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF00E676),
                                        activeTrackColor = Color(0xFF059669)
                                    )
                                )
                            }

                            // 2. Slider Ukuran Tombol Sembunyi
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Ukuran Tombol Sembunyi (Mata):", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                                    Text("[ $headerEyeSizeDp dp ]", fontSize = 12.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = headerEyeSizeDp.toFloat(),
                                    onValueChange = { headerEyeSizeDp = it.roundToInt() },
                                    onValueChangeFinished = { saveVisualPrefs() },
                                    valueRange = 24f..52f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF00E5FF),
                                        activeTrackColor = Color(0xFF0284C7)
                                    )
                                )
                            }

                            // 3. Slider Ukuran Tombol Menu
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Ukuran Tombol Menu (Titik Tiga):", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                                    Text("[ $headerMenuSizeDp dp ]", fontSize = 12.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = headerMenuSizeDp.toFloat(),
                                    onValueChange = { headerMenuSizeDp = it.roundToInt() },
                                    onValueChangeFinished = { saveVisualPrefs() },
                                    valueRange = 24f..52f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF10B981),
                                        activeTrackColor = Color(0xFF059669)
                                    )
                                )
                            }
                        }
                    }

                    // Section: Target ELO & Bullet Mode
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161E2E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("⚡ Bullet Mode (Fast Play)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text("Batasi kalkulasi (max 1200ms) & Auto-Hide/Show ke 1.0s", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                                Switch(
                                    checked = isBulletMode,
                                    onCheckedChange = { checked ->
                                        isBulletMode = checked
                                        if (checked) {
                                            autoHideDelayFloat = 1.0f
                                            autoHideDelaySec = 1
                                            autoShowDelayFloat = 1.0f
                                            autoShowDelaySec = 1
                                        }
                                        saveVisualPrefs()
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFF1E293B))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🎯 Target Engine Power: [ $eloRating ELO ]", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                Text("$eloRating ELO", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF38BDF8))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                com.chessbeater.utils.EloInfoFormatter.formatEloDetails(eloRating, isBulletMode),
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = eloRating.toFloat(),
                                onValueChange = { eloRating = it.roundToInt().coerceIn(800, 3500) },
                                onValueChangeFinished = { saveVisualPrefs() },
                                valueRange = 800f..3500f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF38BDF8),
                                    activeTrackColor = Color(0xFF0284C7)
                                )
                            )
                        }
                    }

                    // Section: Gaya Highlight
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161E2E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🎨 Gaya Highlight Petak", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { isHighlightFilled = true; saveVisualPrefs() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isHighlightFilled) Color(0xFF10B981) else Color(0xFF1E293B)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🟩 Filled (Penuh)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { isHighlightFilled = false; saveVisualPrefs() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isHighlightFilled) Color(0xFF10B981) else Color(0xFF1E293B)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🔲 Outlined (Garis)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Section: KUSTOMISASI WARNA HIGHLIGHT
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161E2E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🎨 Kustomisasi Warna Petak Highlight", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF00E676))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Warna Kotak Asal
                            Text("Petak Asal (From Square):", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FROM_COLOR_OPTIONS.forEach { opt ->
                                    val isSelected = fromColor == opt.hexInt
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0F172A))
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF00E676) else Color(0xFF334155),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                fromColor = opt.hexInt
                                                saveVisualPrefs()
                                                Toast.makeText(context, "Warna petak asal: ${opt.name}", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(opt.color))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(opt.name, fontSize = 11.sp, color = Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Warna Kotak Tujuan
                            Text("Petak Tujuan (To Square):", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TO_COLOR_OPTIONS.forEach { opt ->
                                    val isSelected = toColor == opt.hexInt
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0F172A))
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF00E676) else Color(0xFF334155),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                toColor = opt.hexInt
                                                saveVisualPrefs()
                                                Toast.makeText(context, "Warna petak tujuan: ${opt.name}", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(opt.color))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(opt.name, fontSize = 11.sp, color = Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section: 6 Transparansi Sliders
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161E2E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🎨 Transparansi Detail Elemen", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            Spacer(modifier = Modifier.height(10.dp))

                            // 1. Move Guide
                            TransparencySliderRow(
                                title = "🎯 Titik Panduan Langkah",
                                value = moveGuideAlpha,
                                onValueChange = { moveGuideAlpha = it },
                                onFinished = { saveVisualPrefs() }
                            )

                            // 2. Bidak
                            TransparencySliderRow(
                                title = "♟️ Transparansi Bidak",
                                value = pieceAlpha,
                                onValueChange = { pieceAlpha = it },
                                onFinished = { saveVisualPrefs() }
                            )

                            // 3. Grid
                            TransparencySliderRow(
                                title = "🏁 Transparansi Papan / Grid",
                                value = gridAlpha,
                                onValueChange = { gridAlpha = it },
                                onFinished = { saveVisualPrefs() }
                            )

                            // 4. Panah
                            TransparencySliderRow(
                                title = "➔ Panah Rekomendasi",
                                value = arrowAlpha,
                                onValueChange = { arrowAlpha = it },
                                onFinished = { saveVisualPrefs() }
                            )

                            // 5. Highlight
                            TransparencySliderRow(
                                title = "🟩 Highlight Petak",
                                value = highlightAlpha,
                                onValueChange = { highlightAlpha = it },
                                onFinished = { saveVisualPrefs() }
                            )

                            // 6. Floating Eye
                            TransparencySliderRow(
                                title = "👁️ Tombol Floating Eye",
                                value = floatingEyeAlpha,
                                onValueChange = { floatingEyeAlpha = it },
                                onFinished = { saveVisualPrefs() }
                            )
                        }
                    }

                    // Section: Lock & Ghost Toggles
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161E2E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("🔒 Kunci Posisi Papan", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text("Cegah pergeseran papan akibat gesture sentuh tidak disengaja", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                                Switch(
                                    checked = isBoardLocked,
                                    onCheckedChange = {
                                        isBoardLocked = it
                                        saveVisualPrefs()
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFF1E293B))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("👻 Ghost Controls", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text("Sembunyikan header & footer agar tampilan 100% transparan", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                                Switch(
                                    checked = isGhostControlsEnabled,
                                    onCheckedChange = {
                                        isGhostControlsEnabled = it
                                        saveVisualPrefs()
                                    }
                                )
                            }
                        }
                    }

                    // Section: Auto-Hide & Auto-Show Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161E2E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("⏱️ Otomasi Sembunyi / Tampil", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.height(10.dp))

                            // 1. Auto-Hide
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("⏳ Auto-Hide Papan", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text("Sembunyi otomatis setelah mesin selesai melangkah", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                                Switch(
                                    checked = isAutoHideEnabled,
                                    onCheckedChange = {
                                        isAutoHideEnabled = it
                                        autoHideDelaySec = if (it) autoHideDelayFloat.toInt() else -1
                                        saveVisualPrefs()
                                    }
                                )
                            }

                            if (isAutoHideEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Durasi Sembunyi Otomatis", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                                    Text("[ ${String.format(java.util.Locale.US, "%.1f", autoHideDelayFloat)} dtk ]", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                }
                                Slider(
                                    value = autoHideDelayFloat,
                                    onValueChange = {
                                        autoHideDelayFloat = (it * 2).roundToInt() / 2f
                                        autoHideDelaySec = autoHideDelayFloat.toInt()
                                    },
                                    onValueChangeFinished = { saveVisualPrefs() },
                                    valueRange = 0.5f..30.0f,
                                    steps = 58,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF38BDF8),
                                        activeTrackColor = Color(0xFF38BDF8),
                                        inactiveTrackColor = Color(0xFF334155)
                                    )
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFF1E293B))

                            // 2. Auto-Show
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("✨ Auto-Show Papan", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text("Muncul kembali otomatis dari Floating Eye", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                                Switch(
                                    checked = isAutoShowEnabled,
                                    onCheckedChange = {
                                        isAutoShowEnabled = it
                                        saveVisualPrefs()
                                    }
                                )
                            }

                            if (isAutoShowEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Durasi Muncul Kembali", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                                    Text("[ ${String.format(java.util.Locale.US, "%.1f", autoShowDelayFloat)} dtk ]", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                }
                                Slider(
                                    value = autoShowDelayFloat,
                                    onValueChange = {
                                        autoShowDelayFloat = (it * 2).roundToInt() / 2f
                                        autoShowDelaySec = autoShowDelayFloat.toInt()
                                    },
                                    onValueChangeFinished = { saveVisualPrefs() },
                                    valueRange = 0.5f..30.0f,
                                    steps = 58,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF38BDF8),
                                        activeTrackColor = Color(0xFF38BDF8),
                                        inactiveTrackColor = Color(0xFF334155)
                                    )
                                )
                            }
                        }
                    }

                } else if (selectedTab == SettingsActivityTab.ANTI_CHEAT) {
                    // --- TAB 2 CONTENT (ANTI-CHEAT & HUMANIZE) ---

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161E2E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("🛡️ Humanize Move (Anti-Cheat)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF10B981))
                                    Text("Variasi langkah manusiawi agar tidak terdeteksi bot", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                                Switch(
                                    checked = isHumanizeEnabled,
                                    onCheckedChange = {
                                        isHumanizeEnabled = it
                                        saveVisualPrefs()
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Tingkat Humanis: Level $humanizeLevel", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                val desc = when (humanizeLevel) {
                                    0 -> "Grandmaster Solid"
                                    in 1..3 -> "Konsisten Kuat"
                                    in 4..6 -> "Manusiawi Aktif"
                                    in 7..9 -> "Casual & Dinamis"
                                    else -> "Paling Alami"
                                }
                                Text(desc, fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = humanizeLevel.toFloat(),
                                onValueChange = { humanizeLevel = it.roundToInt() },
                                onValueChangeFinished = { saveVisualPrefs() },
                                valueRange = 0f..10f,
                                steps = 9,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF10B981),
                                    activeTrackColor = Color(0xFF059669)
                                )
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161E2E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("🛡️ Blunder Guard", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text("Cegah kesalahan fatal saat memilih variasi non-top engine", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                                Switch(
                                    checked = isBlunderGuardEnabled,
                                    onCheckedChange = {
                                        isBlunderGuardEnabled = it
                                        saveVisualPrefs()
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFF1E293B))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("⏱️ Natural Move Delay", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text("Jeda waktu acak realistis antar langkah (1.5s - 4s)", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                                Switch(
                                    checked = isNaturalDelayEnabled,
                                    onCheckedChange = {
                                        isNaturalDelayEnabled = it
                                        saveVisualPrefs()
                                    }
                                )
                            }
                        }
                    }
                } else if (selectedTab == SettingsActivityTab.SYSTEM_LOGS) {
                    // --- TAB 3 CONTENT: AUDIT ACTIVITY LOG ---
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161E2E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("📁 Audit Log & Aktivitas Sistem", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF38BDF8))
                            Text(
                                "Seluruh interaksi, perubahan konfigurasi slider, perpindahan bidak FEN, perintah Stockfish UCI, dan exception sistem dicatat secara real-time di background (non-blocking IO).",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )

                            Button(
                                onClick = onExportLogs,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text("📁 Simpan / Ekspor File Log (.txt)", fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = onClearLogs,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text("🧹 Bersihkan Log Aktivitas", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun TransparencySliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 12.sp, color = Color(0xFFE2E8F0))
            Text("${(value.coerceIn(0f, 1f) * 100).roundToInt()}%", fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            onValueChangeFinished = onFinished,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF38BDF8),
                activeTrackColor = Color(0xFF0284C7)
            )
        )
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
