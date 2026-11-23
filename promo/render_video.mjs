#!/usr/bin/env node
/* render_video.mjs — render animasi BeatPlay menjadi video (mp4/gif) + thumbnail.
 *
 * Animasi punya mode deterministik: window.__render(t) mematok semua animasi CSS
 * ke waktu t, jadi tiap frame diambil tepat pada waktunya — hasilnya mulus, tidak
 * bergantung kecepatan mesin. Skrip ini memakai Chrome DevTools Protocol (tanpa
 * dependency npm) dan ffmpeg untuk encoding.
 *
 * Contoh:
 *   node render_video.mjs                     # 16:9 + 9:16, 30fps, mp4
 *   node render_video.mjs --only 9:16
 *   node render_video.mjs --fps 30 --clean    # HUD disembunyikan
 *   node render_video.mjs --thumbs --poster   # + PNG tiap scene + cover
 *   node render_video.mjs --gif               # + GIF (15fps, 720px)
 *   node render_video.mjs --jpeg              # pipe JPEG (lebih cepat)
 *   node render_video.mjs --dur 6 --fps 12    # potongan pendek untuk uji
 */
import { spawn } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { once } from "node:events";

const HERE = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1"));
const OUT = path.join(HERE, "out");

const CHROME_CANDIDATES = [
  "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
  "/usr/bin/google-chrome", "/usr/bin/chromium", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
];
const FFMPEG = process.env.FFMPEG || "ffmpeg";

/* ---------- argumen ---------- */
const argv = process.argv.slice(2);
const has = (f) => argv.includes(f);
const opt = (f, d) => { const i = argv.indexOf(f); return i >= 0 && argv[i + 1] ? argv[i + 1] : d; };

const FPS = parseInt(opt("--fps", "30"), 10);
const DUR = parseFloat(opt("--dur", "40"));       // durasi animasi (detik)
const HOLD = parseFloat(opt("--hold", "0"));      // tambahan tahan frame akhir
const CLEAN = has("--clean");
const USE_JPEG = has("--jpeg");
const DO_GIF = has("--gif");
const DO_THUMBS = has("--thumbs");
const DO_POSTER = has("--poster");
const ONLY = opt("--only", "");

const VERSIONS = {
  "16:9": { file: "beatplay-promo.html", w: 1920, h: 1080 },
  "9:16": { file: "beatplay-promo-vertical.html", w: 1080, h: 1920 }
};

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const chrome = CHROME_CANDIDATES.find((p) => fs.existsSync(p));
if (!chrome) { console.error("Chrome tidak ditemukan."); process.exit(1); }
fs.mkdirSync(OUT, { recursive: true });

/* ---------- CDP ---------- */
class Session {
  constructor(ws) { this.ws = ws; this.id = 1; this.pending = new Map(); this.listeners = new Map();
    ws.onmessage = (e) => { const m = JSON.parse(e.data);
      if (m.id && this.pending.has(m.id)) { const p = this.pending.get(m.id); this.pending.delete(m.id);
        m.error ? p.reject(new Error(JSON.stringify(m.error))) : p.resolve(m.result); return; }
      (this.listeners.get(m.sessionId + ":" + m.method) || []).forEach((fn) => fn(m.params)); };
  }
  send(method, params = {}, sessionId) {
    const i = this.id++; this.ws.send(JSON.stringify({ id: i, method, params, sessionId }));
    return new Promise((resolve, reject) => this.pending.set(i, { resolve, reject }));
  }
  once(sessionId, method) {
    return new Promise((resolve) => {
      const k = sessionId + ":" + method;
      const fn = (p) => { this.listeners.set(k, (this.listeners.get(k) || []).filter((x) => x !== fn)); resolve(p); };
      this.listeners.set(k, [...(this.listeners.get(k) || []), fn]);
    });
  }
}

async function connect(port) {
  for (let i = 0; i < 120; i++) {
    try { const r = await fetch(`http://127.0.0.1:${port}/json/version`); if (r.ok) return await r.json(); } catch {}
    await sleep(150);
  }
  throw new Error("Chrome tidak siap");
}

function ffmpegWrite(ff, buf) {
  if (ff.stdin.write(buf)) return Promise.resolve();
  return once(ff.stdin, "drain");
}

