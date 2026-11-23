# Prompt: Animasi Web "BeatPlay" (~40 detik)

Dokumen ini berisi **prompt siap-tempel**. Salin seluruh isi bagian
"PROMPT LENGKAP" ke AI pembuat kode (Claude/DeepSeek/GPT) atau ke
generator video berbasis HTML. Bagian "PROMPT SINGKAT" untuk tool dengan
konteks terbatas. Bagian "STORYBOARD" dipakai kalau kamu mau mengatur
bagian tertentu saja.

---

## PROMPT LENGKAP

```
Buatkan satu file HTML mandiri (tanpa build step, tanpa framework) bernama
beatplay-promo.html yang menampilkan animasi promo berdurasi TEPAT 40 detik,
berjalan otomatis seperti video pendek begitu file dibuka. Animasi mempromosikan
"BeatPlay" — pemutar YouTube Music independen untuk Windows, Linux, dan Android.

=== 1. FORMAT & TEKNIS ===
- Kanvas 16:9, target 1920x1080, di-scale agar pas di layar mana pun (letterbox,
  latar hitam). Tanpa UI browser: tidak ada scroll, kursor disembunyikan saat play.
- Autoplay saat load. Durasi total 40.0 detik, lalu tahan frame terakhir 2 detik,
  lalu fade ke hitam. Sediakan tombol kecil "Putar ulang" (replay) muncul setelah selesai.
- Semua animasi dikendalikan SATU master timeline berbasis performance.now() +
  requestAnimationFrame (bukan timer terpisah-pisah), supaya sinkron dan bisa
  di-pause. Sediakan juga mode `?scene=N` (lompat ke scene ke-N, freeze) untuk
  keperluan tangkap layar.
- Semua teks, warna, transisi di-hardcode di file. Tidak ada dependency eksternal
  selain Google Fonts (Space Grotesk + JetBrains Mono). Tidak ada audio; animasi
  harus terasa utuh tanpa suara.
- Elemen dekoratif wajib: hairline grid 64px, radial glow di atas, dan partikel
  putih melayang halus (canvas 2D) yang sangat pelan (opacity ~0.9, density rendah).
- Hormati `prefers-reduced-motion`: tampilkan versi statis (semua scene fade
  sederhana, tanpa gerak partikel).
- Semua animasi transisi memakai easing "snap" (cubic-bezier(.2,.8,.2,1)) atau
  step(), jangan easing lembut berkepanjangan — kesannya tegas/brutal, bukan lembut.

=== 2. BAHASA DESAIN (WAJIB DIPATUHI) ===
Gaya: dark brutalist / Swiss-tech. Kanvas hampir-hitam, garis rambut putih tipis,
label mikro monospace huruf kapital dengan letter-spacing besar, sudut tajam
(tanpa border-radius, kecuali tombol bulat 50%), dan SATU aksen: putih (inverted).
Kesan: developer-first, presisi, jujur — bukan neon, bukan gradien warna-warni.

Palet (pakai nilai persis ini):
  --bg:#050505      --panel:#0a0a0a     --panel2:#0e0e0f
  --ink:#f8fafc     --muted:#9ca3af     --faint:#6b7280
  --line:rgba(255,255,255,.10)  --line2:rgba(255,255,255,.18)  --line3:rgba(255,255,255,.30)
  --accent:#ffffff  --accent-ink:#050505
  --ok:#22c55e      --warn:#f59e0b
  --line:#9ca3af    (garis rambut abu-abu, bukan hitam)

Tipografi:
  Judul  = "Space Grotesk", weight 600–700, letter-spacing -0.02em, huruf besar.
  Mikro  = "JetBrains Mono", weight 400–600, ukuran 10–14px, UPPERCASE,
           letter-spacing .16em. Dipakai untuk label, nomor, versi, caption.
Aksen "out": baris kedua judul dibalik — putih penuh, teks hitam (highlight block).

Dekorasi pembuka tiap scene: label monospace bernomor ("01 / 07"), garis tipis
pemisah, dan panel gelap berbingkai 1px.

=== 3. ASET (jika tersedia di sebelah file; kalau tidak ada, gambar ulang dengan CSS) ===
- Logo: img/logo-128.png  (kotak putih berisi huruf B/E/A/T/P/L/A/Y, atau wordmark)
- Screenshot: img/shots/desktop-home.webp  &  img/shots/android-home.webp
- Wordmark ditulis sebagai BEAT (putih) + PLAY (putih di blok/garis), mono, rapat.
Kalau aset gambar tidak tersedia, buat placeholder geometris: kotak berbingkai
dengan ikon garis (stroke 1.7) bergaya sama seperti ikon fitur.

=== 4. STORYBOARD 40 DETIK (8 SCENE) ===
Gunakan pembagian waktu ini persis. Setiap scene masuk dengan "hard cut" atau
invert-flash (kilatan putih 2 frame), bukan crossfade lembut.

[0.0–4.0s] SCENE 1 — COLD OPEN / BRAND
  Latar hitam total. Hairline grid muncul (garis tumbuh dari 0 → penuh).
  Partikel mulai melayang. Label monospace terketik satu per satu (typewriter):
  "PEMUTAR YOUTUBE MUSIC  ·  DESKTOP + PONSEL". Lalu wordmark BEATPLAY
  terakit: tiap huruf naik dari bawah dengan step() bertingkat + bunyi visual
  (garis scan horizontal tipis melintas). Selesai dengan kilatan putih singkat.

[4.0–9.0s] SCENE 2 — HEADLINE
  Dua baris teks besar masuk dari bawah, panjang bergantian:
    Baris 1 (putih):  "Musik favoritmu."
    Baris 2 (blok putih, teks hitam — aksen "out"):  "Dengan caramu."
  Efek: mask-reveal (clip-path naik), stagger 120ms. Garis bawah tipis tumbuh
  dari kiri ke kanan. Label kecil di atas: "RILIS 1.5.21 / WINDOWS · LINUX · ANDROID".

[9.0–14.0s] SCENE 3 — FITUR 1: AUTOMIX
  Label "01 / 07". Judul: "Automix — baur tanpa jeda."
  Visual: DUA waveform monokrom (bar-bar tipis seperti equalizer) berdekatan;
  di detik ke-11 keduanya saling meluncur dan melebur jadi satu waveform mulus
  (transisi, bukan potongan). Caption: "Akhir lagu membaur ke awal lagu berikutnya."

[14.0–19.0s] SCENE 4 — FITUR 2: LIRIK SINKRON
  Label "02 / 07". Judul: "Lirik yang mengikuti tiap kata."
  Visual: satu baris lirik contoh, sorotan (highlight putih) menyapu kata per kata
  dari kiri ke kanan seperti karaoke, dengan kursor tipis berkedip di ujungnya.
  Kata yang sudah lewat tetap putih; yang belum redup (--faint).

[19.0–24.0s] SCENE 5 — 7 FITUR (MONTAGE CEPAT)
  Label "03 / 07". Tujuh kartu kecil (panel berbingkai) muncul berurutan, tiap
  ~0.55s, dengan nomor monospace dan nama:
    Automix · Lirik sinkron · Offline tetap jalan · Listen Together ·
    Equalizer & pitch · Tema dinamis · Volume ternormalisasi
  Tiap kartu: garis atas tumbuh + judul naik. Ini bagian paling cepat/berirama.
  Di akhir, satu angka besar menghitung cepat: "07 FITUR · 3 PLATFORM" (count-up
  dari 0, ukuran besar, mono).

[24.0–29.0s] SCENE 6 — TIGA PLATFORM
  Label "04 / 07". Headline: "Satu aplikasi. / Tiga platform."
  Tiga kolom: WINDOWS (beatplay.exe · v1.5.21), LINUX (deb · rpm), ANDROID (apk · 1.5.5).
  Tiap kolom: ikon garis + nama + ukuran berkas dalam mono. Muncul stagger kiri→kanan,
  masing-masing dengan kilatan invert saat masuk.

[29.0–34.0s] SCENE 7 — PREMIUM
  Label "05 / 07". Headline: "Tanpa iklan. / Tanpa jeda."
  Tiga kartu harga bergeser masuk: Rp20rb (1 bulan), Rp55rb (3 bulan · PALING POPULER,
  diberi bingkai putih tebal/aksen), Rp199rb (12 bulan). Caption mono:
  "PEMBAYARAN VIA QRIS · DIVERIFIKASI MANUAL". Tanda centang hijau (--ok) muncul
  di tiap fitur yang tidak ada iklan.

[34.0–40.0s] SCENE 8 — CTA PENUTUP
  Headline: "Tekan play. / Jadi dirimu."
  Tombol putih besar (teks hitam): "UNDUH GRATIS". Di bawahnya URL dalam mono:
  "beatplay.wuaze.com". Logo BeatPlay di tengah. Semua elemen naik lalu diam.
  Detik 38–40: fade mulai ke hitam, sisakan kursor teks yang berkedip di tengah
  (efek terminal). Frame akhir: hitam + kursor berkedip.

=== 5. TRANSISI & DETAIL RASA ===
- Satu progress bar tipis (2px) di tepi atas sebagai penanda waktu sepanjang 40s.
- Nomor scene selalu tampil kecil di pojok (mono, --faint): "01 / 07" ... dsb.
- Tiap perpindahan scene: hard cut + kilatan invert putih 2 frame (kecuali scene 2 & 8
  yang masuk dengan mask-reveal).
- Gerak kamera halus: seluruh komposisi bergeser 8–14px (parallax mikro), bukan zoom.
- Jangan pernah pakai: warna neon, gradien ungu/biru, sudut membulat besar, bayangan
  blur tebal, emoji, atau ikon berwarna. Semua monokrom + satu aksen hijau (--ok) saja.

=== 6. OUTPUT YANG DIMINTA ===
Balas HANYA dengan satu blok kode HTML lengkap (file tunggal, bisa langsung dibuka
di browser). Sertakan di dalam file: komentar pembatas tiap scene agar mudah diubah,
variabel palet di :root, dan satu objek konfigurasi `const SCENES = [...]` berisi
{start, end, id} supaya durasi tiap scene bisa diubah di satu tempat. Pastikan tidak
ada error di console, animasi mulus 60fps, dan berjalan 40 detik lalu berhenti.
```

