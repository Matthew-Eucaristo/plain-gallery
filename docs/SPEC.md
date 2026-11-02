# Gallery (plain-gallery): Spesifikasi MVP

> Galeri Android yang *just works*: tanpa iklan, tanpa ajakan backup, tanpa internet. Fokus MVP: **dasar galeri yang sangat cepat dan mulus**. ML/AI datang belakangan, di atas fondasi ini.

## 1. Prinsip

1. **Cepat adalah fitur nomor satu.** Setiap interaksi terasa instan, tanpa spinner di jalur utama.
2. **Tanpa internet.** Manifest tidak punya izin `INTERNET` (dihapus paksa dengan `tools:node="remove"`). Janji privasi yang bisa dicek siapa saja.
3. **Native terbaru.** Kotlin + Jetpack Compose + Material 3 Expressive, API platform asli (MediaStore, trash sistem, favorit sistem).
4. **Ramah orang tua.** Label jelas, target sentuh besar, tidak ada gesture tersembunyi yang wajib diketahui.
5. **Aman.** Hapus = masuk Sampah sistem (30 hari). Tidak ada penghapusan permanen tanpa konfirmasi.
6. **Sederhana.** Tidak ada fitur yang tidak dipakai keluarga sehari-hari.

## 2. Target platform

| | |
|---|---|
| minSdk | 30 (Android 11): trash/favorit MediaStore, `GENERATION_MODIFIED` |
| targetSdk / compileSdk | 37 (Android 17) |
| UI | Compose (BOM 2026.09), Material 3 `1.5.0-beta` (Expressive) |
| Navigasi | Navigation 3 |
| Gambar | Coil 3 (thumbnail via `ContentResolver.loadThumbnail`), Telephoto (zoom + subsampling) |
| Video | Media3 ExoPlayer + `media3-ui-compose` |
| Index | Room 3 (driver framework SQLite, tanpa native lib) |

## 3. Anggaran performa (target di HP budget, ~20.000 item)

| Interaksi | Target |
|---|---|
| Cold start → grid terisi | < 500 ms (data dari index lokal, bukan scan MediaStore) |
| Buka foto dari grid | frame pertama < 50 ms (pakai thumbnail yang sudah di memori sebagai placeholder) |
| Scroll grid | tanpa jank, thumbnail dari cache sistem |
| Hapus 1 atau 500 foto | item hilang dari layar di frame berikutnya (optimistic), lalu sistem memproses |
| Kirim ke WhatsApp | 2 ketukan dari viewer: **Bagikan → WhatsApp** |
| Hasil pencarian | < 50 ms per ketukan huruf (filter di memori) |
| Sinkronisasi perubahan | inkremental (diff `_ID` + `GENERATION_MODIFIED`), di background |

Teknik: index Room sebagai cermin MediaStore, satu snapshot `Library` di memori yang dipakai semua layar, thumbnail dari cache sistem, Baseline Profile, R8 penuh, grid seragam (`key` + `contentType`), tanpa crossfade di grid.

## 4. Arsitektur informasi

Bottom navigation (4 tab):

1. **Foto**: *kenangan*. Hasil kamera + foto lain yang bukan kiriman, ditambah kiriman yang sudah kamu **Simpan**.
2. **Kiriman**: *inbox*. WhatsApp, screenshot, download, Telegram. Diurutkan berdasarkan kapan diterima.
3. **Album**: folder (bucket) + Favorit, Video, Sampah.
4. **Cari**

Pengaturan dibuka dari ikon di top bar.

## 5. Layar & perilaku

### 5.1 Grid (dipakai Foto, Kiriman, Album, Cari)
- Header per **hari** ("Hari ini", "Kemarin", "Sen, 28 Okt"), per **bulan**, atau per **tahun** sesuai tingkat zoom.
- **Pinch** untuk mengubah kepadatan: 3 → 4 → 6 → 10 kolom (hari → hari → bulan → tahun). Item di tengah tetap menjadi anchor.
- **Scrubber cepat** di tepi kanan, dengan label bulan/tahun saat diseret.
- **Ketuk** untuk membuka viewer. **Tekan lama** untuk mulai memilih; **seret** setelah tekan lama untuk memilih rentang (auto-scroll di tepi).
- Video: badge durasi.

