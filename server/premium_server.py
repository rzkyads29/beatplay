"""BeatPlay Premium server (QRIS BuatQris/Tripay/Xendit + manual admin).

Fitur:
- Premium ditentukan dari Gmail: manual (lifetime oleh admin) atau berbayar (expiry).
- Pembelian via provider QRIS: BuatQris, Tripay Closed Payment (QRIS2), atau
  Xendit Payments API v3. Mode "mock" untuk tes tanpa kredensial.
- Dashboard admin di / : kelola Gmail manual, lihat plans/orders, aktivasi manual.

Run lokal:  python3 premium_server.py [--port 8080] [--admin-token admin123]
Run VPS:    pastikan HTTPS + domain publik agar callback provider sampai,
    lalu isi kredensial provider di environment (jangan commit!) dan set
    --public-base https://domain-anda agar callback URL benar.

Kredensial Tripay: dashboard Tripay > Member > Merchant > Opsi > Edit
(production) atau API & Integrasi > Simulator > Merchant > Detail (sandbox).
Lihat https://tripay.co.id/developer untuk dokumentasi resmi.
Kredensial Xendit: dashboard Xendit > Settings > API keys (secret key) dan
Settings > Webhooks (token + URL callback). Lihat https://docs.xendit.co/
- Buat payment:  POST https://api.xendit.co/v3/payment_requests (Basic auth
  pakai secret key, header api-version: 2024-11-11), body type=PAY,
  channel_code=QRIS, channel_properties={expires_at, qr_string_type:DYNAMIC}.
  QR ada di actions[] (PRESENT_TO_CUSTOMER / QR_STRING).
- Cek status:   GET https://api.xendit.co/v3/payment_requests/{pr_id}.
- Webhook:      POST berisi {event, data{reference_id, payment_request_id,
  status}} + header x-callback-token (samakan dengan token dashboard).
- Test mode:    POST /v3/payment_requests/{pr_id}/simulate (ada endpoint admin).
"""
import argparse
import base64
import datetime
import hashlib
import hmac
import json
import os
import re
import secrets
import smtplib
import time
import threading
import urllib.parse
import urllib.request
from email.message import EmailMessage
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
SAFE_REF_RE = re.compile(r"[^A-Za-z0-9-]")
HERE = os.path.dirname(os.path.abspath(__file__))
PROOF_DIR = os.path.join(HERE, "proofs")
AVATARS_DIR = os.path.join(HERE, "avatars")
MAX_PHOTO_BYTES = 4 * 1024 * 1024
PHOTO_MIMES = {"image/jpeg": "jpg", "image/png": "png"}
# Follow codes avoid look-alike characters (0/O, 1/I) so they are easy to read out loud.
FOLLOW_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
FOLLOW_CODE_LEN = 8

TRIPAY_SANDBOX = "https://tripay.co.id/api-sandbox"
TRIPAY_PROD = "https://tripay.co.id/api"
XENDIT_API = "https://api.xendit.co"
XENDIT_API_VERSION = "2024-11-11"
BUATQRIS_API = "https://api.buatqris.site"
BUATQRIS_STATUS_MIN_INTERVAL = 20
BUATQRIS_STATUS_SCAN_INTERVAL = 5


def load_json(path, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, ValueError):
        return default


def save_json(path, obj):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
    os.replace(tmp, path)


def tripay_signature(merchant_code, merchant_ref, amount, private_key):
    """HMAC-SHA256(merchant_code + merchant_ref + amount, private_key).

    Vektor uji dari docs Tripay: T0001 + INV55567 + 1500000 dengan key
    ytf6ooi2gmlNPfpchd94jDOk8hRWOu = 9f167eba844d1fcb...8c86a.
    """
    msg = "{}{}{}".format(merchant_code, merchant_ref, amount).encode("latin-1")
    return hmac.new(private_key.encode("latin-1"), msg, hashlib.sha256).hexdigest()


def verify_callback_signature(raw_body: bytes, signature: str, private_key: str) -> bool:
    expected = hmac.new(private_key.encode("latin-1"), raw_body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, (signature or "").strip().lower())


def verify_buatqris_signature(raw_body: bytes, signature: str, signing_secret: str) -> bool:
    """Verify BuatQris' X-BuatQris-Signature: sha256=<HMAC-SHA256(raw body)> header."""
    if not signing_secret or not signature:
        return False
    expected = "sha256=" + hmac.new(signing_secret.encode(), raw_body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, str(signature).strip().lower())


def buatqris_map_status(status):
    """Map BuatQris transaction state to the premium order state."""
    s = str(status or "").strip().lower()
    if s == "success":
        return "PAID"
    if s == "expired":
        return "EXPIRED"
    if s == "failed":
        return "FAILED"
    return "UNPAID"


def _provider_bool(value):
    if isinstance(value, bool):
        return value
    return str(value or "").strip().lower() in ("1", "true", "yes")


def xendit_extract_qr(data):
    """Ambil QR string dari respons payment request Xendit (actions[])."""
    for a in (data or {}).get("actions", []):
        if a.get("type") == "PRESENT_TO_CUSTOMER" and a.get("descriptor") == "QR_STRING" and a.get("value"):
            return a["value"]
    return None


def xendit_map_status(status):
    """Petakan status Xendit ke status order internal."""
    s = str(status or "").upper()
    if s == "SUCCEEDED":
        return "PAID"
    if s == "EXPIRED":
        return "EXPIRED"
    if s in ("FAILED", "CANCELED"):
        return "FAILED"
    return "UNPAID"


def xendit_expiry_iso(minutes):
    return (datetime.datetime.now(datetime.timezone.utc) +
            datetime.timedelta(minutes=minutes)).strftime("%Y-%m-%dT%H:%M:%SZ")


def validate_proof_photo(mime, raw):
    """Kembalikan ekstensi (jpg/png) bila foto valid, else None.

    Syarat: mime diizinkan, ukuran <= 4MB, magic bytes cocok
    (JPEG FFD8, PNG 89504E47). Mencegah upload non-gambar.
    """
    ext = PHOTO_MIMES.get((mime or "").lower())
    if not ext or not raw or len(raw) > MAX_PHOTO_BYTES:
        return None
    if ext == "jpg" and raw[:2] != b"\xff\xd8":
        return None
    if ext == "png" and raw[:4] != b"\x89PNG":
        return None
    return ext


def proof_filename(merchant_ref, ext):
    safe = SAFE_REF_RE.sub("", merchant_ref)[:64] or "order"
    return "{}.{}".format(safe, ext)


def build_multipart(fields, file_field, filename, file_mime, file_data, boundary):
    """Bangun body multipart/form-data (murni, untuk sendPhoto Telegram)."""
    out = bytearray()
    for k, v in fields.items():
        out += "--{}\r\n".format(boundary).encode()
        out += 'Content-Disposition: form-data; name="{}"\r\n\r\n'.format(k).encode()
        out += "{}\r\n".format(v).encode()
    out += "--{}\r\n".format(boundary).encode()
    out += 'Content-Disposition: form-data; name="{}"; filename="{}"\r\n'.format(file_field, filename).encode()
    out += "Content-Type: {}\r\n\r\n".format(file_mime).encode()
    out += file_data
    out += "\r\n--{}--\r\n".format(boundary).encode()
    return bytes(out)


def build_proof_message(order, plan):
    lines = ["Bukti bayar premium baru",
             "Ref: {}".format(order.get("merchant_ref", "-")),
             "Email: {}".format(order.get("email", "-")),
             "Paket: {} (Rp{:,})".format((plan or {}).get("title", order.get("plan_id", "-")), order.get("amount", 0)).replace(",", ".")]
    proof = order.get("proof", {})
    lines.append("Dari: {}".format(proof.get("sender", "-")))
    if proof.get("note"):
        lines.append("Catatan: {}".format(proof["note"]))
    lines.append("Verifikasi di dashboard admin, lalu Aktifkan.")
    return "\n".join(lines)


def build_paid_message(order, plan):
    return "\n".join(["Premium lunas (otomatis)",
                      "Ref: {}".format(order.get("merchant_ref", "-")),
                      "Email: {}".format(order.get("email", "-")),
                      "Paket: {}".format((plan or {}).get("title", order.get("plan_id", "-")))])


def send_telegram(bot_token, chat_id, text, photo_path=None):
    """Kirim pesan Telegram (foto + caption bila ada), fire-and-forget di thread."""
    import threading

    def _do():
        try:
            if photo_path and os.path.exists(photo_path):
                with open(photo_path, "rb") as f:
                    data = f.read()
                mime = "image/png" if photo_path.lower().endswith(".png") else "image/jpeg"
                boundary = "BeatPlay{}".format(secrets.token_hex(8))
                body = build_multipart(
                    {"chat_id": chat_id, "caption": text[:1000]}, "photo",
                    os.path.basename(photo_path), mime, data, boundary)
                req = urllib.request.Request(
                    "https://api.telegram.org/bot{}/sendPhoto".format(bot_token), data=body,
                    headers={"Content-Type": "multipart/form-data; boundary=" + boundary}, method="POST")
            else:
                payload = urllib.parse.urlencode({"chat_id": chat_id, "text": text}).encode()
                req = urllib.request.Request(
                    "https://api.telegram.org/bot{}/sendMessage".format(bot_token),
                    data=payload, method="POST")
            with urllib.request.urlopen(req, timeout=30) as r:
                r.read()
        except Exception as e:
            print("Telegram notify gagal: {}".format(e))

    threading.Thread(target=_do, daemon=True).start()


