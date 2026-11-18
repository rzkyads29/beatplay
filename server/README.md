# BeatPlay Premium server (QRIS manual + BuatQris/Tripay/Xendit + admin)

- Gratis: iklan audio tiap X lagu. Premium: tanpa iklan, berbasis Gmail + **expiry**.
- Mode QRIS manual lama tetap tersedia untuk kompatibilitas, tetapi saat provider
  otomatis seperti BuatQris aktif, informasi dan endpoint QRIS manual disembunyikan.
- Alternatif otomatis: **BuatQris**, **Tripay**, atau **Xendit**.
  BuatQris memakai API QRIS dan callback HMAC-SHA256 yang diverifikasi di server.
- Paket default di `plans.json`: 1 bln Rp20.000 / 3 bln Rp55.000 / 12 bln Rp199.000.

## Mode

- `mock` (default, tanpa kredensial): order dibuat lokal, admin klik **Simulasi bayar** di dashboard. Untuk tes flow end-to-end.
- BuatQris: mulai dari **Sandbox**; panduannya di bagian [Integrasi BuatQris](#integrasi-buatqris).
- `sandbox`: isi `tripay_config.json` dari dashboard Tripay (API & Integrasi > Simulator).
- `production`: isi dari Member > Merchant > Opsi > Edit + aktifkan channel QRIS2.

```bash
# Pengembangan lokal saja (token default tidak aman untuk server publik).
python3 premium_server.py --port 8080
# VPS: jalankan lewat systemd; ADMIN_TOKEN/PUBLIC_BASE dibaca dari EnvironmentFile.
```

## Deploy ke VPS (Ubuntu, mis. Azure)

Mode manual tidak membutuhkan callback provider; untuk server publik, tetap gunakan
HTTPS melalui reverse proxy dan jangan membuka port aplikasi 8080 ke internet.

```bash
# 1. Di VPS:
sudo apt update && sudo apt install -y python3 git
sudo useradd -r -m -d /opt/beatplay beatplay
sudo mkdir -p /opt/beatplay && sudo chown beatplay:beatplay /opt/beatplay

# 2. Dari PC (PowerShell), kirim hanya file aplikasi/template (bukan file rahasia):
scp server\premium_server.py server\plans.json server\premium.env.example server\beatplay-premium.service <user>@<ip-vps>:/tmp/server-new/
# Di VPS, salin file aplikasi ke /opt/beatplay/server. Pertahankan file runtime
# tripay_config.json yang sudah ada; jangan pernah menyalin versi lokal berisi rahasia.
# Taruh manual_qris.png bila memang memakai QRIS manual.

# 3. Env + service:
sudo cp server/premium.env.example /etc/beatplay-premium.env
sudo nano /etc/beatplay-premium.env   # isi ADMIN_TOKEN kuat
sudo chmod 600 /etc/beatplay-premium.env
sudo cp server/beatplay-premium.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now beatplay-premium
systemctl status beatplay-premium --no-pager

# 4. Jangan buka port 8080 ke internet. Batasi akses publik ke SSH 22 dan
#    Caddy HTTP/HTTPS 80/443. Caddy meneruskan request ke 127.0.0.1:8080.
#    Jangan aktifkan UFW tanpa aturan SSH yang benar; firewall Azure/NSG cukup.

# 5. Tes lokal di VPS: curl http://127.0.0.1:8080/api/plans
#    Tes publik: https://premium.domain-anda/api/plans
#    Di app BeatPlay: Server Premium = https://premium.domain-anda
```

HTTPS diperlukan untuk callback provider: arahkan domain ke IP publik VPS, lalu
pasang reverse proxy Caddy (contoh: `pay.domain-anda { reverse_proxy 127.0.0.1:8080 }`).
Set `PUBLIC_BASE=https://pay.domain-anda` di `/etc/beatplay-premium.env`.

## Integrasi BuatQris

Backend memakai `POST https://api.buatqris.site` dengan form-urlencoded.
Webhook adalah jalur utama. Fallback server juga memindai order BuatQris yang
masih `UNPAID` dan memanggil `api_check_status`, maksimal sekali per transaksi
setiap 20 detik; permintaan status dari aplikasi memakai batas yang sama. Waktu
cek disimpan bersama order sebelum request API dikirim, termasuk saat timeout
atau restart. Premium hanya diaktifkan setelah signature webhook valid atau
status API mengonfirmasi `success`, ID transaksi cocok, dan `amount` serta
`total_amount` cocok dengan order.

1. Di BuatQris, buka **Sandbox** dan bagian **Dokumentasi & API**. Salin Account
   ID, Secret Token, serta Signing Secret callback ke file environment VPS;
   jangan commit atau kirim rahasia tersebut.
2. Isi `/etc/beatplay-premium.env` (izin file `chmod 600`):

   ```ini
   PUBLIC_BASE=https://premium.domain-anda
   PAYMENT_PROVIDER=buatqris
   BUATQRIS_MODE=sandbox
   BUATQRIS_ACCOUNT_ID=
   BUATQRIS_SECRET_TOKEN=
   BUATQRIS_SIGNING_SECRET=
   BUATQRIS_QRIS_METHOD=qris_two
   BUATQRIS_FEE_BY=user
   BUATQRIS_EXPIRY_MINUTES=15
   ```

   `fee_by=user` adalah default (fee dipotong dari saldo merchant); `buyer`
   menambahkan fee ke total yang ditagihkan. Pastikan pilihan ini sesuai ketentuan
   gateway dan aturan pembayaran yang berlaku untuk akun Anda.
3. Di dashboard BuatQris, atur Callback URL ke
   `https://premium.domain-anda/api/payments/buatqris/callback` dan pastikan
   Signing Secret yang disimpan di VPS adalah secret untuk callback tersebut.
   Backend juga mengirim URL callback pada setiap pembuatan QRIS.
4. Restart dan cek server:

   ```bash
   sudo systemctl restart beatplay-premium
   sudo systemctl status beatplay-premium --no-pager
   ```

5. Buat order uji dari BeatPlay saat BuatQris masih di Sandbox dan tandai lunas.
   Webhook atau fallback background akan memperbarui order menjadi `PAID` dan
   mengaktifkan Premium; fallback dibatasi sekali setiap 20 detik per transaksi.
   Setelah uji berhasil, pindah ke **Real**, ganti ketiga kredensial
   dengan kredensial Real, ubah
   `BUATQRIS_MODE=production`, lalu restart service. Jangan mengirim kredensial
   ke aplikasi desktop.

- Tripay: di dashboard Tripay set callback URL ke `https://pay.domain-anda/api/payments/tripay/callback`.
- Xendit: di dashboard Xendit (Settings > Webhooks) set URL ke
  `https://pay.domain-anda/api/payments/xendit/callback` + samakan token.
  Di test mode, admin bisa picu simulasi bayar dari dashboard (endpoint
  `/api/admin/xendit/simulate`) — hasil masuk via webhook seperti asli.

## API

- `GET /api/plans` -> `{plans, tripay_method, provider}`; objek `manual` hanya ada bila provider `manual` aktif.
- `POST /api/orders/create` `{"email","plan_id","customer_name"?}` -> `{order}` (email opsional jika ada header `Authorization`)
- `POST /api/orders/proof` `{"merchant_ref","sender_name","note"?}` -> order `PENDING` (QRIS manual)
- Provider `manual` saja: `GET /api/manual-qris` -> info QRIS statis; `GET /manual-qris.png` -> gambar.
- `POST /api/orders/create` `{"email","plan_id","customer_name"?}` -> `{order:{merchant_ref,reference,amount,status,checkout_url,qr_string,qr_url,expired_at}}`
- `GET /api/orders/status?merchant_ref=` -> `{order, premium, expiry}`
- `POST /api/payments/tripay/callback` (dari Tripay, header `X-Callback-Signature/Event`) -> `{success:true}`
- `POST /api/payments/xendit/callback` (dari Xendit, header `x-callback-token`) -> `{success:true}`
- `POST /api/payments/buatqris/callback` (BuatQris, header `X-BuatQris-Signature`) -> `{success:true}`
- `GET /api/premium?email=` -> `{email, premium, expiry?}` (`expiry` epoch detik; absen = lifetime manual)
- Admin (`X-Admin-Token`): `GET /api/admin/list`, `GET /api/admin/orders`, `POST /api/admin/add|remove|revoke|activate|verify`, `POST /api/orders/mock-pay` (mock saja), `POST /api/admin/notify/test` (kirim pesan tes ke Telegram), `POST /api/admin/smtp/test` (kirim tes kode verifikasi ke email)

### Auth akun BeatPlay (tanpa Google)

- `POST /api/auth/register` `{"email","password","verify_mode"}` -> `{ok, email, token?, requires_verification, verification?, dev_code?}`
  - `verify_mode="code"` (dipakai aplikasi): server membuat kode 6 digit. Dengan SMTP aktif kode dikirim ke email; `requires_verification=true` dan **tanpa** `token` sampai user memasukkan kode.
  - Tanpa SMTP: `requires_verification=true` tetapi kode dikembalikan di `dev_code` (mode uji, aplikasi menampilkan kode di layar).
  - `verify_mode` kosong + SMTP aktif: kirim link verifikasi (`GET /api/auth/verify?token=...`).
  - `verify_mode` kosong + tanpa SMTP: langsung verified + token session (dev/self-host mode).
- `POST /api/auth/verify-code` `{"email","code"}` -> `{ok, email, token, verified}` (kode 6 digit; TTL 15 menit, maks 5 percobaan)
- `POST /api/auth/login` `{"email","password"}` -> `{ok, email, token}` (403 + `verification=code` bila akun belum diverifikasi)
- `GET /api/auth/me` (header `Authorization: Bearer <token>`) -> `{email, verified, premium, expiry?}`
- `POST /api/auth/logout` (header `Authorization: Bearer <token>`) -> `{ok}`
- `POST /api/auth/resend` `{"email"}` -> kirim ulang kode verifikasi (SMTP wajib); tanpa SMTP mengembalikan `dev_code`
- `POST /api/auth/forgot` `{"email"}` -> kirim kode reset password ke email (SMTP wajib). Selalu membalas `{ok:true, verification:"code"}` agar tidak bisa dipakai menebak email terdaftar; tanpa SMTP mengembalikan `dev_code`
- `POST /api/auth/reset` `{"email","code","password"}` -> verifikasi kode reset lalu ganti password (kode 6 digit, TTL 15 menit, maks 6 percobaan). Sesi lama dicabut, akun otomatis terverifikasi
- `GET /api/auth/verify?token=...` -> aktifkan akun + link premium lama berbasis Gmail (kalau ada)

**Mengaktifkan kode verifikasi email (SMTP):** isi `smtp_config.json` (lihat `smtp_config.json.example`) **atau** variabel lingkungan `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_USE_TLS`, `SMTP_USE_SSL` di `/etc/beatplay-premium.env`. Env menang atas file. Port 587 memakai STARTTLS (`SMTP_USE_TLS=true`); port 465 memakai SMTPS (`SMTP_USE_SSL=true`, `SMTP_USE_TLS=false`). Setelah diisi, restart `beatplay-premium` dan log akan menampilkan `SMTP aktif: ...`.

Cara tercepat di VPS: jalankan `./enable_smtp.sh` (meminta host/port/username/password lalu menulis env + restart). Uji kirim: `POST /api/admin/smtp/test` dengan header `X-Admin-Token` dan body `{"to":"email@contoh.com"}`.

**Panduan Gmail (paling cepat, tanpa ubah DNS):**
1. Aktifkan Verifikasi 2 Langkah di akun Google.
2. Buka https://myaccount.google.com/apppasswords → buat App Password (16 karakter). Ini **bukan** password Gmail biasa.
3. Di VPS: `sudo /opt/beatplay/server/enable_smtp.sh`, lalu:
   - host `smtp.gmail.com` (Enter), port `465` (Enter)
   - alamat Gmail lengkap, mis. `nama@gmail.com`
   - App Password (spasi boleh, otomatis dibuang)
   - nama pengirim (Enter = `BeatPlay`)
4. Cek `journalctl -u beatplay-premium -n 20 --no-pager | grep -i smtp` → harus `SMTP aktif: smtp.gmail.com`.
5. `From` akan menjadi `BeatPlay <nama@gmail.com>`. Kalau ingin pengirim `@adminbeat.my.id`, perlu mailbox domain atau provider transaksional (Brevo/Resend) + record DNS.

Password disimpan dengan scrypt (salt acak per user). Session token acak 256-bit, TTL 30 hari, disimpan di `sessions.json`. Status premium lama berbasis Gmail di `premium_emails.json` otomatis di-link ke akun BeatPlay saat user dengan email itu mendaftar/verifikasi. Dashboard admin di `/` menampilkan daftar akun BeatPlay beserta tombol **Verifikasi** dan **Cabut**.

### Playlist publik, like, dan jumlah diputar

Playlist disimpan di `beatplay_playlists.json` (runtime, gitignored). Setiap playlist milik satu akun dan menyimpan snapshot lagu, daftar email yang menyukai, dan penghitung pemutaran.

- `POST /api/playlists/sync` `{"playlists":[{"id","name","cover","songs":[{"id","title","artist","thumbnail","duration"}]}]}` (Bearer token) -> `{ok, count}`. Menggantikan snapshot playlist akun; jumlah like dan pemutaran playlist lama dipertahankan.
- `GET /api/user/<kode>/playlists` (Bearer opsional untuk status `liked`) -> `{profile, playlists:[{id,name,cover,songCount,likes,liked,plays,updatedAt}]}`
- `GET /api/playlist/<id>` (Bearer opsional) -> detail playlist + `songs` + `owner`
- `POST /api/playlist/like` `{"id"}` (Bearer token) -> `{likes, liked}` (toggle)
- `POST /api/playlist/play` `{"id"}` -> `{plays}` (menambah satu pemutaran)

Aplikasi memakai endpoint ini setelah pengguna menekan **Publikasikan** di halaman profil; QR pada kartu kode BeatPlay berisi link `/f/<kode>` sehingga bisa dipindai dari tab Pencarian.

File runtime (`orders.json`, `premium_expiry.json`, `users.json`, `sessions.json`, `beatplay_playlists.json`, `tripay_config.json`, `smtp_config.json`) di-gitignore.
Kredensial BuatQris disimpan di `/etc/beatplay-premium.env`, bukan di file aplikasi.
Tes logika: `python3 test_tripay.py`.

## Notif HP (Telegram, tiap ada bukti masuk)

1. Di HP: chat ke `@BotFather` → `/newbot` → dapat **token** (`123:ABC...`).
2. Chat ke `@userinfobot` (atau buka `https://api.telegram.org/bot<token>/getUpdates` setelah chat bot kamu) → dapat **chat ID** angka.
3. Isi di `tripay_config.json`: `notify.telegram_bot_token` + `notify.telegram_chat_id`, restart server.
4. Di dashboard admin klik **Tes notif** — kalau masuk ke HP berarti siap.
   Tiap bukti bayar masuk (dan tiap pembayaran auto lunas) kamu dapat pesan
   berisi ref, email, paket, nama pengirim + catatan.
