/* BeatPlay — interaction layer.
 *
 * Semua efek di sini progresif: kalau JavaScript mati, halaman tetap utuh dan
 * semua teks sudah berisi nilai akhirnya. Tidak ada library, semuanya memakai
 * IntersectionObserver / requestAnimationFrame supaya tetap ringan.
 *
 * Isi: scrollspy navigasi, angka yang menghitung naik, lightbox tangkapan
 * layar, sorot kursor di hero + kemiringan jendela aplikasi, sorot di kartu
 * fitur, menu mobile, dan deteksi platform di halaman unduh.
 */
(() => {
  "use strict";

  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const finePointer = window.matchMedia("(hover: hover) and (pointer: fine)").matches;

  /* ---- scrollspy -------------------------------------------------------- */
  const nav = document.querySelector(".navlinks");
  const links = nav
    ? [...nav.querySelectorAll('a[href*="#"]')].filter((a) => a.hash && document.getElementById(a.hash.slice(1)))
    : [];
  if (links.length) {
    const targets = links.map((a) => ({ a, el: document.getElementById(a.hash.slice(1)) }));
    let queued = false;

    const sync = () => {
      queued = false;
      // Sorot bagian yang sedang berada di pita baca (40% atas layar).
      // Bagian yang tidak punya tautan navigasi tidak akan menyorot apa pun.
      const band = Math.min(window.innerHeight * 0.4, 360);
      let active = null;
      for (const t of targets) {
        const rect = t.el.getBoundingClientRect();
        if (rect.top <= band && rect.bottom > band) { active = t; break; }
      }
      for (const t of targets) {
        const on = t === active;
        t.a.classList.toggle("on", on);
        if (on) t.a.setAttribute("aria-current", "true");
        else t.a.removeAttribute("aria-current");
      }
    };

    const schedule = () => {
      if (queued) return;
      queued = true;
      window.requestAnimationFrame(sync);
    };

    window.addEventListener("scroll", schedule, { passive: true });
    window.addEventListener("resize", schedule, { passive: true });
    sync();
  }

  /* ---- angka menghitung naik ------------------------------------------- */
  const counters = document.querySelectorAll("[data-count]");
  if (counters.length && !reduceMotion) {
    const run = (el) => {
      const target = Number(el.dataset.count);
      if (!Number.isFinite(target)) return;
      const suffix = el.dataset.suffix || "";
      const started = performance.now();
      const duration = 900;
      const step = (now) => {
        const p = Math.min(1, (now - started) / duration);
        const eased = 1 - Math.pow(1 - p, 3);
        el.textContent = Math.round(target * eased) + suffix;
        if (p < 1) window.requestAnimationFrame(step);
        else el.textContent = target + suffix;
      };
      window.requestAnimationFrame(step);
    };

    if ("IntersectionObserver" in window) {
      const io = new IntersectionObserver((entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          io.unobserve(entry.target);
          run(entry.target);
        }
      }, { threshold: 0.7 });
      counters.forEach((el) => io.observe(el));
    } else {
      counters.forEach(run);
    }
  }

  /* ---- lightbox tangkapan layar ---------------------------------------- */
  const zoomables = document.querySelectorAll(".shot img, .phone img");
  if (zoomables.length) {
    const box = document.createElement("div");
    box.className = "lightbox";
    box.hidden = true;
    box.setAttribute("role", "dialog");
    box.setAttribute("aria-modal", "true");
    box.setAttribute("aria-label", "Pratinjau tangkapan layar");
    box.innerHTML =
      '<button class="lb-close" type="button" aria-label="Tutup pratinjau">×</button>' +
      "<figure><img alt=\"\"><figcaption></figcaption></figure>";
    document.body.appendChild(box);

    const preview = box.querySelector("img");
    const caption = box.querySelector("figcaption");
    const closeBtn = box.querySelector(".lb-close");
    let opener = null;

    const open = (source) => {
      const figure = source.closest("figure");
      const label = figure && figure.querySelector("figcaption");
      preview.src = source.currentSrc || source.src;
      preview.alt = source.alt || "";
      caption.textContent = label ? label.textContent.trim() : source.alt || "Tangkapan layar";
      box.hidden = false;
      document.body.style.overflow = "hidden";
      opener = source;
      closeBtn.focus({ preventScroll: true });
    };

    const close = () => {
      box.hidden = true;
      preview.removeAttribute("src");
      document.body.style.overflow = "";
      if (opener) opener.focus({ preventScroll: true });
      opener = null;
    };

    for (const source of zoomables) {
      source.setAttribute("tabindex", "0");
      source.setAttribute("role", "button");
      source.addEventListener("click", () => open(source));
      source.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          open(source);
        }
      });
    }

    box.addEventListener("click", (event) => {
      if (event.target === box) close();
    });
    closeBtn.addEventListener("click", close);
    window.addEventListener("keydown", (event) => {
      if (box.hidden) return;
      if (event.key === "Escape") close();
      else if (event.key === "Tab") {
        event.preventDefault();
        closeBtn.focus();
      }
    });
  }

  /* ---- sorot kursor di hero + kemiringan jendela aplikasi --------------- */
  const hero = document.querySelector(".hero");
  const heroVisual = document.querySelector(".hero-visual");
  const frame = heroVisual && heroVisual.querySelector(".frame");

  const trackPointer = (el, onFrame) => {
    let queued = 0;
    let latest = null;
    el.addEventListener("pointermove", (event) => {
      latest = event;
      if (queued) return;
      queued = window.requestAnimationFrame(() => {
        queued = 0;
        if (latest) onFrame(latest, el.getBoundingClientRect());
      });
    }, { passive: true });
  };

  if (finePointer && !reduceMotion) {
    if (hero) {
      trackPointer(hero, (event, rect) => {
        hero.style.setProperty("--mx", (event.clientX - rect.left).toFixed(0) + "px");
        hero.style.setProperty("--my", (event.clientY - rect.top).toFixed(0) + "px");
      });
    }
    if (heroVisual && frame) {
      trackPointer(heroVisual, (event, rect) => {
        const nx = (event.clientX - rect.left) / rect.width - 0.5;
        const ny = (event.clientY - rect.top) / rect.height - 0.5;
        frame.style.transform =
          "perspective(1000px) rotateY(" + (nx * 3.4).toFixed(2) + "deg) rotateX(" +
          (-ny * 2.4).toFixed(2) + "deg)";
      });
      heroVisual.addEventListener("pointerleave", () => {
        frame.style.transform = "";
      }, { passive: true });
    }
  }

  /* ---- sorot di kartu fitur -------------------------------------------- */
  const features = document.querySelector(".features");
  if (features && finePointer) {
    features.addEventListener("pointermove", (event) => {
      const card = event.target.closest(".feat");
      if (!card) return;
      const rect = card.getBoundingClientRect();
      card.style.setProperty("--fx", (event.clientX - rect.left).toFixed(0) + "px");
      card.style.setProperty("--fy", (event.clientY - rect.top).toFixed(0) + "px");
    }, { passive: true });
  }

  /* ---- menu mobile ------------------------------------------------------ */
  const topbar = document.querySelector(".topbar");
  const toggle = document.querySelector(".navtoggle");
  if (topbar && toggle) {
    const setOpen = (open) => {
      topbar.classList.toggle("open", open);
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
    };
    toggle.addEventListener("click", () => setOpen(!topbar.classList.contains("open")));
    topbar.querySelectorAll(".navlinks a").forEach((a) => a.addEventListener("click", () => setOpen(false)));
    document.addEventListener("click", (event) => {
      if (topbar.classList.contains("open") && !topbar.contains(event.target)) setOpen(false);
    });
    window.addEventListener("keydown", (event) => {
      if (event.key === "Escape") setOpen(false);
    });
    window.addEventListener("resize", () => {
      if (window.innerWidth > 820) setOpen(false);
    }, { passive: true });
  }

  /* ---- deteksi platform di halaman unduh -------------------------------- */
  const tabs = document.querySelectorAll(".tabs button[data-p]");
  // Hormati pilihan eksplisit (?platform=...), abaikan parameter lain seperti
  // ?i=1 yang disisipkan tantangan JavaScript InfinityFree.
  const hasPlatformParam = new URLSearchParams(window.location.search).has("platform");
  if (tabs.length && !hasPlatformParam) {
    const ua = navigator.userAgent;
    const guess = /Android/i.test(ua) ? "android" : /Linux|X11/i.test(ua) ? "linux" : "";
    const target = guess && [...tabs].find((b) => b.dataset.p === guess);
    if (target && !target.classList.contains("on")) target.click();
  }
})();