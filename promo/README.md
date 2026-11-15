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
| `promo.css` | — | Sistem desain bersama (warna, tipografi, komponen) |
| `render_all.py` | — | Render semua template jadi PNG resolusi tinggi |
| `assets/` | — | Logo, tangkapan layar, font, QR code |
| `out/` | — | Hasil render PNG (yang kamu unggah ke media sosial) |
| `CAPTION_PROMOSI.md` | — | Caption siap salin-tempel per platform + kalender konten |
| `VIDEO_SCRIPT.md` | — | Skrip video pendek (TikTok / Reels / Shorts) |
| `PANDUAN_PROMOSI.md` | — | Panduan promosi lengkap: unggah, jadwal, etika, FAQ |

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
