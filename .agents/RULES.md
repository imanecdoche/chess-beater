# ChessBeater — Rules & Instruction Log

## Acuan Utama & Mutlak
File ini adalah catatan instruksi dan aturan mutlak untuk proyek **Chess Beater**.

### Log Instruksi Proyek
1. **Sprint 1 (2026-08-30): NDK Setup, Stockfish Engine C++, dan JNI Bridge**
   - Implementasikan CMakeLists.txt untuk build NDK (arm64-v8a & armeabi-v7a).
   - Implementasikan C++ JNI Wrapper (native-lib.cpp / stockfish-bridge.cpp) dengan non-blocking pipe IPC / thread-safe UCI bridge.
   - Implementasikan Kotlin Native Bridge (StockfishNativeBridge.kt).
   - Implementasikan Service Handler (ChessEngineService.kt) berbasis Coroutines & Flow dengan parser UCI output (bestmove, eval centipawns/mate) dan power/Elo calibration (PRD Section 4.2).
   - Buat Unit Test untuk verifikasi evaluasi FEN awal dan parsing respons bestmove.

2. **Sprint 2 (2026-08-30): Vision Pipeline (OpenCV + TFLite Board Detector & FEN Assembler)**
   - Implementasikan OpenCvBoardDetector.kt (kontur board, warpPerspective 8x8 orthogonal, orientasi white/black).
   - Implementasikan SquareExtractor.kt (slicing 64 sub-petak 32x32 RGB & Frame Difference Change Detector min 2 petak).
   - Implementasikan TfLitePieceClassifier.kt (TFLite interpreter, NNAPI/GPU delegate, batch inference 64 petak, 13 kelas).
   - Implementasikan FenAssembler.kt (matriks 8x8 ke FEN string, kompresi kosong, castling & en-passant tracking).
   - Implementasikan BoardVisionPipeline.kt (Orchestrator fasade: Frame -> Detection -> Slicing -> Inference -> FEN < 50ms).
   - Buat Unit Tests untuk FenAssembler (starting pos, midgame, orientasi White/Black).

