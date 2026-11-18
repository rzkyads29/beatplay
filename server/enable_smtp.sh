#!/usr/bin/env bash
# Aktifkan kode verifikasi email BeatPlay lewat SMTP domain (adminbeat.my.id).
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

default_host="mail.adminbeat.my.id"
default_user="no-reply@adminbeat.my.id"
default_from="BeatPlay <no-reply@adminbeat.my.id>"

read -rp "SMTP host [$default_host]: " SMTP_HOST
SMTP_HOST="${SMTP_HOST:-$default_host}"

read -rp "SMTP port (465 = SSL, 587 = STARTTLS) [465]: " SMTP_PORT
SMTP_PORT="${SMTP_PORT:-465}"

read -rp "SMTP username [$default_user]: " SMTP_USERNAME
SMTP_USERNAME="${SMTP_USERNAME:-$default_user}"

read -rsp "SMTP password: " SMTP_PASSWORD; echo
if [ -z "${SMTP_PASSWORD}" ]; then
  echo "Password kosong, dibatalkan." >&2
  exit 1
fi

read -rp "Alamat pengirim (From) [$default_from]: " SMTP_FROM
SMTP_FROM="${SMTP_FROM:-$default_from}"

if [ "$SMTP_PORT" = "465" ]; then
  SMTP_USE_TLS=false
  SMTP_USE_SSL=true
else
  SMTP_USE_TLS=true
  SMTP_USE_SSL=false
fi

sudo touch "$ENV_FILE"
sudo chmod 600 "$ENV_FILE"
# Hapus baris SMTP lama, sisakan variabel lain (ADMIN_TOKEN, PUBLIC_BASE, dst).
sudo sed -i '/^SMTP_/d' "$ENV_FILE"
sudo tee -a "$ENV_FILE" >/dev/null <<EOF
SMTP_HOST=$SMTP_HOST
SMTP_PORT=$SMTP_PORT
SMTP_USERNAME=$SMTP_USERNAME
SMTP_PASSWORD=$SMTP_PASSWORD
SMTP_FROM=$SMTP_FROM
SMTP_USE_TLS=$SMTP_USE_TLS
SMTP_USE_SSL=$SMTP_USE_SSL
EOF

echo "Merestart $SERVICE ..."
sudo systemctl restart "$SERVICE"
sleep 2
echo "--- log terakhir ---"
sudo journalctl -u "$SERVICE" -n 15 --no-pager || true
echo
echo "Selesai. Pastikan log menampilkan 'SMTP aktif: ...'."
echo "Uji kirim email dari server (ganti ADMIN_TOKEN):"
echo "  curl -sS -X POST https://premium.adminbeat.my.id/api/admin/smtp/test \\"
echo "    -H 'Content-Type: application/json' -H 'X-Admin-Token: ADMIN_TOKEN' \\"
echo "    -d '{\"to\":\"email-kamu@gmail.com\"}'"