ADMIN_HTML = """<!doctype html><html lang="id"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>BeatPlay Admin - Premium</title>
<style>
:root{--bg:#0a0f1e;--card:#131a2e;--line:#26314d;--txt:#e8ecf5;--mut:#9aa4bf;--pink:#f471d6;--cyan:#38e1ff;--green:#3ddc84;--amber:#ffb020;--red:#ff6b6b}
*{box-sizing:border-box}
body{margin:0;min-height:100vh;color:var(--txt);font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;background:radial-gradient(1100px 480px at 50% -8%,#1c2650 0%,var(--bg) 62%) fixed,var(--bg)}
.wrap{max-width:980px;margin:0 auto;padding:28px 18px 70px}
header.top{display:flex;align-items:center;gap:14px}
.logo{width:48px;height:48px;border-radius:15px;background:linear-gradient(135deg,#1b2a63,#0e1530);display:flex;align-items:center;justify-content:center;box-shadow:0 0 26px #f471d655;flex:none}
.logo svg{width:28px;height:28px}
h1{font-size:23px;margin:0}
.grad{background:linear-gradient(90deg,var(--cyan),var(--pink));-webkit-background-clip:text;background-clip:text;color:transparent}
.sub{color:var(--mut);font-size:13px;margin:8px 0 0}
.sub code{background:#0d1330;border:1px solid var(--line);border-radius:6px;padding:1px 7px;font-size:12px}
.card{background:linear-gradient(180deg,#151d33,#0f1528);border:1px solid var(--line);border-radius:16px;padding:18px;margin-top:16px;box-shadow:0 10px 30px #0007}
.card h2{margin:0 0 12px;font-size:15px;letter-spacing:.3px}
.row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
input,select{background:#0b1124;border:1px solid var(--line);color:var(--txt);border-radius:10px;padding:9px 12px;font-size:14px;outline:none}
input:focus,select:focus{border-color:var(--cyan);box-shadow:0 0 0 3px #38e1ff33}
button{cursor:pointer;border:none;border-radius:10px;padding:9px 16px;font-size:14px;font-weight:600}
.btn{background:linear-gradient(90deg,#1f9df0,#7b5cff);color:#fff}
.btn-green{background:linear-gradient(90deg,#1faa6b,#3ddc84);color:#04240f}
.btn-ghost{background:#0b1124;color:var(--txt);border:1px solid var(--line)}
.btn-mini{padding:5px 12px;font-size:12px;border-radius:8px}
.danger{background:#2a1420;color:var(--red);border:1px solid #5c2436}
button:hover{filter:brightness(1.12)}
.grid2{display:grid;grid-template-columns:1fr 1fr;gap:16px}
.grid2 .card{margin-top:16px}
@media(max-width:760px){.grid2{grid-template-columns:1fr}}
ul.clean{list-style:none;margin:12px 0 0;padding:0;display:flex;flex-direction:column;gap:8px}
ul.clean li{background:#0b1124;border:1px solid var(--line);border-radius:10px;padding:8px 12px;display:flex;align-items:center;gap:10px;font-size:14px}
ul.clean li .grow{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
ul.clean li.section{background:none;border:none;color:var(--mut);font-weight:700;padding:8px 2px 0}
table.tbl{border-collapse:separate;border-spacing:0;width:100%;font-size:13px;border:1px solid var(--line);border-radius:12px;overflow:hidden}
.tbl th{background:#1a2340;text-align:left;padding:9px 10px;color:var(--mut);font-weight:600}
.tbl td{padding:9px 10px;border-top:1px solid var(--line);vertical-align:middle}
.tbl tr:hover td{background:#ffffff08}
.tbl code{background:#0b1124;padding:2px 7px;border-radius:6px;font-size:12px}
.pill{display:inline-block;padding:3px 11px;border-radius:99px;font-size:12px;font-weight:700}
.st-UNPAID{background:#2a2f45;color:#aeb6cf}
.st-PENDING{background:#3a2a10;color:var(--amber);animation:pulse 1.6s infinite}
.st-PAID{background:#0f3524;color:var(--green)}
@keyframes pulse{50%{opacity:.55}}
img.proof{max-height:110px;border-radius:10px;border:1px solid var(--line);display:block;margin-top:6px}
.mut{color:var(--mut);font-size:13px}
a{color:var(--cyan)}
footer{color:var(--mut);font-size:12px;text-align:center;margin-top:28px}
</style>
</head><body><div class="wrap">
<header class="top">
<div class="logo"><svg viewBox="0 0 24 24" fill="none"><path d="M6 4.5v15l13-7.5z" fill="#38e1ff"/><path d="M17 4v16M20.5 7v10" stroke="#f471d6" stroke-width="2.2" stroke-linecap="round"/></svg></div>
<div><h1>Beat<span class="grad">Play</span> Admin</h1><p class="sub">Token admin via header <code>X-Admin-Token</code> &bull; Provider: <b id="tmode">?</b> &bull; Notif HP: <b id="nmode">?</b></p></div>
</header>
<div class="card"><div class="row">
<input id="token" type="password" placeholder="Admin token" style="width:200px" autocomplete="off">
<button class="btn" onclick="refreshAll()">Refresh</button>
<button class="btn-ghost" onclick="testNotify()">Tes notif</button>
</div></div>
<div class="grid2">
<div class="card"><h2>Manual lifetime</h2>
<div class="row"><input id="email" placeholder="nama@gmail.com" style="flex:1;min-width:180px"><button class="btn" onclick="add()">Tambah premium</button></div>
<ul id="list" class="clean"></ul></div>
<div class="card"><h2>Aktivasi berbayar manual</h2>
<div class="row"><input id="memail" placeholder="nama@gmail.com" style="flex:1;min-width:160px">
<select id="mdays"><option value="30">30 hari</option><option value="90">90 hari</option><option value="365">365 hari</option></select>
<button class="btn-green" onclick="activate()">Aktifkan</button></div>
<p class="mut">Cek mutasi DANA dulu, lalu aktifkan. Email langsung premium + expiry.</p></div>
</div>
<div class="card" id="usersCard"><h2>Akun BeatPlay</h2><div id="users"></div></div>
<div class="card"><h2>Paket</h2><div id="plans"></div></div>
<div class="card"><h2>Order 20 terakhir</h2><div id="orders"></div></div>
<div class="card" id="manualQrisCard"><h2>QRIS manual</h2><div id="manualbox"></div></div>
<footer>BeatPlay Premium Server &bull; pembayaran otomatis diverifikasi lewat callback provider</footer>
</div>
<script>
let PROVIDER = "mock";
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
async function api(path, opts={}) {
  opts.headers = Object.assign({}, opts.headers||{}, {"X-Admin-Token": document.getElementById("token").value});
  const r = await fetch(path, opts);
  if (!r.ok) { alert("HTTP "+r.status+": "+await r.text()); return null; }
  return r.json();
}
function fmtExp(s){ if(!s) return "lifetime"; return new Date(s*1000).toLocaleString("id-ID"); }
function tokenQS(){ return encodeURIComponent(document.getElementById("token").value); }
async function refreshAll(){
  const t = await api("/api/admin/list"); if(!t) return;
  PROVIDER = t.provider || "mock";
  document.getElementById("tmode").textContent = PROVIDER + (t.provider_configured ? " (terkonfigurasi)" : " (mock/belum isi kredensial)");
  document.getElementById("nmode").textContent = t.notify_configured ? "aktif (Telegram)" : "mati (isi notify di tripay_config.json)";
  const ul = document.getElementById("list"); ul.innerHTML = "";
  t.manual.forEach(e => {
    const li=document.createElement("li");
    const sp=document.createElement("span"); sp.className="grow"; sp.textContent=e;
    const b=document.createElement("button"); b.className="btn-mini danger"; b.textContent="Hapus";
    b.onclick=async()=>{await api("/api/admin/remove",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email:e})});refreshAll();};
    li.append(sp,b); ul.appendChild(li);
  });
  const ex = document.createElement("li"); ex.className="section"; ex.textContent="Berbayar (expiry):";
  ul.appendChild(ex);
  Object.entries(t.expiry).forEach(([e,s])=>{
    const li=document.createElement("li");
    const sp=document.createElement("span"); sp.className="grow"; sp.textContent=e+"  ·  s/d "+fmtExp(s);
    const b=document.createElement("button"); b.className="btn-mini danger"; b.textContent="Cabut";
    b.onclick=async()=>{await api("/api/admin/revoke",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email:e})});refreshAll();};
    li.append(sp,b); ul.appendChild(li);
  });
  const users = t.users || [];
  document.getElementById("users").innerHTML = users.length
    ? '<table class="tbl"><tr><th>Email</th><th>Verifikasi</th><th>Premium</th><th>Dibuat</th><th>Aksi</th></tr>'+
      users.map(u=>{
        const prem = u.premium_manual ? "lifetime" : (u.premium_expiry ? fmtExp(u.premium_expiry) : "—");
        const vb = u.verified
          ? '<span class="pill st-PAID">terverifikasi</span>'
          : `<button class="btn-mini btn-green" onclick="verifyUser('${esc(u.email)}')">Verifikasi</button>`;
        const act = (u.premium_manual||u.premium_expiry)
          ? `<button class="btn-mini danger" onclick="revokeUser('${esc(u.email)}')">Cabut</button>`
          : "";
        return `<tr><td>${esc(u.email)}</td><td>${vb}</td><td>${esc(String(prem))}</td><td class="mut">${u.created_at ? new Date(u.created_at*1000).toLocaleString("id-ID") : "—"}</td><td>${act}</td></tr>`;
      }).join("")+"</table>"
    : '<p class="mut">Belum ada akun BeatPlay. Akun muncul setelah pengguna mendaftar dari aplikasi.</p>';
  const p = await api("/api/plans");
  document.getElementById("plans").innerHTML = '<table class="tbl"><tr><th>ID</th><th>Paket</th><th>Harga</th><th>Hari</th></tr>'+
    p.plans.map(x=>`<tr><td><code>${esc(x.id)}</code></td><td>${esc(x.title)}</td><td>Rp${x.price.toLocaleString("id-ID")}</td><td>${x.days}</td></tr>`).join("")+"</table>";
  const o = await api("/api/admin/orders");
  const plans = p.plans;
  const daysOf = (pid) => (plans.find(x=>x.id===pid)||{}).days || 30;
  const rows = o.orders.map(x=>{
    let btn = "";
    let proof = '<span class="mut">—</span>';
    if (x.proof) {
      proof = `dari <b>${esc(x.proof.sender)}</b>${x.proof.note ? " ("+esc(x.proof.note)+")" : ""}`;
      if (x.proof.photo) {
        const imgUrl = `/api/admin/proof-photo?merchant_ref=${encodeURIComponent(x.merchant_ref)}&token=${tokenQS()}`;
        proof += `<br><a href="${imgUrl}" target="_blank"><img class="proof" src="${imgUrl}" loading="lazy"></a>`;
      }
    }
    if (x.status==="UNPAID" && PROVIDER==="mock") btn = `<button class="btn-mini btn-ghost" onclick="mockpay('${x.merchant_ref}')">Simulasi bayar</button>`;
    else if (x.status==="UNPAID" && PROVIDER==="xendit") btn = `<button class="btn-mini btn-ghost" onclick="xsimulate('${x.merchant_ref}')">Simulasi (test)</button>`;
    else if (x.status==="PENDING") btn = `<button class="btn-mini btn-green" onclick="activateOrder('${esc(x.email)}','${esc(x.plan_id)}')">Aktifkan ${daysOf(x.plan_id)} hari</button>`;
    return `<tr><td><code>${esc(x.merchant_ref)}</code></td><td>${esc(x.email)}</td><td>${esc(x.plan_id)}</td><td>Rp${x.amount.toLocaleString("id-ID")}</td><td><span class="pill st-${x.status}">${x.status}</span></td><td>${proof}<div style="margin-top:6px">${btn}</div></td></tr>`;
  }).join("");
  document.getElementById("orders").innerHTML = '<table class="tbl"><tr><th>Ref</th><th>Email</th><th>Paket</th><th>Nominal</th><th>Status</th><th>Bukti / Aksi</th></tr>'+rows+"</table>";
  document.getElementById("manualQrisCard").hidden = PROVIDER !== "manual";
  if (PROVIDER === "manual") {
    const m = await api("/api/manual-qris");
    if (m) document.getElementById("manualbox").innerHTML =
      `<p><b>${esc(m.account_name)||"(belum diisi di plans.json)"}</b><br><span class="mut">${esc(m.instructions)||""}</span></p>`+
      (m.has_image ? `<a href="/manual-qris.png" target="_blank"><img class="proof" src="/manual-qris.png"></a><p class="mut">Taro file <code>manual_qris.png</code> di folder server untuk mengganti.</p>` : `<p class="mut">Belum ada gambar — taro file <code>manual_qris.png</code> di folder server agar tampil di aplikasi.</p>`);
  } else {
    document.getElementById("manualbox").innerHTML = "";
  }
}
async function add(){
  const email=document.getElementById("email").value;
  await api("/api/admin/add",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email})});
  document.getElementById("email").value=""; refreshAll();
}
async function activate(){
  const email=document.getElementById("memail").value, days=+document.getElementById("mdays").value;
  await api("/api/admin/activate",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email,days})});
  refreshAll();
}
async function mockpay(ref){
  await api("/api/orders/mock-pay",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({merchant_ref:ref})});
  refreshAll();
}
async function testNotify(){
  const r = await api("/api/admin/notify/test",{method:"POST"});
  if (r) alert("Pesan tes dikirim. Cek Telegram di HP.");
}
async function activateOrder(email, planId){
  const days = ({"monthly":30,"quarterly":90,"yearly":365})[planId] || 30;
  if (!confirm(`Aktifkan ${email} ${days} hari? Pastikan pembayaran ke DANA sudah masuk.`)) return;
  await api("/api/admin/activate",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email,days})});
  refreshAll();
}
async function xsimulate(ref){
  await api("/api/admin/xendit/simulate",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({merchant_ref:ref})});
  alert("Simulasi dikirim (test mode). Tunggu webhook, lalu Refresh.");
  refreshAll();
}
async function verifyUser(email){
  await api("/api/admin/verify",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email})});
  refreshAll();
}
async function revokeUser(email){
  if (!confirm(`Cabut premium untuk ${email}?`)) return;
  await api("/api/admin/revoke",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email})});
  refreshAll();
}
document.getElementById("token").value="admin123";
refreshAll();
</script></body></html>"""


def _open_smtp(smtp_cfg):
    """Open an SMTP connection. Supports STARTTLS (587) and implicit SSL (465).

    `use_ssl` forces SMTPS; otherwise SSL is assumed on port 465. When `use_tls` is true
    (the default) a STARTTLS upgrade is performed on plain connections.
    """
    host = smtp_cfg["host"]
    port = int(smtp_cfg.get("port", 587) or 587)
    use_ssl = _provider_bool(smtp_cfg.get("use_ssl", port == 465))
    if use_ssl:
        conn = smtplib.SMTP_SSL(host, port, timeout=15)
    else:
        conn = smtplib.SMTP(host, port, timeout=15)
        if smtp_cfg.get("use_tls", True):
            conn.starttls()
    if smtp_cfg.get("username"):
        conn.login(smtp_cfg["username"], smtp_cfg.get("password", ""))
    return conn


def send_email_verification(smtp_cfg, to_email, verify_url):
    """Kirim link verifikasi. Return (ok, error). Jika SMTP tidak dikonfigurasi, anggap terkirim (dev mode)."""
    if not smtp_cfg or not smtp_cfg.get("host"):
        return True, None
    try:
        msg = EmailMessage()
        msg["Subject"] = "Verifikasi akun BeatPlay"
        msg["From"] = smtp_cfg.get("from", "BeatPlay <no-reply@beatplay>")
        msg["To"] = to_email
        msg.set_content(
            "Klik link berikut untuk mengaktifkan akun BeatPlay kamu:\n\n"
            + verify_url
            + "\n\nAbaikan email ini jika kamu tidak mendaftar."
        )
        with _open_smtp(smtp_cfg) as s:
            s.send_message(msg)
        return True, None
    except Exception as exc:  # noqa: BLE001
        return False, str(exc)


