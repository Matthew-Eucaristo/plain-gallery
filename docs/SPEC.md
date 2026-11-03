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

Bottom navigation (4 tab). Pindah tab dengan **geser kiri/kanan** di mana saja, atau ketuk bar (tab sebelah bergeser, tab jauh langsung muncul). Back dari tab lain kembali ke Foto.

1. **Foto**: *kenangan*. Hasil kamera + foto lain yang bukan kiriman, ditambah kiriman yang sudah kamu **Simpan**.
2. **Kiriman**: *inbox*. WhatsApp, screenshot, download, Telegram. Diurutkan berdasarkan kapan diterima.
3. **Album**: folder (bucket) + Favorit, Video, Sampah.
4. **Cari**

Pengaturan dibuka dari ikon di top bar.

## 5. Layar & perilaku

### 5.1 Grid (dipakai Foto, Kiriman, Album, Cari)
- Header per **hari** ("Hari ini", "Kemarin", "Sen, 28 Okt"), per **bulan**, atau per **tahun** sesuai tingkat zoom.
- **Pinch** untuk mengubah kepadatan: 3 → 4 → 6 → 10 kolom (hari → hari → bulan → tahun). Grid ikut membesar/mengecil mengikuti jari, lalu berganti level saat ukuran sel hampir sama dengan level berikutnya; foto di bawah jari tetap di tempat. Layout semua level disiapkan di latar belakang agar pergantian terjadi dalam satu frame.
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
- Aksi bawah: **Bagikan · Edit · Favorit · Info · Hapus**. Edit membuka editor bawaan (5.14); setelah disimpan, viewer langsung menampilkan salinannya.
- Chip sumber: "WhatsApp · Diterima", "Screenshot", "Kamera".
- Info: tanggal, nama file, ukuran, resolusi, folder, sumber, lokasi (jika ada) → "Buka di peta" (intent ke app peta).
- Video: play/pause, seek.

### 5.4 Bagikan (sheet sendiri, satu layar)
- Baris target besar: **WhatsApp**, **WA Business**, **Telegram** (jika terpasang), lalu app yang terakhir dipakai, **Lainnya** (sharesheet sistem).
- Preset (diingat), aturannya ada di `SharePlanner` dan diuji unit test:
  - **Disarankan** (default): yang bisa dibuka semua app dikirim apa adanya (JPEG, PNG, GIF, MP4 H.264/AAC). Sisanya dikonversi: HEIC/HEIF/AVIF/WebP/RAW(DNG)/BMP → JPEG 92; HEVC/VP8/VP9/AV1/MPEG-4, MKV/WebM/MOV/AVI → MP4 H.264/AAC (maks 1080p).
  - **Asli**: file apa adanya.
  - **Hemat**: foto JPEG 1600 px kualitas 82 (metadata/lokasi terbuang), video H.264 720p 2 Mbps.
  - **Atur**: format foto (Sama/JPG/PNG/WEBP), ukuran foto (Penuh/2048/1600/1080 px), video (Asli/1080p/720p/480p).
- GIF tidak pernah di-encode ulang (animasinya bisa hilang). Tidak pernah upscale.
- Estimasi ukuran dan jumlah file yang dikonversi ditampilkan; progres + Batal saat konversi.
- Konversi foto: `ImageDecoder` (format apa pun yang didukung platform). Video: Media3 Transformer. Jika konversi satu file gagal, file asli yang dikirim.
- File sementara dibersihkan otomatis (1 jam).

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

- Baris **Orang** di bawah pintasan (jika pengelompokan wajah aktif): wajah bulat + nama, "Lihat semua". Selama wajah masih dikenali: progres "Mengenali wajah… 42%".

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
  - Nama orang: kata yang cocok dengan awal kata di nama seseorang ("budi", "bud") juga menemukan foto orang itu.
  - Sisa kata → dicocokkan ke nama file, nama album, path.
- Saran saat kosong juga menampilkan orang yang sudah diberi nama.

