# Chess Beater — Physical Device Runtime Verification Checklist

Panduan pengujian runtime, permission flow, dan performa real-time aplikasi **Chess Beater** pada perangkat Android fisik (API 29+ / Android 10 s/d Android 15+).

---

## 📋 1. Persiapan & Instalasi APK Rilis

```bash
# 1. Hubungkan perangkat Android via USB (USB Debugging Enabled)
adb devices

# 2. Install APK Release
adb install -r app/build/outputs/apk/release/app-release.apk

# 3. Jalankan Activity Utama
adb shell am start -n com.chessbeater/.MainActivity
```

---

## 🛡️ 2. Verifikasi Permission Flow

| No | Izin / Fitur | Ekspektasi Perilaku | Status |
|:--:|---|---|:---:|
| 1 | **`SYSTEM_ALERT_WINDOW`** | Saat tombol *"Start Capture Service"* ditekan pertama kali, aplikasi membuka pengaturan sistem *"Display over other apps"*. Setelah diaktifkan, kembali ke aplikasi tanpa crash. | [ ] |
| 2 | **`MediaProjection` Screen Capture** | Sistem Android memunculkan dialog konfirmasi: *"Chess Beater will start capturing everything that's displayed on your screen"*. Memilih *"Start Now"* menginisialisasi VirtualDisplay. | [ ] |
| 3 | **Foreground Notification** | Notifikasi persisten *"Chess Beater Active"* muncul di notification shade dengan tipe `mediaProjection` (tidak di-kill oleh OS). | [ ] |
| 4 | **`POST_NOTIFICATIONS` & `VIBRATE`** | Izin getaran taktil dan notifikasi diizinkan secara otomatis/manual pada Android 13+. | [ ] |

---

## 🎯 3. Pengujian Floating Overlay & Interaksi Sentuh

| No | Komponen UI | Skenario Uji & Ekspektasi | Status |
|:--:|---|---|:---:|
| 1 | **Click-Through Canvas Arrow** | Panah visual muncul di atas petak target (contoh `e2 → e4`), namun pemain **tetap dapat menyentuh, menggeser, dan mengetuk bidak catur di aplikasi game** di bawahnya tanpa terhalang (`FLAG_NOT_TOUCHABLE`). | [ ] |
| 2 | **Draggable Floating HUD** | Widget HUD evaluasi dapat digeser (drag & drop) ke sudut layar mana saja secara mulus tanpa lag. | [ ] |
| 3 | **HUD Collapse / Expand** | Mengetuk (single tap) widget HUD mengubah tampilannya menjadi mode pill mini / ikon evaluasi, dan ketukan berikutnya mengembalikannya ke HUD penuh. | [ ] |
| 4 | **Adaptive Arrow Colors** | - **Hijau Terang (`#00E676`)**: Muncul saat posisi unggul (+1.00 atau lebih).<br>- **Biru Elektrik (`#2979FF`)**: Muncul saat posisi imbang/solid (-0.50 s/d +0.99).<br>- **Kuning (`#FFD600`)**: Muncul pada langkah alternatif taktis.<br>- **Merah (`#FF1744`)**: Muncul saat posisi kritis / blunder alert. | [ ] |

---

## ♟️ 4. Pengujian Lintas Platform Catur & Tema Papan

Uji aplikasi Chess Beater saat membuka game catur pada platform berikut:

- [ ] **Chess.com App:** Papan Hijau Standar, Papan Kayu (Wood theme), Dark Mode.
- [ ] **Lichess App:** Papan Cokelat Standar, Animasi Bidak Cepat.
- [ ] **Browser Chess (Chrome/Firefox):** Papan web responsive.
- [ ] **Orientasi Bermain:** Uji deteksi otomatis saat bermain sebagai **White (Bawah)** dan **Black (Bawah)**.

---

## ⚡ 5. Validasi Multi-Engine & Power Slider

1. **Stockfish 16.1 NNUE:**
   - [ ] Ubah slider kekuatan dari 0% (800 Elo / Beginner) ke 100% (3500+ Elo / GM).
   - [ ] Pastikan rekomendasi langkah menyesuaikan tingkat kedalaman (Depth 1 s/d 25 ply).
2. **Leela Chess Zero (Lc0 AlphaZero):**
   - [ ] Lakukan hot-switch ke engine Lc0.
   - [ ] Pastikan evaluasi posisional MCTS aktif.
3. **Deep Blue 1997 Classic:**
   - [ ] Lakukan hot-switch ke Retro Minimax.
   - [ ] Pastikan kalkulasi Minimax material murni berjalan tanpa neural network.

---

## 🔋 6. Verifikasi Performa, Baterai, & Watchdog

- [ ] **End-to-End Latency:** Total waktu dari momen bidak dilepas hingga panah visual muncul $\le \mathbf{350\text{ ms}}$.
- [ ] **Smart Battery Governor:** Frekuensi tangkapan layar turun ke **5–10 FPS** saat lawan berpikir (statis), dan naik instan ke **30 FPS** saat bidak bergerak.
- [ ] **RAM Footprint:** Penggunaan memori tidak melebihi **180MB RAM** (pantau via `adb shell dumpsys meminfo com.chessbeater`).
- [ ] **Engine Auto-Recovery:** Jika terjadi interupsi OS, watchdog memulihkan engine dalam waktu $<\mathbf{100\text{ ms}}$.
- [ ] **Haptic Feedback:** Terasa 1 getaran pendek saat best move ditemukan dan getaran ganda saat taktik penting/skak.
