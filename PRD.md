# Product Requirement Document (PRD)

## Project: Chess Beater (Android Real-Time Chess Assistance Engine)
- **Document Version:** 1.0.0
- **Target Platform:** Android (API Level 29+ / Android 10 to Android 15+)
- **Author:** Product & Engineering Architecture Team
- **Status:** Approved for Development
- **Last Updated:** August 2026

---

## 1. Executive Summary & Product Vision

### 1.1 Problem Statement
Pemain catur digital sering menghadapi kesulitan dalam menganalisis posisi kompleks secara *real-time* saat bermain di platform catur mobile atau saat menonton siaran pertandingan. Solusi analisis catur konvensional umumnya mengharuskan pengguna menyalin manual format PGN/FEN ke aplikasi pihak ketiga, yang memakan waktu dan mengganggu alur bermain.

### 1.2 Product Solution
**Chess Beater** adalah aplikasi Android berbasis *Computer Vision* dan *On-Device Engine Orchestration* yang berjalan di latar belakang (Background Foreground Service). Aplikasi ini menangkap tampilan layar (*screen capture*), mengekstraksi posisi papan catur menjadi format standard FEN (*Forsyth–Edwards Notation*), meneruskannya ke mesin catur kelas dunia (*Stockfish*, *Lc0*, *Classic Engine*), dan memproyeksikan rekomendasi langkah terbaik (*Best Move*) secara instan melalui *Floating Overlay Window*, *Transparent Arrow Canvas*, atau *Real-Time Toast Notifications*.

### 1.3 Key Value Proposition
- **Zero Manual Input:** Deteksi otomatis papan catur dan bidak dari layar tanpa input PGN/FEN manual.
- **100% On-Device Processing:** Semua pemrosesan citra dan kalkulasi engine berjalan lokal di smartphone tanpa latency jaringan atau ketergantungan server eksternal.
- **Dynamic Engine Power Adjustment:** Kekuatan mesin catur dapat diatur secara presisi dari level pemula (Elo 800) hingga level superhuman Grandmaster (Elo 3500+).
- **Multi-Engine Support:** Opsi pemilihan mesin berbasis evaluasi Minimax tradisional, NNUE modern, dan Neural Network (AlphaZero architecture via Leela Chess Zero).

---

## 2. System Architecture & Data Pipeline

```
+-----------------------------------------------------------------------------------+
|                                ANDROID SYSTEM UI                                  |
|  [Active Chess App: Chess.com / Lichess / Custom Viewer]                         |
+-----------------------------------------------------------------------------------+
                                         │
                                         ▼ (MediaProjection API / ImageReader)
+-----------------------------------------------------------------------------------+
|                          LAYER 1: SCREEN INGESTION SERVICE                        |
|  • Foreground MediaProjection Service (Partial WakeLock)                          |
|  • Dynamic Screen Resolution Downscaler (720p 15-30 FPS)                         |
|  • Frame Difference Change Detector (Save CPU/Battery when idle)                 |
+-----------------------------------------------------------------------------------+
                                         │
                                         ▼ (Raw Bitmap Frame)
+-----------------------------------------------------------------------------------+
|                     LAYER 2: VISION & BOARD PARSER (OPENCV + TFLITE)              |
|  • Board Localization: Perspective Transform, Warp to 8x8 Orthogonal Grid         |
|  • Square Slicing: 64 sub-bitmaps (A1 - H8)                                       |
|  • Piece Classifier (TFLite MobileNetV3-Tiny): Classify (K,Q,R,B,N,P,Empty) x 2   |
|  • State Validator & Turn Detector: Generate Valid FEN String                     |
+-----------------------------------------------------------------------------------+
                                         │
                                         ▼ (Standard FEN String: "rnbqkbnr/pppppppp/...")
+-----------------------------------------------------------------------------------+
|                   LAYER 3: ENGINE ORCHESTRATION (JNI / NDK / UCI)                 |
|  • Engine Selector (Stockfish 16+ NNUE / Lc0 ONNX / Retro Minimax)               |
|  • UCI Process Bridge: stdin / stdout Non-Blocking Pipe                           |
|  • Dynamic Power Regulator (Depth, Elo Limit, Skill Level, Node Count)            |
|  • Move Extraction: Parse `bestmove <from><to>` & Multi-PV Analysis Lines        |
+-----------------------------------------------------------------------------------+
                                         │
                                         ▼ (Best Move: e2e4, Eval: +0.65)
+-----------------------------------------------------------------------------------+
|                        LAYER 4: REAL-TIME OVERLAY & HUD                           |
|  • WindowManager Overlay (TYPE_APPLICATION_OVERLAY)                               |
|  • Canvas Dynamic Vector Arrows (Rendered over active game board)                |
|  • Floating HUD Widget (Evaluation Bar, Advantage Score, Best Move Text)         |
|  • Low-Profile Toast / Haptic Feedback Engine                                     |
+-----------------------------------------------------------------------------------+
```

