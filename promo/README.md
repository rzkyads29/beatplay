# Paket Promosi BeatPlay

Folder ini berisi semua yang kamu butuhkan untuk mempromosikan BeatPlay: gambar
siap unggah, caption, skrip video, dan panduan langkah demi langkah.

## Isi folder

| File | Ukuran desain | Cocok untuk |
|---|---|---|
| `feed.html` | 1080×1350 | Post Instagram/Facebook (potret), sampul TikTok |
| `square.html` | 1080×1080 | Post Instagram/Facebook (persegi) |
| `story.html` | 1080×1920 | IG Story, FB Story, WhatsApp Status, Reels/TikTok |
| `banner.html` | 1600×900 | Post X/Twitter, pratinjau link Facebook, header blog |
| `thumbnail.html` | 1280×720 | Thumbnail video YouTube, sampul video pendek |
| `carousel-1.html` | 1080×1350 | Carousel IG/FB — slide 1 (hook) |
| `carousel-2.html` | 1080×1350 | Carousel IG/FB — slide 2 (fitur) |
| `carousel-3.html` | 1080×1350 | Carousel IG/FB — slide 3 (premium + ajakan) |
| `poster.html` | 1080×1350 | Poster versi lama, masih bisa dipakai |
| `beatplay-promo.html` | 1920×1080 (16:9) | Animasi promo 40 detik — berjalan sendiri seperti video pendek |
| `beatplay-promo.src.html` | — | Versi sumber animasi 16:9 (gambar pakai path relatif, mudah diedit) |
| `beatplay-promo-vertical.html` | 1080×1920 (9:16) | Animasi sama, format tegak untuk TikTok/Reels/Shorts |
| `beatplay-promo-vertical.src.html` | — | Versi sumber animasi 9:16 |
| `prompt-animasi-beatplay.md` | — | Prompt siap pakai untuk membuat/mengubah animasi dengan AI |
| `render_video.mjs` | — | Render animasi jadi video mp4/gif + thumbnail (Node + CDP + ffmpeg) |
| `promo.css` | — | Sistem desain bersama (warna, tipografi, komponen) |
| `render_all.py` | — | Render semua template jadi PNG resolusi tinggi |
| `assets/` | — | Logo, tangkapan layar, font, QR code |
| `out/` | — | Hasil render PNG (yang kamu unggah ke media sosial) |
| `CAPTION_PROMOSI.md` | — | Caption siap salin-tempel per platform + kalender konten |
| `VIDEO_SCRIPT.md` | — | Skrip video pendek (TikTok / Reels / Shorts) |
| `PANDUAN_PROMOSI.md` | — | Panduan promosi lengkap: unggah, jadwal, etika, FAQ |

## Animasi web 40 detik (`beatplay-promo.html`, `-vertical`)

Satu file HTML mandiri (tanpa server, tanpa build) yang memutar animasi promo
tepat 40 detik seperti video pendek — 8 scene, teks, warna, dan transisi. Bergaya
**dark brutalist** sesuai situs BeatPlay (hitam `#050505`, putih, Space Grotesk +
JetBrains Mono). Tersedia **dua rasio**:

- `beatplay-promo.html` → **16:9** (1920×1080) untuk YouTube, pratinjau link, header blog.
- `beatplay-promo-vertical.html` → **9:16** (1080×1920) untuk TikTok, Reels, YouTube Shorts.
  Tata letak ditumpuk (kartu fitur 2 kolom, harga bertingkat, jendela + ponsel susun vertikal).

**Cara pakai**

- Buka file-nya di browser (klik dua kali). Animasi jalan otomatis, lalu menampilkan
  tombol **Putar ulang**.
- Kontrol: <kbd>Spasi</kbd> = jeda/lanjut, <kbd>R</kbd> = ulang.
- Pratinjau satu scene: tambahkan `?scene=N` (1–8), mis.
  `beatplay-promo-vertical.html?scene=6` → hanya scene platform.
- Mode mesin: `?render` mematikan autoplay dan menunggu `window.__render(t)`
  (dipakai skrip video); `?clean` menyembunyikan HUD (progress bar + nomor scene).
- Semua durasi/urutan scene ada di satu tempat: objek `const SCENES = [...]` di dalam
  file. Kanvas 16:9 / 9:16 otomatis pas di layar mana pun (letterbox).