3. **Sprint 3 (2026-08-30): MediaProjection Screen Capture Service, Floating Overlay Window, & Integration Orchestrator**
   - Implementasikan ScreenCaptureService.kt: Android ForegroundService (FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION), VirtualDisplay & ImageReader (15-30 FPS, downscaled 720p), thread-safe Bitmap extraction tanpa memory leak.
   - Implementasikan OverlayService.kt / OverlayManager.kt: WindowManager floating window (TYPE_APPLICATION_OVERLAY), SYSTEM_ALERT_WINDOW permission handling, status touchability (FLAG_NOT_TOUCH_MODAL, FLAG_NOT_FOCUSABLE).
   - Implementasikan BoardArrowOverlayView.kt: Custom transparent Canvas overlay di atas koordinat catur, rendering panah vektor dinamis (smooth curved/straight), pewarnaan adaptif (Hijau #00E676 untuk best move >= +1.00, Biru #2979FF untuk solid/equal move, Kuning #FFD600 untuk alternative taktis).
   - Implementasikan FloatingHudView.kt: Minimalist draggable HUD widget (vertical Evaluation Bar, centipawn/mate score, best move notation).
   - Implementasikan HapticFeedbackManager.kt: Pengatur getaran taktil morse code / getaran diskrit saat langkah baru ditemukan / risiko blunder.
   - Implementasikan GameOrchestrator.kt: Integration glue layer menghubungkan ScreenCaptureService -> BoardVisionPipeline -> ChessEngineService -> OverlayManager / HapticFeedbackManager, menjaga throttling dan end-to-end latency < 350ms.

4. **Sprint 4 (2026-08-30): Multi-Engine Support (Lc0 & Retro Minimax), Dashboard UI (Jetpack Compose), & Multi-Resolution Adaptation**
   - Implementasikan Lc0EngineBridge.kt: Neural Network AlphaZero style engine wrapper dengan pemetaan playouts/nodes (10-1510) dan minibatch.
   - Implementasikan RetroMinimaxEngine.kt: Pure Minimax engine klasik (Deep Blue style) tanpa neural network dengan material evaluation + piece-square positional tables dan kedalaman 1-13 ply.
   - Implementasikan EngineManager.kt: Unified manager/factory untuk hot-switching dinamis antara Stockfish, Lc0, dan Retro Minimax.
   - Implementasikan EnginePreferencesRepository.kt: DataStore / persistent settings untuk engine type, Elo rating / power slider, visual overlays, HUD, dan haptic toggles.
   - Implementasikan DisplayMetricsAdapter.kt: Utilitas kalibrasi koordinat papan catur adaptif terhadap berbagai rasio aspek layar (16:9, 19.5:9, 20:9, tablet, notch/cutouts).
   - Implementasikan MainActivity.kt & DashboardScreen.kt: Jetpack Compose Dashboard UI sesuai PRD Section 6.1 (Engine Selection, Elo Power Slider, Toggles, Start/Stop Capture CTA dengan ActivityResultLauncher).
   - Buat Unit Tests untuk RetroMinimaxEngine, EnginePreferences, dan DisplayMetricsAdapter.

5. **Sprint 5 (2026-08-30): Performance Optimization, Battery Efficiency, Edge-Case Handling, Fault Tolerance, & Final QA**
   - Implementasikan BatteryGovernor.kt: Dynamic FPS governor (5-10 FPS saat posisi statis, 30 FPS saat transisi pergerakan) & power state manager.
   - Implementasikan BufferPoolManager.kt: DirectByteBuffer & Bitmap memory buffer pool recycling untuk zero-allocation frames dan eliminasi GC Pauses.
   - Implementasikan BoardEdgeCaseHandler.kt: Highlight & arrow filtering (menghilangkan overlay/last move highlights kuning/hijau/merah), adaptive contrast normalizer (HSV / CLAHE) untuk custom board themes (wood, emerald, bubblegum, dark mode, glassmorphism), dan piece occlusion guard (finger drag/hover).
   - Implementasikan EngineWatchdog.kt: Health check native process C++/JNI, auto-recovery restart < 100ms saat broken pipe/timeout, dan graceful fallback ke RetroMinimaxEngine saat low memory threshold (<180MB RAM).
   - Konfigurasi ProGuard / R8 rules (proguard-rules.pro): Keep rules untuk JNI native symbols, TensorFlow Lite delegates, DataStore preferences, dan engine models.
   - Benchmarking & Stress Test Suite (PerformanceBenchmarkTest.kt): End-to-End Latency Budget test (<300-350ms) dan Memory Leak 500-frame stress test (<180MB RAM).

6. **Release Configuration & Build Verification (2026-08-30)**
   - Konfigurasi app/build.gradle.kts (App Level): buildTypes release dengan minify & shrinkResources, ProGuard rules, NDK ABI filters (arm64-v8a, armeabi-v7a), dan signingConfigs.
   - Script Verifikasi Build (verify-release.sh & verify-release.ps1): Otomasi ./gradlew assembleRelease, inspeksi ukuran file APK/AAB, dan validasi keberadaan shared libraries (.so) tanpa stripping issue.
   - Device Testing Checklist (DEVICE_CHECKLIST.md): Panduan runtime verification (MediaProjection, Overlay SYSTEM_ALERT_WINDOW, Haptics, HUD Draggable, dan Battery Governor).

8. **Sprint 8 (2026-08-30): Pipeline Fixes, Chess App Profiles & Vision Diagnostics**
   - MediaProjection & Capture Fixes: Meneruskan Intent result data dengan aman ke ScreenCaptureService, menambahkan lifecycle exception handler di GameOrchestrator, dan safe-load dengan graceful mock fallback untuk model .tflite & NNUE.
   - Chess App Profiles (ChessAppProfile.kt): Target profiles (CHESS_COM, LICHESS, UNIVERSAL_AUTO) dengan automatic Fallback Center-Square Cropping jika OpenCV dynamic contour detection gagal mendeteksi 4 sudut papan.
   - Live Diagnostic Indicator di FloatingHudView: Status baris mini `Frames: [FPS] | Board: [OK / NOT_FOUND] | Engine: [CALC / IDLE]` untuk pemantauan runtime langsung di atas layar.
   - Dashboard UI Target Selector: Komponen pemilihan aplikasi target di DashboardScreen & integrasi Jetpack DataStore Preferences.
   - Fast Verification & Deployment: Validasi via compileDebugKotlin dan instalasi langsung via installDebug ke perangkat fisik R9CW605ECXM.

9. **Sprint 9 (2026-08-30): Installed Chess App Scanner & Picker**
   - AndroidManifest.xml: Tambahkan izin QUERY_ALL_PACKAGES untuk pemindaian aplikasi terpasang di Android 11+.
   - InstalledAppScanner.kt: Helper PackageManager untuk membaca daftar aplikasi terinstal (launch intent, icon, name, packageName) dengan smart sorting (prioritas kata kunci "chess", "catur", "lichess").
   - AppPickerDialog.kt / DashboardScreen.kt: ModalBottomSheet / Dialog Jetpack Compose lengkap dengan Search bar, Icon aplikasi, pemilihan target, dan persistensi ke DataStore (selectedAppPackage & selectedAppName).
   - GameOrchestrator Integration: Mengaitkan package aplikasi terpilih ke visual crop profile secara dinamis saat start.
   - Fast Validation & Deploy: Validasi via compileDebugKotlin dan deploy langsung via installDebug.

10. **Sprint 10 (2026-08-30): Interactive Chess Board Calibration Overlay**
    - BoardCalibrationOverlayView.kt: Custom View overlay interaktif dengan 1:1 neon bounding box, 8x8 grid lines, drag center movement, bottom-right resize handle, dan tombol aksi melayang [✔ Simpan Area] & [Batal].
    - CalibrationPreferencesRepository.kt / EnginePreferencesRepository.kt: Penyimpanan koordinat kalibrasi manual (boardX, boardY, boardSize) per package name / preset di DataStore.
    - Vision Pipeline & Orchestrator Integration: Jika koordinat kalibrasi manual tersedia, lewati OpenCV contour detection dan langsung gunakan Rect yang telah dikalibrasi untuk slicing 64 petak.
    - UI Triggers: Tombol "📐 Kalibrasi Papan Catur" di DashboardScreen dan tombol kalibrasi interaktif pada Floating HUD overlay.
    - Fast Validation & Deploy: Validasi via compileDebugKotlin dan deploy langsung via installDebug.

11. **Sprint 11 (2026-08-30): Overlay & MediaProjection Diagnostics and Fixes**
    - Diagnosis Error Runtime: Menjalankan adb logcat mendalam untuk menangkap BadTokenException (SYSTEM_ALERT_WINDOW), ForegroundServiceStartNotAllowedException (MediaProjection), dan NPE pada view/model.
    - Fix Overlay Attachment & Permissions: Memastikan overlay diluncurkan di UI Main Thread (Handler/Dispatchers.Main), permission SYSTEM_ALERT_WINDOW dicek dan diminta secara eksplisit, serta notifikasi Foreground Service aktif seketika sebelum startForeground.
    - Validasi & Deploy: Kompilasi dan deploy langsung ke perangkat fisik R9CW605ECXM.

12. **Sprint 12 (2026-08-30): Auto-Launch Target Chess App on Service Start**
    - DataStore & Preferences: Menambahkan `autoLaunchTargetApp: Boolean = true` (`KEY_AUTO_LAUNCH_TARGET_APP`) di EnginePreferencesRepository.
    - Auto-Launch Logic: Saat ScreenCaptureService aktif dan MediaProjection berhasil didapatkan, periksa `autoLaunchTargetApp` & `selectedAppPackage`. Jika aktif, luncurkan aplikasi target dengan `Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`.
    - Dashboard UI: Menambahkan Switch interaktif "Buka Aplikasi Catur Otomatis" di DashboardScreen.
    - Validasi & Deployment: Kompilasi via compileDebugKotlin dan instalasi langsung ke HP fisik R9CW605ECXM via installDebug / ADB.

13. **Sprint 13 (2026-08-30): Real-Time Vision Pipeline, Reactive FEN, & Accurate Arrow Mapping**
    - Frame Capture & ImageReader Flow (ScreenCaptureService.kt / ScreenCaptureProcessor.kt): Menjamin Image buffer selalu ditutup di blok try...finally { image?.close() } tanpa buffer starvation. Frame dialirkan stabil (5–10 FPS aktif) tanpa memory leak.
    - Reactive FEN Pipeline (GameOrchestrator.kt): Menjalankan ekstraksi petak -> klasifikasi bidak -> penyusunan FEN string. Mendeteksi perubahan FEN dari `lastEvaluatedFen`, mengupdate state overlay ke CALC, memicu kalkulasi engine, dan mengupdate best move secara reaktif.
    - Sinkronisasi Koordinat Panah (BoardArrowOverlayView.kt / FloatingHudView.kt): Memetakan notasi catur (e2->e4) ke titik tengah piksel (X, Y) menggunakan bounding box `boardRect` aktual di layar HP (memperhitungkan orientasi White/Black).
    - Validasi & Deployment: Kompilasi via compileDebugKotlin dan deploy langsung ke HP via installDebug.

14. **Sprint 14 (2026-08-30): Interactive Floating Mini-Chessboard Overlay**
    - Interactive Mini-Chessboard View (`InteractiveBoardOverlayView.kt`): Custom View melayang di layar dengan papan catur 8x8 interaktif, tombol warna ([⚪ Putih] / [⚫ Hitam]), [↺ Reset / New Game], [📐 Resize/Minimize], dan drag handle untuk diposisikan di mana saja.
    - Flip Board & Legal Input: Membalik papan sesuai warna pemain. Memungkinkan tap petak asal -> tap petak tujuan dengan validasi langkah legal dan state board internal.
    - Stockfish Engine Integration: Menghitung best move otomatis saat giliran pemain, memberi highlight neon pada petak tujuan/asal dan petak best move, serta menampilkan notasi "🎯 Rekomendasi: e2 ➔ e4" atau "⏳ Masukkan langkah lawan".
    - Persistence & Config (`OverlayStyleConfig.kt` / `EnginePreferencesRepository.kt`): Menyimpan ukuran (140-280dp), opasitas papan, dan posisi overlay (posX, posY).
    - Validasi & Deploy: Kompilasi via compileDebugKotlin dan instalasi langsung ke HP fisik via installDebug.

15. **Sprint 15 (2026-08-30): Dual Service Mode + Ghost Match Relay + Mini Board Reconstruction**
    - Dual Service Buttons di Dashboard: Tombol 1 "Mode Deteksi Otomatis (Vision AI)" meminta MediaProjection & menjalankan ScreenCaptureService. Tombol 2 "Mode Mini Chessboard (Manual Relay)" TIDAK meminta MediaProjection, hanya SYSTEM_ALERT_WINDOW, menjalankan MiniBoardOverlayService.
    - MiniBoardOverlayService.kt: Foreground Service terpisah tanpa dependensi MediaProjection/OpenCV. Inisialisasi Stockfish engine independen di background.
    - InteractiveBoardOverlayView.kt Rekonstruksi: Desain borderless transparan, kontrol melayang di luar papan ([⚪ Lawan Putih] / [⚫ Lawan Hitam] / [🔄 Flip] / [↺ Reset] / [✖ Tutup]), 1 jari = drag, 2 jari (pinch) = scale via ScaleGestureDetector.
    - Ghost Match / Relay Mode: Pengguna input langkah lawan di mini board. Stockfish bermain sisi KITA dan bergerak OTOMATIS. Skenario A (Kita Putih): lawan=Hitam, Stockfish=Putih bergerak pertama. Skenario B (Kita Hitam): lawan=Putih, Stockfish=Hitam merespons otomatis.

16. **Sprint 16 (2026-08-30): Perbaikan Bug Engine Stockfish & Logika Giliran Interactive Mini Chessboard**
    - Kunci Interaksi Touch: Mengunci sentuhan papan (`isEngineThinking = true`) saat giliran komputer/engine agar tidak menggeser bidak secara tidak sengaja.
    - Perbaikan Matriks 8x8 & Validasi Bidak: Pemindahan bidak mempertahankan status petak dan mencegah bidak hilang pada sentuhan berulang/pembatalan langkah.
    - Logika Giliran [⚫ Lawan Hitam] vs [⚪ Lawan Putih]:
      * [⚫ Lawan Hitam]: Pengguna = Hitam, Komputer = Putih. Komputer (Putih) LANGSUNG memicu perhitungan Stockfish dan jalan otomatis pertama.
      * [⚪ Lawan Putih]: Pengguna = Putih, Komputer = Hitam. Menunggu langkah pertama Pengguna (Putih), lalu Komputer (Hitam) otomatis membalas.

17. **Sprint 17 (2026-08-30): Perbaikan Freeze Engine Stockfish, CMake/JNI NDK, & UCI Non-Blocking Pipeline**
    - Native Stockfish CMake/JNI: Pastikan native library (`chessbeater_engine`) dikompilasi dengan C++17 (-O3) untuk `arm64-v8a` & `armeabi-v7a`.
    - Handshake UCI Stabil: Kirim `uci` -> `uciok`, `setoption name UCI_LimitStrength value true`, `setoption name UCI_Elo value [Elo]`, `isready` -> `readyok`.
    - Dedicated Non-blocking Reader: Pembacaan stdout native/stdin via coroutine `Dispatchers.IO` tanpa buffer lock.
    - Timeout Watchdog: Max 3 detik. Jika Stockfish tidak mengembalikan `bestmove`, fallback reset status agar UI Mini Board tidak freeze selamanya. Pindahkan bidak & swap turn di `Dispatchers.Main`.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

18. **Sprint 18 (2026-08-30): Perbaikan Parser UCI Move & Main Thread Visual Execution di InteractiveBoardOverlayView**
    - Method `applyEngineMove(uciMove: String)`: Mengonversi notasi UCI (misal `e2e4`, `g1f3`, `e7e8q`) ke koordinat matriks 8x8 secara aman.
    - Pemindahan bidak, promosi pion (`q/r/b/n`), pencatatan `lastMoveFrom`/`lastMoveTo`, pergeseran giliran (`currentTurn`), dan perbaikan status UI dipastikan berjalan di `Dispatchers.Main` via `postInvalidate()`.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat fisik R9CW605ECXM.

19. **Sprint 19 (2026-08-30): Integrasi Native Engine Asli (Stockfish Core/UCI) & Validasi Ketat Giliran Papan Mini**
    - Native Engine Asli: Hapus dummy/hardcoded move di `stockfish-bridge.cpp`. Integrasikan core chess engine UCI asli (atau Stockfish native C++) yang memproses perintah UCI nyata (`position fen ...`, `go ...`) dan menghasilkan `bestmove` legal secara dinamis untuk setiap FEN.
    - Validasi Langkah & Sinkronisasi Giliran di `executeMove`:
      * Validasi petak asal (`board[from] != '.'`).
      * Validasi warna bidak: Bidak yang dipindahkan HARUS sesuai dengan giliran yang aktif (`currentTurn`). Jika tidak sesuai, tolak mutasi dan jangan ubah giliran.
      * Jika langkah valid: pindahkan bidak, ganti `currentTurn`, picu engine jika giliran `stockfishColor`, atau buka input sentuhan jika giliran `opponentColor`.
    - Debug Logcat: Tambahkan log `Log.d("StockfishNative", ...)` untuk perintah UCI dan BestMove.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

20. **Sprint 20 (2026-08-30): Arsitektur Standalone Process Engine (DroidFish Standard)**
    - Subprocess Manager (`StockfishProcessManager.kt`): Mengelola binary Stockfish / Process executable (`chmod 755`) via `ProcessBuilder` dengan pipa stdin/stdout murni non-blocking tanpa JNI deadlock.
    - Siklus Handshake & Evaluasi (`ChessEngineService.kt`):
      * `uci` -> `uciok`, `isready` -> `readyok`.
      * Evaluasi: kirim `position fen ...\n` dan `go movetime 1000\n`, tangkap baris `bestmove <move>`.
      * Timeout Watchdog 2.5s dengan fallback move legal agar game tidak pernah freeze.
    - Sinkronisasi UI (`InteractiveBoardOverlayView.kt`): `applyEngineMove` mengeksekusi bestmove di Main Thread (`postInvalidate()`).
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

21. **Sprint 21 (2026-08-30): Perbaikan Total Binding Callback MiniBoardOverlayService & Internal Safety Timeout View**
    - Binding di `MiniBoardOverlayService.kt`:
      * `onCreate()`: Langsung inisialisasi `serviceScope.launch { chessEngineService.initializeEngine() }`.
      * Callback `onEvaluateRequested`: `serviceScope.launch(Dispatchers.IO) { ... evaluatePosition(fen) ... withContext(Dispatchers.Main) { boardOverlayView?.onEngineResult(result) } }`.
    - Internal Safety Timeout & Fallback di `InteractiveBoardOverlayView.kt`:
      * Di `triggerEval()`: handler/coroutine timeout 2.5s -> jika `isEngineCalculating == true`, cari langkah legal pertama via `SimpleMoveFallback` / `findFirstLegalMove()`, eksekusi langkah tersebut, dan render ulang dengan `postInvalidate()`.
    - Fallback Legal Move Generator (`SimpleMoveFallback.kt`): Menyediakan pembantu pencarian langkah legal darurat.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

22. **Sprint 22 (2026-08-30): Pure Kotlin Fallback Engine, Max Thinking Time Control, Visualisasi Langkah & Move History**
    - Pure Kotlin Fallback Engine (`LocalChessEngine.kt`): Generator langkah legal lengkap & Minimax PST evaluator mandiri di Kotlin, mengembalikan UCI move instan jika Stockfish lambat.
    - Max Thinking Time & Strict Timeout di `ChessEngineService.kt`:
      * Parameter `maxThinkingTimeMs` (500ms, 1000ms, 1500ms, 2000ms, 3000ms).
      * `withTimeoutOrNull(maxThinkingTimeMs + 200L)`: Jika timeout langsung memanggil `LocalChessEngine.getBestMove(fen)`.
    - Perbaikan `applyEngineMove` & `executeMove` di `InteractiveBoardOverlayView.kt`:
      * Reset `isEngineCalculating = false` di awal.
      * Translasi orientasi / fallback indexing otomatis.
      * Riwayat langkah (`moveHistory`), format notasi catur, dan status bar 3 langkah terakhir.
    - Visualisasi Trayek & Jejak (`onDraw`): Highlight petak asal & tujuan (`lastMoveFrom`, `lastMoveTo`), panah bergerak, HUD status informatif.
    - Tombol Toggle Durasi Berpikir di Header Mini Board (`[⚡ 1.0s]` toggle: 0.5s -> 1.0s -> 2.0s -> 3.0s).
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

23. **Sprint 23 (2026-08-30): Runtime ADB Audit, Logcat Diagnostics, & Engine Pipeline Root Cause Resolution**
    - ADB Buffer Clean & Launch: `adb -s R9CW605ECXM logcat -c` dan launch `com.chessbeater/.MainActivity`.
    - Realtime Capture to `logs/debug_session.log`.
    - Log Analysis: Periksa `onEvaluateRequested`, pipeline output stdout/stderr engine, parsing `bestmove`, dan callback UI di Main Thread.
    - Resolusi dan perbaikan kode langsung berdasarkan temuan logcat.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

24. **Sprint 24 (2026-08-30): Akurasi Sentuhan Flipped Board & Visual Feedback Interaksi Bidak**
    - Sinkronisasi Sentuhan Flipped (`handleBoardTap`):
      * Konversi koordinat sentuhan `(tapX, tapY)` ke `clickedIdx`:
        `val bRow = if (isBoardFlipped) 7 - row else row; val bCol = if (isBoardFlipped) 7 - col else col; val clickedIdx = bRow * 8 + bCol`.
      * Logcat diagnostik: `Log.d("InteractiveBoardTouch", "Tap di sq=$clickedIdx, Bidak=${board[clickedIdx]}, Giliran=$currentTurn, OpponentColor=$opponentColor")`.
    - Feedback Visual Sentuhan:
      * Seleksi bidak: Highlight kotak kuning tebal pada `selectedSquare`.
      * Titik petak tujuan legal: Render lingkaran hijau terang pada seluruh `legalDestinations`.
      * Eksekusi sentuhan tujuan: Pindahkan bidak seketika, ganti giliran ke `stockfishColor`, dan picu evaluasi langkah balasan.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

25. **Sprint 25 (2026-08-30): Pembersihan Instalasi, Real-Time Vision & Arrow Overlay Logika Giliran, dan Live ADB Debugging**
    - Uninstall APK lama: `adb uninstall com.chessbeater` dan `adb uninstall com.chessbeater.debug`, buffer clean `logcat -c`.
    - Real-Time Vision (`GameOrchestrator.kt`, `BoardArrowOverlayView.kt`, `FloatingHudView.kt`):
      * Player Color Locking: Kunci `playerColor` setelah 3 frame pertama (tidak berkedip/berubah di tengah game), sediakan manual toggle [⚪ / ⚫] di HUD.
      * Turn Filtering: Parse active color dari FEN ('w' / 'b'). Jika giliran pemain -> evaluasi Stockfish & gambar panah rekomendasi hijau. Jika giliran musuh -> sembunyikan panah & HUD "⏳ Menunggu giliran lawan...".
      * FEN Debouncer: Evaluasi hanya dieksekusi jika FEN stabil selama 2 frame berturut-turut.
    - Validasi, Deploy, & Stream Logcat:
      * `./gradlew compileDebugKotlin` → `./gradlew installDebug`
      * Launch `com.chessbeater.debug/com.chessbeater.MainActivity`
      * Stream logcat real-time.

26. **Sprint 26 (2026-08-30): Crash Stack Trace Audit, ImageReader & Memory Leak Prevention, Global Exception Handling, WindowManager Safe Operations**
    - ADB Crash Log Capture: `adb logcat -b crash -d` dan `adb logcat -s AndroidRuntime DEBUG *:F` ke `logs/crash_dump.log`.
    - ImageReader & Memory Management (`ScreenCaptureService.kt` / `SquareExtractor.kt`):
      * `ImageReader.newInstance(..., 2)` dengan mandatory `try-finally { image.close() }`.
      * Daur ulang / recycle Bitmap slice sub-petak sementara untuk mencegah OOM.
    - Global Uncaught Exception Handler (`ChessBeaterApp.kt`):
      * Pasang `Thread.setDefaultUncaughtExceptionHandler` dan `CoroutineExceptionHandler` di Application dan Foreground Services.
    - WindowManager Safe Operations (`OverlayManager.kt`):
      * Guard `isAttachedToWindow` dan try-catch pada setiap `updateViewLayout` / `removeView`.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

27. **Sprint 27 (2026-08-30): Real-time Vision Board Orientation & Precision Arrow Coordinate Mapping**
    - Deteksi Warna & Orientasi Baku (`OpenCvBoardDetector.kt` / `FenAssembler.kt`):
      * Aturan baku: Bidak pemain selalu di baris 6 dan 7 (2 baris terbawah bounding box layar).
      * Hitung rasio bidak putih vs hitam di baris 6-7: Mayoritas putih -> `playerColor = WHITE`, `isFlipped = false`; mayoritas hitam -> `playerColor = BLACK`, `isFlipped = true`.
      * Kunci orientasi setelah deteksi awal stabil agar tidak berfluktuasi.
    - State Giliran & Filter Tampilan Panah (`GameOrchestrator.kt`):
      * `activeColor == playerColor`: Stockfish hitung langkah terbaik, gambar panah hijau rekomendasi, HUD: "👉 Giliran Anda ([Putih/Hitam])".
      * `activeColor != playerColor`: Sembunyikan & bersihkan panah rekomendasi dari canvas, HUD: "⏳ Menunggu giliran lawan...".
    - Rumus Pemetaan Koordinat Piksel Panah (`BoardArrowOverlayView.kt`):
      * `notationToScreenPoint(pos, boardRect, isFlipped)`:
        `val col = pos[0] - 'a'`; `val rank = pos[1] - '1'` (0=rank 1, 7=rank 8);
        `val displayCol = if (isFlipped) 7 - col else col`; `val displayRow = if (isFlipped) rank else 7 - rank`.
        `x = boardRect.left + (displayCol + 0.5f) * sqW`; `y = boardRect.top + (displayRow + 0.5f) * sqH`.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

28. **Sprint 28 (2026-08-30): Transparent Ghost Mini-Board with Calibration Sync & Touch-Forwarding**
    - Sinkronisasi Kalibrasi 1:1 (`MiniBoardOverlayService.kt` / `OverlayManager.kt`):
      * Baca `calibratedRect` (left, top, width, height) dari preferences.
      * Set dimensi dan koordinat `WindowManager.LayoutParams` overlay mini board agar pas 100% menempel di atas papan catur asli.
    - Mode Visual Ghost (`InteractiveBoardOverlayView.kt`):
      * Toggle Ghost Mode: Petak dibuat semi-transparan tipis / transparan penuh.
      * Opsi sembunyikan teks bidak (hanya menampilkan highlight seleksi, titik legal, panah rekomendasi Stockfish, dan jejak langkah terakhir).
    - Touch-Forwarding Accessibility Service (`ChessAccessibilityService.kt`):
      * `AccessibilityService` yang menyuntikkan gesture via `dispatchGesture(GestureDescription)` saat pengguna menyentuh/menggeser bidak di mini board ke game di bawahnya.
    - Pengaturan Toggle:
      * "Ghost Mode" [Aktif/Nonaktif] dan "Touch Forwarding" [Aktif/Nonaktif].
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

29. **Sprint 29 (2026-08-30): Mini Chessboard Three Dots Overflow Menu (⋮) & Action Sheet Modal**
    - UI Refactor Header Control (`InteractiveBoardOverlayView.kt`):
      * Hapus deretan 6 tombol yang menumpuk di atas papan.
      * Ganti dengan 1 tombol floating bulat transparan `⋮` (Three Dots Menu) di pojok kanan atas: `btnMenuBounds = RectF(boardSizePx - 44dp, 0f, boardSizePx, 44dp)`.
      * State internal: `var isMenuOpen: Boolean = false`.
    - Action List Modal Canvas (`drawMenuModal`):
      * Saat `isMenuOpen == true`: Gambar dark scrim blur/semi-transparan menutupi papan + Card rounded modal di tengah.
      * Daftar Item Menu Interaktif:
        1. ⚪ Lawan: Putih (Kita pegang Hitam)
        2. ⚫ Lawan: Hitam (Kita pegang Putih)
        3. 🎯 Pas ke Kalibrasi Papan (Snap ukuran & posisi ke game asli)
        4. 🔄 Putar Papan (Flip)
        5. ↺ Reset / Game Baru
        6. ⚡ Waktu Berpikir: [0.5s / 1.0s / 2.0s / 3.0s]
        7. 👻 Mode: [Ghost / Klasik]
        8. ✖ Tutup Mini Board
    - Event Handling (`onTouchEvent`):
      * Saat menu terbuka: kunci input petak catur, deteksi klik item menu modal, jalankan callback, lalu tutup modal otomatis.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

30. **Sprint 30 (2026-08-30): Persistent Last Board Position, Precision Touch-Forwarding (Tap & Drag), and Full ADB Accessibility Enable**
    - Simpan & Pulihkan Posisi/Ukuran Papan Terakhir (`BoardPreferencesRepository.kt` & `MiniBoardOverlayService.kt`):
      * `saveLastBoardPosition(x: Int, y: Int, sizePx: Int)` & `getLastBoardPosition(): Flow<BoardPosition?>`.
      * Restore posisi dan ukuran otomatis saat start; simpan dengan debounce 500ms saat drag/scale.
    - Accessibility Service untuk Touch-Forwarding (`service.ChessAccessibilityService`):
      * `res/xml/accessibility_service_config.xml` dengan `canPerformGestures="true"`, `canRetrieveWindowContent="true"`.
      * Singleton instance: `forwardClick(rawX, rawY)` dan `forwardDrag(startX, startY, endX, endY, durationMs = 150)`.
    - Integrasi Sentuhan di `InteractiveBoardOverlayView.kt`:
      * `ACTION_DOWN` & `ACTION_UP` tracking: jika jarak < 20px (Tap) panggil `forwardClick`, jika >= 20px (Drag) panggil `forwardDrag`.
    - ADB Auto-Enable Accessibility:
      * `adb shell settings put secure enabled_accessibility_services com.chessbeater.debug/com.chessbeater.service.ChessAccessibilityService:...`
      * `adb shell settings put secure accessibility_enabled 1`.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

31. **Sprint 31 (2026-08-30): Arrow Duration Configuration & Floating Eye Show/Hide Toggle**
    - Durasi Panah Petunjuk (`BoardPreferencesRepository.kt`, `InteractiveBoardOverlayView.kt`):
      * Konfigurasi durasi `arrowDurationMs`: 1000ms (1.0s), 2000ms (2.0s), 3000ms (3.0s), -1L (Selamanya).
      * Persistensi ke DataStore `BoardPreferencesRepository` (`KEY_ARROW_DURATION_MS`).
      * Auto-clear timer `arrowDismissJob` pada `onEngineResult` / eksekusi langkah dengan `delay(arrowDurationMs)`.
      * Siklus toggle opsi durasi pada Action Sheet Menu Titik Tiga (`⋮`): "🏹 Durasi Panah: [1.0s / 2.0s / 3.0s / Selamanya]".
    - Floating Eye Show/Hide Toggle (`FloatingEyeToggleView.kt`, `MiniBoardOverlayService.kt`, `OverlayManager.kt`):
      * Tombol cepat ikon mata `👁` di header mini board di samping `⋮` dan item "👁 Sembunyikan Papan (Floating Eye)" di Action Sheet.
      * Custom View `FloatingEyeToggleView`: Lingkaran 48x48dp, background `rgba(18, 26, 38, 0.85)`, neon border, draggable, dan tap untuk membuka kembali papan mini secara instan.
      * WindowManager State Preservation: Melepas board view saat hide dan memasangnya kembali saat tap Floating Eye tanpa mereset FEN, giliran, riwayat, atau state engine.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat fisik R9CW605ECXM.

32. **Sprint 32 (2026-08-30): Persistent Action Sheet Modal & Independent Board vs Floating Eye Coordinates**
    - Persistent Toggle Modal Menu (`InteractiveBoardOverlayView.kt`):
      * Ubah sistem sentuhan menu: Hilangkan penutupan prematur pada ACTION_UP dari tap tombol `⋮`.
      * Evaluasi gesture tap vs drag di ACTION_UP: Jika jarak < touchSlop pada `btnMenuBounds`, toggle `isMenuOpen = !isMenuOpen`.
      * Modal tetap terbuka persisten di layar sampai pengguna memilih salah satu item aksi atau tap tombol [✕] / luar dialog.
    - Pemisahan Koordinat Independen Papan & Tombol Mata (`MiniBoardOverlayService.kt` & `OverlayManager.kt`):
      * Pisahkan state koordinat: `savedBoardX`, `savedBoardY`, `savedBoardSize` untuk mini-chessboard vs `eyePosX`, `eyePosY` untuk tombol `FloatingEyeToggleView`.
      * Pergerakan/drag tombol mata HANYA mengubah `eyePosX/Y` dan tidak pernah menimpa koordinat papan.
      * Mengembalikan papan dari mode hide menempatkan papan 100% tepat pada `savedBoardX/Y` dan ukuran `savedBoardSize`.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat fisik R9CW605ECXM.

33. **Sprint 33 (2026-08-30): Eye Size Customization, Element Transparency, Shake Sensor Detector, & Click-Through Touch Forwarding**
    - Ukuran & Gesture Tombol Mata (`FloatingEyeToggleView.kt`):
      * Ukuran default 72dp x 72dp (touch area 80dp x 80dp) dengan opsi `NORMAL` (56dp), `LARGE` (72dp), `EXTRA_LARGE` (88dp).
      * Single Tap: Membuka mini board di koordinat tersimpan.
      * Long Press (>600ms): Menyembunyikan tombol mata sepenuhnya (Ghost Invisible Mode).
      * Dragging mulus tanpa mengubah koordinat papan catur.
    - Sensor Guncang HP / Shake-to-Show (`ShakeDetector.kt` & `MiniBoardOverlayService.kt`):
      * Akselerometer detektor `gForce > 2.5F` untuk memunculkan kembali tombol mata / papan saat tersembunyi + getar haptik 50ms.
      * Auto-register saat service aktif dan unregister saat service destroy.
    - Granular Transparansi per Elemen & Sub-Menu Tampilan (`BoardPreferencesRepository.kt`, `InteractiveBoardOverlayView.kt`):
      * Pengaturan `gridAlpha` (0..255), `highlightAlpha` (0..255), `arrowAlpha` (0..255), `eyeButtonAlpha` (0..255), `isClickThroughMode`.
      * Sub-menu "🎨 Pengaturan Tampilan & Transparansi" di Action Sheet dengan siklus Grid, Panah, Ukuran Mata, dan Mode Sentuh.
    - Touch-Forwarding & Click-Through Mode:
      * Opsi A: `FLAG_NOT_TOUCHABLE` untuk click-through hardware 0ms lag langsung ke aplikasi catur di bawahnya.
      * Opsi B: Synthetic Gesture Fix untuk Samsung One UI di `ChessAccessibilityService.kt`.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

34. **Sprint 34 (2026-08-30): Rollback / Restore to Sprint 33 Stable State**
    - Memulihkan seluruh basis kode ke versi stabil Sprint 33:
      * Pemulihan arsitektur single-window `InteractiveBoardOverlayView` dan `OverlayManager.kt`.
      * Tombol mata floating toggle 72dp dengan Shake-to-Show sensor detector dan ghost mode.
      * Pengaturan granular transparansi (grid, arrow, highlight, eye) di DataStore `BoardPreferencesRepository`.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat fisik R9CW605ECXM.

35. **Sprint 35 (2026-08-30): Interactive GUI Sliders for Transparencies, Piece Alpha, & Move Auto-Hide**
    - Preferensi & DataStore (`BoardPreferencesRepository.kt`):
      * `gridAlpha: Float` (0.0f..1.0f, default 0.15f), `pieceAlpha: Float` (0.0f..1.0f, default 1.0f), `highlightAlpha: Float` (0.0f..1.0f, default 0.65f), `arrowAlpha: Float` (0.0f..1.0f, default 0.90f), `floatingEyeAlpha: Float` (0.0f..1.0f, default 0.85f), `autoHideDelaySec: Int` (-1..10, default -1 [Off]).
    - Interactive Sliders GUI & Enlarged Hitbox Modal (`InteractiveBoardOverlayView.kt`):
      * Modal pengaturan menempati 92% lebar papan dengan slider interaktif (Grid, Bidak, Highlight, Panah, Mata, Auto-Hide).
      * Hitbox tinggi slider min 48dp untuk kemudahan drag/sentuhan jari.
      * Live visual update saat slider digeser.
    - Canvas Paint Alpha Sync:
      * Pembaruan alpha real-time untuk grid, buah catur (piece), highlight seleksi & move, panah best move, serta background/icon tombol mata.
    - Auto-Hide Pasca Gerakan (`InteractiveBoardOverlayView.kt` & `MiniBoardOverlayService.kt`):
      * Saat langkah selesai diproses (pemain / Stockfish), jika `autoHideDelaySec >= 0`, papan otomatis menciutkan diri ke floating eye (0s seketika atau timer N detik) dengan posisi papan tersimpan 100%.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

36. **Sprint 36 (2026-08-30): Multi-Preset Kalibrasi Papan Catur & Package Binding Auto-Switching**
    - Model & DataStore Repository (`CalibrationPreset.kt` & `PresetRepository.kt`):
      * Data class `CalibrationPreset(id, name, packageName, x, y, width, height, isFlipped, createdAt)`.
      * CRUD operations: `savePreset`, `deletePreset`, `getAllPresets`, `getPresetByPackage`, `getActivePresetId`, `setActivePresetId` menggunakan serialisasi JSON built-in.
    - Dialog & Alur Kalibrasi Baru (`BoardCalibrationOverlayView.kt` & `OverlayManager.kt`):
      * Tombol `[💾 Simpan Preset]` mendeteksi package foreground saat ini via `ChessAccessibilityService.currentForegroundPackage`.
      * Input nama preset & opsi toggle penautan package game catur (`com.chess`, `org.lichess.mobileapp`, dsb).
    - Sub-Menu Preset di Action Sheet Overlay (`InteractiveBoardOverlayView.kt`):
      * Opsi `🎯 Lakukan Kalibrasi Baru` dan `📁 Preset Tersimpan` (daftar preset dengan indikator aktif & sekali klik untuk apply).
    - Auto-Switching Preset Foreground Detection (`ChessAccessibilityService.kt`):
      * Deteksi `TYPE_WINDOW_STATE_CHANGED` untuk mencocokkan foreground package dengan preset tersimpan.
      * Panggil `MiniBoardOverlayService.instance?.applyPreset(matchedPreset)` secara otomatis dengan feedback toast `⚡ Preset aktif: [Nama]`.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

37. **Sprint 37 (2026-08-30): Dukungan Penuh Gerakan Rokade (Castling: Kingside O-O & Queenside O-O-O)**
    - State Hak Rokade (`castlingRights: String = "KQkq"`):
      * Inisialisasi awal & reset ke "KQkq", pertahankan hak rokade dari FEN (`loadFen`).
      * Inklusi hak rokade dinamis ke dalam FEN string pada `generateFen()`.
    - Deteksi Langkah Legal Rokade Raja (`computeLegal` di `InteractiveBoardOverlayView.kt`):
      * Putih (Raja di e1 / idx 60):
        - O-O (g1 / idx 62): Sah jika hak 'K' aktif, petak 61 & 62 kosong ('.'), dan benteng h1 (63) adalah 'R'.
        - O-O-O (c1 / idx 58): Sah jika hak 'Q' aktif, petak 59, 58, 57 kosong ('.'), dan benteng a1 (56) adalah 'R'.
      * Hitam (Raja di e8 / idx 4):
        - O-O (g8 / idx 6): Sah jika hak 'k' aktif, petak 5 & 6 kosong ('.'), dan benteng h8 (7) adalah 'r'.
        - O-O-O (c8 / idx 2): Sah jika hak 'q' aktif, petak 3, 2, 1 kosong ('.'), dan benteng a8 (0) adalah 'r'.
    - Eksekusi Perpindahan Bidak saat Rokade (`executeMove` & `applyEngineMove`):
      * Deteksi jika Raja melangkah 2 petak horizontal:
        - White O-O (60->62): Pindahkan Benteng 63 ('R') ke 61 ('R'), kosongkan 63.
        - White O-O-O (60->58): Pindahkan Benteng 56 ('R') ke 59 ('R'), kosongkan 56.
        - Black O-O (4->6): Pindahkan Benteng 7 ('r') ke 5 ('r'), kosongkan 7.
        - Black O-O-O (4->2): Pindahkan Benteng 0 ('r') ke 3 ('r'), kosongkan 0.
      * Update Hak Rokade:
        - Raja Putih bergerak (from == 60) -> Hapus 'K' dan 'Q'.
        - Raja Hitam bergerak (from == 4) -> Hapus 'k' dan 'q'.
        - Benteng bergerak / ditangkap (idx 63 -> hapus 'K', idx 56 -> hapus 'Q', idx 7 -> hapus 'k', idx 0 -> hapus 'q').
        - Jika kosong -> set "-".
    - Kompatibilitas Notasi UCI Stockfish & Local Engine:
      * Stockfish bestmove `e1g1`, `e1c1`, `e8g8`, `e8c8` otomatis memindahkan Raja dan Benteng yang bersangkutan.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

38. **Sprint 38 (2026-08-30): Undo Move, Mode Koreksi Posisi Papan (Board Editor), & Filled Solid White Pieces**
    - Fitur Undo Move (`InteractiveBoardOverlayView.kt`):
      * Snapshot state `BoardSnapshot(boardState, currentTurn, castlingRights, lastMoveFrom, lastMoveTo, lastEngineBestMove, lastEvalText, historyLog)`.
      * Riwayat snapshot `snapshotHistory = ArrayDeque<BoardSnapshot>()` (maksimum 50 item).
      * Pemicu `undoLastMove()` membatalkan evaluasi engine aktif, memulihkan 1 atau 2 snapshot sebelumnya (mengembalikan giliran ke pemain), dan merender ulang papan.
      * Tombol `[ ↺ Undo Langkah ]` ditambahkan pada Action Sheet Menu.
    - Mode Koreksi Posisi Papan / Board Editor (`isCorrectionMode`):
      * Toggle di menu `🛠️ Koreksi Posisi Papan`.
      * Menjeda Stockfish dan menampilkan palette bidak mini `[ ♔ ♕ ♖ ♗ ♘ ♙ ] [ ♚ ♛ ♜ ♝ ♞ ♟ ] [ 🗑️ Hapus ] [ ⚪/⚫ Giliran ] [ ✅ Selesai ]`.
      * Pengguna dapat menempatkan bidak atau memindahkan bidak bebas ke petak mana pun (*illegal moves allowed*).
      * Tombol `[ ✅ Selesai ]` memvalidasi keberadaan kedua Raja, menyusun ulang FEN baru, dan memicu kalkulasi engine jika aktif.
    - Dual-Pass Filled Solid White Piece Rendering:
      * Render bidak putih dengan Pass 1: `Paint.Style.FILL` warna `#FFFFFF` / solid putih murni.
      * Pass 2: `Paint.Style.STROKE` dengan garis tepi kontras `#0F172A` (strokeWidth 2.5dp) agar tajam dan terbaca jelas pada tema papan apa pun.
      * Render bidak hitam dengan `Paint.Style.FILL` `#111827` dan outline kontras halus.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

39. **Sprint 39 (2026-08-30): Perbaikan Rumus Notasi UCI ke Koordinat Piksel Kanvas & Panah Kuda Organik**
    - Fungsi Konversi Presisi `uciToPixel(uciMove: String, boardRect: RectF, isFlipped: Boolean)`:
      * File 'a'..'h' -> Kolom X (0..7).
      * Rank '1'..'8' -> Baris Y (0..7, 0=Rank 1, 7=Rank 8).
      * Mapping Tampilan: `displayCol = if (isFlipped) 7 - col else col`, `displayRow = if (isFlipped) rank else 7 - rank`.
      * Titik Tengah Petak: `(displayCol + 0.5f) * sqW`, `(displayRow + 0.5f) * sqH`.
    - Rendering Panah Kuda (Knight Jump Curve):
      * Deteksi langkah Kuda (`isKnightMove`).
      * Gambar lengkungan kurva kuadratik (Quad Bezier) dengan offset tegak lurus untuk gerakan bentuk L yang halus dan akurat, lengkap dengan mata panah di petak tujuan.
    - Sinkronisasi Panah Rekomendasi Stockfish:
      * Prioritaskan menggambar panah rekomendasi `engineBestMove` saat giliran pemain aktif.
    - Debug Logging:
      * Log detail `StockfishMove` mencetak `Raw UCI`, `From` (col/rank), `To` (col/rank), dan `Bidak Asal`.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

40. **Sprint 40 (2026-08-30): Standarisasi Perakitan FEN, Komunikasi UCI Stop-Position-Go, & Pencegahan Langkah Ilegal Raja**
    - Standarisasi Baku Perakitan FEN (`generateFen`):
      * Papan 8x8 diekspor mulai dari Baris Teratas (Rank 8 / row index 0) ke Baris Terbawah (Rank 1 / row index 7).
      * Hitung petak kosong (`emptyCount`) dan format standar FEN `rnbqkbnr/... w/b KQkq - 0 1`.
    - Komunikasi UCI Presisi (`ChessEngineService.kt`):
      * Kirim `stop` sebelum posisi baru, lalu `position fen <FEN>`, kemudian `go movetime <MS>`.
      * Logging eksplisit `Log.d("StockfishDebug", "Evaluating FEN: $fen")`.
    - Pencegahan Langkah Ilegal Raja Mendekat ke Raja Lawan (`LocalChessEngine.kt` & `InteractiveBoardOverlayView.kt`):
      * Verifikasi posisi Raja lawan (`enemyKingRow`, `enemyKingCol`).
      * Tolak tujuan Raja jika `abs(nr - enemyKingRow) <= 1 && abs(nc - enemyKingCol) <= 1` (aturan baku catur: Raja tidak boleh berada di petak yang berdekatan dengan Raja lawan).
    - Verifikasi Mapping Indeks 64 Petak:
      * $f4$ ➔ `row = 4` (Rank 4), `col = 5` (File F) -> `board[4 * 8 + 5] = 'k'`.
      * $f2$ ➔ `row = 6` (Rank 2), `col = 5` (File F) -> `board[6 * 8 + 5] = 'K'`.
    - Validasi & Deploy: `./gradlew compileDebugKotlin` → `./gradlew installDebug` ke perangkat R9CW605ECXM.

41. **Sprint 41 (2026-08-30): TAHAP 1: Perbaikan Kritis Sinkronisasi FEN/UCI, Reset Panah Langkah, Presisi Kalibrasi 8x8, dan Auto-Reset Ganti Pihak**
    - Perbaikan Eksekusi Langkah & Sinkronisasi FEN (`InteractiveBoardOverlayView.kt` / `ChessLogic.kt`):
      * `executeMove` & `applyEngineMove`: Validasi pemindahan array 64 petak (kosongkan `fromIndex` dengan `.` dan tempatkan di `toIndex`).
      * Ganti giliran aktif: `activeTurn = if (activeTurn == Side.WHITE) Side.BLACK else Side.WHITE` (atau `currentTurn = if (currentTurn == Side.WHITE) Side.BLACK else Side.WHITE`).
      * Update halfmove clock (`halfMoveClock = 0` saat pion bergerak atau capture) & fullMoveNumber (increment saat giliran kembali ke Putih).
      * Clear panah lama seketika: `currentBestMove = null`, sembunyikan panah, `postInvalidate()`.
      * Bangun string FEN baru secara presisi dan langsung kirim ke Stockfish jika giliran saat ini adalah giliran mesin/pemain.
    - Perbaikan Siklus Evaluasi Stockfish & Panah Tidak Macet (`MiniBoardOverlayService.kt` / `InteractiveBoardOverlayView.kt` / `GameOrchestrator.kt`):
      * Setiap ada langkah baru, batalkan job evaluasi sebelumnya (`stockfishJob?.cancel()` / `evalJob?.cancel()`).
      * Jalankan evaluasi baru dengan FEN terbaru, lalu update `currentBestMove` dan tampilkan panah rekomendasi di Main Thread.
    - Kalibrasi Murni Papan 8x8 Tanpa Offset Header (`InteractiveBoardOverlayView.kt` & `MiniBoardOverlayService.kt`):
      * Pisahkan layout kanvas: `HEADER_HEIGHT = 48dp`, `BOARD_RECT = RectF(0f, HEADER_HEIGHT, boardWidth, HEADER_HEIGHT + boardHeight)` bujur sangkar 1:1.
      * Saat menerapkan hasil kalibrasi `(calibratedX, calibratedY, calibratedWidth, calibratedHeight)`:
        `boardSizePx = calibratedWidth.toInt()`, `windowLayoutParams.x = calibratedX.toInt()`, `windowLayoutParams.y = (calibratedY - HEADER_HEIGHT).toInt()`, `windowLayoutParams.width = boardSizePx`, `windowLayoutParams.height = boardSizePx + HEADER_HEIGHT.toInt()`.
    - Auto-Reset Game saat Ganti Pihak di Tengah Jalan:
      * Pada handler switch side / `setPlayerSide`: Jika game sedang berjalan (riwayat > 0), tampilkan Toast "Pihak diubah, mereset papan...".
      * Reset total: board awal, activeTurn Putih, clear riwayat, clear best move, batalkan job stockfish, postInvalidate.
      * Jika pihak baru adalah Hitam (Lawan Putih), otomatis trigger Stockfish untuk mencari langkah pertama Putih.
    - Validasi & Compile: `./gradlew compileDebugKotlin` dan `./gradlew installDebug`.

42. **Sprint 42 (2026-08-30): TAHAP 2: Peningkatan UI/UX, Dragger Super Halus, Tombol Simpan Posisi Preset, Pembersihan Shadow Bidak Neo, dan Tombol Matikan Service**
    - Free-Move Dragging Papan Super Mulus (`InteractiveBoardOverlayView.kt` / `OverlayManager.kt`):
      * Pada area header drag handle (`ACTION_DOWN` & `ACTION_MOVE`):
        Perbarui posisi WindowManager LayoutParams secara real-time setiap pergerakan frame tanpa snapping/pembulatan kasar.
    - Tombol "💾 Simpan Posisi ke Preset" di Action Sheet Menu (`⋮`):
      * Hitung posisi murni papan: `pureBoardX = windowLayoutParams.x.toFloat()`, `pureBoardY = (windowLayoutParams.y + HEADER_HEIGHT).toFloat()`.
      * Simpan koordinat `(pureBoardX, pureBoardY, boardSizePx, boardSizePx)` ke preset aktif di `PresetRepository`.
      * Tampilkan Toast: "✅ Posisi presisi papan berhasil disimpan ke preset!".
    - Pembersihan Shadow pada Visual Bidak Neo:
      * `pieceBitmapPaint.clearShadowLayer()`, `pieceBitmapPaint.isDither = true`, `pieceBitmapPaint.isFilterBitmap = true`.
      * Render bitmap Neo asli tanpa filter gelap/blur.
    - Perbesar GUI & Hitbox Menu Overlay (Bullet-Friendly):
      * Tinggi item list modal Action Sheet minimal 56dp.
      * Padding dialog modal 24dp dengan ukuran font 16sp - 18sp.
      * Tombol menu titik tiga `[ ⋮ ]` diperbesar dengan ukuran hitbox 52dp x 52dp.
    - Ganti Tombol "Tutup Papan" Menjadi "🛑 Matikan Service" (`MiniBoardOverlayService.kt`):
      * Menu & Tombol Action Sheet menggunakan label "🛑 Matikan Service".
      * Menutup seluruh overlay, menghentikan Foreground Service (`stopForeground(true)`, `stopSelf()`), dan membuka MainActivity jika ditekan.
    - Validasi & Deploy:
      * `./gradlew compileDebugKotlin` dan `./gradlew installDebug`.

43. **Sprint 43 (2026-08-30): TAHAP 3: Implementasi Animasi Meluncur Bidak dan Siklus Cerdas Auto-Hide / Auto-Show untuk Catur Bullet**
    - Konfigurasi Auto-Show di DataStore (`BoardPreferencesRepository.kt` & `BoardVisualPreferences`):
      * `isAutoHideEnabled: Boolean` (default false / autoHideDelaySec >= 0).
      * `autoHideDelaySec: Int` (0..10 detik, default -1 [Off]).
      * `isAutoShowEnabled: Boolean` (default false).
      * `autoShowDelaySec: Int` (1..10 detik, default 2 detik).
    - Siklus Berantai Auto-Hide & Auto-Show (`MiniBoardOverlayService.kt` & `InteractiveBoardOverlayView.kt`):
      * Pada eksekusi langkah pemain: Jika auto-hide aktif -> delay `autoHideDelaySec` -> hide ke Floating Eye.
      * Saat masuk ke mode Floating Eye: Jika auto-show aktif -> delay `autoShowDelaySec` -> pulihkan papan otomatis tepat di posisi tersimpan.
      * Slider konfigurasi "⏱️ Auto-Show Setelah Sembunyi" di modal pengaturan (aktif jika auto-hide aktif).
    - Animasi Gerak Bidak Meluncur Halus (`InteractiveBoardOverlayView.kt`):
      * `PieceAnimation(pieceChar, fromSq, toSq, startX, startY, endX, endY, currentX, currentY)`.
      * `ValueAnimator.ofFloat(0f, 1f)` durasi 140ms (`FastOutSlowInInterpolator`).
      * Render bidak meluncur di canvas `(anim.currentX, anim.currentY)`, sembunyikan rendering statis pada `toSq` & `fromSq` selama animasi berjalan, dan commit mutasi saat animasi selesai.
    - Validasi, Build Akhir, & Pasang ke Perangkat:
      * `./gradlew compileDebugKotlin`
      * `./gradlew installDebug`
      * Launch via ADB: `adb -s R9CW605ECXM shell am start -n com.chessbeater/.MainActivity` (atau `com.chessbeater.MainActivity`).

44. **Sprint 44 (2026-08-30): Perbaikan Validasi Rokade & Deteksi Petak Terancam (In-Check / Traversed Attack)**
    - Implementasi `isSquareAttacked` & `isKingInCheck` (`ChessLogic.kt` / `InteractiveBoardOverlayView.kt`):
      * Deteksi serangan Pion, Kuda, Garis lurus (Benteng/Ratu), Diagonal (Gajah/Ratu), dan Raja Lawan (1 petak).
      * `isKingInCheck(board, side)`: Memeriksa apakah posisi Raja `side` sedang diserang oleh lawan.
    - Validasi Ketat Hak Rokade pada Pembangkit Langkah Legal (`computeLegal`):
      * Jika `isKingInCheck(board, currentTurn)` == true: Batalkan seluruh opsi rokade.
      * White Kingside (e1->g1 / 60->62): 'K' in castlingRights, 61 & 62 kosong, 61 & 62 TIDAK diserang Black (`!isSquareAttacked(61, board, Side.BLACK) && !isSquareAttacked(62, board, Side.BLACK)`).
      * White Queenside (e1->c1 / 60->58): 'Q' in castlingRights, 57, 58, 59 kosong, 59 & 58 TIDAK diserang Black (`!isSquareAttacked(59, board, Side.BLACK) && !isSquareAttacked(58, board, Side.BLACK)`).
      * Black Kingside (e8->g8 / 4->6): 'k' in castlingRights, 5 & 6 kosong, 5 & 6 TIDAK diserang White (`!isSquareAttacked(5, board, Side.WHITE) && !isSquareAttacked(6, board, Side.WHITE)`).
      * Black Queenside (e8->c8 / 4->2): 'q' in castlingRights, 1, 2, 3 kosong, 3 & 2 TIDAK diserang White (`!isSquareAttacked(3, board, Side.WHITE) && !isSquareAttacked(2, board, Side.WHITE)`).
    - Validasi & Deploy ke HP:
      * `./gradlew compileDebugKotlin`
      * `./gradlew installDebug`
      * Launch via ADB: `adb -s R9CW605ECXM shell am start -n com.chessbeater.debug/com.chessbeater.MainActivity`.

45. **Sprint 45 (2026-08-30): Perbaikan Alur Kalibrasi & Eksekusi Simpan Posisi Papan**
    - Perbaikan Alur Buka Kalibrasi (`InteractiveBoardOverlayView.kt` & `MiniBoardOverlayService.kt` / `OverlayManager.kt`):
      * Tombol "🎯 Atur Ulang Kalibrasi" / "🎯 Lakukan Kalibrasi Baru": menutup modal (`isMenuOpen = false`), memanggil `onStartCalibrationRequested?.invoke()`.
      * `MiniBoardOverlayService.kt` / `OverlayManager.kt`: Menghapus/menyembunyikan sementara board overlay, menampilkan `CalibrationOverlayView` fullscreen transparan.
      * Saat kalibrasi disimpan (`onCalibrationFinished`): Menyimpan hasil kalibrasi, menghitung offset header (`y = calibratedRect.top - HEADER_HEIGHT`), menerapkan ukuran papan, dan merestorasi board overlay.
      * Saat kalibrasi dibatalkan (`onCalibrationCancelled`): Merestorasi board overlay ke posisi awal.
    - Implementasi Tuntas Simpan Posisi Papan Saat Ini:
      * Tombol "💾 Simpan Posisi ke Preset": Memanggil `onSaveCurrentPositionRequested?.invoke()`.
      * Menyimpan `currentX`, `currentY`, dan `currentSize` ke `BoardPreferencesRepository` dan memperbarui koordinat preset aktif di `PresetRepository`.
      * Menampilkan feedback Toast: "✅ Posisi tersimpan! (X: ..., Y: ..., Size: ...px)".
    - Validasi & Deploy:
      * `./gradlew compileDebugKotlin`
      * `./gradlew installDebug`

46. **Sprint 46 (2026-08-30): Implementasi Deteksi Gerakan Otomatis Lawan (Highlight Pixel Sampling) dengan Toggle ON/OFF**
    - State & Preferensi DataStore (`BoardPreferencesRepository.kt`):
      * `isAutoDetectionEnabled: Boolean` (default false).
      * `saveAutoDetectionEnabled(enabled: Boolean)` & `getAutoDetectionEnabled(): Flow<Boolean>`.
    - Toggle UI & Indikator Status (`InteractiveBoardOverlayView.kt`):
      * Menu Action Sheet: `🤖 Deteksi Otomatis Lawan: [ ON / OFF ]`.
      * Header badge: `[AUTO]` jika auto detection aktif.
    - Engine Deteksi Petak Highlight (`BoardHighlightDetector.kt`):
      * Sampling warna pixel tengah pada 64 petak bitmap.
      * Konversi RGB ke HSV untuk mendeteksi highlight petak catur (kuning/hijau khas Chess.com/Lichess dll).
      * Cocokkan 2 petak highlight dengan daftar langkah legal yang valid (`legalMovesFromBoard`).
    - Loop Pemindaian & Otomasi Eksekusi (`MiniBoardOverlayService.kt`):
      * Saat giliran lawan & `isAutoDetectionEnabled == true`: Polling frame screen capture (300ms) -> deteksi langkah -> `executeOpponentMove(from, to)` -> Haptic feedback 30ms -> picu Stockfish.
      * Saat `isAutoDetectionEnabled == false`: 0% konsumsi CPU (polling stopped).
    - Validasi & Deploy ke HP:
      * `./gradlew compileDebugKotlin`
      * `./gradlew installDebug`
      * Launch via ADB: `adb -s R9CW605ECXM shell am start -n com.chessbeater.debug/com.chessbeater.MainActivity`.

47. **Sprint 47 (2026-08-30): Audit & Integrasi Slider Level ELO pada StockfishBridge dan UI Overlay**
    - Konfigurasi UCI Engine Strength (`StockfishBridge.kt` / `ChessEngineService.kt` / `StockfishProcessManager.kt`):
      * Standardisasi `setEloRating(targetElo: Int)` (1320..3190):
        `skillLevel = (((clampedElo - 1320f) / (3190f - 1320f)) * 20f).toInt().coerceIn(0, 20)`.
        Kirim opsi UCI: `setoption name UCI_LimitStrength value true`, `setoption name UCI_Elo value $clampedElo`, `setoption name Skill Level value $skillLevel`, `isready`.
      * Kirim dan terapkan nilai ELO tersimpan saat inisialisasi awal engine.
    - Slider ELO di UI & Sinkronisasi DataStore:
      * `BoardPreferencesRepository.kt`: `eloRating: Int` (default 2200, range 1350..2850).
      * `InteractiveBoardOverlayView.kt`: Slider `⚡ Kekuatan Engine: [XXXX ELO] (Title)` (Grandmaster/Master/Club/Novice).
      * Callback `onEloRatingChanged(newElo)` -> DataStore & `setEloRating(newElo)`.
    - Penyesuaian Kedalaman Pencarian Minimum (*Search Depth Budget*):
      * Di `getBestMove(fen, moveTimeMs)`: Gunakan depth minimum jika movetime singkat untuk mencegah blunder.
      * Tambahkan log listener pada respon UCI Stockfish (`Log.d("StockfishOutput", line)`).
    - Validasi & Deploy:
      * `./gradlew compileDebugKotlin`
      * `./gradlew installDebug`
48. **Sprint 48 (2026-08-31): Perbaikan Bug Kritis Validasi Langkah Legal saat Skak & Audit Reset State UCI**
    - Audit Array Pembersihan Bidak (`ChessLogic.kt` & `InteractiveBoardOverlayView.kt`):
      * `applyMoveToBoardArray(fromIndex, toIndex, board)` menjamin petak asal dikosongkan secara mutlak tanpa ghost piece.
    - Reset Bersih State UCI Engine (`ChessEngineService.kt` / `StockfishBridge.kt`):
      * Mengirim `stop`, `setoption name Clear Hash value true`, `position fen $fen`, `go movetime $limitMs`.
      * Log `StockfishAudit`: `Log.d("StockfishAudit", "FEN Dikirim ke Engine: $fen")`.
    - Filter Validasi Langkah Legal Internal Saat Skak:
      * Sebelum panah digambar di `onEngineResult` / `drawArrow`, simulasikan langkah pada salinan board sementara.
      * Jika `isKingInCheck(tempBoard, activeTurn) == true`, tolak langkah dengan log `StockfishError`, bentuk ulang FEN visual, dan picu fallback/re-evaluasi langkah legal yang menyelamatkan raja.
    - Logcat FEN Audit:
      * `Log.d("CheckAudit", "Turn: $activeTurn | InCheck: ${isKingInCheck(board, activeTurn)} | FEN: $currentFen")`.
    - Validasi & Deploy:
      * `./gradlew compileDebugKotlin` & `./gradlew installDebug`.
      * Monitor: `adb -s R9CW605ECXM logcat -s "StockfishAudit" "CheckAudit" "StockfishError"`.

49. **Sprint 49 (2026-08-31): Audit Lifecycle Binary Stockfish & Raw Handshake UCI Output ke Logcat**
    - Audit & Eksekusi Binary Stockfish Asli (`StockfishProcessManager.kt` / `StockfishNativeBridge.kt`):
      * Memastikan binary executable Stockfish C++ berjalan dan mengeksekusi handshake UCI (`uci` -> `uciok`).
      * Mencetak seluruh raw response handshake Stockfish ke Logcat:
        `id name Stockfish ...`
        `id author the Stockfish developers (see AUTHORS file)`
        `uciok`
      * Validasi lifecycle start/ready/stop binary.
    - Validasi & Deploy:
      * `./gradlew compileDebugKotlin` & `./gradlew installDebug`.
50. **Sprint 50 (2026-08-31): Audit & Verifikasi Total StockfishBridge: Eksekusi Binary/Native Asli C++ & Pembersihan Mock Move Generator**
    - Eksekusi Asli C++ & Penghapusan Total Mock/Fallback Generator:
      * Menghapus semua mock fallback, random move, atau local heuristic generator jika engine gagal load. Jika Stockfish gagal inisialisasi, wajib lempar Fatal Exception / tampilkan Toast: "❌ Gagal memuat binary Stockfish asli!".
      * Memastikan permission binary dapat dieksekusi (`file.setExecutable(true)`).
    - Raw UCI Handshake Logger (`StockfishRaw`):
      * Mengirim `"uci"` dan mencetak seluruh baris output stdout: `Log.d("StockfishRaw", "Stdout: $line")`.
      * Respon resmi: `id name Stockfish ...`, `id author the Stockfish developers (see AUTHORS file)`, `uciok`.
    - Parser `bestmove` Regex Presisi:
      * `Regex("""bestmove\s+([a-h][1-8][a-h][1-8][qrbn]?)""").find(rawOutput)?.groupValues?.get(1)`.
    - Logging Audit FEN & Raw Output (`StockfishBridge`):
      * `Log.d("StockfishBridge", "=== REQUEST EVALUATION ===")`, `INPUT FEN`, `RAW ENGINE OUTPUT`, `PARSED BESTMOVE`.
51. **Sprint 51 (2026-08-31): Integrasi Binary Native Stockfish ARM64 ke jniLibs & Non-Blocking Async StockfishBridge**
    - Binary Native Stockfish ARM64 (`jniLibs/arm64-v8a/libstockfish.so` & `armeabi-v7a`):
      * Mengunduh binary executable Stockfish Android ARM64 dan menyimpannya sebagai `libstockfish.so` di `app/src/main/jniLibs/arm64-v8a/` dan `armeabi-v7a/`.
      * Konfigurasi `sourceSets.main.jniLibs.srcDirs("src/main/jniLibs")` di `app/build.gradle.kts`.
    - Rombak Total `StockfishBridge.kt`:
      * Eksekusi langsung executable native dari `context.applicationInfo.nativeLibraryDir + "/libstockfish.so"`.
      * Handshake UCI terverifikasi (`uci` -> `uciok` -> `ucinewgame` -> `isready`) dengan log `StockfishInit`.
      * `getBestMove(fen, moveTimeMs)` non-blocking async coroutine dengan safeguard timeout.
    - Update State UI saat Engine Selesai / Gagal (`InteractiveBoardOverlayView.kt`):
      * Jika `bestMove == null`, set `isEngineCalculating = false`, postInvalidate().
52. **Sprint 52 (2026-08-31): Error Trap, Watchdog Anti-Freeze & Embedded Alpha-Beta Fallback Engine**
    - Audit & Error Trap Eksekusi Process Stockfish (`StockfishBridge.kt`):
      * Logging path binary, exists, dan canExecute dengan tag `StockfishDebug`.
      * Cek `isAlive` & exit value jika proses mati mendadak saat spawn.
    - Watchdog Anti-Freeze 1.5s & Stream Logger (`StockfishBridge.kt`):
      * Strict timeout 1500ms pada `getBestMove`, logging output raw ke `StockfishRawOut`.
    - UI Graceful Failure Handling (`InteractiveBoardOverlayView.kt`):
      * Reset `isEngineCalculating = false`, reset giliran catur, dan tampilkan indikator "⚠️ Engine tidak merespons, input langkah manual".
    - Embedded Alpha-Beta Fallback Engine (`FallbackMoveEngine.kt`):
53. **Sprint 53 (2026-08-31): Konfigurasi 100% Stockfish C++ Uncapped Grandmaster Mode (3500+ ELO) & Status Engine UI**
    - Konfigurasi Stockfish Full Strength / Uncapped Mode (`StockfishBridge.kt` & `ChessEngineService.kt`):
      * Jika ELO >= 2800: `UCI_LimitStrength = false`, `Skill Level = 20`, `Threads = 2`, `Hash = 16`, `Log.d("StockfishBeast", "🔥 Stockfish diatur ke Full Uncapped Grandmaster Mode (3500+ ELO)")`.
      * Jika ELO < 2800: `UCI_LimitStrength = true`, `UCI_Elo = $clampedElo`, `Skill Level = $skill`.
    - Matikan Silent Fallback & Pasang Banner Status Engine (`InteractiveBoardOverlayView.kt`):
      * Log `Log.d("EngineCheck", "Engine Aktif: ...")`.
      * Jika binary native gagal dimuat, tampilkan banner merah `[ ⚠️ Stockfish Native Gagal Load ]` di UI Header.
    - Optimasi Waktu & Kedalaman Berpikir Engine:
      * Minimum depth 12 atau movetime 400ms untuk kalkulasi super tajam tanpa blunder.
54. **Sprint 54 (2026-08-31): Stockfish JNI Wrapper / Stderr Logging & Non-Blocking Polling Anti-Deadlock**
    - Error Stream Non-Blocking Logger (`StockfishBridge.kt`):
      * Tangkap `process.errorStream` ke coroutine terpisah untuk menangkap pesan crash / linker / SELinux error: `Log.e("StockfishCrash", "Stderr: $errorLine")`.
    - Stockfish Embedded NNUE / Self-Contained Binary:
      * Menjamin binary / JNI C++ library self-contained tanpa dependensi external file.
    - Standarisasi I/O Stream Anti-Deadlock:
      * Menggunakan `readAvailableLines` non-blocking polling dengan `reader?.ready()` dan delay coroutine.
    - Feedback Visual Transparan di UI Header:
      * `[ ❌ Engine C++ Crash: Cek Logcat ]` jika proses mati/crash.
55. **Sprint 55 (2026-08-31): Pre-Warming Engine NNUE (Cold Start Buster) & Optimasi Timeout/Movetime**
    - Pre-Warming Engine NNUE di `startEngine()` (`StockfishBridge.kt`):
      * Eksekusi `position startpos` dan `go depth 1` saat inisialisasi agar network NNUE dimuat ke memori sebelum game dimulai.
      * Log `Log.d("StockfishInit", "⚡ Engine NNUE Pre-Warmed & Siap Tempur Instan!")`.
    - Optimasi Timeout & Movetime (`StockfishBridge.kt` & `ChessEngineService.kt`):
      * Hapus `setoption name Clear Hash` per langkah untuk memanfaatkan caching transpositions table.
      * Tingkatkan timeout buffer menjadi `moveTimeMs + 3000L` agar coroutine tidak dibatalkan sebelum engine menyelesaikan pencarian.
    - Penggambaran Panah Langkah Instan (`InteractiveBoardOverlayView.kt`):
      * Saat `bestMove` diterima: update `currentBestMove = bestMove`, `isArrowVisible = true`, `isThinking = false`, `postInvalidate()`, log `Log.d("StockfishSuccess", "🏹 Menggambar panah untuk langkah: $bestMove")`.
56. **Sprint 56 (2026-08-31): Perbaikan Timing Auto-Hide ke Event Selesai Evaluasi Engine & Pembatalan Interaktif**
    - Trigger Auto-Hide Pasca Selesai Evaluasi Engine (`InteractiveBoardOverlayView.kt`):
      * Hapus pemanggilan timer auto-hide dari input user move (`executeMove`).
      * Pindahkan inisialisasi timer auto-hide ke `onEngineResult` setelah panah berhasil digambar dan terlihat di layar:
        `if (isAutoHideEnabled && autoHideDelaySec > 0 && !isEngineCalculating) { autoHideJob?.cancel(); autoHideJob = viewScope.launch { delay(autoHideDelaySec * 1000L); onToggleVisibilityRequested?.invoke(true) } }`
    - Pembatalan Timer Auto-Hide pada Interaksi Pengguna:
      * Batalkan `autoHideJob?.cancel()` saat event touch `ACTION_DOWN` pada board/palette/header atau saat mode berganti.
      * Dilarang keras auto-hide saat Stockfish sedang menghitung (`isEngineCalculating == true`).
57. **Sprint 57 (2026-08-31): Perbaikan Indikator Status Kesehatan Engine JNI & Pembersihan Visual Header**
    - Pengecekan Status Kesehatan Engine JNI (`StockfishBridge.kt`):
      * `fun isEngineHealthy(): Boolean = StockfishNativeBridge.isNativeLoaded()`
      * Saat `getBestMove` berhasil mendapatkan `bestmove`: set `hasEngineCrashed = false` dan `hasEngineError = false`.
    - Header Tampilan Overlay Bersih (`InteractiveBoardOverlayView.kt`):
      * Hapus teks merah palsu `Stockfish Native Gagal Load` saat JNI sudah sehat.
      * Hanya tampilkan warning jika `!bridge.isEngineHealthy()`.
      * Saat evaluasi selesai di `onEngineResult`: pastikan `hasEngineError = false` dan `postInvalidate()`.
58. **Sprint 58 (2026-08-31): Pencegahan Race Condition & Reset Mutlak Stale Cache BestMove**
    - Reset Mutlak State BestMove Sebelum Evaluasi Baru (`InteractiveBoardOverlayView.kt`):
      * Saat giliran berganti atau FEN baru dikirim: `engineBestMove = null`, `isArrowVisible = false`, `isEngineCalculating = true`, `postInvalidate()`.
      * Hapus interceptor penggantian langkah otomatis di tengah jalan agar engine murni menyelesaikan FEN yang sedang aktif.
    - Sinkronisasi & Buffer Draining pada `StockfishBridge.kt` & `StockfishProcessManager.kt`:
      * Bersihkan sisa buffer output sebelum mengirim perintah `position fen` dan `go movetime`.
      * Tunggu respon `bestmove` resmi dari FEN yang dikirimkan.
    - Update UI Panah Resmi:
      * Saat `evaluatedMove` diterima: `engineBestMove = evaluatedMove`, `isArrowVisible = true`, `isEngineCalculating = false`, `postInvalidate()`.
      * Logcat: `Log.d("StockfishSuccess", "🏹 Panah RESMI digambar: $evaluatedMove")`.
59. **Sprint 59 (2026-08-31): Mutex Single-Thread Engine Locking & Cancel Evaluasi Lama**
    - Kunci Komunikasi Stockfish dengan Mutex (`StockfishBridge.kt`):
      * Gunakan `private val engineMutex = Mutex()` dan `engineMutex.withLock` pada `getBestMove`.
      * Dilarang keras multi-thread membaca reader secara bersamaan.
      * Catat log setiap baris output: `Log.d("StockfishSingleThread", line)`.
    - Pembatalan Evaluasi Sebelumnya & Bersihkan State UI (`InteractiveBoardOverlayView.kt` & `MiniBoardOverlayService.kt`):
      * Batalkan coroutine job evaluasi lama (`stockfishJob?.cancel()`, `evalTimeoutJob?.cancel()`).
      * Kosongkan state visual: `engineBestMove = null`, `isArrowVisible = false`, `isEngineCalculating = true`, `postInvalidate()`.
      * Update visual panah valid saat hasil resmi diterima: `Log.d("StockfishSuccess", "🏹 Panah VALID digambar: $bestMove")`.
60. **Sprint 60 (2026-08-31): Dedicated SingleThreadDispatcher Engine Queue & Bebas Deadlock 100%**
    - Rombak Total StockfishBridge dengan Dedicated SingleThreadDispatcher (`StockfishBridge.kt`):
      * Menggunakan `Executors.newSingleThreadExecutor().asCoroutineDispatcher()` dan `engineScope`.
      * Seluruh perintah UCI (`uci`, `position fen`, `go movetime`, `stop`, `quit`) diproses secara berurutan dan aman tanpa Mutex blocking.
      * Non-blocking reading output dengan timeout dinamis dan reader drain.
    - Sinkronisasi Asinkron Pemicu Evaluasi (`MiniBoardOverlayService.kt` & `InteractiveBoardOverlayView.kt`):
      * Pemicu evaluasi `getBestMove` dijalankan secara asinkron tanpa menahan UI thread.
      * Menggambar panah dan mematikan thinking state saat respon `bestMove` valid diterima: `Log.d("StockfishSuccess", "🏹 Panah VALID digambar: $bestMove")`.
61. **Sprint 61 (2026-08-31): Single Dedicated Reader Loop + CompletableDeferred & Penghapusan Safety Timeout Manual**
    - Arsitektur Single Reader Loop + CompletableDeferred (`StockfishBridge.kt`):
      * SATU-SATUNYA loop pembaca seumur hidup proses (`startSingleReaderLoop`).
      * Menyelesaikan `pendingBestMoveDeferred` dan `readyOkDeferred` secara reaktif dan instan begitu baris `bestmove` diterima dari stdout engine.
      * `getBestMove` murni meng-await `movePromise.await()` dengan buffer timeout aman (`moveTimeMs + 1500L`).
    - Penghapusan Safety Timeout Manual yang Konflik di UI (`InteractiveBoardOverlayView.kt`):
      * Hapus timer manual 1000ms independen yang mendahului selesainya kalkulasi Stockfish.
      * Biarkan hasil resmi dari `getBestMove` yang mengontrol selesai tidaknya status `isEngineCalculating` dan penggambaran panah: `Log.d("StockfishSuccess", "🏹 Panah VALID digambar: $bestMove")`.
62. **Sprint 62 (2026-08-31): Pembersihan Zombie Process, Batch Atomic UCI Commands & Penggambaran Panah UI**
    - Singleton & Pembersihan Zombie Process (`StockfishBridge.kt`):
      * `stopEngineInternal()` sebelum membuat instance proses baru untuk mencegah duplikasi binary Stockfish di memori.
      * Inisialisasi awal dikirim dalam batch (`sendBatchCommands`).
    - Pengiriman Perintah Evaluasi Atomik (Anti-Terbalik):
      * `val batchCommand = "stop\nposition fen $fen\ngo movetime $moveTimeMs\n"` dikirim dalam 1 kali `write()` & `flush()`.
    - Penggambaran Panah UI ([`InteractiveBoardOverlayView.kt`](file:///media/fatihfarhat/New%20Volume/PROJECTS/ChessBeater/app/src/main/java/com/chessbeater/overlay/InteractiveBoardOverlayView.kt)):
      * Saat `bestMove` diterima: `engineBestMove = bestMove`, `isArrowVisible = true`, `isEngineCalculating = false`, `Log.d("StockfishSuccess", "🏹 Menggambar panah untuk langkah: $bestMove")`, `postInvalidate()`.
63. **Sprint 63 (2026-08-31): Modul Deteksi Langkah Lawan Otomatis (BoardPixelSampler) & Sinkronisasi Stockfish**
    - Modul Vision Corner-Sampling (`BoardPixelSampler.kt`):
      * Algoritma 4 titik corner per petak (15% & 85% width/height) menghindari oklusi bidak.
      * Profil deteksi warna highlight Chess.com (Yellow/Green highlight `rgb(245, 246, 130)` / `rgb(186, 202, 68)`, Classic, Green, Wood) & Lichess.
      * Pemetaan 2 petak highlight menjadi langkah catur legal berdasarkan turn dan board array.
    - Integrasi Sampling Loop di `MiniBoardOverlayService.kt`:
      * Sampling bitmap potongan area papan (~100ms / 10 FPS saat giliran lawan).
      * Debounce stabilitas 2 frame berurutan untuk mencegah noise/animasi transisi.
    - Sinkronisasi Otomatis ke Stockfish:
      * Eksekusi langkah lawan ke papan internal `overlayManager?.applyOpponentMove(fromIdx, toIdx)`.
      * Evaluasi otomatis posisi baru dengan Stockfish & tampilkan panah balasan (*bestmove*).
64. **Sprint 64 (2026-08-31): Panel Pengaturan 2-Kolom & Modul Humanization (Anti-Cheat Engine)**
    - Panel Pengaturan 2-Kolom (`InteractiveBoardOverlayView.kt` / `SettingsOverlayView.kt`):
      * Kolom Kiri: ELO slider (800-3500), Flip Board, Auto-Hide slider, Auto-Show slider, Reset Papan.
      * Kolom Kanan: Humanize Move Switch (Default: ON), Slider Level (0-10, Default: 6 Rekomendasi ~82% akurasi), Blunder Guard Switch (Default: ON), Natural Move Delay Switch (Default: ON).
    - Modul Humanization Selector (`HumanizationEngine.kt`):
      * MultiPV candidate parsing (T1, T2, T3) dengan pembobotan probabilitas berdasarkan level dan Blunder Guard threshold (delta cp <= 45).
    - Siklus Evaluasi & Natural Delay:
      * Jeda acak alami 1200ms - 3000ms saat Natural Move Delay aktif sebelum panah digambar: `Log.d("Humanize", "Natural delay diterapkan...")`.
      * Persistence preferensi Humanization di SharedPreferences repository.
65. **Sprint 65 (2026-08-31): Debounce Coroutine Slider ELO & Pengaturan (SettingsDebounce)**
    - Debounce Input Slider (`InteractiveBoardOverlayView.kt`):
      * Nilai UI ELO ter-update seketika (real-time).
      * Pengiriman perintah `setoption` ke Stockfish engine C++ di-debounce selama 250ms via `eloDebounceJob` untuk mencegah spam UCI command saat slider digeser: `Log.d("SettingsDebounce", "⚡ ELO resmi disetel ke Engine: $elo")`.
      * Debounce penyimpanan preferensi visual (`notifyVisualPrefsChanged`) dan preferensi Humanize (`humanizeSaveJob`).
66. **Sprint 66 (2026-08-31): Slider Transparansi Overlay Real-Time & SharedPreferences (SettingsTransparency)**
    - Slider Transparansi Overlay (`InteractiveBoardOverlayView.kt` & `MiniBoardOverlayService.kt`):
      * Slider Transparansi Overlay (30% s/d 100%, Default: 95%).
      * Mengubah alpha view overlay / elemen grafis secara real-time saat slider digeser: `Log.d("SettingsTransparency", "👻 Transparansi diubah ke: $alphaPercent%")`.
      * Persistence nilai `overlay_alpha` di SharedPreferences dan dimuat kembali saat startup.
67. **Sprint 67 (2026-08-31): Style Highlight Petak (Filled vs Outlined) & Rendering Canvas**
    - Opsi Style Highlight Petak (`InteractiveBoardOverlayView.kt`):
      * Switch/Tombol toggle "Gaya Highlight" di Kolom Kiri panel pengaturan: [ 🟩 Filled ] vs [ 🔲 Outlined ].
      * `isHighlightFilled` disimpan ke `SharedPreferences` (`highlight_is_filled`) dan dimuat saat startup: `Log.d("SettingsStyle", "🎨 Style highlight diubah ke: ...")`.
    - Rendering Kanvas Dinamis:
      * `drawSquareHighlight(canvas, rect, color)`: Mode Filled (semi-transparan fill alpha ~110) vs Mode Outlined (stroke ~5dp, inset rect, alpha ~240).
      * Diterapkan ke petak langkah asal (`lastMoveFrom`), petak tujuan (`lastMoveTo`), dan petak klik aktif (`selectedSquare`).
68. **Sprint 68 (2026-08-31): Perombakan Total UI Dialog Modal Pengaturan & Main Menu (Ukuran Besar, Proporsional, Finger-Friendly)**
    - Redesign Container Dialog Modal (`InteractiveBoardOverlayView.kt`):
      * Lebar container proporsional 94% layar (`width * 0.94f`), tinggi dinamis disesuaikan item tanpa void kosong.
      * Background card modern dark slate `#1A1D24` dengan rounded 16dp dan border halus `#2E3440`.
    - Tipografi Standar & Touch Target Besar:
      * Header dialog: 16sp Bold + Tombol Tutup (X) 36x36dp.
      * Section title Kolom 1 & 2: 14sp Bold dengan aksen warna jelas.
      * Label & slider value: 13sp Semi-Bold (#E2E8F0) dan 13sp Bold Highlight (#38BDF8).
      * Toggle / Button touch target: min-height 44dp, corner radius 8dp, font 13sp Bold.
    - Grid 2 Kolom Seimbang:
      * Kolom Kiri: Slider ELO (800-3500), Slider Transparansi Overlay (30%-100%), Style Highlight ([ 🟩 Filled ] vs [ 🔲 Outlined ]), Slider Auto-Hide (0-10s), Slider Auto-Show (0-10s).
      * Kolom Kanan: Switch Humanize Move (Anti-Cheat), Slider Level Humanis (0-10), Teks Info Dinamis (11sp, #94A3B8), Switch Blunder Guard, Switch Natural Move Delay.
    - Tombol Navigasi Bawah:
      * Lebar penuh match container, tinggi 46dp, background #334155, teks 14sp Bold "⬅️ Kembali ke Menu Papan".
    - Main Menu "Kontrol & Pengaturan":
      * List item height 44dp per baris, font 13.5sp - 14sp dengan spacing ikon bersih dan nyaman disentuh.
69. **Sprint 69 (2026-08-31): Pemulihan Lengkap Transparansi Detail Elemen (Bidak, Grid, Panah, Highlight, Floating Eye)**
    - Pemulihan 5 Slider Transparansi Detail (`InteractiveBoardOverlayView.kt` / `MiniBoardOverlayService.kt`):
      * Slider 1: Transparansi Bidak (0% - 100%, Default: 100%).
      * Slider 2: Transparansi Grid Papan (0% - 100%, Default: 85%).
      * Slider 3: Transparansi Panah Petunjuk (20% - 100%, Default: 95%).
      * Slider 4: Transparansi Highlight Petak (10% - 100%, Default: 50%).
      * Slider 5: Transparansi Tombol Floating Eye (20% - 100%, Default: 85%).
    - Tab Segmented Navigation di Panel Pengaturan:
      * Tab 1: `[ ⚙️ Engine & Anti-Cheat ]`
      * Tab 2: `[ 🎨 Detail Transparansi ]`
    - Real-Time Canvas Alpha Rendering:
      * Bidak: `piecePaint.alpha = (pieceAlpha * 255).toInt()` & `pieceAlpha` diaplikasikan pada bitmap & vector piece rendering.
      * Grid: `lightPaint.alpha = (gridAlpha * 255).toInt()`, `darkPaint.alpha = (gridAlpha * 255).toInt()`.
      * Panah: `arrowStrokePaint.alpha = (arrowAlpha * 255).toInt()`, `arrowFillPaint.alpha = (arrowAlpha * 255).toInt()`.
      * Highlight: `customHighlightPaint.alpha` dikalikan dengan `highlightAlpha`.
      * Floating Eye: `floatingEyeAlpha` disimpan & diterapkan saat mode mata aktif.
70. **Sprint 70 (2026-08-31): Rombak Total Sistem 2-Tab Modern Full-Width 1-Kolom (Anti-Overlap)**
    - Pembuangan Total Sistem 2 Kolom Menyamping:
      * Mengganti layout menyamping yang sempit menjadi Sistem 2-Tab Modern (Full Width 1-Kolom per Tab) dengan Tab Bar setinggi 42dp.
      * Tab 1: `[ 🎮 Tampilan & Engine ]` (9 Item 1-Kolom penuh: Target ELO, Style Highlight [🟩 Filled vs 🔲 Outlined], 5 Slider Transparansi Detail [Bidak, Grid, Panah, Highlight, Floating Eye], Slider Auto-Hide, Slider Auto-Show).
      * Tab 2: `[ 🛡️ Anti-Cheat & Humanize ]` (5 Item 1-Kolom: Switch Humanize Move 48dp, Slider Level Humanis 0-10, Info Dinamis 12sp, Switch Blunder Guard 48dp, Switch Natural Delay 48dp).
    - Scrollable Viewport & Full Width Layout:
      * Seluruh slider & switch menempati lebar horizontal 100% dari container modal dialog (`width * 0.94f`).
      * Tidak ada tabrakan / overlap antar elemen di semua resolusi HP Android portrait.
      * Dukungan vertical touch drag scrolling di viewport dialog.
    - Standarisasi Tipografi & Jari:
      * Tab header: 13sp Bold.
      * Label slider: 13sp Semi-Bold (#E2E8F0) dengan value indicator highlight (#38BDF8).
      * Padding antar elemen: 12dp vertikal.
      * Bottom action button: `[ ⬅️ Kembali ke Menu Papan ]` 46dp (#334155, 14sp Bold).
71. **Sprint 71 (2026-08-31): Custom ThumbOnlySeekBar, Sticky Tab Bar, Compact UI & Move Guide Alpha Slider**
    - Komponen Custom ThumbOnlySeekBar (`com/chessbeater/ui/ThumbOnlySeekBar.kt`):
      * SeekBar yang hanya merespons sentuhan tepat pada area thumb (`hitRect` + slop 24dp) dan mengabaikan tap sembarangan di batang slider agar scroll layar tidak terganggu.
      * Di `InteractiveBoardOverlayView.kt`, sentuhan slider kanvas dibatasi secara ketat hanya pada `thumbHitRect` sehingga gesture scroll vertikal layar 100% bebas dari pergeseran slider tidak sengaja.
    - Sticky Tab Switcher Bar Pinned di Atas:
      * Tab bar ("🎮 Tampilan & Engine" vs "🛡️ Anti-Cheat & Humanize") diposisikan tetap (pinned) tepat di bawah Header Judul, di luar scrollable viewport.
      * Tab dapat ditekan kapan saja secara instan tanpa terhalang atau bergeser oleh posisi scroll.
    - UI Ringkas, Bersih, & Nyaman (Visual Breathing Room):
      * Dialog Width: 90% lebar layar, Max Height: 75% tinggi layar. Padding container 12dp, jarak antar grup slider 8dp.
      * Tipografi proporsional: Title 14sp Bold, Tab 12.5sp Bold, Label Slider 12sp Semi-Bold, Indikator Nilai 12sp Bold Cyan (#38BDF8).
    - Slider MAX ELO Power & Transparansi Move Guide:
      * Slider MAX ELO (800 - 3500, Default: 3500) dengan debounce dan `stockfishBridge.setMaxElo()`.
      * Slider Transparansi Move Guide Dots (0% - 100%, Default: 80%) yang tersimpan di `SharedPreferences` (`move_guide_alpha`) dan diterapkan langsung ke `dotPaint.alpha` & `dotRingPaint.alpha`.
72. **Sprint 72 (2026-08-31): Sinkronisasi Koordinat 1:1 Kalibrasi Layar & InteractiveBoardOverlayView**
    - Penyelarasan LayoutParams WindowManager (`OverlayManager.kt`):
      * Window Kalibrasi (`BoardCalibrationOverlayView`) dan Window Papan Interaktif (`InteractiveBoardOverlayView`) sama-sama dikonfigurasi sebagai Fullscreen Overlay: `MATCH_PARENT x MATCH_PARENT`, `FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS`, `gravity = TOP or START`, `x = 0, y = 0`, serta `layoutInDisplayCutoutMode = SHORT_EDGES`.
      * Menghilangkan distorsi offset status bar, notch cutouts, dan translasi window sehingga titik (0,0) di kedua canvas 100% identik terhadap piksel layar fisik HP.
    - Penyimpanan Koordinat Presisi (`BoardCalibrationOverlayView.kt`):
      * Menyimpan koordinat batas kotak kalibrasi (`board_left`, `board_top`, `board_right`, `board_bottom`) langsung ke `SharedPreferences` (`chessbeater_visual_prefs`).
    - Rendering Grid Presisi 1:1 (`InteractiveBoardOverlayView.kt`):
      * Memuat `boardRect` saat inisialisasi dari preferensi visual.
      * Menggambar ke-64 petak catur, highlight, bidak, dan move guide TEPAT di dalam area `boardRect` (`sqW = boardRect.width() / 8f`, `sqH = boardRect.height() / 8f`).
      * Floating Header Bar diposisikan dinamis di atas papan (`boardRect.top - headerH - 8dp`) atau di bawah papan jika mepet status bar.
      * Touch handling dan dragging papan terikat 1:1 dengan `boardRect` dan langsung tersimpan ke preferensi.
73. **Sprint 73 (2026-08-31): Perbaikan Mode Koreksi Papan (Board Editor), Solid Palette Pieces & Rekonstruksi Dialog Pengaturan 1:1 Papan Catur**
    - Preset Transparansi Otomatis Mode Koreksi (`InteractiveBoardOverlayView.kt`):
      * Saat masuk mode koreksi (`enterBoardCorrectionMode`): backup state transparansi pengguna (`backupPieceAlpha`, `backupGridAlpha`, `backupHighlightAlpha`, `backupArrowAlpha`, `backupMoveGuideAlpha`). Nilai diubah ke `pieceAlpha = 0.50f` (50% tembus pandang untuk melihat bidak asli game), `gridAlpha = 0.0f` (tembus total), `highlightAlpha = 0.0f`, `arrowAlpha = 0.0f`, `moveGuideAlpha = 0.0f`.
      * Saat keluar mode koreksi (`exitBoardCorrectionMode`): seluruh preferensi visual dikembalikan 100% ke nilai backup pengguna semula.
      * Dipanggil otomatis saat tombol "✅ Selesai", "Reset", atau aksi koreksi papan ditutup.
    - Ikon 12 Bidak Palet Bawah 100% Solid & Terpisah:
      * Menggunakan `palettePiecePaint` (alpha = 255) serta fill & stroke solid khusus (`palettePieceWhiteFill`, `palettePieceBlackFill`) sehingga 12 bidak palet (6 Putih di baris 1, 6 Hitam di baris 2) selalu tampil 100% solid, jelas, dan tidak terkena transparansi papan.
      * Ditambahkan visual border & fill highlight hijau neon pada bidak palet yang sedang aktif dipilih.
    - Rekonstruksi Dimensi Dialog Pengaturan Sesuai Papan Catur (1:1 Board Area):
      * Container dialog `MenuPage.APPEARANCE` dikunci secara presisi menutupi area papan catur `boardRect` (`modalW = boardRect.width()`, `modalH = boardRect.height()`, `modalL = boardRect.left`, `modalT = boardRect.top`).
      * Card background elegan: `#181B20` (95% solid), corner radius 12dp, border tipis 1dp `#2A303C`.
    - Scaling Elemen UI 40% Lebih Kecil (Compact HUD Layout):
      * Header bar (28dp): Title 11.5sp Bold (#E2E8F0), Close button 22x22dp (font 11sp).
      * Sticky Tab Bar (28dp): Tab 1 ("🎮 Tampilan & Engine") vs Tab 2 ("🛡️ Anti-Cheat & Humanize"), font 10sp Bold, corner radius 6dp.
      * Bottom action bar (30dp): Tombol "⬅️ Kembali ke Menu Papan" font 10.5sp Bold.
      * Scrollable viewport: Item slider (32dp), track (3.5dp), thumb radius (7dp / diameter 14dp), touch slop (18dp), font 9.5sp Semi-Bold (#CBD5E1) dan nilai 9.5sp Bold (#38BDF8), switch toggle (26dp), text info humanis (8.5sp).
74. **Sprint 74 (2026-08-31): Kunci Posisi Papan, Touch Pass-Through Luar Papan, Ghost Controls, Session Logger & Relatif Piece Animation**
    - Kunci Posisi Papan (Cegah Geser):
      * Menambahkan toggle preferensi `board_is_locked` (Default: true) di Tab 1 Pengaturan dan `InteractiveBoardOverlayView.kt`.
      * Ketika `isBoardLocked == true`, event `ACTION_MOVE` pada papan dan `scaleDetector` diabaikan sepenuhnya sehingga papan tidak akan pernah bergeser secara tidak sengaja.
    - Touch Pass-Through Layar Luar Papan (`InteractiveBoardOverlayView.kt`):
      * Mengecek apakah sentuhan `ACTION_DOWN` berada di dalam `boardRect`, `headerBounds`, `statusBounds`, `editorBounds`, atau `modalCardRect`.
      * Jika sentuhan berada di luar seluruh bounding box tersebut, `onTouchEvent` me-return `false` sehingga interaksi sentuhan langsung menembus 100% ke aplikasi game di bawahnya (Chess.com, Lichess, dll) berkat flag `FLAG_NOT_TOUCH_MODAL`.
    - Ghost Controls (Sembunyikan Header & Footer 100% Transparan):
      * Menambahkan toggle `ghost_controls_enabled` (Default: false) di Tab 1 Pengaturan dan `InteractiveBoardOverlayView.kt`.
      * Saat aktif, header background, border, title, serta status bar disembunyikan total, namun tombol mata (hide) dan tombol tiga titik (menu) tetap aktif dan responsif di posisinya dengan sentuhan halus/transparan.
    - Sistem Saved Logs & Session Recorder (`SessionLogger.kt`, `DashboardViewModel.kt`, `DashboardScreen.kt`):
      * Modul `SessionLogger` merekam timestamp, FEN, pergerakan, evaluasi bestmove engine, dan durasi ke file internal (`/files/logs/session_TIMESTAMP.log`).
      * Ditambahkan Switch "Rekam Log Sesi Pertandingan" dan Section UI "📁 SAVED LOGS & SESSION RECORDER" di layar utama `MainActivity` dengan daftar card sesi, dialog penampil isi log (ScrollView monospace), dan tombol hapus log.
    - Presisi Animasi Pergerakan Bidak Catur Relatif `boardRect`:
      * Titik awal interpolasi (`startX`, `startY`) dan titik akhir (`endX`, `endY`) dihitung strictly relatif terhadap `boardRect.left` dan `boardRect.top`.
      * Ditambahkan fungsi helper `getSquareCenterCoordinates(square: String): PointF` dan `getSquareTopLeftCoordinates(sq: Int): PointF`.
75. **Sprint 75 (2026-08-31): Perbaikan Logika Auto-Show Berdasarkan OverlayHideReason (Hanya Aktif Saat Auto-Hide)**
    - Enum State Alasan Sembunyi (`OverlayHideReason.kt`):
      * Dibuat enum `OverlayHideReason` (`NONE`, `AUTO_HIDE`, `MANUAL`) untuk membedakan pemicu overlay disembunyikan.
    - Pembatasan Timer Auto-Show (`MiniBoardOverlayService.kt` & `InteractiveBoardOverlayView.kt`):
      * `onToggleVisibilityRequested` dan `onMiniBoardToggleVisibilityRequested` kini mengirimkan parameter `(hide: Boolean, reason: OverlayHideReason)`.
      * Timer Auto-Show (`autoShowJob`) HANYA dijadwalkan jika `reason == OverlayHideReason.AUTO_HIDE`.
76. **Sprint 76 (2026-08-31): Perbaikan Touch Pass-Through & Cegah Pergeseran Papan dari Luar Batas Elemen**
    - Hit-Testing & Pass-Through Presisi (`InteractiveBoardOverlayView.kt`):
      * Mengimplementasikan `dispatchTouchEvent(event)` & `onTouchEvent(event)`: Mengecek apakah sentuhan berada di dalam `boardRect`, `headerBounds` (bukan ghost), `btnMenuBounds`, `btnEyeBounds`, `statusBounds`, `editorBounds`, atau `modalCardRect`.
      * Jika sentuhan berada di luar seluruh elemen tersebut, event langsung di-return `false` sehingga interaksi sentuhan pada game/aplikasi di bawahnya (Chess.com, Lichess) bekerja 100% tanpa delay dan tanpa menggeser papan overlay.
    - Kunci Drag Papan (`isBoardLocked` & Origin Check):
      * Jika `isBoardLocked == true`, event `ACTION_MOVE` pada papan diabaikan (return `true` tanpa memindahkan koordinat papan).
      * Jika tidak terkunci, gesture drag HANYA diproses jika titik awal sentuhan (`rawDownX, rawDownY`) benar-benar berada di dalam `boardRect`.
77. **Sprint 77 (2026-08-31): Komponen TapOnReleaseSeekBar & Mekanisme Commit on Release untuk Slider Pengaturan**
    - Komponen Custom `TapOnReleaseSeekBar` (`TapOnReleaseSeekBar.kt`):
      * Mencegah lompatan nilai progress saat menyentuh batang slider di `ACTION_DOWN`.
      * Scroll Immunity: Jika pergerakan vertikal `dy > touchSlop && dy > dx`, intersep dilepas sehingga `ScrollView` dapat menggulung dengan mulus.
      * Commit on Release: Jika terjadi tap pada batang slider atau drag selesai, progress diperbarui HANYA saat jari diangkat (`ACTION_UP`).
78. **Sprint 78 (2026-08-31): Inisialisasi Startup Layar Bersih (Papan Default Sembunyi, Floating Eye Aktif)**
    - Mode Hidden Default Startup (`MiniBoardOverlayService.kt` & `OverlayManager.kt`):
      * Parameter `startHidden: Boolean = true` pada `showInteractiveBoard`: Menyiapkan instance papan interaktif tanpa langsung menempelkannya (`addView`) ke WindowManager.
      * Saat service pertama kali dimulai: `isBoardShowing = false`, `currentHideReason = OverlayHideReason.MANUAL`, dan tombol Floating Eye langsung dimunculkan di tepi layar (`showFloatingEyeFromSaved()`).
79. **Sprint 79 (2026-08-31): Alur Kalibrasi Otomatis (Auto-Launch Game Target & Auto-Return ke Chess Beater)**
    - Alur Mulai Kalibrasi (`MainActivity.kt`):
      * Saat tombol "Mulai Kalibrasi Papan" ditekan, aplikasi secara otomatis mendeteksi target package (`com.chess`) dan meluncurkannya ke foreground via `packageManager.getLaunchIntentForPackage()`.
      * Overlay kalibrasi langsung aktif di atas tampilan papan catur game asli.
    - Simpan & Batal Otomatis Kembali ke Aplikasi (`MainActivity.kt` & `BoardCalibrationOverlayView.kt`):
      * Tombol "💾 Simpan": Menyimpan koordinat kalibrasi, menutup overlay, menampilkan toast sukses, dan secara otomatis memanggil intent `FLAG_ACTIVITY_REORDER_TO_FRONT` untuk mengembalikan user ke tampilan utama `MainActivity` Chess Beater.
80. **Sprint 80 (2026-08-31): Quick Alignment Grid HUD Overlay (Auto-Display saat Service Start, Drag/Resize, Hold 2 Detik Kunci)**
    - Custom View `QuickAlignmentOverlayView` (`QuickAlignmentOverlayView.kt`):
      * Garis luar neon cyan & 64 petak grid 8x8 transparan.
      * Handle resize di sudut kanan-bawah & touch dragging di dalam grid.
      * Fitur Hold-to-Lock: Menahan jari selama 2 detik menggetarkan HP (haptic 70ms) dan otomatis mengunci serta menyimpan koordinat ke preferensi.
81. **Sprint 81 (2026-08-31): Halaman Fullscreen SettingsActivity, Kustomisasi Warna Highlight, & Navigasi Pengaturan**
    - Halaman Pengaturan Fullscreen Modern 2-Tab (`SettingsActivity.kt` & `activity_settings`):
      * Tab 1 (Tampilan & Engine): Target ELO (800-3500), Gaya Highlight (Filled/Outlined), Kustomisasi Warna Petak Asal (From Square: Cyan, Kuning, Orange, Putih) & Petak Tujuan (To Square: Hijau, Lime, Magenta, Biru), 6 Slider Transparansi Detail (Move Guide, Bidak, Grid, Panah, Highlight, Floating Eye), Toggle Kunci Posisi Papan, dan Ghost Controls.
      * Tab 2 (Anti-Cheat & Humanize): Toggle Humanize Move, Slider Level Humanis (0-10) dengan status dinamis, Blunder Guard, dan Natural Move Delay.
    - Integrasi Tombol Navigasi Menu Utama (`DashboardScreen.kt` & `MainActivity.kt`):
      * Tombol "⚙️ Pengaturan Lengkap & Tampilan" ditambahkan langsung di bawah tombol kalibrasi pada `DashboardScreen.kt` dan membuka `SettingsActivity.kt`.
82. **Sprint 82 (2026-08-31): Live Preview Card Scaling & Kustomisasi Ukuran Floating Eye + Tombol Kontrol Header**
    - Live Preview Card Reaktif (`SettingsActivity.kt`):
      * Preview interaktif 3 kolom di `SettingsActivity.kt` yang menampilkan visualisasi Floating Eye, Tombol Sembunyi (Mata), dan Tombol Menu (Titik Tiga) yang membesar/mengecil secara real-time saat slider digeser.
      * 3 Slider Ukuran Dinamis: Floating Eye (28 - 64 dp, default 44 dp), Tombol Sembunyi (24 - 52 dp, default 34 dp), Tombol Menu (24 - 52 dp, default 34 dp).
83. **Sprint 83 (2026-08-31): Integrasi Ikon Aplikasi Utama (Application Launcher Icon)**
    - Aset Ikon `icon.png`:
      * Disalin ke `app/src/main/res/drawable/icon.png` dan seluruh folder resolusi mipmap (`mipmap-mdpi`, `mipmap-hdpi`, `mipmap-xhdpi`, `mipmap-xxhdpi`, `mipmap-xxxhdpi`) untuk `ic_launcher.png` dan `ic_launcher_round.png`.
    - Adaptive Icon XML (`mipmap-anydpi-v26`):
      * Dibuat `ic_launcher.xml` dan `ic_launcher_round.xml` dengan background gelap solid dan layer foreground `drawable/icon`.
84. **Sprint 84 (2026-08-31): Perbaikan Touch Pass-Through, Pelepasan QuickAlignment View, & Reload Board Bounds**
    - Pelepasan Bersih QuickAlignmentOverlayView (`QuickAlignmentOverlayView.kt` & `OverlayManager.kt`):
      * Saat posisi dikunci, `holdHandler` dibatalkan, `visibility = GONE`, `isEnabled = false`, dan view langsung dilepas segera dari WindowManager menggunakan `removeViewImmediate(view)` untuk mencegah blokir sentuhan latar belakang.
    - Sinkronisasi & Reload Board Bounds (`InteractiveBoardOverlayView.kt` & `OverlayManager.kt`):
      * Ditambahkan method publik `reloadBoardBounds()` yang memuat ulang koordinat `board_left`, `board_top`, `board_right`, `board_bottom` dari `SharedPreferences` saat overlay dipulihkan (`restoreInteractiveBoard`).
    - Hierarki & Prioritas Touch Event (`InteractiveBoardOverlayView.kt`):
      * Di `dispatchTouchEvent`, sentuhan tombol mata [👁] dan tombol menu [⋮] diproses langsung pada prioritas pertama (`ACTION_UP`).
85. **Sprint 85 (2026-08-31): Popup Dialog QuickSideSelectorView (Pilih Sisi Cepat Pasca Kalibrasi)**
    - Dialog Komponen `QuickSideSelectorView` (`QuickSideSelectorView.kt`):
      * Desain card mengambang modern (slate dark `#EE1E222B`, stroke `#334155`, corner radius 12dp).
      * Pilihan: "⚪ Lawan Putih (Anda: Putih Atas, Mesin: Hitam Bawah)" vs "⚫ Lawan Hitam (Anda: Hitam Atas, Mesin: Putih Bawah)" dan tombol "✖️ Lewati".
    - Alur Otomatis Pasca Kalibrasi (`MiniBoardOverlayService.kt` & `OverlayManager.kt`):
86. **Sprint 86 (2026-08-31): Perbaikan Eksklusivitas Touch Menu Pengaturan & Fleksibilitas Gerakan Bidak**
    - Eksklusivitas Sentuhan Menu Pengaturan (`InteractiveBoardOverlayView.kt`):
      * Saat `isMenuOpen == true`, timer `autoHideJob` dibatalkan otomatis dan seluruh touch event di dalam `modalCardRect` dikonsumsi secara eksklusif oleh modal view tanpa pernah memicu hide overlay papan.
      * Mengetuk di luar dialog hanya menutup modal menu dan kembali ke papan.
    - Fleksibilitas Sentuh Petak & Gerakan Bidak (`InteractiveBoardOverlayView.kt`):
87. **Sprint 87 (2026-08-31): Jendela Mandiri SettingsOverlayView via WindowManager & Pemisahan Touch Handling**
    - Komponen Jendela Mandiri `SettingsOverlayView` (`SettingsOverlayView.kt`):
      * Dibuat sebagai `FrameLayout` independen yang ditempelkan langsung ke `WindowManager` (`showSettingsOverlay()`).
      * 2-Tab interaktif (Tampilan & Engine, Anti-Cheat) lengkap dengan switches, SeekBars Commit-on-Release, tombol [✖] dan [⬅️ Kembali ke Papan Catur].
      * Menghilangkan seluruh masalah event interception dan nesting touch dispatching.
    - Integrasi & Sinkronisasi (`OverlayManager.kt` & `MiniBoardOverlayService.kt`):
      * Disediakan method `showSettingsOverlay()` dan `hideSettingsOverlay()` yang menghapus jendela secara instan dengan `removeViewImmediate()`.
      * Memanggil `reloadVisualSettings()` pada `InteractiveBoardOverlayView` secara otomatis saat dialog ditutup.
    - Menu Header & Pilihan Menu (`InteractiveBoardOverlayView.kt`):
      * Pilihan menu "🎨 Kustomisasi Tampilan & Anti-Cheat" langsung memicu `onOpenSettingsRequested` untuk membuka jendela pengaturan mandiri.
88. **Sprint 88 (2026-08-31): Perbaikan Touch Pass-Through & Routing Interaksi Papan Catur**
    - Touch Pass-Through Presisi (`InteractiveBoardOverlayView.kt`):
      * `dispatchTouchEvent` mengembalikan `false` secara tegas jika koordinat sentuhan berada di luar area papan (`boardRect`) dan header, sehingga 100% sentuhan di luar papan tembus langsung ke layar game di bawahnya tanpa hambatan.
    - Penanganan Bidak Catur & Sentuhan Petak (`InteractiveBoardOverlayView.kt`):
      * Dibuat fungsi terpusat `handleChessBoardTouch(event)` yang menghitung baris & kolom petak catur (memperhitungkan mode flip papan).
      * Bidak catur langsung terpilih saat disentuh dan dipindahkan ke petak tujuan tanpa memandang status `isBoardLocked` (kunci papan hanya mengunci posisi drag kanvas papan, bukan bidak).
    - Tombol Menu Header (`InteractiveBoardOverlayView.kt`):
      * Tombol menu [⋮] pada header memicu `onOpenSettingsRequested` untuk membuka dialog pengaturan mandiri.
89. **Sprint 89 (2026-08-31): Alur Menu 2-Level (Main Control Menu vs Settings), Sinkronisasi Hide, & Preservasi State Papan**
    - Menu Kontrol Utama Level 1 (`MainControlMenuView.kt`):
      * Dibuat sebagai menu kontrol utama (Lawan Putih/Hitam, Deteksi Otomatis, Undo, Koreksi Posisi, Simpan Preset, Kalibrasi Baru, Flip Board, Reset Game, Pengaturan & Anti-Cheat ➔, Sembunyikan, Matikan).
      * Klik tombol [⋮] membuka `MainControlMenuView`. Tombol "⚙️ Pengaturan & Anti-Cheat ➔" membuka `SettingsOverlayView`.
      * Tombol "⬅️ Kembali ke Menu Kontrol" di `SettingsOverlayView` mengembalikan pengguna ke `MainControlMenuView` secara mulus.
    - Sinkronisasi Penutupan Seluruh Dialog saat Papan Di-Hide (`OverlayManager.kt` & `MiniBoardOverlayService.kt`):
      * Method `hideAllDialogs()` secara otomatis menutup `MainControlMenuView`, `SettingsOverlayView`, `QuickSideSelectorView`, dan `QuickAlignmentOverlayView` secara serempak saat papan overlay di-hide via tombol mata atau timer auto-hide.
90. **Sprint 90 (2026-08-31): Kunci Preservasi State Game saat Navigasi Menu & Restorasi Slider Transparansi Papan**
    - Kunci Preservasi Game State (`InteractiveBoardOverlayView.kt`):
      * Menghapus pemanggilan `setOpponentColor()` (yang sebelumnya memicu reset FEN/papan) dari `reloadBoardOrientation()` dan `reloadVisualSettings()`.
      * Disediakan `updateVisualPaintsOnly()` yang hanya memuat ulang paints, alpha, warna highlight, dan ukuran tombol tanpa menyentuh status `board`, `snapshotHistory`, `currentTurn`, atau FEN aktif.
      * Posisi papan catur 100% terjaga utuh saat pengguna membuka menu, mengatur slider, dan kembali ke permainan.
91. **Sprint 91 (2026-08-31): Restorasi Kontrol UI Auto-Hide & Auto-Show Lengkap serta Sinkronisasi Runtime Timer**
    - Komponen Pengaturan Otomasi Visibilitas (`SettingsOverlayView.kt` & `SettingsActivity.kt`):
      * Ditambahkan Section "⏱️ Otomasi Sembunyi / Tampil" di Tab 1 (Tampilan & Engine).
      * Toggle switch & Slider "⏳ Auto-Hide Papan" (1.0s - 30.0s, step 0.5s, key: `auto_hide_enabled` & `auto_hide_delay_sec`).
      * Toggle switch & Slider "✨ Auto-Show Papan" (1.0s - 30.0s, step 0.5s, key: `auto_show_enabled` & `auto_show_delay_sec`).
    - Sinkronisasi Timer Runtime Service (`MiniBoardOverlayService.kt` & `InteractiveBoardOverlayView.kt`):
      * Membaca status dan durasi Auto-Hide dan Auto-Show secara dinamis dari SharedPreferences.
      * Auto-Show hanya memunculkan papan kembali jika alasan penyembunyian berasal dari `OverlayHideReason.AUTO_HIDE`, menjaga mode manual hide tetap aman dan terisolasi.
92. **Sprint 92 (2026-08-31): Perbaikan Crash Type Cast SharedPreferences & Range Slider pada SettingsActivity dan SettingsOverlayView**
    - Perbaikan `ClassCastException` SharedPreferences (`SettingsOverlayView.kt` & `SettingsActivity.kt`):
      * Menambahkan helper extension `getSafeFloat` dan `getSafeInt` yang aman terhadap perbedaan tipe data (Int vs Float) pada key `auto_hide_delay_sec`, `auto_show_delay_sec`, `grid_alpha`, `max_elo_rating`, dan tombol skala.
      * Mencegah dialog `SettingsOverlayView` langsung tertutup sendiri ke papan overlay saat dibuka.
    - Pencegahan Crash Compose Slider pada `SettingsActivity.kt`:
      * Memastikan seluruh nilai slider di-`coerceIn` ke rentang legal (misal `1.0f..30.0f` untuk auto-hide/auto-show dan `0f..1f` untuk alpha).
      * Mengubah `android:exported="true"` pada `SettingsActivity` di `AndroidManifest.xml`.
93. **Sprint 93 (2026-08-31): Perbaikan Crash Fatal ClassCastException Auto-Show & Global PreferenceExtensions**
    - Modul Global `PreferenceExtensions.kt` (`com.chessbeater.util`):
      * Menyediakan extension function `getSafeFloat`, `getSafeInt`, `getSafeBoolean`, dan `getSafeString` yang kebal terhadap `ClassCastException` di seluruh komponen proyek.
    - Perbaikan Crash Service pada `MiniBoardOverlayService.kt` & `InteractiveBoardOverlayView.kt`:
      * Mengganti pembacaan SharedPreferences `auto_show_delay_sec`, `auto_hide_delay_sec`, dan visual bounds menjadi `getSafeFloat` dan `getSafeBoolean`.
      * Menghilangkan crash fatal saat auto-hide/auto-show terpicu setelah kalibrasi cepat (hold 2 detik) atau saat rekomendasi engine selesai.
    - Validasi & Deploy:
      * `./gradlew compileDebugKotlin` & `./gradlew installDebug`.

94. **Sprint 94 (2026-08-31): Kunci Pemilihan Bidak Berdasarkan Kepemilikan & Validasi Langkah Legal Catur**
    - Kunci Pemilihan Bidak Berdasarkan Kepemilikan (`InteractiveBoardOverlayView.kt`):
      * Di `handleBoardTap()` / sentuhan papan:
        - Cek giliran user: hanya izinkan sentuhan jika giliran aktif sesuai dengan giliran user (`currentTurn == opponentColor` saat bukan mode koreksi).
        - Kunci Kepemilikan Bidak: Bidak Putih HANYA dapat disentuh/dipilih oleh pemain Putih (`isPieceWhite == userIsWhite`), dan Bidak Hitam HANYA dapat disentuh/dipilih oleh pemain Hitam. Sentuhan pada bidak lawan/mesin ditolak seketika (`return`).
    - Validasi Langkah Legal Catur Sebelum Eksekusi:
      * Validasi bahwa langkah yang dicoba (`from` ke `to`) wajib valid sesuai aturan catur murni (`ChessLogic.isMoveLegal`), termasuk pencegahan langkah ilegal raja, pion mundur, benteng diagonal, dan pin raja.
      * Langkah ilegal ditolak tanpa mengubah FEN atau memutar giliran.
    - Guardrails Ketat:
      * Menjaga fungsi `dispatchTouchEvent()` agar touch pass-through tetap utuh.
      * Tidak memanggil `resetBoard()` saat membuka atau menutup dialog pengaturan.
    - Validasi & Deploy:
      * `./gradlew compileDebugKotlin` & `./gradlew installDebug`.



















