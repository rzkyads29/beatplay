# Panduan Promosi BeatPlay

Panduan langkah demi langkah dari "belum punya gambar" sampai "posting dan
balas komentar". Ikuti berurutan.

---

## 0. Sebelum mulai (30 menit, sekali saja)

1. **Pastikan aplikasinya benar-benar bisa diunduh.** Buka
   <https://beatplay.wuaze.com> di HP dan komputer, klik tombol download
   Windows / Linux / Android, dan cek filenya benar-benar terunduh.
2. **Siapkan akun** di platform yang mau kamu pakai: Instagram, TikTok,
   Facebook, X, YouTube. Pakai nama yang sama di semua platform, misalnya
   **BeatPlay**.
3. **Isi bio** di semua akun dengan satu kalimat + satu link:

   > Pemutar YouTube Music untuk Windows, Linux & Android 🎧
   > Lirik sinkron · offline · gratis tanpa akun
   > 👇 beatplay.wuaze.com

4. **Simpan link di bio**: pakai layanan gratis seperti Linktree, atau langsung
   arahkan ke `https://beatplay.wuaze.com`.
5. **Buat gambar promo**: jalankan `python render_all.py` di folder `promo/`
   (lihat [`README.md`](README.md)). Semua PNG akan muncul di `promo/out/`.

---

## 1. Tentukan tujuan dan sudut cerita

BeatPlay punya beberapa "alasan orang mau coba". Pilih satu sudut per post,
jangan semua sekaligus:

| Sudut | Cocok dipakai saat |
|---|---|
| **Lirik sinkron** | Sasarannya suka karaoke / lagu asing → hook paling kuat |
| **Gratis tanpa akun** | Orang malas bikin akun / malas bayar |
| **Automix tanpa jeda** | Audiens playlist & kerja/belajar |
| **Offline** | Audiens hemat kuota / sering di perjalanan |
| **Listen Together** | Audiens yang suka dengar bareng teman |
| **Premium murah** | Audiens yang terganggu iklan |

Sudut "lirik sinkron" dan "gratis tanpa akun" biasanya paling banyak menarik
perhatian orang baru.

---

## 2. Pilih aset yang tepat

Semua gambar ada di `promo/out/`. Ukuran yang diminta tiap platform:

| Platform | Format | Pakai file |
|---|---|---|
| Instagram Post | 1080×1350 | `beatplay-feed-2160x2700.png` |
| Instagram Post (persegi) | 1080×1080 | `beatplay-square-2160x2160.png` |
| Instagram Carousel | 1080×1350 ×3 | `beatplay-carousel-1/2/3-2160x2700.png` |
| Instagram Story / Reels | 1080×1920 | `beatplay-story-2160x3840.png` |
| TikTok | 1080×1920 | `beatplay-story-2160x3840.png` |
| Facebook Post | 1080×1350 | `beatplay-feed-2160x2700.png` |
| Facebook Story | 1080×1920 | `beatplay-story-2160x3840.png` |
| X / Twitter | 1600×900 | `beatplay-banner-3200x1800.png` |
| YouTube Thumbnail | 1280×720 | `beatplay-thumbnail-2560x1440.png` |
| WhatsApp Status | 1080×1920 | `beatplay-story-2160x3840.png` |

Gambar yang dirender 2× tetap diterima platform — platform akan memperkecilnya
sendiri, hasilnya tetap tajam.

Caption untuk tiap platform ada di [`CAPTION_PROMOSI.md`](CAPTION_PROMOSI.md).

---

## 3. Siapkan QR code & link

- Poster sudah memuat QR code menuju `beatplay.wuaze.com`.
- Kalau kamu mencetak poster atau spanduk, cetak juga QR besar-besar (minimal
  3×3 cm) dan tulis alamatnya di bawah QR untuk berjaga-jaga.
- Di bio, tulis alamatnya terang-terangan (jangan hanya link) supaya orang bisa
  mengetik manual.

---

## 4. Unggah dengan urutan yang benar

Urutan ini membuat jangkauan awal paling besar:

1. **Hari 1 — post perkenalan** (`feed`). Ini "pintu masuk" profilmu.
2. **Hari 1 (malam)** — bagikan ke Story/Status dengan stiker link.
3. **Hari 2–3 — video pendek** (`VIDEO_SCRIPT.md` skrip A). Video pendek paling
   cepat menyebar di TikTok/Reels/Shorts.
4. **Hari 4–5 — carousel & persegi** untuk yang belum pernah lihat.
5. **Ulangi** dengan sudut berbeda mengikuti kalender 14 hari di
   `CAPTION_PROMOSI.md`.

**Waktu posting yang biasanya bagus (WIB):** 12.00–13.00 (jam istirahat) dan
19.00–21.00 (malam). Coba beberapa jam lalu lihat mana yang paling ramai.

---

## 5. Balas komentar & DM (FAQ siap pakai)

Kecepatan membalas menentukan seberapa besar jangkauan post kamu. Simpan
jawaban ini:

**"Ini bajakan / ilegal nggak?"**
> BeatPlay itu pemutar musik — sama seperti pemutar lain, yang streaming
> memakai layanan YouTube. Ini proyek independen dan tidak berafiliasi dengan
> Google/YouTube. Kamu tetap memakai akun & layanan YouTube sendiri.

**"Gratis?"**
> Ya, gratis dan tanpa akun. Tinggal download dan langsung pakai. Ada Premium
> kalau mau tanpa iklan, tapi itu opsional.

**"Ada untuk iPhone?"**
> Belum. Saat ini tersedia untuk Windows, Linux, dan Android 8.0 ke atas.

**"Windows-nya kenapa muncul peringatan?"**
> Karena installernya belum ditandatangani secara digital, Windows kadang
> menampilkan peringatan SmartScreen. Pastikan kamu mengunduh hanya dari
> beatplay.wuaze.com, lalu pilih "More info" → "Run anyway".

**"Premium dapat apa?"**
> Tanpa iklan audio, dan statusnya aktif di semua perangkat dalam satu akun.
> 30 hari Rp20.000, 90 hari Rp55.000, 365 hari Rp199.000.

**"Bayarnya bagaimana?"**
> Lewat QRIS (scan dari aplikasi bank/e-wallet kamu). Setelah bayar, kirim bukti
> dari dalam aplikasi, lalu Premium aktif otomatis.

**"Data/email aku aman?"**
> Buat akun cukup email dan kata sandi. Kata sandi disimpan dalam bentuk
> terenkripsi (hash), bukan teks biasa. Login YouTube sepenuhnya opsional dan
> hanya untuk menyinkronkan library.

**"Ada iklan?"**
> Di paket gratis ada iklan audio sesekali supaya pengembangan tetap jalan.
> Premium menghilangkannya.

Jangan hapus komentar negatif yang wajar — balas dengan tenang dan sopan.
Itu justru menaikkan kredibilitas.

---

## 6. Ukur hasilnya

Yang perlu kamu lihat tiap minggu (bukan cuma "like"):

- **Klik tautan** di bio → berapa yang benar-benar buka situs.
- **Unduhan** → pantau lewat halaman rilis GitHub
  (`github.com/rzkyads29/beatplay/releases`) — jumlah unduhan tiap file.
- **Penyelesaian video** → kalau video >50% ditonton sampai habis, hook-nya bagus.
- **Simpan & bagikan** → tanda konten kamu dianggap berguna.

Yang jelek jangan diulang; yang bagus diperbanyak dengan sudut berbeda.

---

## 7. Tips menaikkan jangkauan (organik, tanpa iklan)

- **Taruh hook di 1 detik pertama**, sebelum orang sempat scroll.
- **Satu post = satu pesan.** Jangan menjejalkan semua fitur.
- **Pakai tangkapan layar asli** aplikasi — bukan mockup palsu.
- **Bikin seri**, misalnya "Fitur BeatPlay #1, #2, #3..." supaya orang menunggu.
- **Balas semua komentar** di 1 jam pertama.
- **Masuk ke komunitas** yang relevan (grup musik, forum pemutar musik, subreddit
  aplikasi Android) — tapi perkenalkan diri dulu, jangan langsung menempel link.
- **Minta pendapat**, bukan cuma menyuruh download: "menurut kalian fitur mana
  yang paling penting?" membuat orang ikut berkomentar.
- **Konsisten** lebih penting daripada sempurna. Empat post per minggu selama
  sebulan mengalahkan sepuluh post dalam dua hari.

---

## 8. Etika & hal yang harus dihindari

- Selalu tulis **"proyek independen, tidak berafiliasi dengan Google/YouTube"**
  di deskripsi/post yang menyinggung YouTube.
- Jangan memakai logo YouTube/Google, dan jangan mengaku aplikasi resmi.
- Jangan beli *follower*/like — merusak kredibilitas dan bisa kena sanksi platform.
- Jangan spam link di komentar orang lain.
- Jangan menjanjikan fitur yang belum ada. Sebutkan harga apa adanya.
- Kalau ada yang mengeluh bug, catat dan perbaiki — lalu umumkan perbaikannya.
  Itu konten promosi yang sangat kuat.

---

## 9. Checklist peluncuran

- [ ] Situs `beatplay.wuaze.com` hidup (uji di HP & komputer).
- [ ] Semua tombol download mengarah ke file yang benar (Windows/Linux/Android).
- [ ] Gambar promo sudah dirender (`promo/out/`) dan ukurannya sesuai.
- [ ] Bio + link sudah dipasang di semua akun.
- [ ] Caption & hashtag disiapkan (salin dari `CAPTION_PROMOSI.md`).
- [ ] Video pertama sudah direkam (skrip A) dan diberi judul + cover `story`.
- [ ] QR code dicoba di-scan dari HP (harus membuka situs).
- [ ] Disclaimer "tidak berafiliasi" ada di setiap deskripsi.
- [ ] Daftar FAQ sudah disimpan untuk membalas komentar.
- [ ] Jadwal 14 hari sudah dicatat di kalender.

Selamat mempromosikan! 🎧