---

## 3. Technology Stack Specification

| Domain | Technology / Library | Version / Target | Justification & Role |
|---|---|---|---|
| **Core OS & Language** | Kotlin + C++20 | Kotlin 2.0+ / Android 10+ (API 29–35) | Native Android ecosystem with high-performance C++ backend. |
| **Native Toolchain** | Android NDK + CMake | NDK r26+ / CMake 3.22+ | Compiling Stockfish, Lc0, and C++ image processing routines. |
| **Screen Perception** | Android `MediaProjectionManager` | Android Native API | Real-time screen capture without root privileges. |
| **Computer Vision** | OpenCV Android SDK | OpenCV 4.9.0+ | Board contour detection, color segmentation, Hough Line Transform. |
| **Machine Learning** | TensorFlow Lite / LiteRT | TFLite 2.16+ with NNAPI / GPU Delegate | Ultra-fast 64-square chess piece classification (<15ms per board). |
| **Chess Engines** | • Stockfish 16.1 (NNUE)<br>• Leela Chess Zero (Lc0)<br>• Retro-Minimax (Deep Blue style) | Native C++ Binaries compiled with NEON SIMD | Top-tier tactical calculations, neural network evaluations, and adjustable Elo. |
| **UI & Overlays** | Android `WindowManager` + Jetpack Compose Overlay | Compose 1.7+ | Lightweight floating HUDs, configuration menus, and vector overlay rendering. |
| **Threading & Concurrency** | Kotlin Coroutines + Channels / Flow | Coroutines 1.8+ | Dedicated dispatchers (`Dispatchers.Default` for CV/Engine, `Dispatchers.Main` for Overlay). |
| **Local Storage** | Jetpack DataStore + Room DB | Room 2.6+ | Persisting user preferences, custom engine profiles, and historical game telemetry. |

---

## 4. Engine Architecture & Power Calibration

### 4.1 Engine Options
1. **Stockfish 16.1 NNUE (Primary Default):**
   - Menggunakan kombinasi pencarian pohon Alpha-Beta dengan evaluasi jaringan saraf *Efficiently Updatable Neural Network* (NNUE).
   - Menghasilkan performa taktis tertinggi dengan konsumsi memori yang sangat efisien (~30MB RAM).
2. **Leela Chess Zero / Lc0 (AlphaZero Open-Source Variant):**
   - Menggunakan arsitektur Deep Convolutional / Transformer Residual Network yang dilatih dengan *Reinforcement Learning*.
   - Menyediakan gaya bermain posisional menyerupai AlphaZero. Dijalankan via TFLite/ONNX Runtime dengan akselerasi GPU OpenCL / NNAPI.
3. **Retro Engine (Deep Blue Classic Emulation):**
   - Mesin pencarian Minimax klasik murni dengan tabel evaluasi statis material dan kontrol posisi tanpa neural network, mereplikasi karakteristik kalkulasi mesin era 1990-an.

### 4.2 Power Adjustment Mapping (UCI Configuration)

Aplikasi menyediakan slider **"Engine Power"** (0% - 100% atau Elo 800 - 3500+). Nilai slider ini memetakan parameter UCI (*Universal Chess Interface*) secara dinamis:

```kotlin
data class EngineConfig(
    val engineType: EngineType,
    val powerPercentage: Int // 0 to 100
) {
    fun toUciCommands(): List<String> {
        val uciCommands = mutableListOf<String>()
        
        when (engineType) {
            EngineType.STOCKFISH -> {
                // Skala Elo: 800 (Power 0%) s/d 3500 (Power 100%)
                val targetElo = 800 + ((powerPercentage / 100.0) * 2700).toInt()
                val skillLevel = ((powerPercentage / 100.0) * 20).toInt() // 0 - 20
                val searchDepth = 1 + ((powerPercentage / 100.0) * 24).toInt() // Depth 1 - 25
                val moveTimeMs = 50 + ((powerPercentage / 100.0) * 1950).toInt() // 50ms - 2000ms

                uciCommands.add("setoption name UCI_LimitStrength value true")
                uciCommands.add("setoption name UCI_Elo value $targetElo")
                uciCommands.add("setoption name Skill Level value $skillLevel")
                uciCommands.add("setoption name Threads value 2")
                uciCommands.add("setoption name Hash value 32")
                
                // Tambahan: Error Injection untuk Elo rendah (< 1500)
                if (powerPercentage < 30) {
                    uciCommands.add("setoption name Skill Level Maximum Error value ${200 - (powerPercentage * 5)}")
                }
            }
            EngineType.LC0_ALPHAZERO -> {
                val playouts = (10 + (powerPercentage * 15)).toInt() // 10 to 1510 playouts
                uciCommands.add("setoption name NNCacheSize value 200000")
                uciCommands.add("setoption name MiniBatchSize value 16")
                uciCommands.add("setoption name Nodes value $playouts")
            }
            EngineType.DEEP_BLUE_CLASSIC -> {
                val depth = 1 + ((powerPercentage / 100.0) * 12).toInt() // Depth 1 - 13
                uciCommands.add("setoption name ClassicDepth value $depth")
            }
        }
        return uciCommands
    }
}
```

---

## 5. Detailed Feature Specifications

### 5.1 Module 1: Board Perception & Auto-Detection
- **Auto Board Locator:** Mendeteksi batas grid 8x8 papan catur menggunakan OpenCV edge detection (`Canny`), `findContours`, dan `HoughLinesP`.
- **Perspective Warping:** Mengoreksi orientasi miring jika sudut tangkapan tidak presisi menggunakan `Imgproc.warpPerspective`.
- **Orientation Auto-Detection:** Mengidentifikasi apakah pengguna bermain sebagai Putih (White di bagian bawah) atau Hitam (Black di bagian bawah) dengan menganalisis susunan bidak awal atau warna label koordinat (a-h, 1-8).
- **Move Delta Trigger:** Tidak melakukan inference engine setiap frame; sistem hanya memicu engine saat terjadi perubahan citra pada minimal 2 kotak (kotak asal dan kotak tujuan).

### 5.2 Module 2: FEN Generation & Legal Move Validation
- **Classification Pipeline:** 64 sub-petak diumpankan ke model TFLite `chess_piece_classifier_v2.tflite` (Input: 32x32 RGB, Output: 13 kelas: `[K, Q, R, B, N, P, k, q, r, b, n, p, empty]`).
- **FEN Assembler:** Menyusun baris 8 ke 1 menjadi string FEN standar (contoh: `r1bqkb1r/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 2 4`).
- **Castling & En-Passant Heuristic:** Melacak riwayat langkah untuk memvalidasi hak rokade dan kemungkinan *en passant*.

### 5.3 Module 3: Suggestion & Visual Delivery System
Aplikasi mendukung 3 mode visualisasi rekomendasi:
1. **Interactive Vector Arrow (Primary Mode):**
   - Menggambar panah tembus pandang (*semi-transparent dynamic canvas*) tepat di atas aplikasi catur dari petak awal ke petak tujuan (misal: panah dari `e2` ke `e4`).
   - Warna panah mencerminkan kualitas langkah:
     - Hijau Terang (`#00E676`): Best Move (+1.00 atau lebih).
     - Biru Elektrik (`#2979FF`): Solid / Standard Equal Move.
     - Kuning (`#FFD600`): Alternative Taktis.