def send_email_code(smtp_cfg, to_email, code):
    """Kirim kode verifikasi 6 digit ke email pendaftar. Return (ok, error)."""
    if not smtp_cfg or not smtp_cfg.get("host"):
        return False, "SMTP belum dikonfigurasi"
    try:
        msg = EmailMessage()
        msg["Subject"] = "Kode verifikasi BeatPlay: " + code
        msg["From"] = smtp_cfg.get("from", "BeatPlay <no-reply@beatplay>")
        msg["To"] = to_email
        msg.set_content(
            "Halo,\n\n"
            "Kode verifikasi akun BeatPlay kamu adalah:\n\n"
            "    " + code + "\n\n"
            "Kode ini berlaku 15 menit. Masukkan kode tersebut di aplikasi BeatPlay "
            "untuk mengaktifkan akun.\n\n"
            "Abaikan email ini jika kamu tidak mendaftar."
        )
        with _open_smtp(smtp_cfg) as s:
            s.send_message(msg)
        return True, None
    except Exception as exc:  # noqa: BLE001
        return False, str(exc)


def send_reset_code(smtp_cfg, to_email, code):
    """Kirim kode reset password 6 digit. Return (ok, error)."""
    if not smtp_cfg or not smtp_cfg.get("host"):
        return False, "SMTP belum dikonfigurasi"
    try:
        msg = EmailMessage()
        msg["Subject"] = "Reset password BeatPlay: " + code
        msg["From"] = smtp_cfg.get("from", "BeatPlay <no-reply@beatplay>")
        msg["To"] = to_email
        msg.set_content(
            "Halo,\n\n"
            "Kode untuk mereset password akun BeatPlay kamu adalah:\n\n"
            "    " + code + "\n\n"
            "Kode ini berlaku 15 menit. Masukkan kode tersebut di aplikasi BeatPlay "
            "lalu buat password baru.\n\n"
            "Kalau kamu tidak meminta reset password, abaikan email ini; password lama "
            "tetap berlaku."
        )
        with _open_smtp(smtp_cfg) as s:
            s.send_message(msg)
        return True, None
    except Exception as exc:  # noqa: BLE001
        return False, str(exc)


class PlaylistStore:
    """Playlist publik per akun BeatPlay, plus jumlah like dan berapa kali diputar."""

    MAX_PLAYLISTS = 100
    MAX_SONGS = 500

    def __init__(self, path):
        self.path = path
        self.items = {}
        self.lock = threading.RLock()
        self.load()

    def load(self):
        self.items = load_json(self.path, {})

    def save(self):
        save_json(self.path, self.items)

    @staticmethod
    def _sanitize_song(song):
        if not isinstance(song, dict):
            return None
        sid = str(song.get("id", "")).strip()
        if not sid:
            return None
        try:
            duration = int(song.get("duration") or 0)
        except (TypeError, ValueError):
            duration = 0
        return {
            "id": sid[:64],
            "title": str(song.get("title", ""))[:200],
            "artist": str(song.get("artist", ""))[:200],
            "thumbnail": str(song.get("thumbnail", ""))[:600],
            "duration": max(0, duration),
        }

    def sync(self, owner, playlists):
        """Replace the owner's published playlists with [playlists], keeping likes/plays."""
        owner = str(owner or "").strip().lower()
        if not owner or not isinstance(playlists, list):
            return 0
        with self.lock:
            keep = {}
            for pl in playlists[: self.MAX_PLAYLISTS]:
                if not isinstance(pl, dict):
                    continue
                pid = re.sub(r"[^A-Za-z0-9_-]", "", str(pl.get("id", "")).strip())[:64]
                pid = pid or secrets.token_hex(8)
                songs = [
                    s
                    for s in (
                        self._sanitize_song(x)
                        for x in (pl.get("songs") or [])[: self.MAX_SONGS]
                    )
                    if s
                ]
                existing = self.items.get(pid) or {}
                keep[pid] = {
                    "id": pid,
                    "owner": owner,
                    "name": (str(pl.get("name", "")).strip() or "Playlist")[:80],
                    "cover": str(pl.get("cover", ""))[:600],
                    "songs": songs,
                    "likes": existing.get("likes") if isinstance(existing.get("likes"), list) else [],
                    "plays": int(existing.get("plays", 0) or 0),
                    "createdAt": int(existing.get("createdAt", time.time())),
                    "updatedAt": int(time.time()),
                }
            for pid in [p for p, rec in self.items.items() if rec.get("owner") == owner and p not in keep]:
                del self.items[pid]
            self.items.update(keep)
            self.save()
            return len(keep)

    def by_owner(self, email):
        email = str(email or "").strip().lower()
        return [rec for rec in self.items.values() if rec.get("owner") == email]

    def get(self, pid):
        return self.items.get(str(pid or "").strip())

    def toggle_like(self, pid, email):
        email = str(email or "").strip().lower()
        with self.lock:
            rec = self.get(pid)
            if not rec:
                return None
            likes = rec.setdefault("likes", [])
            if email in likes:
                likes.remove(email)
                liked = False
            else:
                likes.append(email)
                liked = True
            self.save()
            return {"likes": len(likes), "liked": liked}

    def add_play(self, pid):
        with self.lock:
            rec = self.get(pid)
            if not rec:
                return None
            rec["plays"] = int(rec.get("plays", 0) or 0) + 1
            self.save()
            return {"plays": rec["plays"]}

    def public_summary(self, rec, viewer_email=None):
        likes = rec.get("likes") if isinstance(rec.get("likes"), list) else []
        return {
            "id": rec.get("id", ""),
            "name": rec.get("name", ""),
            "cover": rec.get("cover", ""),
            "songCount": len(rec.get("songs") or []),
            "likes": len(likes),
            "liked": bool(viewer_email and viewer_email in likes),
            "plays": int(rec.get("plays", 0) or 0),
            "updatedAt": int(rec.get("updatedAt", 0) or 0),
        }


class PremiumStore:
    """Gmail manual (lifetime) + expiry berbayar (epoch detik)."""

    def __init__(self, manual_path, expiry_path):
        self.manual_path = manual_path
        self.expiry_path = expiry_path
        self.manual = set()
        self.expiry = {}
        self.load()

    def load(self):
        data = load_json(self.manual_path, {"premium_emails": []})
        self.manual = {str(e).strip().lower() for e in data.get("premium_emails", []) if str(e).strip()}
        self.expiry = {str(k).lower(): int(v) for k, v in load_json(self.expiry_path, {}).items()}

    def save(self):
        save_json(self.manual_path, {"premium_emails": sorted(self.manual)})
        save_json(self.expiry_path, self.expiry)

    def status(self, email):
        email = email.strip().lower()
        if email in self.manual:
            return True, None
        exp = self.expiry.get(email, 0)
        if exp > time.time():
            return True, exp
        return False, None

    def extend(self, email, days):
        email = email.strip().lower()
        base = max(time.time(), self.expiry.get(email, 0))
        self.expiry[email] = int(base + days * 86400)
        self.save()
        return self.expiry[email]


class OrderStore:
    def __init__(self, path):
        self.path = path
        self.orders = {}
        self.load()

    def load(self):
        self.orders = load_json(self.path, {})

    def save(self):
        save_json(self.path, self.orders)