- Menghormati `prefers-reduced-motion` (pengguna yang mematikan animasi tetap
  melihat semua scene tanpa gerak).

**Jadikan video (mp4) — otomatis**

Sudah tersedia skrip `render_video.mjs`: memakai Chrome DevTools Protocol untuk
memanggil `window.__render(t)` tiap frame (deterministik, jadi mulus) lalu
mengalirkannya ke ffmpeg. Butuh **Node** + **ffmpeg** terpasang.

```bat
cd promo
node render_video.mjs                    :: 16:9 + 9:16, 30fps, mp4
node render_video.mjs --only 9:16        :: hanya versi tegak
node render_video.mjs --clean            :: tanpa HUD
node render_video.mjs --thumbs --poster  :: + PNG tiap scene + gambar cover
node render_video.mjs --gif              :: + GIF (15fps, 720px)
node render_video.mjs --jpeg             :: pipe JPEG (lebih cepat, sedikit lebih lunak)
```

Hasil masuk ke `out/`, mis. `out/beatplay-promo-16x9.mp4` (1920×1080, 40s),
`out/beatplay-promo-9x16.mp4` (1080×1920, 40s), plus `-poster.png` dan
`-s1.png`…`-s8.png` (thumbnail per scene).

> Kalau tidak mau pakai skrip: rekam layar penuh selama 40 detik dengan
> OBS Studio / Xbox Game Bar (1920×1080 atau 1080×1920).

**Mengubah animasi**

- Cepat: buka `beatplay-promo.html` / `beatplay-promo-vertical.html` (gambar sudah
  tertanam/base64).
- Rapi: edit `beatplay-promo.src.html` / `beatplay-promo-vertical.src.html`
  (gambar pakai path relatif ke `../web/img/`), lalu tempel ulang base64 bila perlu.
  Prompt untuk AI ada di `prompt-animasi-beatplay.md`.

## Render ulang gambar

Gambar di `out/` dihasilkan dari file HTML. Kalau kamu mengubah teks/desain,
render ulang dengan Playwright:

```bat
pip install playwright
python -m playwright install chromium

cd promo
python render_all.py                 :: semua template
python render_all.py feed story      :: hanya beberapa
python render_all.py --scale 2       :: skala 2x (default, hasil paling tajam)
```

Hasilnya bernama `out/beatplay-<nama>-<lebar>x<tinggi>.png`, misalnya
`out/beatplay-feed-2160x2700.png` (desain 1080×1350 dirender 2×).

> Hasil render sudah cukup besar untuk media sosial, jadi tidak perlu dikompres lagi.

## Mengubah teks atau desain

- **Ubah kalimat/kata** → edit file `.html` yang sesuai langsung di dalamnya.
- **Ubah warna, font, atau style komponen** → edit `promo.css`.
- **QR code** menunjuk ke `https://beatplay.wuaze.com`. Kalau alamat situs
  berubah, buat ulang QR-nya:

```bat
python -c "import segno; q=segno.make('https://beatplay.wuaze.com'); q.save('assets/qr-beatplay.svg', scale=10, border=2, dark='#14151d', light='white'); q.save('assets/qr-beatplay.png', scale=12, border=2, dark='#14151d', light='white')"
```

- **Tangkapan layar** diambil dari `../docs/screenshots/` (dan `assets/player.webp`).
  Ganti isinya kalau ada tampilan aplikasi yang lebih baru.

## Aset sumber

- Logo: `assets/logo.png`
- Tangkapan layar aplikasi: `../docs/screenshots/{home,explore,search,queue,lyrics-fullscreen,artist-lyrics}.png`
- Font: `assets/PlusJakartaSans.woff2`
- QR menuju situs: `assets/qr-beatplay.svg`

## Alamat resmi

- Situs & unduhan: <https://beatplay.wuaze.com>
- Rilis (unduhan langsung): <https://github.com/rzkyads29/beatplay/releases>
- Harga Premium: 30 hari Rp20.000 · 90 hari Rp55.000 · 365 hari Rp199.000 (bayar QRIS)

Langkah promosi selengkapnya ada di [`PANDUAN_PROMOSI.md`](PANDUAN_PROMOSI.md).