2. **Compact Floating HUD Widget:**
   - Menampilkan bar evaluasi vertikal (*Evaluation Bar*) di sisi layar.
   - Angka centipawn evaluation (contoh: `+1.85` atau `M3` untuk Mate in 3).
   - Teks langkah terbaik (contoh: `Nf3 -> e5`).
3. **Discrete Toast / Status Notification:**
   - Menampilkan Android Toast mengambang singkat berdurasi 1 detik saat langkah baru terdeteksi.
   - Pilihan getaran Haptic (*Tactile Morse Feedback*): 1 getaran pendek (langkah minor), 2 getaran (skak/taktik kritis).

---

## 6. User Interface & Screen Wireframe Layouts

### 6.1 Main Configuration Dashboard (App Activity)
```
+-------------------------------------------------------------+
| [=] Chess Beater v1.0.0                      [Settings] [?] |
+-------------------------------------------------------------+
|                                                             |
|   [ ENGINE SELECTION ]                                      |
|   (o) Stockfish 16.1 NNUE (Recommended)                     |
|   ( ) Leela Chess Zero (AlphaZero AI Engine)                |
|   ( ) Deep Blue 1997 (Classic Minimax)                      |
|                                                             |
|   [ ENGINE STRENGTH / ELO RATING ]                          |
|   [====================●-----------] 2150 ELO (Candidate M)|
|   Depth: 16 ply  |  Time Limit: 450ms  |  Threads: 2        |
|                                                             |
|   [ VISUAL OVERLAY MODE ]                                   |
|   [X] Draw Real-Time Canvas Arrow on Screen                 |
|   [X] Show Floating Evaluation Bar HUD                      |
|   [ ] Compact Toast Mode (Stealth)                          |
|   [X] Haptic Notification on Blunder Risk                   |
|                                                             |
|   [ STATUS: Engine Ready | Screen Service: INACTIVE ]        |
|                                                             |
|   +-----------------------------------------------------+   |
|   |             [ START CAPTURE SERVICE ]               |   |
|   +-----------------------------------------------------+   |
+-------------------------------------------------------------+
```

### 6.2 Active In-Game Overlay Display (Floating Canvas)
```
+-------------------------------------------------------------+
|  Active Screen (e.g. Chess.com Game)                        |
|                                                             |
|  [8] [ r ] [   ] [ b ] [ q ] [ k ] [ b ] [ n ] [ r ]        |
|  [7] [ p ] [ p ] [ p ] [   ] [   ] [ p ] [ p ] [ p ]        |
|  [6] [   ] [   ] [ n ] [   ] [   ] [   ] [   ] [   ]        |
|  [5] [   ] [   ] [   ] [ p ] [ p ] [   ] [   ] [   ]  +---+ |
|  [4] [   ] [   ] [ B ] [   ] [ P ] [   ] [   ] [   ]  |+2.1||
|  [3] [   ] [   ] [   ] [   ] [   ] [ N ] [   ] [   ]  |===||
|  [2] [ P ] [ P ] [ P ] [ P ] [   ] [ P ] [ P ] [ P ]  |   ||
|  [1] [ R ] [ N ] [ B ] [ Q ] [ K ] [   ] [   ] [ R ]  +---+ |
|       [a]   [b]   [c]   [d]   [e]   [f]   [g]   [h]         |
|                                                             |
|     =====> [ ARROW OVERLAY: d1 -> h5 (Qh5#) ]               |
|                                                             |
|  [HUD Pill: Best: Qh5# | Eval: +2.1 | Depth: 18 | 320ms]    |
+-------------------------------------------------------------+
```

---

## 7. Performance, Security & Non-Functional Requirements

### 7.1 Performance Metrics & Benchmarks
- **End-to-End Latency:** Maksimal **350ms** dari momen bidak lawan dilepas hingga panah visual muncul di layar.
  - Screen Frame Grab: ~33ms (30 FPS capture rate)
  - OpenCV Grid Warping & Square Slicing: ~25ms
  - TFLite Batch Piece Classification: ~18ms (via NNAPI)
  - Stockfish Move Calculation (at 2000 Elo depth): ~200ms
  - Overlay Canvas Invalidation & Paint: ~16ms
