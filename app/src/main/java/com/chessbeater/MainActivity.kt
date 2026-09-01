package com.chessbeater

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.chessbeater.capture.ScreenCaptureConfig
import com.chessbeater.capture.ScreenCaptureService
import com.chessbeater.orchestrator.GameOrchestrator
import com.chessbeater.overlay.MiniBoardOverlayService
import com.chessbeater.overlay.OverlayManager
import com.chessbeater.overlay.OverlayService
import com.chessbeater.ui.DashboardScreen
import com.chessbeater.ui.DashboardViewModel
import com.chessbeater.utils.AppLogger
import android.content.SharedPreferences
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var screenCaptureLauncher: ActivityResultLauncher<Intent>? = null
    private var overlayPermissionLauncher: ActivityResultLauncher<Intent>? = null

    private var orchestrator: GameOrchestrator? = null
    private var standaloneOverlayManager: OverlayManager? = null

    private lateinit var composeContainer: ComposeView
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var fabStartServiceCta: FloatingActionButton

    private val configLogListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key != null) {
            val value = prefs.all[key]
            AppLogger.log("CONFIG_CHANGE", "⚙️ Nilai Pengaturan Berubah -> [$key] = $value")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getSharedPreferences("chess_beater_prefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(configLogListener)
        getSharedPreferences("chessbeater_visual_prefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(configLogListener)

        AppLogger.log("NAV", "🏠 MainActivity Dimulai")

        composeContainer = findViewById(R.id.composeContainer)
        bottomNavigationView = findViewById(R.id.bottomNavigationView)
        fabStartServiceCta = findViewById(R.id.fabStartServiceCta)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        registerActivityLaunchers()

        setupBottomNavigation()
        setupCenterCtaFab()

        composeContainer.setContent {
            val uiState by viewModel.uiState.collectAsState()

            // Update FAB color & icon dynamically based on service running state
            androidx.compose.runtime.LaunchedEffect(uiState.isServiceRunning) {
                if (uiState.isServiceRunning) {
                    fabStartServiceCta.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                    fabStartServiceCta.setImageResource(R.drawable.ic_stop)
                } else {
                    fabStartServiceCta.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
                    fabStartServiceCta.setImageResource(R.drawable.ic_play_arrow)
                }
            }

            // Synchronize running orchestrator with current user preferences
            androidx.compose.runtime.LaunchedEffect(uiState.targetApp, uiState.selectedEngine, uiState.powerPercentage) {
                val orchestrator = ScreenCaptureService.activeOrchestrator
                orchestrator?.updateAppProfile(com.chessbeater.vision.models.ChessAppProfile.forTarget(uiState.targetApp))
                orchestrator?.updateEngineConfig(
                    com.chessbeater.engine.models.EngineConfig(
                        engineType = uiState.selectedEngine,
                        powerPercentage = uiState.powerPercentage
                    )
                )
            }

            DashboardScreen(
                uiState = uiState,
                onSelectEngine = { viewModel.selectEngine(it) },
                onSelectTargetApp = { viewModel.selectTargetApp(it) },
                onSelectInstalledApp = { viewModel.selectInstalledApp(it) },
                onCalibrateClicked = { startCalibrationFlow() },
                onPowerChanged = { viewModel.setPowerPercentage(it) },
                onToggleCanvasArrow = { viewModel.toggleCanvasArrow(it) },
                onToggleFloatingHud = { viewModel.toggleFloatingHud(it) },
                onToggleStealthToast = { viewModel.toggleStealthToastMode(it) },
                onToggleAutoLaunch = { viewModel.toggleAutoLaunch(it) },
                onToggleMiniBoard = { viewModel.toggleInteractiveMiniBoard(it) },
                onToggleGhostMode = { viewModel.toggleGhostMode(it) },
                onToggleQuickAlignment = { viewModel.toggleQuickAlignment(it) },
                onToggleSaveSessionLogs = { viewModel.toggleSaveSessionLogs(it) },
                onDeleteLog = { viewModel.deleteLog(it) },
                onClearAllLogs = { viewModel.clearAllLogs() },
                onStartVisionServiceClicked = { requestOverlayAndStartCapture() },
                onStartMiniBoardServiceClicked = { requestOverlayAndStartMiniBoard() },
                onStopServiceClicked = { stopAllServices() },
                onOpenFullSettingsClicked = {
                    startActivity(Intent(this@MainActivity, com.chessbeater.ui.SettingsActivity::class.java))
                },
                onOpenPlaygroundClicked = {
                    startActivity(Intent(this@MainActivity, com.chessbeater.playground.PlaygroundActivity::class.java))
                }
            )
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_target_app -> {
                    AppLogger.log("NAV", "📱 Memilih Nav Target App")
                    true
                }
                R.id.nav_engine -> {
                    AppLogger.log("NAV", "⚙️ Memilih Nav Engine Settings")
                    true
                }
                R.id.nav_presets -> {
                    AppLogger.log("NAV", "🎯 Memilih Nav Kalibrasi Papan")
                    startCalibrationFlow()
                    true
                }
                R.id.nav_settings -> {
                    AppLogger.log("NAV", "⚙️ Membuka Menu Settings")
                    startActivity(Intent(this, com.chessbeater.ui.SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupCenterCtaFab() {
        fabStartServiceCta.setOnClickListener {
            val isRunning = viewModel.uiState.value.isServiceRunning
            showServiceActionSheet(isRunning)
        }
    }

    private fun showServiceActionSheet(isServiceRunning: Boolean) {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_service_action, null)
        dialog.setContentView(sheetView)

        val btnMiniBoard = sheetView.findViewById<View>(R.id.btnActionStartMiniBoard)
        val btnVision = sheetView.findViewById<View>(R.id.btnActionStartVision)
        val btnStop = sheetView.findViewById<View>(R.id.btnActionStopService)

        btnStop.visibility = if (isServiceRunning) View.VISIBLE else View.GONE

        btnMiniBoard.setOnClickListener {
            dialog.dismiss()
            requestOverlayAndStartMiniBoard()
        }

        btnVision.setOnClickListener {
            dialog.dismiss()
            requestOverlayAndStartCapture()
        }

        btnStop.setOnClickListener {
            dialog.dismiss()
            stopAllServices()
        }

        dialog.show()
    }






    private fun registerActivityLaunchers() {
        screenCaptureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                startAllServices(result.resultCode, result.data!!)
            } else {
                Toast.makeText(this, "Screen capture permission required to detect board", Toast.LENGTH_SHORT).show()
                viewModel.updateServiceStatus(false)
            }
        }

        overlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (OverlayManager.canDrawOverlays(this)) {
                launchScreenCaptureIntent()
            } else {
                Toast.makeText(this, "Overlay permission is required for on-screen guidance", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestOverlayAndStartCapture() {
        if (!OverlayManager.canDrawOverlays(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher?.launch(intent)
            }
        } else {
            launchScreenCaptureIntent()
        }
    }

    private fun launchScreenCaptureIntent() {
        val captureIntent = mediaProjectionManager?.createScreenCaptureIntent()
        if (captureIntent != null) {
            screenCaptureLauncher?.launch(captureIntent)
        } else {
            Toast.makeText(this, "MediaProjection not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startAllServices(resultCode: Int, data: Intent) {
        val config = ScreenCaptureConfig.createForDevice(this, targetFps = 20)

        // Start ScreenCaptureService Foreground Service (hosts capture, vision, engine, and overlays)
        ScreenCaptureService.start(this, resultCode, data, config)

        viewModel.updateVisionServiceStatus(true)
        Toast.makeText(this, "Chess Beater Vision AI Service Started", Toast.LENGTH_SHORT).show()
    }

    private fun requestOverlayAndStartMiniBoard() {
        if (!OverlayManager.canDrawOverlays(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                // Use a separate launcher that starts mini board after permission granted
                overlayPermissionLauncher?.launch(intent)
            }
        } else {
            startMiniBoardService()
        }
    }

    private fun startMiniBoardService() {
        val intent = Intent(this, MiniBoardOverlayService::class.java).apply {
            action = MiniBoardOverlayService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        viewModel.updateMiniBoardServiceStatus(true)
        Toast.makeText(this, "♟ Mini Board Relay Mode aktif", Toast.LENGTH_SHORT).show()
    }

    private fun stopAllServices() {
        // Stop Vision AI service
        ScreenCaptureService.stop(this)
        // Stop Mini Board service
        val intent = Intent(this, MiniBoardOverlayService::class.java).apply {
            action = MiniBoardOverlayService.ACTION_STOP
        }
        stopService(intent)
        viewModel.updateVisionServiceStatus(false)
        viewModel.updateMiniBoardServiceStatus(false)
        Toast.makeText(this, "Chess Beater Service Dihentikan", Toast.LENGTH_SHORT).show()
    }

    private fun startCalibrationFlow() {
        if (!OverlayManager.canDrawOverlays(this)) {
            requestOverlayAndStartCapture()
            return
        }

        val calibRepo = com.chessbeater.data.CalibrationPreferencesRepository(this)
        val prefRepo = com.chessbeater.data.EnginePreferencesRepository(this)

        lifecycleScope.launch {
            val prefs = prefRepo.userPreferencesFlow.first()
            val pkg = if (prefs.selectedAppPackage.isNotBlank()) prefs.selectedAppPackage else "com.chess"
            val existing = calibRepo.getCalibrationFlow(pkg).first()
            val initialRect = existing?.toRect()

            // 1. Buka aplikasi game catur target secara otomatis
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Gagal membuka aplikasi target: $pkg", e)
            }

            val overlayMgr = ScreenCaptureService.activeOverlayManager ?: standaloneOverlayManager ?: OverlayManager(this@MainActivity).also { standaloneOverlayManager = it }

            overlayMgr.showCalibrationOverlay(
                initialRect = initialRect,
                onSave = { rect ->
                    lifecycleScope.launch {
                        calibRepo.saveCalibration(pkg, rect.left, rect.top, rect.width())
                        val profile = com.chessbeater.vision.models.ChessAppProfile
                            .forTarget(prefs.targetApp)
                            .copy(customCalibratedRect = rect)
                        ScreenCaptureService.activeOrchestrator?.updateAppProfile(profile)
                        Toast.makeText(this@MainActivity, "✅ Kalibrasi berhasil disimpan! (${rect.width()}x${rect.height()})", Toast.LENGTH_SHORT).show()

                        // Bawa kembali MainActivity Chess Beater ke foreground
                        val backIntent = Intent(this@MainActivity, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(backIntent)
                    }
                },
                onCancel = {
                    Toast.makeText(this@MainActivity, "❌ Kalibrasi dibatalkan", Toast.LENGTH_SHORT).show()
                    val backIntent = Intent(this@MainActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(backIntent)
                }
            )
        }
    }

    override fun onDestroy() {
        standaloneOverlayManager?.hideCalibrationOverlay()
        super.onDestroy()
    }
}


