#!/usr/bin/env bash
# Aktifkan kode verifikasi email BeatPlay lewat SMTP (default: Gmail + App Password).
#
# Jalankan di VPS sebagai user yang punya sudo:
#   chmod +x enable_smtp.sh
#   ./enable_smtp.sh
#
# Script menulis kredensial ke /etc/beatplay-premium.env (chmod 600), lalu
# me-restart service. Password diketik di terminal dan TIDAK pernah ditulis
# ke repo maupun dikirim lewat chat.
set -euo pipefail

ENV_FILE="${ENV_FILE:-/etc/beatplay-premium.env}"
SERVICE="${SERVICE:-beatplay-premium}"

# Pakai sudo hanya kalau bukan root, supaya skrip jalan di `sudo -i` maupun sebagai user biasa.
if [ "$(id -u)" -eq 0 ]; then SUDO=""; else SUDO="sudo"; fi

default_host="smtp.gmail.com"
default_port="465"
default_from_name="BeatPlay"

read -rp "SMTP host [$default_host]: " SMTP_HOST
SMTP_HOST="${SMTP_HOST:-$default_host}"

read -rp "SMTP port (465 = SSL, 587 = STARTTLS) [$default_port]: " SMTP_PORT
SMTP_PORT="${SMTP_PORT:-$default_port}"

read -rp "Alamat Gmail lengkap (contoh: nama@gmail.com): " SMTP_USERNAME
if [ -z "${SMTP_USERNAME}" ]; then
  echo "Alamat Gmail wajib diisi, dibatalkan." >&2
  exit 1
fi

read -rsp "App Password Gmail (16 karakter, spasi boleh): " SMTP_PASSWORD; echo
# App Password sering ditampilkan dengan spasi; Gmail butuh tanpa spasi.
SMTP_PASSWORD="${SMTP_PASSWORD// /}"
if [ -z "${SMTP_PASSWORD}" ]; then
  echo "App Password kosong, dibatalkan." >&2
  exit 1
fi

read -rp "Nama pengirim [$default_from_name]: " SMTP_FROM_NAME
SMTP_FROM_NAME="${SMTP_FROM_NAME:-$default_from_name}"
SMTP_FROM="${SMTP_FROM_NAME} <${SMTP_USERNAME}>"

if [ "$SMTP_PORT" = "465" ]; then
  SMTP_USE_TLS=false
  SMTP_USE_SSL=true
else
  SMTP_USE_TLS=true
  SMTP_USE_SSL=false
fi

$SUDO touch "$ENV_FILE"
$SUDO chmod 600 "$ENV_FILE"
# Hapus baris SMTP lama, sisakan variabel lain (ADMIN_TOKEN, PUBLIC_BASE, dst).
$SUDO sed -i '/^SMTP_/d' "$ENV_FILE"
$SUDO tee -a "$ENV_FILE" >/dev/null <<EOF
SMTP_HOST=$SMTP_HOST
SMTP_PORT=$SMTP_PORT
SMTP_USERNAME=$SMTP_USERNAME
SMTP_PASSWORD=$SMTP_PASSWORD
SMTP_FROM=$SMTP_FROM
SMTP_USE_TLS=$SMTP_USE_TLS
SMTP_USE_SSL=$SMTP_USE_SSL
EOF

echo "Merestart $SERVICE ..."
$SUDO systemctl restart "$SERVICE"
sleep 2
echo "--- log terakhir ---"
$SUDO journalctl -u "$SERVICE" -n 15 --no-pager || true
echo
echo "Selesai. Pastikan log menampilkan 'SMTP aktif: ...'."
echo "Uji kirim email dari server (ganti ADMIN_TOKEN):"
echo "  curl -sS -X POST https://premium.adminbeat.my.id/api/admin/smtp/test \\"
echo "    -H 'Content-Type: application/json' -H 'X-Admin-Token: ADMIN_TOKEN' \\"
echo "    -d '{\"to\":\"email-kamu@gmail.com\"}'"