---

## PROMPT SINGKAT (untuk tool konteks terbatas)

```
Buat satu file HTML mandiri: animasi promo 40 detik, autoplay, gaya dark brutalist
(hitam #050505, teks #f8fafc, aksen putih, hijau #22c55e, garis rambut putih tipis,
tanpa border-radius, font Space Grotesk + JetBrains Mono, label mikro UPPERCASE
letter-spacing besar). Kanvas 16:9 1920x1080, partikel putih halus + hairline grid.
8 scene berurutan, total tepat 40s, transisi hard-cut + kilatan putih 2 frame:
1) COLD OPEN (0-4s): wordmark BEATPLAY terakit, label "PEMUTAR YOUTUBE MUSIC · DESKTOP + PONSEL".
2) HEADLINE (4-9s): "Musik favoritmu." / "Dengan caramu." (baris 2 blok putih teks hitam).
3) AUTOMIX (9-14s): dua waveform melebur jadi satu, "baur tanpa jeda".
4) LIRIK (14-19s): sorotan putih menyapu kata per kata seperti karaoke.
5) 7 FITUR (19-24s): Automix, Lirik sinkron, Offline, Listen Together, Equalizer, Tema dinamis, Volume ternormalisasi — kartu muncul cepat; count-up "07 FITUR · 3 PLATFORM".
6) PLATFORM (24-29s): Windows v1.5.21 / Linux deb·rpm / Android 1.5.5, tiga kolom stagger.
7) PREMIUM (29-34s): Rp20rb / Rp55rb / Rp199rb, "Tanpa iklan. Tanpa jeda.", via QRIS.
8) CTA (34-40s): "Tekan play. / Jadi dirimu.", tombol putih "UNDUH GRATIS", url beatplay.wuaze.com, fade ke hitam + kursor berkedip.
Pakai master timeline performance.now()+rAF dan objek SCENES=[{start,end,id}] agar durasi mudah diubah. Hormati prefers-reduced-motion. Balas hanya satu blok HTML lengkap, tanpa error console.
```