- **Memory Footprint:** Maksimal **180MB RAM** saat Stockfish NNUE aktif dengan hash table 32MB.
- **Battery Optimization:** Penggunaan daya rata-rata di bawah 8% per jam bermain dengan memanfaatkan mekanisme frame-difference skipping saat giliran lawan berpikir.

### 7.2 Permissions & Android Manifest Requirements
```xml
<!-- Foreground Service for Screen Capture -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />

<!-- Floating Window Overlay -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- Notifications & Haptics -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

### 7.3 Compliance, Ethical Use & Disclaimer
1. **Educational & Analytical Intent:** Aplikasi ini ditujukan untuk sarana analisis pembelajaran, evaluasi pertandingan catur, dan riset Computer Vision.
2. **Anti-Fairplay Notice:** Penggunaan aplikasi ini dalam pertandingan online berperingkat (*rated online games*) melanggar *Terms of Service* (ToS) platform seperti Chess.com dan Lichess. Pengguna bertanggung jawab penuh atas segala konsekuensi terhadap akun mereka jika digunakan di luar mode analisis/pembelajaran offline.
3. **Local Privacy:** Aplikasi **tidak mengirim rekaman layar atau data pribadi** ke server cloud. Seluruh pipeline AI berjalan 100% *on-device*.

---

## 8. Development Roadmap & Milestones

| Milestone | Target Waktu | Key Deliverables |
|---|---|---|
| **Phase 1: NDK & Engine Foundation** | Sprint 1 (Minggu 1-2) | Setup JNI bridge untuk Stockfish C++ & UCI IPC pipe, benchmarking engine power slider. |
| **Phase 2: Vision Pipeline & Model Training** | Sprint 2 (Minggu 3-4) | Integrasi OpenCV Android SDK, training model TFLite piece classifier (99.2% accuracy target), parser FEN generator. |
| **Phase 3: Screen Capture & Overlay HUD** | Sprint 3 (Minggu 5-6) | Implementasi `MediaProjectionService`, `WindowManager` transparent overlay canvas, dan dynamic vector arrow renderer. |
| **Phase 4: Multi-Engine & Calibration** | Sprint 4 (Minggu 7-8) | Integrasi Lc0 / Retro engine, fine-tuning parameter Elo, testing pada resolusi layar variatif (16:9, 19.5:9, 20:9, tablet). |
| **Phase 5: QA, Optimization & Release** | Sprint 5 (Minggu 9-10) | Reduksi latency < 300ms, optimasi baterai, perbaikan edge case (papan tema custom, bidak 3D), rilis v1.0.0 APK. |

---

## 9. Appendix: Core Architecture Interfaces (Kotlin Snippets)

### 9.1 Engine Bridge Interface
```kotlin
interface ChessEngineBridge {
    suspend fun initializeEngine(): Boolean
    suspend fun setStrength(config: EngineConfig)
    suspend fun evaluatePosition(fen: String): EngineResult
    suspend fun stopEvaluation()
    fun release()
}

data class EngineResult(
    val bestMove: String,        // e.g., "e2e4"
    val ponderMove: String?,     // e.g., "e7e5"
    val evaluationCentipawns: Int?, // +65 means +0.65 advantage
    val mateInMoves: Int?,       // null if not mate, positive if winning mate
    val depth: Int,
    val calculationTimeMs: Long
)
```

### 9.2 Board Recognition Pipeline Interface
```kotlin
interface BoardDetector {
    fun locateAndExtractBoard(frameBitmap: Bitmap): BoardLocalizationResult?
    fun parsePiecesToFen(boardBitmap: Bitmap, playerColor: PieceColor): String
}

data class BoardLocalizationResult(
    val warpedBoardBitmap: Bitmap,
    val boardBoundingRect: Rect,
    val squareGrid: Array<Array<Rect>> // 8x8 coordinates
)

enum class PieceColor {
    WHITE, BLACK
}
```