# Skrip Video Promosi BeatPlay

Empat skrip pendek untuk TikTok, Instagram Reels, dan YouTube Shorts. Semua
formatnya vertikal 1080×1920 — pakai `story.html` sebagai sampul/cover.

**Cara pakai:** rekam layar aplikasi BeatPlay dengan OBS Studio atau perekam
bawaan Windows/Android, lalu potong mengikuti kolom *Langkah* di bawah. Teks
di layar ditulis dengan font yang sama seperti poster (Plus Jakarta Sans).
Tambahkan musik latar bebas royalti.

> Selalu sisipkan disclaimer "Proyek independen, tidak berafiliasi dengan
> Google/YouTube" di deskripsi, bukan di dalam layar.

---

## Skrip A — "Liriknya sinkron" (30 detik, hook terkuat)

**Tujuan:** bikin orang berhenti scrolling saat melihat lirik bergerak.

| Detik | Gambar | Teks di layar | Suara / aksi |
|---|---|---|---|
| 0–2 | Layar penuh lirik sedang bergerak | **"Lirik yang nempel sama lagunya"** | Musik lagu asli (volume penuh) |
| 2–5 | Zoom keluar, muncul pemutar lengkap | "Ini BeatPlay 🎧" | Musik terus jalan |
| 5–12 | Tunjukkan terjemahan & romanisasi muncul/hilang | "Ada terjemahan + romanisasi" | Musik pelan |
| 12–18 | Pindah ke layar lain (Home → Explore) cepat | "Windows · Linux · Android" | Musik naik lagi |
| 18–24 | Mode fullscreen lirik | "Fullscreen, enak buat karaoke" | Musik |
| 24–27 | Kembali ke pemutar, tekan play | "Gratis, tanpa akun" | Musik |
| 27–30 | Sampul `story.html` + QR | **"Download: beatplay.wuaze.com"** | Musik turun, fade |

**Voice-over opsional (bahasa santai):**
> "Aku pindah ke pemutar ini gara-gara satu hal: liriknya sinkron persis sama
> lagunya. Bisa fullscreen, ada terjemahan juga. Gratis, tanpa akun. Namanya
> BeatPlay."

**Caption + hashtag:** lihat `CAPTION_PROMOSI.md` bagian TikTok.

---

## Skrip B — "Automix, tanpa jeda" (20 detik, buat yang suka playlist)

| Detik | Gambar | Teks di layar |
|---|---|---|
| 0–2 | Lagu hampir habis (progress bar mendekati akhir) | **"Dengerin playlist tanpa jeda?"** |
| 2–8 | Perpindahan lagu mulus (crossfade) | "Automix: lagu nyambung sendiri 🔀" |
| 8–14 | Antrean lagu (`queue` screenshot) | "Nggak ada hening, nggak ada hentakan" |
| 14–17 | Buka Pengaturan → aktifkan Automix | "Tinggal nyalain sekali" |
| 17–20 | Sampul `story.html` | **"beatplay.wuaze.com — gratis"** |

**Hook 1 detik pertama:** pilih lagu dengan intro yang langsung nendang.

---

## Skrip C — "Cara download" (45 detik, tutorial Shorts)

| Detik | Gambar | Teks di layar |
|---|---|---|
| 0–3 | Layar kosong + kursor | **"Cara pasang BeatPlay (gratis)"** |
| 3–10 | Ketik `beatplay.wuaze.com` di browser | "1. Buka situsnya" |
| 10–20 | Klik tombol download sesuai OS (Windows/Linux/Android) | "2. Pilih sesuai perangkatmu" |
| 20–30 | Buka installer / pasang APK (izinkan sumber tak dikenal) | "3. Pasang seperti biasa" |
| 30–40 | Aplikasi terbuka, langsung cari lagu | "4. Buka & langsung pakai — tanpa akun" |
| 40–45 | Sampul `story.html` | **"Selesai. Selamat mendengarkan 🎧"** |

**Catatan:** sebutkan bahwa installer Windows mungkin memunculkan peringatan
SmartScreen karena belum ditandatangani — arahkan pengguna mengunduh hanya dari
situs resmi.

---

## Skrip D — "5 fitur dalam 30 detik" (kompilasi cepat)

Format hitung cepat, satu fitur per 5–6 detik. Cocok untuk audiens baru.

| Detik | Fitur | Teks di layar |
|---|---|---|
| 0–1 | Intro | **"5 hal di BeatPlay yang mungkin kamu belum tahu"** |
| 1–7 | Lirik sinkron | "1. Lirik sinkron + terjemahan" |
| 7–13 | Offline | "2. Unduh lagu, tetap jalan tanpa internet" |
| 13–19 | Listen Together | "3. Dengar bareng teman, tersinkron" |
| 19–24 | Equalizer + statistik | "4. Atur suara & lihat kebiasaan mendengarmu" |
| 24–29 | Premium | "5. Bayar sekali, tanpa iklan. Mulai Rp20rb" |
| 29–30 | Sampul + CTA | **"Gratis di beatplay.wuaze.com"** |

---

## Tips produksi

**Rekam layar yang bersih**
- Windows: <kbd>Win</kbd>+<kbd>G</kbd> (Xbox Game Bar) atau OBS Studio.
- Android: perekam layar bawaan, arahkan ke 1080×1920 kalau bisa.
- Jangan tunjukkan notifikasi, nama folder pribadi, atau data pribadi di layar.

**Teks di layar**
- Maksimal 6 kata per baris, font tebal, warna putih + bayangan atau blok warna.
- Letakkan teks di area tengah; sisakan ±15% ruang atas dan bawah agar tidak
  tertutup tombol aplikasi.

**Suara**
- Gunakan musik yang benar-benar dimainkan di aplikasi (bukan audio dari video
  orang lain) supaya tidak kena keluhan hak cipta.
- Untuk voice-over, rekam di ruangan sepi dan bicara seperti ke teman.

**Panjang ideal**
- TikTok & Reels: 15–34 detik.
- Shorts: 30–60 detik.
- Selalu tutup dengan ajakan yang jelas: **"Download gratis di
  beatplay.wuaze.com."**

**Penutup (template 3 detik terakhir)**
Tampilkan `out/beatplay-story-2160x3840.png` dengan teks "Download gratis —
beatplay.wuaze.com" dan QR code di tengah.
