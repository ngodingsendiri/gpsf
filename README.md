# gpsf 📍

Aplikasi simulator GPS/Mock Location modern untuk Android yang dirancang dengan **Jetpack Compose** dan **Material Design 3**. Aplikasi ini memungkinkan Anda untuk mensimulasikan lokasi GPS perangkat Anda ke koordinat mana pun di seluruh dunia dengan antarmuka yang bersih, intuitif, dan responsif.

---

## 🚀 Fitur Utama

- **Antarmuka Jetpack Compose Modern**: Memanfaatkan komponen Material 3, lengkap dengan dukungan Mode Gelap (Dark Mode) dinamis.
- **Mocking Lokasi Presisi**: Menggunakan standar Android Mock Location API terbaru yang aman, efisien, dan stabil.
- **Peta Interaktif**: Didukung oleh OpenStreetMap (OSMDroid) yang cepat untuk mencari dan memilih koordinat dengan sekali ketuk.
- **Joystick Melayang (Floating Joystick)**: Kontrol posisi koordinat Anda secara real-time dari aplikasi lain dengan panel joystick overlay yang responsif.
- **Mode Pergerakan Fleksibel**:
  - **Jump Mode**: Berpindah tempat secara instan ke koordinat target.
  - **Flight Mode**: Bergerak secara mulus (linear interpolation) menuju lokasi baru dalam rentang waktu tertentu.
- **Sistem Bookmark / Favorit**: Simpan lokasi favorit Anda untuk digunakan kembali dengan cepat.

---

## 📥 Unduh APK Rilis Terbaru

Setiap ada pembaruan di repository ini, file APK akan dibangun secara otomatis menggunakan GitHub Actions. Anda dapat mengunduh versi rilis terbaru di sini:

👉 **[Unduh gpsf APK Terbaru (Halaman Rilis GitHub)](../../releases/latest)**

*(Catatan: Jika Anda baru pertama kali mengimpor repository ini, buat Git Tag seperti `v1.0.0` dan push ke GitHub untuk memicu rilis otomatis pertama).*

---

## 🛠️ Persyaratan Sistem

- **Android 8.0 (Oreo) / API Level 26 atau di atasnya**.
- Koneksi internet aktif untuk memuat peta.
- Izin yang diperlukan:
  - **Izin Lokasi** (`ACCESS_FINE_LOCATION`)
  - **Izin Notifikasi** (Android 13+)
  - **Izin Gambar di Atas Aplikasi Lain** (Overlay) untuk memfungsikan Joystick Melayang.

---

## 📖 Cara Menggunakan & Menginstal

1. **Unduh & Pasang**: Unduh file APK terbaru dari halaman Releases di atas, lalu instal di ponsel Anda.
2. **Aktifkan Opsi Pengembang**:
   - Buka **Pengaturan** > **Tentang Ponsel**.
   - Ketuk **Nomor Bentukan (Build Number)** sebanyak 7 kali hingga muncul pesan bahwa Opsi Pengembang telah aktif.
3. **Pilih Aplikasi Lokasi Palsu (Mock Location App)**:
   - Buka **Pengaturan** > **Opsi Pengembang**.
   - Cari opsi **Pilih aplikasi lokasi palsu (Select mock location app)**.
   - Pilih aplikasi **gpsf**.
4. **Mulai Simulasi**:
   - Buka aplikasi **gpsf**.
   - Cari atau pilih titik lokasi pada peta.
   - Ketuk tombol **Start**.
   - Izinkan notifikasi dan overlay saat diminta agar Joystick Melayang dapat muncul di atas aplikasi lain (seperti Google Maps, dll.).

---

## 🤖 Otomatisasi GitHub Actions (CI/CD)

Repository ini telah dikonfigurasi dengan alur kerja **GitHub Actions** (`.github/workflows/android.yml`) yang andal:
- **Build Otomatis**: Setiap push ke cabang `main` atau `master` akan memicu kompilasi otomatis untuk memastikan kode bebas dari error.
- **Pembuatan Rilis Otomatis**: Ketika Anda membuat tag baru dengan format `v*` (misal: `v1.0.0`, `v1.0.1`), GitHub Actions akan:
  1. Mengompilasi kode menjadi file APK rilis.
  2. Membuat draf rilis baru di halaman GitHub Releases secara otomatis.
  3. Mengunggah file APK siap pakai langsung ke rilis tersebut.

### Cara Memicu Rilis Baru:
Cukup jalankan perintah berikut di komputer Anda setelah melakukan commit baru:
```bash
git tag v1.0.0
git push origin v1.0.0
```

---

## 🏗️ Struktur Proyek

Aplikasi dikembangkan menggunakan arsitektur modern Android:
- **`MainActivity.kt`**: Logika UI berbasis Jetpack Compose dan integrasi OpenStreetMap.
- **`MockLocationService.kt`**: Foreground Service latar belakang yang mengontrol Mock Provider dan menyinkronkan koordinat GPS secara real-time.
- **Jetpack Navigation**: Penjelajahan antar layar yang aman dan terstruktur.

---

## 📝 Lisensi

Proyek ini dilisensikan di bawah **MIT License**.