class UserStore:
    """Akun BeatPlay (email + password) untuk Premium tanpa ketergantungan YouTube."""

    SCRYPT_N = 2 ** 14
    SCRYPT_R = 8
    SCRYPT_P = 1
    SESSION_TTL = 60 * 60 * 24 * 30  # 30 hari
    CODE_TTL = 15 * 60  # kode verifikasi email berlaku 15 menit
    CODE_MAX_ATTEMPTS = 6
    RESEND_INTERVAL = 60  # detik antar kirim ulang kode

    def __init__(self, users_path, sessions_path):
        self.users_path = users_path
        self.sessions_path = sessions_path
        self.users = {}
        self.sessions = {}
        self.lock = threading.RLock()
        self.load()

    def load(self):
        self.users = load_json(self.users_path, {})
        self.sessions = load_json(self.sessions_path, {})

    def save(self):
        save_json(self.users_path, self.users)
        save_json(self.sessions_path, self.sessions)

    def get(self, email):
        return self.users.get(email.strip().lower())

    # ---- follow code + public profile ----

    def _new_follow_code(self):
        while True:
            code = "".join(secrets.choice(FOLLOW_ALPHABET) for _ in range(FOLLOW_CODE_LEN))
            if not any(u.get("follow_code") == code for u in self.users.values()):
                return code

    def follow_code_of(self, user):
        """Return the user's follow code, assigning one lazily for legacy accounts."""
        if not user:
            return ""
        code = user.get("follow_code")
        if code:
            return code
        with self.lock:
            code = user.get("follow_code") or self._new_follow_code()
            user["follow_code"] = code
            self.save()
        return code

    def by_follow_code(self, code):
        code = "".join(ch for ch in str(code or "").upper() if ch.isalnum())
        if not code:
            return None
        for user in self.users.values():
            if self.follow_code_of(user) == code:
                return user
        return None

    def set_profile(self, email, name=None, avatar=None):
        """Update display name and/or avatar filename. avatar='' clears it."""
        email = email.strip().lower()
        with self.lock:
            user = self.users.get(email)
            if not user:
                return None
            if name is not None:
                user["name"] = name.strip()[:32]
            if avatar is not None:
                user["avatar"] = avatar
            self.follow_code_of(user)
            self.save()
            return user

    def public_profile(self, user):
        code = self.follow_code_of(user)
        out = {
            "follow_code": code,
            "name": user.get("name") or user["email"].split("@")[0],
            "email": user["email"],
        }
        avatar = user.get("avatar") or ""
        if avatar:
            out["avatar"] = avatar
        return out

    def _hash(self, password, salt_hex):
        return hashlib.scrypt(
            password.encode("utf-8"),
            salt=bytes.fromhex(salt_hex),
            n=self.SCRYPT_N,
            r=self.SCRYPT_R,
            p=self.SCRYPT_P,
        ).hex()

    def create(self, email, password):
        """Return (user, error). error is None on success."""
        email = email.strip().lower()
        if not EMAIL_RE.match(email):
            return None, "email tidak valid"
        if len(password) < 8:
            return None, "kata sandi minimal 8 karakter"
        with self.lock:
            if email in self.users:
                return None, "email sudah terdaftar"
            salt = secrets.token_hex(16)
            user = {
                "email": email,
                "salt": salt,
                "password_hash": self._hash(password, salt),
                "verified": False,
                "verify_token": secrets.token_hex(32),
                "created_at": int(time.time()),
                "premium_manual": False,
                "premium_expiry": 0,
                "name": email.split("@")[0][:32],
                "follow_code": self._new_follow_code(),
                "avatar": "",
            }
            self.users[email] = user
            self.save()
            return user, None

    def verify(self, token):
        with self.lock:
            for user in self.users.values():
                if user.get("verify_token") == token:
                    user["verified"] = True
                    user.pop("verify_token", None)
                    self.save()
                    return user
        return None

    def _code_hash(self, email, code, salt):
        return hashlib.sha256(
            ("{}|{}|{}".format(email.strip().lower(), code, salt)).encode("utf-8")
        ).hexdigest()

    def issue_code(self, email):
        """Buat kode 6 digit baru (hash + expiry disimpan). Return kode plaintext atau None."""
        email = email.strip().lower()
        with self.lock:
            user = self.users.get(email)
            if not user:
                return None
            salt = secrets.token_hex(16)
            code = "{:06d}".format(secrets.randbelow(1_000_000))
            now = int(time.time())
            user["verify_code_salt"] = salt
            user["verify_code_hash"] = self._code_hash(email, code, salt)
            user["verify_code_expires"] = now + self.CODE_TTL
            user["verify_code_sent_at"] = now
            user["verify_attempts"] = 0
            self.save()
            return code

    def verify_code(self, email, code):
        """Cek kode 6 digit. Return user jika valid, None jika salah/kedaluwarsa/terlalu banyak percobaan."""
        email = email.strip().lower()
        code = "".join(ch for ch in str(code or "") if ch.isdigit())
        with self.lock:
            user = self.users.get(email)
            if not user or user.get("verified"):
                return None
            if int(user.get("verify_code_expires", 0)) < int(time.time()):
                return None
            if int(user.get("verify_attempts", 0)) >= self.CODE_MAX_ATTEMPTS:
                return None
            user["verify_attempts"] = int(user.get("verify_attempts", 0)) + 1
            expected = user.get("verify_code_hash", "")
            salt = user.get("verify_code_salt", "")
            if len(code) != 6 or not expected or not hmac.compare_digest(
                self._code_hash(email, code, salt), expected
            ):
                self.save()
                return None
            user["verified"] = True
            for key in (
                "verify_code_hash", "verify_code_salt", "verify_code_expires",
                "verify_code_sent_at", "verify_attempts", "verify_token",
            ):
                user.pop(key, None)
            self.save()
            return user

    def resend_allowed(self, email):
        user = self.get(email)
        if not user:
            return True
        return int(time.time()) - int(user.get("verify_code_sent_at", 0)) >= self.RESEND_INTERVAL

    # ---- password reset (independent code namespace from account verification) ----

    def request_reset_code(self, email):
        """Buat kode reset password. Return (code, error)."""
        email = email.strip().lower()
        with self.lock:
            user = self.users.get(email)
            if not user:
                return None, "not_found"
            now = int(time.time())
            if now - int(user.get("reset_code_sent_at", 0)) < self.RESEND_INTERVAL:
                return None, "rate_limited"
            salt = secrets.token_hex(16)
            code = "{:06d}".format(secrets.randbelow(1_000_000))
            user["reset_code_salt"] = salt
            # Domain-separated so an account-verification code can never reset a password.
            user["reset_code_hash"] = self._code_hash(email + "|reset", code, salt)
            user["reset_code_expires"] = now + self.CODE_TTL
            user["reset_code_sent_at"] = now
            user["reset_attempts"] = 0
            self.save()
            return code, None

    def _revoke_sessions_for(self, email):
        email = email.strip().lower()
        for token in [t for t, s in self.sessions.items() if s.get("email") == email]:
            self.sessions.pop(token, None)

    def reset_password(self, email, code, new_password):
        """Verifikasi kode reset lalu ganti password. Return (ok, error)."""
        email = email.strip().lower()
        code = "".join(ch for ch in str(code or "") if ch.isdigit())
        if len(new_password or "") < 8:
            return False, "kata sandi minimal 8 karakter"
        with self.lock:
            user = self.users.get(email)
            if not user:
                return False, "kode salah atau kedaluwarsa"
            now = int(time.time())
            if int(user.get("reset_code_expires", 0)) < now:
                return False, "kode kedaluwarsa, minta kode baru"
            if int(user.get("reset_attempts", 0)) >= self.CODE_MAX_ATTEMPTS:
                return False, "terlalu banyak percobaan, minta kode baru"
            user["reset_attempts"] = int(user.get("reset_attempts", 0)) + 1
            expected = user.get("reset_code_hash", "")
            salt = user.get("reset_code_salt", "")
            if len(code) != 6 or not expected or not hmac.compare_digest(
                self._code_hash(email + "|reset", code, salt), expected
            ):
                self.save()
                return False, "kode salah atau kedaluwarsa"
            new_salt = secrets.token_hex(16)
            user["salt"] = new_salt
            user["password_hash"] = self._hash(new_password, new_salt)
            # Proving email ownership also verifies the account.
            user["verified"] = True
            for key in (
                "reset_code_hash", "reset_code_salt", "reset_code_expires",
                "reset_code_sent_at", "reset_attempts",
                "verify_code_hash", "verify_code_salt", "verify_code_expires",
                "verify_code_sent_at", "verify_attempts", "verify_token",
            ):
                user.pop(key, None)
            self._revoke_sessions_for(email)
            self.save()
            return True, None

    def check_password(self, email, password):
        user = self.get(email)
        if not user:
            # burn a constant amount of time so timing does not reveal email existence
            self._hash(password or "", secrets.token_hex(16))
            return None
        if not user.get("verified"):
            return None
        computed = self._hash(password or "", user.get("salt", ""))
        if not hmac.compare_digest(computed, user.get("password_hash", "")):
            return None
        return user

    def check_password_any(self, email, password):
        """Cek kata sandi tanpa peduli status verifikasi (untuk mengirim ulang kode)."""
        user = self.get(email)
        if not user:
            self._hash(password or "", secrets.token_hex(16))
            return None
        computed = self._hash(password or "", user.get("salt", ""))
        if not hmac.compare_digest(computed, user.get("password_hash", "")):
            return None
        return user

    def create_session(self, email):
        email = email.strip().lower()
        token = secrets.token_hex(32)
        with self.lock:
            self.sessions[token] = {
                "email": email,
                "created_at": int(time.time()),
                "expires_at": int(time.time()) + self.SESSION_TTL,
            }
            self.save()
        return token

    def resolve_session(self, token):
        if not token:
            return None
        with self.lock:
            session = self.sessions.get(token)
            if not session:
                return None
            if int(session.get("expires_at", 0)) < time.time():
                self.sessions.pop(token, None)
                self.save()
                return None
            return self.users.get(session["email"])

    def revoke_session(self, token):
        with self.lock:
            if token in self.sessions:
                self.sessions.pop(token, None)
                self.save()

    def set_verified_and_link_manual(self, email, premium_store):
        """Saat user terverifikasi, link status premium Gmail lama bila ada."""
        email = email.strip().lower()
        with self.lock:
            user = self.users.get(email)
            if not user:
                return
            user["verified"] = True
            user.pop("verify_token", None)
            # migrasi premium lama berbasis Gmail ke akun BeatPlay
            ok, exp = premium_store.status(email)
            if ok:
                if exp:
                    user["premium_expiry"] = int(exp)
                else:
                    user["premium_manual"] = True
            self.save()

    def premium_status(self, email):
        user = self.get(email)
        if not user:
            return False, None
        if user.get("premium_manual"):
            return True, None
        exp = int(user.get("premium_expiry", 0) or 0)
        if exp > time.time():
            return True, exp
        return False, None

    def extend(self, email, days):
        email = email.strip().lower()
        with self.lock:
            user = self.users.get(email)
            if not user:
                return None
            base = max(time.time(), int(user.get("premium_expiry", 0) or 0))
            user["premium_expiry"] = int(base + days * 86400)
            self.save()
            return user["premium_expiry"]

    def set_manual(self, email, active):
        email = email.strip().lower()
        with self.lock:
            user = self.users.get(email)
            if not user:
                return False
            user["premium_manual"] = bool(active)
            self.save()
            return True


class TripayClient:
    def __init__(self, cfg):
        self.cfg = cfg

    @property
    def enabled(self):
        c = self.cfg
        return c.get("mode") in ("sandbox", "production") and bool(c.get("api_key") and c.get("private_key") and c.get("merchant_code"))

    @property
    def base(self):
        return TRIPAY_SANDBOX if self.cfg.get("mode") == "sandbox" else TRIPAY_PROD

    def create(self, method, merchant_ref, amount, name, email, plan_title, expired_ts, callback_url, return_url):
        c = self.cfg
        sig = tripay_signature(c["merchant_code"], merchant_ref, amount, c["private_key"])
        fields = {
            "method": method,
            "merchant_ref": merchant_ref,
            "amount": str(amount),
            "customer_name": name,
            "customer_email": email,
            "order_items[0][sku]": "PREMIUM",
            "order_items[0][name]": plan_title,
            "order_items[0][price]": str(amount),
            "order_items[0][quantity]": "1",
            "expired_time": str(expired_ts),
            "signature": sig,
        }
        if callback_url:
            fields["callback_url"] = callback_url
        if return_url:
            fields["return_url"] = return_url
        req = urllib.request.Request(
            self.base + "/transaction/create",
            data=urllib.parse.urlencode(fields).encode(),
            headers={"Authorization": "Bearer " + c["api_key"]},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode())

    def detail(self, reference):
        req = urllib.request.Request(
            self.base + "/transaction/detail?reference=" + urllib.parse.quote(reference),
            headers={"Authorization": "Bearer " + self.cfg["api_key"]},
            method="GET",
        )
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode())


class XenditClient:
    """Xendit Payments API v3 untuk QRIS. Lihat https://docs.xendit.co/."""

    def __init__(self, cfg):
        self.cfg = cfg or {}

    @property
    def enabled(self):
        return bool(self.cfg.get("secret_key"))

    def _headers(self):
        basic = base64.b64encode((self.cfg["secret_key"] + ":").encode()).decode()
        return {"Authorization": "Basic " + basic, "Content-Type": "application/json",
                "api-version": XENDIT_API_VERSION}

    def _call(self, method, path, payload=None):
        req = urllib.request.Request(
            XENDIT_API + path,
            data=json.dumps(payload).encode() if payload is not None else None,
            headers=self._headers(), method=method)
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                return r.status, json.loads(r.read().decode() or "{}")
        except urllib.error.HTTPError as e:
            try:
                return e.code, json.loads(e.read().decode() or "{}")
            except ValueError:
                return e.code, {"message": "HTTP {}".format(e.code)}

    def create(self, reference_id, amount, email, plan_title):
        minutes = int(self.cfg.get("expiry_minutes", 30))
        payload = {
            "reference_id": reference_id,
            "type": "PAY",
            "country": "ID",
            "currency": "IDR",
            "channel_code": self.cfg.get("channel", "QRIS"),
            "channel_properties": {"expires_at": xendit_expiry_iso(minutes), "qr_string_type": "DYNAMIC"},
            "request_amount": amount,
            "capture_method": "AUTOMATIC",
            "description": plan_title,
            "metadata": {"email": email},
        }
        return self._call("POST", "/v3/payment_requests", payload)

    def get(self, payment_request_id):
        return self._call("GET", "/v3/payment_requests/" + urllib.parse.quote(payment_request_id))

    def simulate(self, payment_request_id, amount):
        return self._call("POST", "/v3/payment_requests/" + urllib.parse.quote(payment_request_id) + "/simulate",
                          {"amount": amount})


