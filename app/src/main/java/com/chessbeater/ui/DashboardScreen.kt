package com.chessbeater.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessbeater.engine.models.EngineType

// Color Palette for Chess Beater Dark Sleek Theme
val DarkBackground = Color(0xFF10141C)
val CardBackground = Color(0xFF19202C)
val AccentGreen = Color(0xFF00E676)
val AccentBlue = Color(0xFF2979FF)
val AccentGold = Color(0xFFFFD600)
val DangerRed = Color(0xFFFF1744)
val TextPrimary = Color(0xFFECEFF4)
val TextSecondary = Color(0xFF8F9BB3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onSelectEngine: (EngineType) -> Unit,
    onSelectTargetApp: (com.chessbeater.vision.models.ChessAppTarget) -> Unit,
    onSelectInstalledApp: (com.chessbeater.data.InstalledAppInfo) -> Unit = {},
    onCalibrateClicked: () -> Unit = {},
    onPowerChanged: (Int) -> Unit,
    onToggleCanvasArrow: (Boolean) -> Unit,
    onToggleFloatingHud: (Boolean) -> Unit,
    onToggleStealthToast: (Boolean) -> Unit,
    onToggleHaptic: (Boolean) -> Unit = {},
    onToggleAutoLaunch: (Boolean) -> Unit = {},
    onToggleMiniBoard: (Boolean) -> Unit = {},
    onToggleGhostMode: (Boolean) -> Unit = {},
    onToggleTouchForwarding: (Boolean) -> Unit = {},
    onToggleQuickAlignment: (Boolean) -> Unit = {},
    onToggleSaveSessionLogs: (Boolean) -> Unit = {},
    onDeleteLog: (java.io.File) -> Unit = {},
    onClearAllLogs: () -> Unit = {},
    onStartVisionServiceClicked: () -> Unit,
    onStartMiniBoardServiceClicked: () -> Unit,
    onStopServiceClicked: () -> Unit,
    onOpenFullSettingsClicked: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedLogFile by remember { mutableStateOf<java.io.File?>(null) }
    var selectedLogContent by remember { mutableStateOf("") }




    var showAppPickerDialog by remember { mutableStateOf(false) }

    if (showAppPickerDialog) {
        AppPickerDialog(
            onDismissRequest = { showAppPickerDialog = false },
            onAppSelected = { appInfo ->
                onSelectInstalledApp(appInfo)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Chess Beater",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "v1.0.0",
                            fontSize = 12.sp,
                            color = AccentGreen,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                actions = {
                    IconButton(onClick = { /* Settings / Info */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                    }
                }
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. TARGET CHESS APP / VISION PRESET
            SectionHeader(title = "TARGET CHESS APP / VISION PRESET", icon = Icons.Default.CropFree)
            Spacer(modifier = Modifier.height(8.dp))

            // Installed App Scanner Button Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.selectedAppName.isNotEmpty()) Color(0xFF162D22) else CardBackground
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAppPickerDialog = true }
                    .border(
                        1.5.dp,
                        if (uiState.selectedAppName.isNotEmpty()) AccentGreen else AccentBlue,
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = if (uiState.selectedAppName.isNotEmpty()) AccentGreen else AccentBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (uiState.selectedAppName.isNotEmpty()) "Target: ${uiState.selectedAppName}" else "Pilih dari Aplikasi Terpasang",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (uiState.selectedAppPackage.isNotEmpty()) uiState.selectedAppPackage else "Pindai seluruh aplikasi catur di perangkat",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                    Button(
                        onClick = { showAppPickerDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.selectedAppName.isNotEmpty()) AccentGreen.copy(alpha = 0.2f) else AccentBlue.copy(alpha = 0.2f)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (uiState.selectedAppName.isNotEmpty()) "Ganti" else "Pilih",
                            color = if (uiState.selectedAppName.isNotEmpty()) AccentGreen else AccentBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            ChessAppOptionCard(
                title = "Chess.com (Mobile App / Web)",
                subtitle = "Standard 1:1 center crop • Green & Wood themes",
                isSelected = uiState.targetApp == com.chessbeater.vision.models.ChessAppTarget.CHESS_COM,
                badge = "Optimized",
                onClick = { onSelectTargetApp(com.chessbeater.vision.models.ChessAppTarget.CHESS_COM) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ChessAppOptionCard(
                title = "Lichess (Mobile App / Web)",
                subtitle = "Lichess layout crop • Brown & Blue boards",
                isSelected = uiState.targetApp == com.chessbeater.vision.models.ChessAppTarget.LICHESS,
                badge = "Fast",
                onClick = { onSelectTargetApp(com.chessbeater.vision.models.ChessAppTarget.LICHESS) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ChessAppOptionCard(
                title = "Universal (Auto-Detection)",
                subtitle = "Dynamic OpenCV contour detection with auto fallback",
                isSelected = uiState.targetApp == com.chessbeater.vision.models.ChessAppTarget.UNIVERSAL_AUTO,
                badge = "Dynamic",
                onClick = { onSelectTargetApp(com.chessbeater.vision.models.ChessAppTarget.UNIVERSAL_AUTO) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Calibration Trigger Button
            OutlinedButton(
                onClick = onCalibrateClicked,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentGold),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF1F1C10)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.SquareFoot,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "📐 Kalibrasi Manual Area Papan Catur",
                    color = AccentGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Open Full Settings Button
            Button(
                onClick = onOpenFullSettingsClicked,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E293B)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "⚙️ Pengaturan Lengkap & Tampilan",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))



            // 2. ENGINE SELECTION SECTION (PRD 6.1)
            SectionHeader(title = "ENGINE SELECTION", icon = Icons.Default.Memory)
            Spacer(modifier = Modifier.height(8.dp))

            EngineOptionCard(
                title = "Stockfish 16.1 NNUE",
                subtitle = "Recommended • Hybrid Alpha-Beta + Neural Net",
                isSelected = uiState.selectedEngine == EngineType.STOCKFISH,
                badge = "3500+ ELO",
                onClick = { onSelectEngine(EngineType.STOCKFISH) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            EngineOptionCard(
                title = "Leela Chess Zero (Lc0)",
                subtitle = "AlphaZero Deep Neural Network Architecture",
                isSelected = uiState.selectedEngine == EngineType.LC0_ALPHAZERO,
                badge = "Positional AI",
                onClick = { onSelectEngine(EngineType.LC0_ALPHAZERO) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            EngineOptionCard(
                title = "Deep Blue 1997 Classic",
                subtitle = "Pure Classical Minimax • Material & PST Table",
                isSelected = uiState.selectedEngine == EngineType.DEEP_BLUE_CLASSIC,
                badge = "Retro 90s",
                onClick = { onSelectEngine(EngineType.DEEP_BLUE_CLASSIC) }
            )


            Spacer(modifier = Modifier.height(20.dp))

            // 2. ENGINE STRENGTH & ELO RATING (PRD 6.1)
            SectionHeader(title = "ENGINE STRENGTH / ELO RATING", icon = Icons.Default.Speed)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.estimatedElo} ELO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = AccentGold
                        )
                        Text(
                            text = uiState.eloRankTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Slider(
                        value = uiState.powerPercentage.toFloat(),
                        onValueChange = { onPowerChanged(it.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentGreen,
                            activeTrackColor = AccentGreen,
                            inactiveTrackColor = Color(0xFF2C384B)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryBadge(label = "Depth", value = "${uiState.searchDepth} ply")
                        TelemetryBadge(label = "Time Limit", value = "${uiState.moveTimeMs}ms")
                        TelemetryBadge(label = "Threads", value = "2 Native")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. VISUAL OVERLAY & NOTIFICATION MODE (PRD 6.1)
            SectionHeader(title = "VISUAL OVERLAY MODE", icon = Icons.Default.Visibility)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    ToggleRow(
                        title = "Draw Real-Time Canvas Arrow",
                        subtitle = "Dynamic vector overlay with adaptive quality color",
                        isChecked = uiState.showCanvasArrow,
                        onCheckedChange = onToggleCanvasArrow
                    )
                    Divider(color = Color(0xFF232D3F), thickness = 1.dp)
                    ToggleRow(
                        title = "Show Floating Evaluation Bar HUD",
                        subtitle = "Draggable widget with score & notation pill",
                        isChecked = uiState.showFloatingHud,
                        onCheckedChange = onToggleFloatingHud
                    )
                    Divider(color = Color(0xFF232D3F), thickness = 1.dp)
                    ToggleRow(
                        title = "Compact Toast Mode (Stealth)",
                        subtitle = "Minimal 1-second floating notification",
                        isChecked = uiState.isStealthToastMode,
                        onCheckedChange = onToggleStealthToast
                    )
                    Divider(color = Color(0xFF232D3F), thickness = 1.dp)
                    ToggleRow(
                        title = "Haptic Notification on Blunder Risk",
                        subtitle = "Tactile Morse & discrete vibration alerts",
                        isChecked = uiState.isHapticAlertEnabled,
                        onCheckedChange = onToggleHaptic
                    )
                    Divider(color = Color(0xFF232D3F), thickness = 1.dp)
                    ToggleRow(
                        title = "Buka Aplikasi Catur Otomatis",
                        subtitle = "Otomatis meluncurkan aplikasi catur saat service start",
                        isChecked = uiState.autoLaunchTargetApp,
                        onCheckedChange = onToggleAutoLaunch
                    )
                    Divider(color = Color(0xFF232D3F), thickness = 1.dp)
                    ToggleRow(
                        title = "♟ Papan Catur Mini Melayang",
                        subtitle = "Papan interaktif 8x8 manual 100% akurat & independen dari capture",
                        isChecked = uiState.showInteractiveMiniBoard,
                        onCheckedChange = onToggleMiniBoard
                    )
                    Divider(color = Color(0xFF232D3F), thickness = 1.dp)
                    ToggleRow(
                        title = "👻 Mode Transparan Penuh (Ghost Mode)",
                        subtitle = "Petak transparan tipis menempel 1:1 di atas papan game asli",
                        isChecked = uiState.isGhostMode,
                        onCheckedChange = onToggleGhostMode
                    )
                    Divider(color = Color(0xFF232D3F), thickness = 1.dp)
                    ToggleRow(
                        title = "👆 Teruskan Sentuhan ke Game Asli (Touch Forwarding)",
                        subtitle = "Sentuhan di papan mini otomatis diteruskan ke game catur di bawahnya",
                        isChecked = uiState.isTouchForwardingEnabled,
                        onCheckedChange = onToggleTouchForwarding
                    )
                    Divider(color = Color(0xFF232D3F), thickness = 1.dp)
                    ToggleRow(
                        title = "⚡ Kalibrasi Cepat Saat Startup",
                        subtitle = "Tampilkan grid alignment HUD otomatis saat service aktif",
                        isChecked = uiState.isQuickAlignmentEnabled,
                        onCheckedChange = onToggleQuickAlignment
                    )
                    Divider(color = Color(0xFF232D3F), thickness = 1.dp)
                    ToggleRow(
                        title = "⏺️ Rekam Log Sesi Pertandingan (Session Logs)",
                        subtitle = "Simpan pergerakan, notasi FEN, dan evaluasi bestmove ke file log internal",
                        isChecked = uiState.isSaveSessionLogsEnabled,
                        onCheckedChange = onToggleSaveSessionLogs
                    )
                }
            }




            Spacer(modifier = Modifier.height(20.dp))

            // 4. STATUS BADGE (PRD 6.1)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isServiceRunning) Color(0xFF102820) else Color(0xFF201820)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (uiState.isServiceRunning) AccentGreen.copy(alpha = 0.5f) else DangerRed.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (uiState.isServiceRunning) AccentGreen else DangerRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isServiceRunning) "SERVICE: ACTIVE" else "SERVICE: INACTIVE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "Latency: ${uiState.latencyMs}ms",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. SAVED LOGS & SESSION RECORDER
            SectionHeader(title = "📁 SAVED LOGS & SESSION RECORDER", icon = Icons.Default.FolderSpecial)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daftar Sesi Pertandingan (${uiState.savedLogsList.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        if (uiState.savedLogsList.isNotEmpty()) {
                            TextButton(
                                onClick = onClearAllLogs,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Hapus Semua", color = DangerRed, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.savedLogsList.isEmpty()) {
                        Text(
                            text = if (uiState.isSaveSessionLogsEnabled)
                                "Belum ada log sesi tercatat. Log akan otomatis tersimpan saat Anda bermain catur."
                            else
                                "Fitur pencatatan log nonaktif. Aktifkan switch 'Rekam Log Sesi Pertandingan' di atas untuk mulai mencatat.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    } else {
                        uiState.savedLogsList.forEach { logItem ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF131923)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = logItem.formattedDate,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = AccentGreen
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${logItem.sizeText} • ${logItem.moveCount} Langkah",
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                val logText = com.chessbeater.logging.SessionLogger.readLogContent(logItem.file)
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("ChessBeater Log - ${logItem.fileName}", logText)
                                                clipboard.setPrimaryClip(clip)
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "📋 Log (${logItem.fileName}) berhasil disalin!",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Salin Log",
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(2.dp))
                                        IconButton(
                                            onClick = {
                                                selectedLogFile = logItem.file
                                                selectedLogContent = com.chessbeater.logging.SessionLogger.readLogContent(logItem.file)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Visibility,
                                                contentDescription = "Lihat Log",
                                                tint = AccentBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(2.dp))
                                        IconButton(
                                            onClick = { onDeleteLog(logItem.file) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Hapus Log",
                                                tint = DangerRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (selectedLogFile != null) {
                AlertDialog(
                    onDismissRequest = { selectedLogFile = null },
                    title = {
                        Text(
                            text = "Isi Log: ${selectedLogFile?.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    },
                    text = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = selectedLogContent,
                                fontSize = 11.sp,
                                color = Color(0xFFC9D1D9),
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { selectedLogFile = null },
                            colors = ButtonDefaults.buttonColors(containerColor = CardBackground)
                        ) {
                            Text("Tutup", color = Color.White)
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("ChessBeater Log - ${selectedLogFile?.name}", selectedLogContent)
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(
                                    context,
                                    "📋 Seluruh isi log berhasil disalin ke clipboard!",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salin Semua", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = Color(0xFF161B22)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. DUAL SERVICE BUTTONS
            if (!uiState.isVisionServiceRunning && !uiState.isMiniBoardServiceRunning) {
                // --- Vision AI Mode ---
                Button(
                    onClick = onStartVisionServiceClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MODE DETEKSI OTOMATIS (Vision AI)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                // --- Mini Board Relay Mode ---
                Button(
                    onClick = onStartMiniBoardServiceClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(
                        text = "♟  MODE MINI CHESSBOARD (Manual Relay)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            } else if (uiState.isVisionServiceRunning) {
                Button(
                    onClick = onStopServiceClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "STOP VISION AI SERVICE", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
            } else if (uiState.isMiniBoardServiceRunning) {
                Button(
                    onClick = onStopServiceClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "STOP MINI BOARD SERVICE", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}


@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AccentBlue,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun EngineOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    badge: String,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) AccentGreen else Color(0xFF273142)
    val bgColor = if (isSelected) Color(0xFF172422) else CardBackground

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(selectedColor = AccentGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = subtitle, color = TextSecondary, fontSize = 12.sp)
                }
            }
            Surface(
                color = if (isSelected) AccentGreen.copy(alpha = 0.2f) else Color(0xFF202A38),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = badge,
                    color = if (isSelected) AccentGreen else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentGreen,
                uncheckedTrackColor = Color(0xFF2C384B)
            )
        )
    }
}

@Composable
fun TelemetryBadge(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 11.sp, color = TextSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun ChessAppOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    badge: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF162534) else CardBackground
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) AccentBlue else Color(0xFF232D3F),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(selectedColor = AccentBlue)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = subtitle, color = TextSecondary, fontSize = 11.sp)
                }
            }
            Surface(
                color = if (isSelected) AccentBlue.copy(alpha = 0.2f) else Color(0xFF202A38),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = badge,
                    color = if (isSelected) AccentBlue else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

