/* BeatPlay — latar partikel putih yang mengapung pelan.
 *
 * Satu canvas tetap di belakang konten (z-index -1). Titik-titik melayang
 * ke atas dengan sedikit goyangan, berkedip halus, dan membungkus di tepi.
 * Biaya render dijaga kecil: jumlah titik mengikuti luas layar, DPR dibatasi
 * 2, animasi berhenti saat tab tidak terlihat, dan prefers-reduced-motion
 * hanya menggambar satu bingkai statis.
 */
(() => {
  "use strict";

  const canvas = document.getElementById("bg-particles");
  if (!canvas || typeof canvas.getContext !== "function") return;

  const ctx = canvas.getContext("2d", { alpha: true });
  if (!ctx) return;

  const motionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
  let dpr = 1;
  let width = 0;
  let height = 0;
  let dots = [];
  let raf = 0;
  let lastTime = 0;
  let running = false;

  const rand = (min, max) => min + Math.random() * (max - min);

  function createDot() {
    // depth 0 = jauh (kecil, redup, lambat), 1 = dekat (besar, terang, cepat)
    const depth = Math.random() ** 1.7;
    return {
      x: Math.random() * width,
      y: Math.random() * height,
      radius: 0.4 + depth * 1.8,
      alpha: 0.18 + depth * 0.62,
      speedY: -(3.5 + depth * 14),           // px per detik, ke atas
      speedX: rand(-3, 3) + depth * 1.8,     // px per detik, sedikit menyamping
      phase: rand(0, Math.PI * 2),
      phaseSpeed: rand(0.35, 1.15),          // radian per detik
      drift: rand(0.25, 0.85),
    };
  }

  function paint(dot, time) {
    const twinkle = 0.6 + 0.4 * Math.sin(dot.phase + time * dot.phaseSpeed);
    const alpha = Math.min(1, dot.alpha * twinkle);
    if (alpha <= 0.01) return;
    ctx.beginPath();
    ctx.arc(dot.x, dot.y, dot.radius, 0, Math.PI * 2);
    ctx.fillStyle = "rgba(255,255,255," + alpha.toFixed(3) + ")";
    ctx.fill();
  }

  function draw(time) {
    ctx.clearRect(0, 0, width, height);
    for (let i = 0; i < dots.length; i++) paint(dots[i], time);
  }

  function step(time) {
    if (!running) return;
    const dt = Math.min(64, time - lastTime) / 1000 || 0.016; // detik
    lastTime = time;

    ctx.clearRect(0, 0, width, height);
    for (let i = 0; i < dots.length; i++) {
      const dot = dots[i];
      dot.y += dot.speedY * dt;
      dot.x += (dot.speedX + Math.sin(time / 2600 + dot.phase) * dot.drift * 6) * dt;

      const pad = 8;
      if (dot.y < -pad) {
        dot.y = height + pad;
        dot.x = Math.random() * width;
      }
      if (dot.x < -pad) dot.x = width + pad;
      else if (dot.x > width + pad) dot.x = -pad;

      paint(dot, time);
    }
    raf = window.requestAnimationFrame(step);
  }

  function measure() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    width = canvas.clientWidth || window.innerWidth;
    height = canvas.clientHeight || window.innerHeight;
    canvas.width = Math.max(1, Math.round(width * dpr));
    canvas.height = Math.max(1, Math.round(height * dpr));
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    // ~1 titik per 12.500 px², dijepit agar tetap ringan di HP dan desktop besar.
    const count = Math.max(40, Math.min(170, Math.round((width * height) / 12500)));
    dots = [];
    for (let i = 0; i < count; i++) dots.push(createDot());
  }

  function start() {
    if (running || motionQuery.matches) return;
    running = true;
    lastTime = window.performance.now();
    raf = window.requestAnimationFrame(step);
  }

  function stop() {
    running = false;
    if (raf) window.cancelAnimationFrame(raf);
    raf = 0;
  }

  function refresh() {
    stop();
    measure();
    draw(lastTime);
    if (!motionQuery.matches) start();
  }

  measure();
  if (motionQuery.matches) {
    draw(0); // satu bingkai statis saja
  } else {
    start();
  }

  let resizeTimer = 0;
  window.addEventListener("resize", () => {
    window.clearTimeout(resizeTimer);
    resizeTimer = window.setTimeout(refresh, 150);
  }, { passive: true });

  document.addEventListener("visibilitychange", () => {
    if (document.hidden) stop();
    else if (!motionQuery.matches) start();
  });

  const onMotionChange = () => {
    if (motionQuery.matches) {
      stop();
      draw(0);
    } else {
      start();
    }
  };
  if (typeof motionQuery.addEventListener === "function") {
    motionQuery.addEventListener("change", onMotionChange);
  } else if (typeof motionQuery.addListener === "function") {
    motionQuery.addListener(onMotionChange);
  }
})();