### 5.10 Viewer default (buka dengan…)
- `ViewerActivity` menangani `VIEW`, `android.provider.action.REVIEW`, dan `com.android.camera.action.REVIEW` untuk `image/*` dan `video/*` (content:// dan file://).
- URI MediaStore yang ada di index dibuka di dalam albumnya (bisa swipe). URI dari app lain (file manager, chat) dibuka sebagai satu item dengan tombol Bagikan.
- Berjalan di task pemanggil; tombol back kembali ke app asal. Latar hitam sejak frame pertama.

### 5.11 Format
- Foto: semua yang didekode `ImageDecoder` Android: JPEG, PNG, WebP/GIF (animasi), HEIC/HEIF, AVIF (Android 12+), DNG (RAW), BMP, ICO. Foto Ultra HDR (gain map) ditampilkan dalam mode HDR di Android 14+.
- Video: kontainer MP4, MOV, 3GP, MKV, WebM, AVI, TS. Codec H.264, HEVC, VP8, VP9, AV1, MPEG-4 (tergantung decoder HP). Jika decoder hardware gagal, otomatis pindah ke software decoder.
- Tema: mengikuti light/dark sistem dan warna wallpaper (Material You), berubah langsung tanpa restart.

### 5.12 Pengaturan
- **Hapus tanpa konfirmasi** (izin `MANAGE_MEDIA`, Android 12+).
- **Buka foto dengan Gallery**: petunjuk + tombol ke pengaturan app default.
- Batas umur kiriman "lama": 30 / 60 / 90 hari.
- **Kenangan hari ini** (aktif): kartu di atas Foto.
- **Kelompokkan wajah** (aktif): progres "1.234 dari 8.000 foto". Mematikan = konfirmasi lalu semua data wajah dan nama dihapus.
- Tentang: versi, "Tanpa internet. Tanpa iklan. Open source."

### 5.13 Izin
- Satu layar sambutan → `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `ACCESS_MEDIA_LOCATION` (Android 13+), atau `READ_EXTERNAL_STORAGE` (Android 11–12).
- Akses sebagian (Android 14+): banner "Akses terbatas · Izinkan semua".

### 5.14 Editor foto
- Tiga alat di bar bawah: **Potong** (putar kiri, balik, rasio Bebas/Asli/Persegi/4:3/3:4/16:9/9:16, reset), **Atur** (filter Asli/Cerah/Hangat/Sejuk/Hitam putih/Pudar + slider Kecerahan/Kontras/Saturasi/Kehangatan; tahan foto untuk membandingkan), **Coret** (pena/stabilo, 3 ukuran, 6 warna).
- Urungkan untuk semua langkah. Tutup dengan perubahan → konfirmasi buang.
- **Simpan salinan**: file baru di folder yang sama (DCIM/Pictures; sumber lain seperti WhatsApp → `Pictures/Edited/`), dengan EXIF tanggal, kamera dan lokasi; orientasi dinormalkan. Asli tidak pernah diubah.
- Juga muncul sebagai pilihan "Edit" untuk app lain (intent `ACTION_EDIT` gambar).
- Selalu gelap, seperti viewer.

### 5.15 Kenangan hari ini
- Baris kartu di atas grid Foto, satu kartu per tahun sebelumnya: "1 tahun lalu · 3 Nov 2025". Hanya dari Foto (kamera + yang disimpan), bukan kiriman atau Sampah.
- Tanggal sama persis (29 Feb → 28 Feb di tahun biasa). Jika tahun itu tidak punya foto di tanggal tsb., pakai ±3 hari ("Minggu ini di 2024") asalkan ada ≥ 3 foto.
- Sampul: favorit > ada orang yang dikenal > foto (bukan video) > yang di tengah hari itu.
- Ketuk → viewer, urut dari yang terlama (seperti cerita). Tanggal dicek ulang tiap app kembali dibuka.
- Bisa dimatikan di Pengaturan (ada yang tidak ingin diingatkan).

### 5.16 Orang (pengelompokan wajah)
- Semua di HP: YuNet (deteksi + 5 titik wajah, MIT) → wajah diluruskan 112×112 → SFace (embedding 128-d, Apache-2.0) via LiteRT di CPU. Model ±10 MB dibundel; tanpa internet. Detail & pengukuran: `core/faces/MODELS.md`.
- Yang dianalisis: foto (bukan video), Foto dulu lalu kiriman, terbaru dulu; screenshot dilewati. Hanya saat app terbuka, satu foto per waktu di thread prioritas rendah; progres disimpan tiap beberapa foto dan dilanjutkan nanti. File yang berubah dianalisis ulang; yang terhapus ikut hilang.
- Wajah dilewati jika tidak yakin (skor < 0.7), kecil (< 40 px) atau menyamping.
- Pengelompokan bertahap: wajah masuk ke kelompok dengan rata-rata terdekat (cosine ≥ 0.48) atau membuat kelompok baru; kelompok yang ternyata sama digabung (≥ 0.55). Lebih baik terpecah daripada tercampur.
- Kelompok tanpa nama tampil jika ada di ≥ 3 foto; yang bernama selalu tampil, di depan.
- Layar orang: grid fotonya; ketuk judul/✎ untuk memberi nama. Nama yang sudah dipakai orang lain → kedua kelompok **digabung** (dialog memberitahu dulu). Pilih foto → **Bukan orang ini**. Menu: **Sembunyikan** (bisa dilihat lagi dari "Lihat yang disembunyikan").

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
core:faces           FaceEngine (LiteRT: YuNet + SFace), decoding & alignment murni
core:ui              tema Expressive, MediaGrid (pinch, scrubber, drag-select),
                     thumbnail Coil, share sheet, launcher write-request
feature:library      Foto, Kiriman, Album, detail Album, Sampah
feature:viewer       pager, zoom, video, info
feature:editor       editor foto (potong, atur, coret), simpan salinan
feature:search       layar Cari
feature:tidy         Rapikan
feature:settings     Pengaturan
baselineprofile      generator Baseline Profile
```

Alur data: `MediaStore → (sync diff) → Room → Flow<List<Media>> → Library (memori) → layar`. Penulisan (hapus/favorit) memakai `MediaStore.create*Request` → `IntentSender` diluncurkan dari Activity; UI disembunyikan optimistis dan dipulihkan jika ditolak.

## 8. Roadmap

Diurutkan menurut nilai dibanding usaha (riset Nov 2026). Semua offline.

**Berikutnya (kecil)**
- Bersihkan ruang: video besar, media WhatsApp lama, screenshot (dari index Room; pakai ulang Rapikan + Sampah/Urungkan).
- Putar Motion Photo (Media3 `JpegMotionPhotoExtractor`; HEIC butuh langkah tambahan).
- Predictive back yang mengecilkan foto kembali ke grid (Nav3).
- Foto → PDF (untuk formulir sekolah/kantor).
- Potong durasi video (Media3 Transformer).

**Menengah**
- Deteksi duplikat/mirip (perceptual hash thumbnail, disimpan di Room) dan tumpukan foto beruntun.
- OCR untuk screenshot/struk: ML Kit text recognition v2 (model Latin dibundel, ~4 MB/ABI), **flavor terpisah** karena closed-source dan membawa izin jaringan (dihapus manifest). Alternatif open source: PP-OCRv5 + ONNX Runtime (~25 MB).
- Lokasi offline ("Bali", "Bandung"): reverse-geocode GeoNames, tanpa peta online.

**Besar**
- Cari dengan konsep ("kucing", "pantai", "makanan"): model gambar CLIP berlisensi bebas (TinyCLIP/OpenAI CLIP, MIT) + daftar konsep EN/ID yang embedding teksnya dihitung saat build. Ukur kecepatan di HP budget dulu.
- Folder terkunci.
- Orang: wajah dari video, pilih sampul sendiri, saran "orang yang sama?" untuk kelompok yang mirip.
- Plugin WhatsApp "dari siapa" (eksperimental).

Tidak dikejar: Gemini Nano (hanya HP flagship), F-Droid.

## 9. Status (0.2.1, 3 Nov 2026)

Sudah dibuat dan diuji di emulator Android 17: semua layar di bagian 5, kecuali yang tercatat di bawah.

- Pinch grid diuji dengan gestur dua jari (UiAutomator) dan rekaman layar per frame di emulator.
- Editor diuji di emulator: potong, putar, filter, coret, simpan salinan (WhatsApp → `Pictures/Edited/`, kamera → `DCIM/Camera/` dengan EXIF tanggal/kamera/GPS), kembali ke viewer pada salinan.
- Cold start release dengan Baseline Profile: ~120–160 ms di emulator (HP budget akan lebih lambat).
- Video: retry otomatis ke software decoder jika hardware decoder gagal (player dan konversi).
- Diuji di emulator: 7 format video (AVI/MPEG-4, WebM/VP9, 3GP, MOV, MP4/AV1, MKV/HEVC, MP4/HEVC) dan foto AVIF/HEIC/BMP/GIF; konversi HEVC→H.264, VP9 WebM→H.264 480p, AVIF→JPEG, HEIC→WebP 1080 px.
- Uji stres di HP (Infinix, Android 16): monkey 25.000+ event dan spam tombol per fitur (back, hapus, favorit, edit, undo/simpan editor, tab, pilih-seret, Rapikan, hapus massal 380 item, restore setelah proses dibunuh) tanpa crash; puncak memori ~490 MB.
- Mulai sekarang pengujian dilakukan langsung di HP; emulator tidak dipakai lagi.
- Belum diuji: foto Ultra HDR asli, DNG asli, Android 11–12.

## 10. Verifikasi MVP

- Unit test: klasifikasi sumber, parser pencarian, pengelompokan grid, diff sync.
- Build release (R8) bersih, lint bersih.
- Manifest final tanpa `INTERNET`.
- Uji emulator Android 17: buka → grid, pinch, scrubber, buka viewer, swipe, zoom, hapus + urungkan, pilih banyak, bagikan, Kiriman, Rapikan, Cari, Sampah.