/* ---------- render satu versi ---------- */
async function renderVersion(name, cfg) {
  const url = `file:///${HERE.replace(/\\/g, "/")}/${cfg.file}?render${CLEAN ? "&clean" : ""}`;
  const base = path.join(OUT, `beatplay-promo-${name.replace(":", "x")}`);
  const mp4 = `${base}.mp4`;
  const frames = Math.round((DUR + HOLD) * FPS);
  const PORT = 9400 + Math.floor(Math.random() * 200);
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), "cdp_rv_"));

  console.log(`\n▶ ${name}  ${cfg.w}x${cfg.h}  ${FPS}fps  ${frames} frame  ->  ${path.basename(mp4)}`);
  const proc = spawn(chrome, ["--headless=new", "--disable-gpu", "--hide-scrollbars",
    "--force-device-scale-factor=1", "--disable-lcd-text", "--no-first-run", "--mute-audio",
    `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, "about:blank"], { stdio: "ignore" });

  try {
    const v = await connect(PORT);
    const ws = new WebSocket(v.webSocketDebuggerUrl);
    await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
    const s = new Session(ws);
    const { targetId } = await s.send("Target.createTarget", { url: "about:blank" });
    const { sessionId } = await s.send("Target.attachToTarget", { targetId, flatten: true });
    await s.send("Page.enable", {}, sessionId);
    await s.send("Runtime.enable", {}, sessionId);
    await s.send("Emulation.setDeviceMetricsOverride",
      { width: cfg.w, height: cfg.h, deviceScaleFactor: 1, mobile: false }, sessionId);

    const evaluate = async (expression) => {
      const r = await s.send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true }, sessionId);
      if (r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails));
      return r.result.value;
    };

    const loaded = s.once(sessionId, "Page.loadEventFired");
    await s.send("Page.navigate", { url }, sessionId);
    await loaded.catch(() => {});
    for (let i = 0; i < 80; i++) { if (await evaluate("!!window.__ready").catch(() => false)) break; await sleep(100); }
    await evaluate("document.fonts.ready.then(() => true)").catch(() => {});
    await sleep(600);
    if (!(await evaluate("typeof window.__render === 'function'"))) throw new Error("window.__render tidak ada");

    /* thumbnail per scene + cover (opsional) */
    if (DO_THUMBS) {
      const scenes = await evaluate("(window.__scenes ? window.__scenes : null)");
      const ranges = scenes || [[0, 4], [4, 9], [9, 14], [14, 19], [19, 24], [24, 29], [29, 34], [34, 40]];
      for (let i = 0; i < ranges.length; i++) {
        const st = Array.isArray(ranges[i]) ? ranges[i][0] : ranges[i].start;
        const en = Array.isArray(ranges[i]) ? ranges[i][1] : ranges[i].end;
        const t = st + (en - st) * 0.72;
        await evaluate(`window.__render(${t})`);
        await sleep(90);
        const shot = await s.send("Page.captureScreenshot", { format: "png" }, sessionId);
        const f = `${base}-s${i + 1}.png`;
        fs.writeFileSync(f, Buffer.from(shot.data, "base64"));
        console.log(`   thumbnail ${path.basename(f)} (t=${t.toFixed(1)}s)`);
      }
    }
    if (DO_POSTER) {
      await evaluate(`window.__render(${Math.max(0, DUR - 3.5)})`);
      await sleep(90);
      const shot = await s.send("Page.captureScreenshot", { format: "png" }, sessionId);
      fs.writeFileSync(`${base}-poster.png`, Buffer.from(shot.data, "base64"));
      console.log(`   poster ${path.basename(base)}-poster.png`);
    }

    /* frame -> ffmpeg */
    const inCodec = USE_JPEG ? ["-vcodec", "mjpeg"] : ["-vcodec", "png"];
    const fmt = USE_JPEG ? { format: "jpeg", quality: 95 } : { format: "png" };
    const ff = spawn(FFMPEG, ["-y", "-hide_banner", "-loglevel", "error",
      "-f", "image2pipe", ...inCodec, "-framerate", String(FPS), "-i", "pipe:0",
      "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p",
      "-movflags", "+faststart", mp4], { stdio: ["pipe", "ignore", "inherit"] });
    ff.on("error", (e) => { throw e; });

    const t0 = Date.now();
    for (let i = 0; i < frames; i++) {
      const t = i / FPS;
      await evaluate(`window.__render(${t})`);
      const shot = await s.send("Page.captureScreenshot", { ...fmt, captureBeyondViewport: false }, sessionId);
      await ffmpegWrite(ff, Buffer.from(shot.data, "base64"));
      if (i % Math.max(1, Math.round(frames / 10)) === 0) {
        const pct = Math.round((i / frames) * 100);
        process.stdout.write(`\r   ${String(pct).padStart(3)}%  frame ${i}/${frames}  ${((Date.now() - t0) / 1000).toFixed(0)}s`);
      }
    }
    ff.stdin.end();
    await once(ff, "close");
    const secs = ((Date.now() - t0) / 1000).toFixed(0);
    console.log(`\r   100%  ${frames} frame  ${secs}s  ->  ${(fs.statSync(mp4).size / 1048576).toFixed(1)} MB`);

    if (DO_GIF) {
      const gif = `${base}.gif`;
      const width = name === "9:16" ? 540 : 960;
      await new Promise((resolve, reject) => {
        const g = spawn(FFMPEG, ["-y", "-hide_banner", "-loglevel", "error", "-i", mp4,
          "-vf", `fps=15,scale=${width}:-1:flags=lanczos,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer`,
          "-loop", "0", gif], { stdio: "inherit" });
        g.on("close", (c) => c === 0 ? resolve() : reject(new Error("gif gagal")));
      });
      console.log(`   gif ${path.basename(gif)} ${(fs.statSync(gif).size / 1048576).toFixed(1)} MB`);
    }

    ws.close();
  } finally {
    proc.kill();
    try { fs.rmSync(profile, { recursive: true, force: true }); } catch {}
  }
}

/* ---------- jalan ---------- */
const targets = ONLY ? [ONLY] : Object.keys(VERSIONS);
for (const name of targets) {
  if (!VERSIONS[name]) { console.error(`Versi tidak dikenal: ${name}`); continue; }
  await renderVersion(name, VERSIONS[name]);
}
console.log("\nSelesai. Hasil ada di " + OUT);