### 5.2 Mode pilih
- Top bar: "12 dipilih", tombol tutup, pilih semua.
- Floating toolbar (Expressive): **Bagikan · Favorit · Hapus**.
- Hapus → Sampah + snackbar **Urungkan**.

### 5.3 Viewer
- Pager horizontal di koleksi yang sama dengan asalnya (urutan identik).
- Zoom: pinch, ketuk ganda; gambar besar memakai subsampling.
- **Geser ke bawah** untuk menutup. Predictive back.
- Ketuk untuk menyembunyikan/menampilkan kontrol (immersive).
- Aksi bawah: **Bagikan · Edit · Favorit · Info · Hapus**.
- Chip sumber: "WhatsApp · Diterima", "Screenshot", "Kamera".
- Info: tanggal, nama file, ukuran, resolusi, folder, sumber, lokasi (jika ada) → "Buka di peta" (intent ke app peta).
- Video: play/pause, seek.

### 5.4 Bagikan (sheet sendiri, satu layar)
- Baris target besar: **WhatsApp**, **WA Business**, **Telegram** (jika terpasang), lalu 2 app terakhir yang dipakai, **Lainnya** (sharesheet sistem).
- Kualitas (diingat):
  - **Asli**: file apa adanya.
  - **Kompatibel**: HEIC/AVIF/WebP/PNG → JPEG kualitas penuh; JPEG dikirim apa adanya.
  - **Hemat**: JPEG sisi terpanjang 1600 px, kualitas 82. Metadata dibuang.
- Estimasi ukuran ditampilkan. Video selalu asli (kompres video menyusul di fase 2).
- Konversi di background; file sementara dibersihkan otomatis.

### 5.5 Kiriman
- Chip filter: Semua · WhatsApp · Screenshot · Download · Telegram · Lainnya.
- Kartu **Rapikan**: "342 kiriman · 1,2 GB → Rapikan".
- Kartu saran: "143 kiriman lebih dari 60 hari · 820 MB → Pindahkan ke Sampah" (dengan konfirmasi).

### 5.6 Rapikan (swipe triage)
- Satu kartu besar per foto. **Geser kiri = Buang**, **kanan = Simpan** (pindah ke Foto). Tombol yang sama di bawah + **Lewati** + **Urungkan**.
- Urutan: terlama atau terbesar.
- Yang dibuang dikumpulkan lalu dikirim ke Sampah **sekaligus** saat selesai (satu dialog sistem, atau tanpa dialog bila izin "Kelola media" aktif).

### 5.7 Album
- Baris khusus: Favorit, Video, Screenshot, Sampah.
- Folder diurutkan dari yang terbaru; cover = item terbaru.
- Detail album: urutan Tanggal diambil / Tanggal ditambahkan / Ukuran / Nama.

### 5.8 Sampah
- Item yang ada di Sampah sistem, dengan sisa hari sebelum dihapus permanen.
- Pulihkan, Hapus permanen (konfirmasi), Kosongkan.

### 5.9 Cari
- Input langsung fokus. Hasil muncul tiap ketukan huruf.
- Chip saran saat kosong: sumber, tanggal cepat, tahun yang ada di library, album.
- Kueri yang dipahami ditampilkan sebagai chip ("Juli 2024 · WhatsApp · "nota"").
- Grammar (Indonesia + Inggris):
  - Jenis: `video`, `foto`/`photo`/`gambar`
  - Sumber: `wa`/`whatsapp`, `ss`/`screenshot`/`tangkapan`, `kamera`/`camera`, `download`/`unduhan`, `telegram`
  - Favorit: `favorit`/`favorite`/`suka`
  - Tanggal: `hari ini`, `kemarin`, `minggu ini`, `minggu lalu`, `bulan ini`, `bulan lalu`, `tahun ini`, `tahun lalu`, nama bulan (+ tahun), `17 agustus 2023`, tahun `2023`, `lebaran 2024`, `natal`, `tahun baru`
  - Sisa kata → dicocokkan ke nama file, nama album, path.

