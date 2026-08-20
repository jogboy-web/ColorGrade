# 🎨 ColorGrade

Selamat datang di **ColorGrade** — aplikasi **Android** untuk *photo color grading* (pengaturan warna foto). Dengan ColorGrade, kamu bisa memperbaiki dan menyesuaikan tampilan warna foto langsung dari HP Android tanpa ribet.

## 📲 Download APK

APK bisa diunduh dari halaman **[Releases](https://github.com/jogboy-web/ColorGrade/releases/latest)** (tombol di bawah). Halaman download ini menampilkan versi & catatan rilis.

| 🏷️ File | 📦 Ukuran | 💾 Cara Pasang |
|---------|-----------|----------------|
| 📱 **ColorGrade.apk** | 5.7 MB | Download → buka file → klik **Install** |

[⬇️ **Download ColorGrade.apk (Release v1.0.0)**](https://github.com/jogboy-web/ColorGrade/releases/latest)

> ⚠️ **Catatan instalasi:** Jika HP meminta, aktifkan **"Install from unknown sources"** (Sumber tidak dikenal) di pengaturan keamanan HP, karena APK ini tidak dipasang melalui Play Store.

---

## ✨ Tentang Aplikasi Ini (APK)

**ColorGrade** adalah aplikasi pengeditan warna foto yang berjalan **sepenuhnya di perangkat Android** (offline, tanpa internet). File `ColorGrade.apk` berisi seluruh aplikasi — cukup install sekali, langsung bisa dipakai.

### 🎛️ Fitur Pengaturan Warna

| Fitur | Fungsi |
|-------|--------|
| ☀️ **Brightness** | Mengatur tingkat kecerahan foto |
| 🌗 **Contrast** | Mempertegas perbedaan terang-gelap |
| 🎨 **Saturation** | Mengatur kepekatan/jenuh warna |
| 🌡️ **Temperature** | Membuat foto lebih hangat (kuning) atau lebih dingin (biru) |
| 🔵 **Tint** | Menyesuaikan rona hijau-merah |

### ⚙️ Kemampuan Lain
- 📂 **Import foto** dari galeri HP
- 💾 **Ekspor/hasil edit** & simpan kembali ke galeri
- 🗂️ **Preset warna** siap pakai untuk hasil cepat

---

## 🛠️ Teknologi

- **Bahasa**: Kotlin
- **UI**: Material (View-based, `activity_main.xml` & `activity_editor.xml`)
- **Build**: Gradle + Android Gradle Plugin
- **Pengolahan warna**: engine dedicated untuk tiap parameter (brightness, contrast, saturation, temperature, tint)

---

## 🚀 Menjalankan / Build dari Source Code

Project ini adalah source code project Android (Gradle). Untuk membangun sendiri APK dari kode sumber:

```bash
# Pastikan JDK & Android SDK sudah terpasang
./gradlew assembleDebug
```

Hasil build: `app/build/outputs/apk/debug/app-debug.apk`

> APK siap pakai yang bisa langsung dipasang tersedia di halaman **[Releases](https://github.com/jogboy-web/ColorGrade/releases/latest)**.

---

## 🌐 Struktur Proyek

```
ColorGrade/
├── app/
│   └── src/main/java/com/colorgrade/app/
│       ├── MainActivity.kt         # Halaman utama
│       ├── editor/                 # Engine & tampilan editor warna
│       │   ├── EditorActivity.kt
│       │   ├── BrightnessEngine.kt
│       │   ├── ContrastEngine.kt
│       │   ├── SaturationEngine.kt
│       │   ├── TemperatureEngine.kt
│       │   └── TintEngine.kt
│       ├── export/PhotoSaver.kt    # Simpan/menyimpankan hasil
│       ├── image/                  # Manajemen & processing foto
│       └── preset/                 # Preset warna siap pakai
├── gradle/                         # Wrapper Gradle
└── ...

> ℹ️ File `ColorGrade.apk` siap pasang tidak disimpan di repo, melainkan di-hosting di halaman **[Releases](https://github.com/jogboy-web/ColorGrade/releases/latest)**.
```

---

## 📋 Tabel Perbandingan Pengaturan Warna

| Parameter | Rentang | Efek |
|-----------|---------|------|
| Brightness | Gelap → Terang | Mencerahkan/menggelapkan keseluruhan foto |
| Contrast | Rendah → Tinggi | Mempertegas / melembutkan perbedaan warna |
| Saturation | Monokrom → Sangat jenuh | Mengatur kepekatan warna |
| Temperature | Dingin → Hangat | Shifting warna biru ↔ kuning |
| Tint | Hijau ↔ Merah | Koreksi rona warna |

---

## 📄 Lisensi

Project & file APK di-repo oleh **jogboy-web**.