---

## STORYBOARD (tabel ringkas untuk referensi)

| # | Waktu | Scene | Teks utama | Visual kunci | Warna |
|---|-------|-------|-----------|--------------|-------|
| 1 | 0–4s | Cold open | "Pemutar YouTube Music · Desktop + Ponsel" | Wordmark terakit, grid, partikel | hitam/putih |
| 2 | 4–9s | Headline | "Musik favoritmu." / "Dengan caramu." | Mask-reveal, baris 2 blok putih | putih + blok |
| 3 | 9–14s | Automix | "Baur tanpa jeda." | 2 waveform melebur | putih |
| 4 | 14–19s | Lirik | "Lirik mengikuti tiap kata." | Sorotan karaoke kata-per-kata | putih/redup |
| 5 | 19–24s | 7 fitur | "07 fitur · 3 platform" | 7 kartu cepat + count-up | panel + putih |
| 6 | 24–29s | Platform | "Satu aplikasi. Tiga platform." | 3 kolom Win/Linux/Android | putih |
| 7 | 29–34s | Premium | "Tanpa iklan. Tanpa jeda." | 3 kartu harga, QRIS | putih + hijau |
| 8 | 34–40s | CTA | "Tekan play. Jadi dirimu." | Tombol UNDUH GRATIS + URL | putih→hitam |

---

## Catatan pemakaian

- **Kalau mau jadi video mp4:** buka `beatplay-promo.html` di layar penuh 1920x1080,
  rekam 40 detik (OBS / Xbox Game Bar), atau render headless (Puppeteer screenshot
  tiap frame → `ffmpeg -framerate 30`). Karena animasi deterministik, hasilnya mulus.
- **Ganti durasi/urutan:** cukup ubah angka `start`/`end` di objek `SCENES`.
- **Ganti isi teks:** semua copy promo diambil dari situs BeatPlay asli (headline,
  7 fitur, versi v1.5.21 & Android 1.5.5, harga Premium) supaya konsisten.
- **Aksesibilitas:** karena situs mendukung `prefers-reduced-motion`, versi animasi
  juga harus menyediakan fallback statis.