### 5.10 Pengaturan
- **Hapus tanpa konfirmasi** (izin `MANAGE_MEDIA`, Android 12+).
- Batas umur kiriman "lama": 30 / 60 / 90 hari.
- Tentang: versi, "Tanpa internet. Tanpa iklan. Open source."

### 5.11 Izin
- Satu layar sambutan → `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `ACCESS_MEDIA_LOCATION` (Android 13+), atau `READ_EXTERNAL_STORAGE` (Android 11–12).
- Akses sebagian (Android 14+): banner "Akses terbatas · Izinkan semua".

## 6. Klasifikasi sumber

Dari `RELATIVE_PATH`, `OWNER_PACKAGE_NAME`, dan nama file (lihat `SourceClassifier`):

| Sumber | Aturan |
|---|---|
| Screenshot | path berisi `screenshot` / `screenrecord` / `screen recording` |
| WhatsApp | path berisi `com.whatsapp` / `WhatsApp`, owner `com.whatsapp(.w4b)`, atau nama `*-WA0000.*` |
| Telegram | path berisi `Telegram`, owner `org.telegram.*` |
| Download | path diawali `Download/` |
| Kamera | path diawali `DCIM/` |
| Lainnya | selain itu (tetap masuk tab Foto) |

Kiriman = Screenshot, WhatsApp, Telegram, Download. "Terkirim" (WA) = path berisi `/Sent/`.

## 7. Arsitektur

```
app                  NavDisplay (Nav3), tab, izin, wiring DI manual
core:model           Media, Album, MediaSource, MediaCollection (Kotlin murni)
core:data            MediaStore source, index Room 3, sync inkremental,
                     MediaRepository (snapshot Library + optimistic hide),
                     aksi tulis (trash/favorit/hapus), share & konversi, pencarian, settings
core:ui              tema Expressive, MediaGrid (pinch, scrubber, drag-select),
                     thumbnail Coil, share sheet, launcher write-request
feature:library      Foto, Kiriman, Album, detail Album, Sampah
feature:viewer       pager, zoom, video, info
feature:search       layar Cari
feature:tidy         Rapikan
feature:settings     Pengaturan
baselineprofile      generator Baseline Profile
```

Alur data: `MediaStore → (sync diff) → Room → Flow<List<Media>> → Library (memori) → layar`. Penulisan (hapus/favorit) memakai `MediaStore.create*Request` → `IntentSender` diluncurkan dari Activity; UI disembunyikan optimistis dan dipulihkan jika ditolak.

## 8. Roadmap

**Fase 2**
- OCR on-device opsional (ML Kit text recognition, flavor terpisah agar build inti tetap bebas Google).
- Pencarian lokasi offline (reverse-geocode GeoNames).
- Crop/rotate bawaan.
- Kompres video (Media3 Transformer).
- Notifikasi saran pembersihan mingguan (WorkManager).
- Deteksi duplikat/mirip (perceptual hash).

**Fase 3**
- Plugin WhatsApp "dari siapa" (NotificationListener + korelasi waktu, opt-in, eksperimental).
- Pencarian semantik on-device (MobileCLIP/SigLIP).

## 9. Status MVP (2 Nov 2026)

Sudah dibuat dan diuji di emulator Android 17: semua layar di bagian 5, kecuali yang tercatat di bawah.

- Pinch-zoom grid belum diuji otomatis (adb tidak bisa multi-touch); perlu dicoba di HP asli.
- Cold start release dengan Baseline Profile: ~120–160 ms di emulator (HP budget akan lebih lambat).
- Video: retry otomatis ke software decoder jika hardware decoder gagal.

## 10. Verifikasi MVP

- Unit test: klasifikasi sumber, parser pencarian, pengelompokan grid, diff sync.
- Build release (R8) bersih, lint bersih.
- Manifest final tanpa `INTERNET`.
- Uji emulator Android 17: buka → grid, pinch, scrubber, buka viewer, swipe, zoom, hapus + urungkan, pilih banyak, bagikan, Kiriman, Rapikan, Cari, Sampah.