class BuatQrisClient:
    """BuatQris QRIS API and webhook configuration."""

    def __init__(self, cfg):
        self.cfg = cfg or {}

    @property
    def mode(self):
        value = str(self.cfg.get("mode", "sandbox")).strip().lower()
        if value in ("sandbox", "test"):
            return "sandbox"
        if value in ("production", "prod", "real", "live"):
            return "production"
        return "invalid"

    @property
    def valid_mode(self):
        return self.mode in ("sandbox", "production")

    @property
    def test_mode(self):
        # Fail closed: an unrecognized mode must never create a live payment.
        return self.mode != "production"

    @property
    def enabled(self):
        return bool(self.cfg.get("account_id") and self.cfg.get("secret_token") and self.cfg.get("signing_secret"))

    def create(self, merchant_ref, amount, plan_title, callback_url):
        """Create one QRIS transaction; secrets are sent only to BuatQris over HTTPS."""
        if not self.valid_mode:
            raise ValueError("BUATQRIS_MODE harus sandbox atau production")
        fields = {
            "action": "api_create_qris",
            "account_id": self.cfg["account_id"],
            "secret_token": self.cfg["secret_token"],
            "amount": str(int(amount)),
            "description": "BeatPlay Premium {} ({})".format(plan_title, merchant_ref),
            "qris_method": self.cfg.get("qris_method", "qris_two"),
            "fee_by": self.cfg.get("fee_by", "user"),
        }
        if callback_url:
            fields["callback_url"] = callback_url
        if self.test_mode:
            fields["test"] = "1"

        req = urllib.request.Request(
            BUATQRIS_API,
            data=urllib.parse.urlencode(fields).encode(),
            headers={
                "Content-Type": "application/x-www-form-urlencoded",
                "User-Agent": "Mozilla/5.0 (compatible; BeatPlayPremium/1.0)",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=25) as r:
                body = r.read().decode()
                return r.status, json.loads(body or "{}")
        except urllib.error.HTTPError as e:
            try:
                return e.code, json.loads(e.read().decode() or "{}")
            except (ValueError, UnicodeDecodeError):
                return e.code, {"message": "HTTP {} dari BuatQris".format(e.code)}

    def check_status(self, transaction_id):
        """Check one transaction; callers must enforce BuatQris's 20s rate limit."""
        fields = {
            "action": "api_check_status",
            "account_id": self.cfg["account_id"],
            "secret_token": self.cfg["secret_token"],
            "transaction_id": str(transaction_id),
        }
        req = urllib.request.Request(
            BUATQRIS_API,
            data=urllib.parse.urlencode(fields).encode(),
            headers={
                "Content-Type": "application/x-www-form-urlencoded",
                "User-Agent": "Mozilla/5.0 (compatible; BeatPlayPremium/1.0)",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=15) as r:
                body = r.read().decode()
                return r.status, json.loads(body or "{}")
        except urllib.error.HTTPError as e:
            try:
                return e.code, json.loads(e.read().decode() or "{}")
            except (ValueError, UnicodeDecodeError):
                return e.code, {"message": "HTTP {} dari BuatQris".format(e.code)}


def resolve_provider(cfg, tripay, xendit, buatqris):
    p = (cfg.get("provider") or "").strip().lower()
    if p in ("buatqris", "tripay", "xendit", "mock", "manual"):
        return p
    if xendit.enabled:
        return "xendit"
    if tripay.enabled:
        return "tripay"
    if buatqris.enabled:
        return "buatqris"
    return "manual"


class Handler(BaseHTTPRequestHandler):
    premium = None
    orders = None
    plans = {"plans": [], "tripay_method": "QRIS2"}
    pay_cfg = {"provider": "mock", "mode": "mock"}
    tripay = None
    xendit = None
    buatqris = None
    payment_lock = threading.RLock()
    provider = "mock"
    admin_token = "admin123"
    public_base = ""
    users = None
    sessions = None
    playlists = None
    smtp_cfg = None
    auth_require_verify = False

    def log_message(self, *a):
        pass

    def _send(self, code, obj, ctype="application/json"):
        body = obj if isinstance(obj, bytes) else (json.dumps(obj).encode() if ctype == "application/json" else obj.encode())
        self.send_response(code)
        self.send_header("Content-Type", ctype + "; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, X-Admin-Token, X-Callback-Signature, X-Callback-Event, x-callback-token, X-BuatQris-Signature, Authorization")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _body_json(self):
        try:
            n = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(n) if n > 0 else b"{}"
            return raw, json.loads(raw.decode() or "{}")
        except Exception:
            return b"{}", None

    def _need_admin(self):
        if self.headers.get("X-Admin-Token") != self.admin_token:
            self._send(401, {"error": "unauthorized"})
            return False
        return True

    def _bearer_token(self):
        header = self.headers.get("Authorization", "")
        if header.startswith("Bearer "):
            return header[7:].strip()
        return ""

    def _auth_user(self):
        token = self._bearer_token()
        if not token:
            return None
        return self.users.resolve_session(token)

    def _abs(self, path):
        """Absolute URL for a served asset, falling back to the request Host."""
        base = (self.public_base or "").rstrip("/")
        if not base:
            host = self.headers.get("Host", "")
            if host:
                base = "https://" + host
        return base + path

    def _provider_configured(self):
        if self.provider == "buatqris":
            return bool(self.buatqris and self.buatqris.enabled and self.buatqris.valid_mode and
                        self.public_base.startswith("https://"))
        if self.provider == "xendit":
            return self.xendit.enabled
        if self.provider == "tripay":
            return self.tripay.enabled
        return True  # mock & manual selalu bisa dipakai

    def _manual_info(self):
        m = dict(self.plans.get("manual", {}))
        m["has_image"] = os.path.exists(os.path.join(HERE, "manual_qris.png"))
        return m

    def _notify_cfg(self):
        return self.pay_cfg.get("notify", {}) if isinstance(self.pay_cfg.get("notify"), dict) else {}

    def _notify(self, text, photo_path=None):
        n = self._notify_cfg()
        if n.get("telegram_bot_token") and n.get("telegram_chat_id"):
            send_telegram(n["telegram_bot_token"], n["telegram_chat_id"], text, photo_path)

    def _proof_photo_path(self, order):
        fn = (order.get("proof") or {}).get("photo")
        if not fn:
            return None
        p = os.path.join(PROOF_DIR, os.path.basename(fn))
        return p if os.path.exists(p) else None

    # ---- GET ----
    def do_OPTIONS(self):
        self._send(200, b"", ctype="text/plain")

    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        qs = urllib.parse.parse_qs(url.query)
        if url.path == "/":
            return self._send(200, ADMIN_HTML, ctype="text/html")
        if url.path == "/api/auth/me":
            user = self._auth_user()
            if not user:
                return self._send(401, {"error": "unauthorized"})
            ok, exp = self.users.premium_status(user["email"])
            out = {"email": user["email"], "verified": bool(user.get("verified")), "premium": ok}
            if exp:
                out["expiry"] = exp
            out.update(self.users.public_profile(user))
            if out.get("avatar"):
                out["avatar_url"] = self._abs("/avatar/" + urllib.parse.quote(out["avatar"]))
            return self._send(200, out)
        if url.path.startswith("/api/user/") and url.path.rstrip("/").endswith("/playlists"):
            code = urllib.parse.unquote(
                url.path[len("/api/user/"): url.path.rstrip("/").rfind("/playlists")]
            ).strip()
            user = self.users.by_follow_code(code)
            if not user:
                return self._send(404, {"error": "kode tidak ditemukan"})
            viewer = self._auth_user()
            viewer_email = (viewer or {}).get("email")
            records = sorted(
                self.playlists.by_owner(user["email"]),
                key=lambda rec: rec.get("updatedAt", 0),
                reverse=True,
            )
            profile = self.users.public_profile(user)
            profile.pop("email", None)
            if profile.get("avatar"):
                profile["avatar_url"] = self._abs("/avatar/" + urllib.parse.quote(profile["avatar"]))
            return self._send(200, {
                "profile": profile,
                "playlists": [self.playlists.public_summary(rec, viewer_email) for rec in records],
            })
        if url.path.startswith("/api/playlist/"):
            pid = urllib.parse.unquote(url.path[len("/api/playlist/"):]).strip().split("/")[0]
            rec = self.playlists.get(pid)
            if not rec:
                return self._send(404, {"error": "playlist tidak ditemukan"})
            viewer = self._auth_user()
            viewer_email = (viewer or {}).get("email")
            owner = self.users.get(rec.get("owner"))
            out = self.playlists.public_summary(rec, viewer_email)
            out["songs"] = rec.get("songs") or []
            owner_profile = self.users.public_profile(owner) if owner else {"name": "Pengguna BeatPlay"}
            owner_profile.pop("email", None)
            out["owner"] = owner_profile
            return self._send(200, out)
        if url.path.startswith("/api/user/"):
            code = urllib.parse.unquote(url.path[len("/api/user/"):]).strip()
            user = self.users.by_follow_code(code)
            if not user:
                return self._send(404, {"error": "kode tidak ditemukan"})
            prof = self.users.public_profile(user)
            prof.pop("email", None)
            ok, _ = self.users.premium_status(user["email"])
            prof["premium"] = ok
            if prof.get("avatar"):
                prof["avatar_url"] = self._abs("/avatar/" + urllib.parse.quote(prof["avatar"]))
            return self._send(200, {"profile": prof})
        if url.path.startswith("/api/find-user"):
            code = (qs.get("code", [""])[0] or "").strip()
            user = self.users.by_follow_code(code)
            if not user:
                return self._send(404, {"error": "kode tidak ditemukan"})
            prof = self.users.public_profile(user)
            prof.pop("email", None)
            if prof.get("avatar"):
                prof["avatar_url"] = self._abs("/avatar/" + urllib.parse.quote(prof["avatar"]))
            return self._send(200, {"profile": prof})
        if url.path.startswith("/avatar/"):
            name = os.path.basename(urllib.parse.unquote(url.path[len("/avatar/"):]))
            p = os.path.join(AVATARS_DIR, name)
            if not name or not os.path.exists(p):
                return self._send(404, {"error": "avatar tidak ada"})
            with open(p, "rb") as f:
                data = f.read()
            self.send_response(200)
            self.send_header("Content-Type", "image/png" if p.lower().endswith(".png") else "image/jpeg")
            self.send_header("Cache-Control", "public, max-age=86400")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        if url.path.startswith("/f/"):
            code = urllib.parse.unquote(url.path[len("/f/"):]).strip()
            user = self.users.by_follow_code(code)
            name = (user.get("name") if user else "") or "Pengguna BeatPlay"
            avatar = (user.get("avatar") if user else "") or ""
            img = ("<img src='/avatar/" + urllib.parse.quote(avatar) + "' style='width:96px;height:96px;border-radius:48px;object-fit:cover'>"
                   if avatar else "")
            html = (
                "<!doctype html><meta charset='utf-8'>"
                "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                "<title>BeatPlay - " + name + "</title>"
                "<body style='margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;"
                "background:#0C0908;color:#F5F0E1;font-family:system-ui,Segoe UI,sans-serif'>"
                "<div style='text-align:center;padding:32px'>" + img +
                "<h1 style='margin:16px 0 4px;font-size:24px'>" + name + "</h1>"
                "<p style='margin:0;color:#A79F92'>Kode BeatPlay <b style='letter-spacing:3px'>" + code + "</b></p>"
                "<p style='margin-top:24px;color:#A79F92;max-width:320px'>Buka aplikasi BeatPlay dan pilih "
                "&ldquo;Ikuti dengan kode&rdquo; untuk mengikuti profil ini.</p>"
                "</div></body>"
            )
            return self._send(200, html, ctype="text/html")
        if url.path == "/api/auth/verify":
            token = (qs.get("token", [""])[0] or "").strip()
            user = self.users.verify(token) if token else None
            if not user:
                return self._send(400, {"error": "token verifikasi tidak valid"})
            self.users.set_verified_and_link_manual(user["email"], self.premium)
            return self._send(200, {"ok": True, "email": user["email"], "verified": True})
        if url.path == "/api/premium":
            email = (qs.get("email", [""])[0] or "").strip().lower()
            if not EMAIL_RE.match(email):
                return self._send(400, {"error": "email tidak valid"})
            user = self.users.get(email)
            if user:
                ok, exp = self.users.premium_status(email)
            else:
                ok, exp = self.premium.status(email)
            out = {"email": email, "premium": ok}
            if exp:
                out["expiry"] = exp
            return self._send(200, out)
        if url.path == "/api/plans":
            out = dict(self.plans)
            out["provider"] = self.provider
            if self.provider == "manual":
                out["manual"] = self._manual_info()
            else:
                out.pop("manual", None)
            return self._send(200, out)
        if url.path == "/api/manual-qris":
            if self.provider != "manual":
                return self._send(404, {"error": "QRIS manual tidak aktif"})
            return self._send(200, self._manual_info())
        if url.path == "/manual-qris.png":
            if self.provider != "manual":
                return self._send(404, {"error": "QRIS manual tidak aktif"})
            img = os.path.join(HERE, "manual_qris.png")
            if not os.path.exists(img):
                return self._send(404, {"error": "belum ada gambar QRIS (taro manual_qris.png di folder server)"})
            with open(img, "rb") as f:
                data = f.read()
            self.send_response(200)
            self.send_header("Content-Type", "image/png")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        if url.path == "/api/orders/status":
            ref = (qs.get("merchant_ref", qs.get("ref", [""]))[0] or "").strip()
            o = self.orders.orders.get(ref)
            if not o:
                return self._send(404, {"error": "order tidak ditemukan"})
            if o["status"] == "UNPAID":
                self._refresh_order(o)
            ok, exp = self.premium.status(o["email"])
            return self._send(200, {"order": o, "premium": ok, "expiry": exp})
        if url.path == "/api/admin/list":
            if not self._need_admin():
                return
            return self._send(200, {
                "manual": sorted(self.premium.manual),
                "expiry": self.premium.expiry,
                "users": [
                    {"email": u["email"], "verified": bool(u.get("verified")),
                     "premium_manual": bool(u.get("premium_manual")),
                     "premium_expiry": int(u.get("premium_expiry", 0) or 0),
                     "created_at": int(u.get("created_at", 0))}
                    for u in sorted(self.users.users.values(), key=lambda x: x.get("created_at", 0), reverse=True)[:50]
                ],
                "provider": self.provider,
                "provider_configured": self._provider_configured(),
                "notify_configured": bool(self._notify_cfg().get("telegram_bot_token") and self._notify_cfg().get("telegram_chat_id")),
                "tripay_mode": self.pay_cfg.get("mode", "mock"),  # kompat lama
                "tripay_configured": self.tripay.enabled,
                "buatqris_mode": self.buatqris.mode,
                "buatqris_configured": self._provider_configured() if self.provider == "buatqris" else self.buatqris.enabled,
            })
        if url.path == "/api/admin/orders":
            if not self._need_admin():
                return
            items = sorted(self.orders.orders.values(), key=lambda o: o.get("created_at", 0), reverse=True)[:20]
            return self._send(200, {"orders": items})
        if url.path == "/api/admin/proof-photo":
            token = self.headers.get("X-Admin-Token") or (qs.get("token", [""])[0] or "")
            if token != self.admin_token:
                return self._send(401, {"error": "unauthorized"})
            ref = (qs.get("merchant_ref", [""])[0] or "").strip()
            o = self.orders.orders.get(ref)
            fn = (o.get("proof") or {}).get("photo") if o else None
            p = os.path.join(PROOF_DIR, os.path.basename(fn or ""))
            if not fn or not os.path.exists(p):
                return self._send(404, {"error": "foto tidak ada"})
            with open(p, "rb") as f:
                data = f.read()
            self.send_response(200)
            self.send_header("Content-Type", "image/png" if p.lower().endswith(".png") else "image/jpeg")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        if url.path == "/api/ads/config":
            return self._send(200, {"adEveryNSongs": 3, "adSeconds": 15,
                                    "adText": "BeatPlay Premium - dengarkan tanpa jeda iklan."})
        return self._send(404, {"error": "not found"})

    # ---- POST ----
    def do_POST(self):
        url = urllib.parse.urlparse(self.path)
        if url.path == "/api/payments/tripay/callback":
            return self._tripay_callback()
        if url.path == "/api/payments/xendit/callback":
            return self._xendit_callback()
        if url.path == "/api/payments/buatqris/callback":
            return self._buatqris_callback()
        if url.path not in ("/api/admin/add", "/api/admin/remove", "/api/admin/revoke",
                            "/api/admin/activate", "/api/admin/orders", "/api/admin/xendit/simulate",
                            "/api/admin/notify/test", "/api/admin/smtp/test", "/api/admin/verify",
                            "/api/orders/mock-pay", "/api/orders/create", "/api/orders/proof",
                            "/api/auth/register", "/api/auth/login", "/api/auth/logout",
                            "/api/auth/verify-code", "/api/auth/resend", "/api/auth/profile",
                            "/api/auth/forgot", "/api/auth/reset",
                            "/api/playlists/sync", "/api/playlist/like", "/api/playlist/play"):
            return self._send(404, {"error": "not found"})
        needs_body = url.path not in ("/api/orders/mock-pay", "/api/auth/logout")
        raw, data = self._body_json()
        if data is None and needs_body:
            return self._send(400, {"error": "body harus JSON"})
        if data is None:
            data = {}

        if url.path == "/api/auth/register":
            email = str(data.get("email", "")).strip().lower()
            password = str(data.get("password", ""))
            verify_mode = str(data.get("verify_mode", "")).strip().lower()
            user, err = self.users.create(email, password)
            smtp_ready = bool(self.smtp_cfg and self.smtp_cfg.get("host"))
            if err:
                # An unfinished registration may request a fresh code with the same password.
                if err == "email sudah terdaftar" and verify_mode == "code":
                    existing = self.users.check_password_any(email, password)
                    if existing and not existing.get("verified") and self.users.resend_allowed(email):
                        code = self.users.issue_code(email)
                        if smtp_ready:
                            sent, send_err = send_email_code(self.smtp_cfg, email, code)
                            if not sent:
                                return self._send(500, {"error": "gagal mengirim email verifikasi: " + str(send_err)})
                            return self._send(200, {"ok": True, "email": email,
                                                    "requires_verification": True, "verification": "code"})
                        return self._send(200, {"ok": True, "email": email,
                                                "requires_verification": True, "verification": "code",
                                                "dev_code": code})
                return self._send(400, {"error": err})
            if verify_mode == "code":
                code = self.users.issue_code(email)
                if smtp_ready:
                    sent, send_err = send_email_code(self.smtp_cfg, email, code)
                    if not sent:
                        return self._send(500, {"error": "gagal mengirim email verifikasi: " + str(send_err)})
                    return self._send(200, {"ok": True, "email": email,
                                            "requires_verification": True, "verification": "code"})
                # SMTP belum dikonfigurasi: kode dikembalikan agar alur verifikasi tetap bisa diuji.
                return self._send(200, {"ok": True, "email": email,
                                        "requires_verification": True, "verification": "code",
                                        "dev_code": code})
            if smtp_ready:
                verify_url = self.public_base.rstrip("/") + "/api/auth/verify?token=" + user["verify_token"]
                sent, send_err = send_email_verification(self.smtp_cfg, email, verify_url)
                if not sent:
                    return self._send(500, {"error": "gagal mengirim email verifikasi: " + str(send_err)})
                return self._send(200, {"ok": True, "email": email, "requires_verification": True})
            # Dev/self-host mode: no SMTP configured, auto-activate + link Gmail-based premium.
            self.users.set_verified_and_link_manual(email, self.premium)
            token = self.users.create_session(email)
            return self._send(200, {"ok": True, "email": email, "token": token,
                                    "requires_verification": False})

        if url.path == "/api/auth/verify-code":
            email = str(data.get("email", "")).strip().lower()
            code = str(data.get("code", "")).strip()
            user = self.users.verify_code(email, code)
            if not user:
                return self._send(400, {"error": "kode verifikasi salah atau sudah kedaluwarsa"})
            self.users.set_verified_and_link_manual(user["email"], self.premium)
            token = self.users.create_session(user["email"])
            return self._send(200, {"ok": True, "email": user["email"], "token": token,
                                    "verified": True})

        if url.path == "/api/auth/forgot":
            email = str(data.get("email", "")).strip().lower()
            # Always answer ok so the endpoint cannot be used to discover registered emails.
            response = {"ok": True, "verification": "code"}
            if not EMAIL_RE.match(email):
                return self._send(200, response)
            code, err = self.users.request_reset_code(email)
            if code is None:
                # Unknown email or still inside the resend cooldown: answer the same way.
                return self._send(200, response)
            if self.smtp_cfg and self.smtp_cfg.get("host"):
                sent, send_err = send_reset_code(self.smtp_cfg, email, code)
                if not sent:
                    return self._send(500, {"error": "gagal mengirim email reset: " + str(send_err)})
                return self._send(200, response)
            # No SMTP configured: expose the code so the flow can be tested.
            response["dev_code"] = code
            return self._send(200, response)

        if url.path == "/api/auth/reset":
            email = str(data.get("email", "")).strip().lower()
            code = str(data.get("code", "")).strip()
            password = str(data.get("password", ""))
            ok, err = self.users.reset_password(email, code, password)
            if not ok:
                return self._send(400, {"error": err or "kode salah atau kedaluwarsa"})
            return self._send(200, {"ok": True, "email": email})

        if url.path == "/api/auth/login":
            email = str(data.get("email", "")).strip().lower()
            password = str(data.get("password", ""))
            user = self.users.check_password(email, password)
            if not user:
                pending = self.users.check_password_any(email, password)
                if pending and not pending.get("verified"):
                    return self._send(403, {"error": "email belum diverifikasi. Masukkan kode verifikasi.",
                                            "verification": "code", "email": email})
                return self._send(401, {"error": "email atau kata sandi salah"})
            token = self.users.create_session(user["email"])
            return self._send(200, {"ok": True, "email": user["email"], "token": token})

        if url.path == "/api/playlists/sync":
            user = self._auth_user()
            if not user:
                return self._send(401, {"error": "unauthorized"})
            count = self.playlists.sync(user["email"], data.get("playlists") or [])
            return self._send(200, {"ok": True, "count": count})

        if url.path == "/api/playlist/like":
            user = self._auth_user()
            if not user:
                return self._send(401, {"error": "unauthorized"})
            result = self.playlists.toggle_like(data.get("id"), user["email"])
            if not result:
                return self._send(404, {"error": "playlist tidak ditemukan"})
            return self._send(200, result)

        if url.path == "/api/playlist/play":
            result = self.playlists.add_play(data.get("id"))
            if not result:
                return self._send(404, {"error": "playlist tidak ditemukan"})
            return self._send(200, result)

        if url.path == "/api/auth/logout":
            token = self._bearer_token()
            if token:
                self.users.revoke_session(token)
            return self._send(200, {"ok": True})

        if url.path == "/api/auth/resend":
            email = str(data.get("email", "")).strip().lower()
            user = self.users.get(email)
            if not user or user.get("verified"):
                # Do not reveal whether the account exists or is already verified.
                return self._send(200, {"ok": True})
            if not self.users.resend_allowed(email):
                return self._send(429, {"error": "tunggu sebentar sebelum meminta kode baru"})
            code = self.users.issue_code(email)
            if self.smtp_cfg and self.smtp_cfg.get("host"):
                sent, send_err = send_email_code(self.smtp_cfg, email, code)
                if not sent:
                    return self._send(500, {"error": "gagal mengirim email: " + str(send_err)})
                return self._send(200, {"ok": True, "verification": "code"})
            return self._send(200, {"ok": True, "verification": "code", "dev_code": code})

        if url.path == "/api/auth/profile":
            user = self._auth_user()
            if not user:
                return self._send(401, {"error": "unauthorized"})
            name = data.get("name", None)
            if name is not None:
                name = str(name).strip()
                if not (1 <= len(name) <= 32):
                    return self._send(400, {"error": "nama harus 1-32 karakter"})
            avatar_name = None
            if data.get("remove_avatar"):
                avatar_name = ""
            raw_b64 = data.get("avatar_base64")
            if raw_b64:
                try:
                    raw = base64.b64decode(str(raw_b64), validate=True)
                except Exception:
                    return self._send(400, {"error": "avatar tidak valid (base64 rusak)"})
                ext = validate_proof_photo(str(data.get("avatar_mime", "")).strip().lower(), raw)
                if not ext:
                    return self._send(400, {"error": "avatar harus JPEG/PNG maks 4MB"})
                os.makedirs(AVATARS_DIR, exist_ok=True)
                digest = hashlib.sha256(user["email"].encode("utf-8")).hexdigest()[:16]
                avatar_name = "u{}.{}".format(digest, ext)
                with open(os.path.join(AVATARS_DIR, avatar_name), "wb") as f:
                    f.write(raw)
            if name is None and avatar_name is None:
                return self._send(400, {"error": "tidak ada perubahan"})
            updated = self.users.set_profile(user["email"], name=name, avatar=avatar_name)
            prof = self.users.public_profile(updated)
            if prof.get("avatar"):
                prof["avatar_url"] = self._abs("/avatar/" + urllib.parse.quote(prof["avatar"]))
            prof["premium"], _ = self.users.premium_status(updated["email"])
            return self._send(200, {"ok": True, "profile": prof})

        if url.path == "/api/orders/create":
            return self._create_order(data)

        if url.path == "/api/orders/proof":
            ref = str(data.get("merchant_ref", "")).strip()
            sender = str(data.get("sender_name", "")).strip()
            note = str(data.get("note", "")).strip()[:300]
            o = self.orders.orders.get(ref)
            if not o:
                return self._send(404, {"error": "order tidak ditemukan"})
            if o.get("provider") != "manual":
                return self._send(400, {"error": "order ini bukan QRIS manual"})
            if o["status"] == "PAID":
                return self._send(400, {"error": "order sudah lunas"})
            if not sender:
                return self._send(400, {"error": "nama pengirim wajib diisi"})
            photo_b64 = data.get("photo_base64") or ""
            photo_mime = data.get("photo_mime") or ""
            raw = None
            if photo_b64:
                try:
                    raw = base64.b64decode(photo_b64, validate=True)
                except Exception:
                    return self._send(400, {"error": "foto tidak valid (base64 rusak)"})
            ext = validate_proof_photo(photo_mime, raw) if raw else None
            if o.get("provider") == "manual" and ext is None:
                if not photo_b64:
                    return self._send(400, {"error": "foto bukti wajib diunggah (JPG/PNG, maks 4MB)"})
                return self._send(400, {"error": "foto tidak valid (harus JPG/PNG asli, maks 4MB)"})
            fn = None
            if ext:
                os.makedirs(PROOF_DIR, exist_ok=True)
                fn = proof_filename(ref, ext)
                with open(os.path.join(PROOF_DIR, fn), "wb") as f:
                    f.write(raw)
            o["status"] = "PENDING"
            o["proof"] = {"sender": sender, "note": note, "at": int(time.time()), "photo": fn}
            self.orders.save()
            self._notify(build_proof_message(o, self._plan(o.get("plan_id", ""))), self._proof_photo_path(o))
            return self._send(200, {"ok": True, "order": o})

        if not self._need_admin():
            return
        if url.path == "/api/admin/add":
            email = str(data.get("email", "")).strip().lower()
            if not EMAIL_RE.match(email):
                return self._send(400, {"error": "email tidak valid"})
            user = self.users.get(email)
            if user:
                self.users.set_manual(email, True)
            else:
                self.premium.manual.add(email)
                self.premium.save()
            return self._send(200, {"ok": True})
        if url.path == "/api/admin/remove":
            email = str(data.get("email", "")).strip().lower()
            user = self.users.get(email)
            if user:
                self.users.set_manual(email, False)
            self.premium.manual.discard(email)
            self.premium.save()
            return self._send(200, {"ok": True})
        if url.path == "/api/admin/revoke":
            email = str(data.get("email", "")).strip().lower()
            user = self.users.get(email)
            if user:
                self.users.set_manual(email, False)
                with self.users.lock:
                    user["premium_expiry"] = 0
                    self.users.save()
            self.premium.expiry.pop(email, None)
            self.premium.manual.discard(email)
            self.premium.save()
            return self._send(200, {"ok": True})
        if url.path == "/api/admin/activate":
            email = str(data.get("email", "")).strip().lower()
            try:
                days = int(data.get("days", 30))
            except (TypeError, ValueError):
                return self._send(400, {"error": "days harus angka"})
            if not EMAIL_RE.match(email) or not (1 <= days <= 730):
                return self._send(400, {"error": "email/days tidak valid"})
            user = self.users.get(email)
            if user:
                exp = self.users.extend(email, days)
            else:
                exp = self.premium.extend(email, days)
            return self._send(200, {"ok": True, "email": email, "expiry": exp})
        if url.path == "/api/admin/verify":
            email = str(data.get("email", "")).strip().lower()
            user = self.users.get(email)
            if not user:
                return self._send(404, {"error": "user tidak ditemukan"})
            self.users.set_verified_and_link_manual(email, self.premium)
            return self._send(200, {"ok": True, "email": email})
        if url.path == "/api/orders/mock-pay":
            if self.provider != "mock":
                return self._send(400, {"error": "hanya tersedia di mode mock"})
            ref = str(data.get("merchant_ref", "")).strip()
            o = self.orders.orders.get(ref)
            if not o:
                return self._send(404, {"error": "order tidak ditemukan"})
            o["status"] = "PAID"
            o["paid_at"] = int(time.time())
            self._activate(o)
            return self._send(200, {"ok": True, "order": o})
        if url.path == "/api/admin/xendit/simulate":
            if self.provider != "xendit" or not self.xendit.enabled:
                return self._send(400, {"error": "provider xendit belum dikonfigurasi"})
            ref = str(data.get("merchant_ref", "")).strip()
            o = self.orders.orders.get(ref)
            if not o or not o.get("reference"):
                return self._send(404, {"error": "order tidak ditemukan"})
            code, resp = self.xendit.simulate(o["reference"], o["amount"])
            if code != 200:
                return self._send(502, {"error": "Xendit simulate gagal: {}".format(resp)})
            return self._send(200, {"ok": True, "resp": resp})
        if url.path == "/api/admin/notify/test":
            n = self._notify_cfg()
            if not (n.get("telegram_bot_token") and n.get("telegram_chat_id")):
                return self._send(400, {"error": "isi notify.telegram_bot_token + telegram_chat_id di tripay_config.json"})
            self._notify("Tes notif BeatPlay: kalau pesan ini masuk ke HP, notif bukti bayar sudah siap.")
            return self._send(200, {"ok": True})
        if url.path == "/api/admin/smtp/test":
            to = str(data.get("to", "")).strip()
            if not EMAIL_RE.match(to):
                return self._send(400, {"error": "email tujuan tidak valid"})
            if not (self.smtp_cfg and self.smtp_cfg.get("host")):
                return self._send(400, {"error": "SMTP belum dikonfigurasi: isi SMTP_HOST/SMTP_USERNAME/SMTP_PASSWORD atau smtp_config.json"})
            code = "{:06d}".format(secrets.randbelow(1000000))
            sent, err = send_email_code(self.smtp_cfg, to, code)
            if not sent:
                return self._send(502, {"error": "gagal mengirim email: {}".format(err)})
            return self._send(200, {"ok": True, "to": to})
        return self._send(404, {"error": "not found"})

    # ---- helpers ----
    def _plan(self, plan_id):
        for p in self.plans.get("plans", []):
            if p.get("id") == plan_id:
                return p
        return None

    def _activate(self, order):
        plan = self._plan(order.get("plan_id", "")) or {}
        days = int(plan.get("days", 30))
        email = order["email"].strip().lower()
        user = self.users.get(email)
        if user:
            exp = self.users.extend(email, days)
        else:
            exp = self.premium.extend(email, days)
        order["expiry"] = exp
        self.orders.save()
        return exp

    def _mark_paid(self, order, extra=None):
        """Tandai lunas + aktifkan + notif (untuk callback auto/mock)."""
        with self.payment_lock:
            if order["status"] != "PAID":
                order["status"] = "PAID"
                order["paid_at"] = int(time.time())
                if extra:
                    order.update(extra)
                self._activate(order)
                self._notify(build_paid_message(order, self._plan(order.get("plan_id", ""))),
                             self._proof_photo_path(order))

    def _refresh_order(self, o, log_pending=True):
        """Sinkron status provider; BuatQris checks are persisted and throttled."""
        try:
            if o.get("provider") == "xendit" and self.xendit.enabled and o.get("reference"):
                code, d = self.xendit.get(o["reference"])
                if code == 200:
                    st = xendit_map_status(d.get("status"))
                    if st != o["status"]:
                        if st == "PAID":
                            self._mark_paid(o)
                        else:
                            o["status"] = st
                            self.orders.save()
            elif o.get("provider") == "tripay" and self.tripay.enabled and o.get("reference"):
                d = self.tripay.detail(o["reference"])
                if d.get("success"):
                    st = (d.get("data") or {}).get("status", o["status"])
                    if st != o["status"]:
                        if st == "PAID":
                            self._mark_paid(o)
                        else:
                            o["status"] = st
                            self.orders.save()
            elif o.get("provider") == "buatqris" and self.buatqris and self.buatqris.enabled and o.get("reference"):
                # Do not ask the currently configured sandbox/live account about
                # an order created in the other mode (or a legacy order with no
                # known mode). The webhook remains valid for either mode.
                if bool(o.get("test_mode", True)) != bool(self.buatqris.test_mode):
                    return

                now = time.time()
                with self.payment_lock:
                    if o.get("status") != "UNPAID":
                        return
                    try:
                        last_check = float(o.get("buatqris_last_status_check_at", 0) or 0)
                    except (TypeError, ValueError):
                        last_check = 0
                    if now - last_check < BUATQRIS_STATUS_MIN_INTERVAL:
                        return
                    # Persist before the network call. This also throttles after
                    # a timeout, process restart, or concurrent status requests.
                    o["buatqris_last_status_check_at"] = now
                    self.orders.save()

                code, response = self.buatqris.check_status(str(o["reference"]))
                data = response.get("data") if isinstance(response, dict) else None
                if code != 200 or not isinstance(response, dict) or not response.get("success"):
                    o["provider_check_error"] = "BuatQris status API gagal (HTTP {})".format(code)
                    print("BuatQris status check failed: HTTP {} or unsuccessful response".format(code), flush=True)
                    self.orders.save()
                    return
                if not isinstance(data, dict) or str(data.get("transaction_id") or "") != str(o["reference"]):
                    o["provider_check_error"] = "BuatQris status transaction_id tidak cocok"
                    print("BuatQris status check rejected: transaction_id mismatch", flush=True)
                    self.orders.save()
                    return

                st = buatqris_map_status(data.get("status"))
                if st == "PAID":
                    try:
                        paid_amount = int(data.get("amount"))
                        expected_amount = int(o.get("amount"))
                        paid_total = int(data.get("total_amount"))
                        expected_total = int(o.get("gateway_total_amount"))
                    except (TypeError, ValueError):
                        o["provider_check_error"] = "BuatQris status amount/total_amount tidak valid"
                        print("BuatQris status check rejected: invalid amount/total_amount", flush=True)
                        self.orders.save()
                        return
                    if paid_amount != expected_amount or paid_total != expected_total:
                        o["provider_check_error"] = "BuatQris status amount/total_amount tidak cocok"
                        print("BuatQris status check rejected: amount/total_amount mismatch", flush=True)
                        self.orders.save()
                        return
                    activated = False
                    with self.payment_lock:
                        if o.get("status") == "UNPAID":
                            self._mark_paid(o, {
                                "buatqris_transaction_id": data["transaction_id"],
                                "buatqris_total_amount": data["total_amount"],
                                "payment_test": bool(o.get("test_mode")),
                            })
                            activated = True
                    print("BuatQris status check accepted: payment.success ({})".format(
                        "premium activated" if activated else "already paid"), flush=True)
                elif st in ("EXPIRED", "FAILED"):
                    with self.payment_lock:
                        if o.get("status") == "UNPAID":
                            o["status"] = st
                            self.orders.save()
                    print("BuatQris status check accepted: {}".format(st.lower()), flush=True)
                else:
                    if o.pop("provider_check_error", None) is not None:
                        self.orders.save()
                    if log_pending:
                        print("BuatQris status check: pending", flush=True)
        except Exception as e:
            o["provider_check_error"] = str(e)[:200]
            self.orders.save()

    def _create_order(self, data):
        email = str(data.get("email", "")).strip().lower()
        if not email:
            user = self._auth_user()
            if not user:
                return self._send(401, {"error": "login diperlukan atau sertakan email di body"})
            email = user["email"].strip().lower()
        plan_id = str(data.get("plan_id", "")).strip()
        name = str(data.get("customer_name", "")).strip() or email.split("@")[0]
        if not EMAIL_RE.match(email):
            return self._send(400, {"error": "email tidak valid"})
        plan = self._plan(plan_id)
        if not plan:
            return self._send(400, {"error": "plan_id tidak dikenal", "plans": [p.get("id") for p in self.plans.get("plans", [])]})
        amount = int(plan["price"])
        merchant_ref = "BP-" + time.strftime("%y%m%d%H%M%S") + "-" + secrets.token_hex(3).upper()
        base_order = {"merchant_ref": merchant_ref, "email": email, "plan_id": plan_id,
                      "amount": amount, "status": "UNPAID", "provider": self.provider,
                      "created_at": int(time.time())}

        if self.provider == "xendit":
            if not self.xendit.enabled:
                return self._send(502, {"error": "Xendit belum dikonfigurasi (isi secret_key di tripay_config.json)"})
            code, r = self.xendit.create(merchant_ref, amount, email, str(plan.get("title", plan_id)))
            if code not in (200, 201):
                return self._send(502, {"error": "Xendit menolak: {}".format(r)})
            qr = xendit_extract_qr(r)
            order = dict(base_order, reference=r.get("payment_request_id"), status=xendit_map_status(r.get("status")),
                         checkout_url=None, qr_string=qr, qr_url=None, pay_code=None,
                         expired_at=int(time.time()) + int(self.xendit.cfg.get("expiry_minutes", 30)) * 60)
        elif self.provider == "tripay":
            if not self.tripay.enabled:
                return self._send(502, {"error": "Tripay belum dikonfigurasi"})
            expired_ts = int(time.time()) + 30 * 60  # QRIS2: 10 mnt - 24 jam
            cb = (self.pay_cfg.get("callback_url") or (self.public_base.rstrip("/") + "/api/payments/tripay/callback") if self.public_base else "")
            ret = self.pay_cfg.get("return_url") or ""
            try:
                r = self.tripay.create(self.plans.get("tripay_method", "QRIS2"), merchant_ref, amount,
                                       name, email, str(plan.get("title", plan_id)), expired_ts, cb, ret)
            except Exception as e:
                return self._send(502, {"error": "Tripay error: {}".format(str(e)[:300])})
            if not r.get("success"):
                return self._send(502, {"error": "Tripay menolak: {}".format(r.get("message", "?"))})
            d = r["data"]
            order = dict(base_order, reference=d.get("reference"), status=d.get("status", "UNPAID"),
                         checkout_url=d.get("checkout_url"), qr_string=d.get("qr_string"),
                         qr_url=d.get("qr_url"), pay_code=d.get("pay_code"), expired_at=expired_ts)
        elif self.provider == "buatqris":
            if not self.buatqris or not self.buatqris.enabled:
                return self._send(502, {"error": "BuatQris belum dikonfigurasi (Account ID, Secret Token, dan Signing Secret harus diisi di environment VPS)"})
            if not self.buatqris.valid_mode:
                return self._send(502, {"error": "BUATQRIS_MODE tidak valid (gunakan sandbox atau production)"})
            if not self.public_base.startswith("https://"):
                return self._send(502, {"error": "PUBLIC_BASE HTTPS belum dikonfigurasi untuk callback BuatQris"})
            callback_url = self.public_base.rstrip("/") + "/api/payments/buatqris/callback"
            try:
                code, r = self.buatqris.create(merchant_ref, amount, str(plan.get("title", plan_id)), callback_url)
            except Exception as e:
                return self._send(502, {"error": "BuatQris error: {}".format(str(e)[:200])})
            if code not in (200, 201) or not isinstance(r, dict) or not r.get("success"):
                message = r.get("message") or r.get("error") or "respons API tidak sukses" if isinstance(r, dict) else "respons API tidak valid"
                return self._send(502, {"error": "BuatQris menolak: {}".format(str(message)[:200])})
            d = r.get("data") or {}
            if not isinstance(d, dict) or not d.get("transaction_id"):
                return self._send(502, {"error": "BuatQris tidak mengembalikan transaction_id"})
            # Never trust a checkout/create response to mark an order paid;
            # payment must be confirmed by signed webhook or authenticated status API.
            provider_status = buatqris_map_status(d.get("status"))
            initial_status = provider_status if provider_status in ("EXPIRED", "FAILED") else "UNPAID"
            try:
                expiry_minutes = max(1, int(self.buatqris.cfg.get("expiry_minutes", 15)))
            except (TypeError, ValueError):
                expiry_minutes = 15
            order = dict(base_order, reference=str(d["transaction_id"]), status=initial_status,
                         checkout_url=d.get("payment_url"), qr_string=d.get("qr_string"),
                         qr_url=d.get("qr_url"), pay_code=None,
                         expired_at=int(time.time()) + expiry_minutes * 60,
                         gateway_total_amount=d.get("total_amount"),
                         test_mode=self.buatqris.test_mode)
        else:
            if self.provider == "mock":
                order = dict(base_order, reference="MOCK-" + merchant_ref,
                             checkout_url=None, qr_string="MOCK-QRIS:" + merchant_ref,
                             qr_url=None, pay_code=None, mock=True,
                             expired_at=int(time.time()) + 30 * 60)
            else:  # manual: bayar ke QRIS statis merchant, kirim bukti, admin aktifkan
                order = dict(base_order, reference="MANUAL-" + merchant_ref,
                             checkout_url=None, qr_string=None,
                             qr_url=None, pay_code=None, manual=True,
                             expired_at=int(time.time()) + 24 * 3600)
        with self.payment_lock:
            self.orders.orders[merchant_ref] = order
            self.orders.save()
        return self._send(200, {"order": order})

    def _tripay_callback(self):
        n = int(self.headers.get("Content-Length", "0") or 0)
        raw = self.rfile.read(n) if n > 0 else b"{}"
        if self.headers.get("X-Callback-Event") != "payment_status":
            return self._send(400, {"success": False, "message": "event tidak dikenal"})
        if not self.tripay.enabled:
            return self._send(400, {"success": False, "message": "Tripay belum dikonfigurasi"})
        if not verify_callback_signature(raw, self.headers.get("X-Callback-Signature"), self.pay_cfg.get("private_key", "")):
            return self._send(400, {"success": False, "message": "signature tidak valid"})
        try:
            data = json.loads(raw.decode())
        except ValueError:
            return self._send(400, {"success": False, "message": "body bukan JSON"})
        if str(data.get("status", "")).upper() != "PAID":
            return self._send(200, {"success": True})  # EXPIRED/FAILED/REFUND: ack saja
        ref = str(data.get("merchant_ref", ""))
        o = self.orders.orders.get(ref)
        if not o:
            return self._send(200, {"success": True})  # ack agar Tripay tidak retry
        if o["status"] != "PAID":
            self._mark_paid(o, {"tripay_reference": data.get("reference")})
        return self._send(200, {"success": True})

    def _xendit_callback(self):
        n = int(self.headers.get("Content-Length", "0") or 0)
        raw = self.rfile.read(n) if n > 0 else b"{}"
        token = self.xendit.cfg.get("webhook_token", "") if self.xendit else ""
        if not token:
            return self._send(400, {"success": False, "message": "webhook_token belum dikonfigurasi"})
        if not hmac.compare_digest(str(self.headers.get("x-callback-token") or ""), token):
            return self._send(401, {"success": False, "message": "token tidak valid"})
        try:
            data = json.loads(raw.decode())
        except ValueError:
            return self._send(400, {"success": False, "message": "body bukan JSON"})
        if data.get("event") == "payment.capture":
            d = data.get("data", {})
            if str(d.get("status", "")).upper() == "SUCCEEDED":
                o = self.orders.orders.get(str(d.get("reference_id", "")))
                if o is None:  # fallback cocokkan payment_request_id
                    for cand in self.orders.orders.values():
                        if cand.get("reference") == d.get("payment_request_id"):
                            o = cand
                            break
                if o is not None and o["status"] != "PAID":
                    self._mark_paid(o, {"xendit_payment_id": d.get("payment_id")})
        return self._send(200, {"success": True})

    def _buatqris_callback(self):
        n = int(self.headers.get("Content-Length", "0") or 0)
        raw = self.rfile.read(n) if n > 0 else b"{}"
        if not self.buatqris or not self.buatqris.enabled:
            print("BuatQris callback rejected: provider not configured", flush=True)
            return self._send(503, {"success": False, "message": "BuatQris webhook belum dikonfigurasi"})
        if not verify_buatqris_signature(raw, self.headers.get("X-BuatQris-Signature", ""),
                                         self.buatqris.cfg.get("signing_secret", "")):
            print("BuatQris callback rejected: invalid signature", flush=True)
            return self._send(401, {"success": False, "message": "signature BuatQris tidak valid"})
        try:
            data = json.loads(raw.decode())
        except (ValueError, UnicodeDecodeError):
            print("BuatQris callback rejected: invalid JSON", flush=True)
            return self._send(400, {"success": False, "message": "body bukan JSON"})
        if not isinstance(data, dict):
            print("BuatQris callback rejected: payload is not an object", flush=True)
            return self._send(400, {"success": False, "message": "payload harus JSON object"})

        event = str(data.get("event") or "")
        transaction_id = str(data.get("transaction_id") or "")
        if not transaction_id:
            print("BuatQris callback ignored: missing transaction_id", flush=True)
            return self._send(200, {"success": True})
        order = next((o for o in self.orders.orders.values()
                      if o.get("provider") == "buatqris" and str(o.get("reference") or "") == transaction_id), None)
        if order is None:
            print("BuatQris callback ignored: transaction does not match an order", flush=True)
            return self._send(200, {"success": True})  # unknown/old transaction: ack, do not retry

        if "is_test" in data and _provider_bool(data.get("is_test")) != bool(order.get("test_mode")):
            print("BuatQris callback ignored: test/live mode mismatch", flush=True)
            return self._send(200, {"success": True, "ignored": True})

        status = str(data.get("status") or "").strip().lower()
        if event == "payment.expired" and status == "expired":
            with self.payment_lock:
                if order.get("status") != "PAID":
                    order["status"] = "EXPIRED"
                    self.orders.save()
            print("BuatQris callback accepted: payment.expired", flush=True)
            return self._send(200, {"success": True})
        if event == "payment.failed" and status == "failed":
            with self.payment_lock:
                if order.get("status") != "PAID":
                    order["status"] = "FAILED"
                    self.orders.save()
            print("BuatQris callback accepted: payment.failed", flush=True)
            return self._send(200, {"success": True})
        if event != "payment.success" or status != "success":
            print("BuatQris callback ignored: unexpected event/status", flush=True)
            return self._send(200, {"success": True})

        try:
            paid_amount = int(data.get("amount"))
            expected_amount = int(order.get("amount"))
            paid_total = int(data.get("total_amount"))
            expected_total = int(order.get("gateway_total_amount", expected_amount))
        except (TypeError, ValueError):
            print("BuatQris callback rejected: invalid amount/total_amount", flush=True)
            return self._send(400, {"success": False, "message": "amount/total_amount callback tidak valid"})
        if paid_amount != expected_amount or paid_total != expected_total:
            print("BuatQris callback rejected: amount/total_amount mismatch", flush=True)
            return self._send(400, {"success": False, "message": "nominal callback tidak cocok dengan order"})

        already_paid = order.get("status") == "PAID"
        if order.get("status") != "PAID":
            self._mark_paid(order, {
                "buatqris_transaction_id": transaction_id,
                "buatqris_total_amount": data.get("total_amount"),
                "payment_test": _provider_bool(data.get("is_test")),
            })
        print("BuatQris callback accepted: payment.success ({})".format(
            "already paid" if already_paid else "premium activated"), flush=True)
        return self._send(200, {"success": True})


def refresh_pending_buatqris_orders(checker):
    """Run one safe sweep; each individual status check has a persisted 20s limit."""
    if not checker.buatqris or not checker.buatqris.enabled:
        return
    with checker.payment_lock:
        pending = [o for o in checker.orders.orders.values()
                   if o.get("provider") == "buatqris" and o.get("status") == "UNPAID"]
    for order in pending:
        checker._refresh_order(order, log_pending=False)


def buatqris_status_worker():
    """Background fallback so payment activation does not depend on an app refresh."""
    checker = object.__new__(Handler)
    while True:
        try:
            if Handler.provider == "buatqris":
                refresh_pending_buatqris_orders(checker)
        except Exception as e:
            print("BuatQris background status checker error: {}".format(str(e)[:200]), flush=True)
        time.sleep(BUATQRIS_STATUS_SCAN_INTERVAL)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--admin-token", default=None)
    ap.add_argument("--store", default=os.path.join(HERE, "premium_emails.json"))
    ap.add_argument("--expiry-store", default=os.path.join(HERE, "premium_expiry.json"))
    ap.add_argument("--orders-store", default=os.path.join(HERE, "orders.json"))
    ap.add_argument("--plans", default=os.path.join(HERE, "plans.json"))
    ap.add_argument("--tripay-config", default=os.path.join(HERE, "tripay_config.json"))
    ap.add_argument("--public-base", default="", help="cth https://pay.domain-anda (untuk callback provider)")
    ap.add_argument("--users-store", default=os.path.join(HERE, "users.json"))
    ap.add_argument("--sessions-store", default=os.path.join(HERE, "sessions.json"))
    args = ap.parse_args()
    Handler.premium = PremiumStore(args.store, args.expiry_store)
    Handler.orders = OrderStore(args.orders_store)
    Handler.users = UserStore(args.users_store, args.sessions_store)
    Handler.playlists = PlaylistStore(os.path.join(HERE, "beatplay_playlists.json"))
    Handler.smtp_cfg = load_json(os.path.join(HERE, "smtp_config.json"), {})
    # SMTP for the email verification code. Credentials can come from smtp_config.json or
    # from the service env file; environment variables win so secrets stay out of the repo.
    #   SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, SMTP_FROM,
    #   SMTP_USE_TLS (STARTTLS, default true), SMTP_USE_SSL (implicit TLS, default when port=465)
    smtp_cfg = dict(Handler.smtp_cfg or {})
    for env_name, key in (
        ("SMTP_HOST", "host"),
        ("SMTP_USERNAME", "username"),
        ("SMTP_PASSWORD", "password"),
        ("SMTP_FROM", "from"),
    ):
        value = os.getenv(env_name)
        if value:
            smtp_cfg[key] = value
    if os.getenv("SMTP_PORT"):
        try:
            smtp_cfg["port"] = int(os.getenv("SMTP_PORT"))
        except ValueError:
            pass
    if os.getenv("SMTP_USE_TLS") is not None:
        smtp_cfg["use_tls"] = _provider_bool(os.getenv("SMTP_USE_TLS"))
    if os.getenv("SMTP_USE_SSL") is not None:
        smtp_cfg["use_ssl"] = _provider_bool(os.getenv("SMTP_USE_SSL"))
    Handler.smtp_cfg = smtp_cfg
    if smtp_cfg.get("host"):
        print("SMTP aktif: kode verifikasi akan dikirim via {}".format(smtp_cfg["host"]), flush=True)
    else:
        print("SMTP nonaktif: /api/auth/register mengembalikan dev_code (bukan kirim email)", flush=True)
    Handler.plans = load_json(args.plans, {"plans": [], "tripay_method": "QRIS2"})
    Handler.pay_cfg = load_json(args.tripay_config, {"mode": "mock"})
    provider_env = os.getenv("PAYMENT_PROVIDER", "").strip().lower()
    if provider_env:
        Handler.pay_cfg["provider"] = provider_env
    Handler.tripay = TripayClient(Handler.pay_cfg)
    Handler.xendit = XenditClient(Handler.pay_cfg.get("xendit", {}))
    buatqris_cfg = dict(Handler.pay_cfg.get("buatqris", {}) or {})
    for env_name, key in (
        ("BUATQRIS_ACCOUNT_ID", "account_id"),
        ("BUATQRIS_SECRET_TOKEN", "secret_token"),
        ("BUATQRIS_SIGNING_SECRET", "signing_secret"),
        ("BUATQRIS_QRIS_METHOD", "qris_method"),
        ("BUATQRIS_FEE_BY", "fee_by"),
        ("BUATQRIS_MODE", "mode"),
        ("BUATQRIS_EXPIRY_MINUTES", "expiry_minutes"),
    ):
        value = os.getenv(env_name, "").strip()
        if value:
            buatqris_cfg[key] = value
    Handler.buatqris = BuatQrisClient(buatqris_cfg)
    Handler.provider = resolve_provider(Handler.pay_cfg, Handler.tripay, Handler.xendit, Handler.buatqris)
    Handler.admin_token = args.admin_token or os.getenv("ADMIN_TOKEN") or "admin123"
    Handler.public_base = args.public_base or os.getenv("PUBLIC_BASE", "")
    # Selalu dengar di semua interface agar bisa diakses dari luar (NSG/ufw yang membatasi).
    srv = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    if Handler.provider == "buatqris" and Handler.buatqris.enabled:
        threading.Thread(target=buatqris_status_worker, name="buatqris-status-checker", daemon=True).start()
        print("BuatQris background status checker enabled (max 1 check/order/20s)", flush=True)
    print("Premium server: http://127.0.0.1:{}/ (provider: {}, configured: {})".format(
        args.port, Handler.provider, Handler._provider_configured(Handler)))
    srv.serve_forever()


if __name__ == "__main__":
    main()
