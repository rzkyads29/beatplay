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

  /* ---- lightbox tangkapan layar (dengan navigasi) ---------------------- */
  const zoomables = [...document.querySelectorAll(".shot img, .phone img")].filter((el) => el.src);
  if (zoomables.length) {
    const box = document.createElement("div");
    box.className = "lightbox";
    box.hidden = true;
    box.setAttribute("role", "dialog");
    box.setAttribute("aria-modal", "true");
    box.setAttribute("aria-label", "Pratinjau tangkapan layar");
    box.innerHTML =
      '<button class="lb-close" type="button" aria-label="Tutup pratinjau">&times;</button>' +
      '<button class="lb-nav lb-prev" type="button" aria-label="Tangkapan layar sebelumnya">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" ' +
      'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<path d="m15 5-7 7 7 7"/></svg></button>' +
      '<button class="lb-nav lb-next" type="button" aria-label="Tangkapan layar berikutnya">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" ' +
      'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<path d="m9 5 7 7-7 7"/></svg></button>' +
      "<figure><img alt=\"\"><figcaption></figcaption></figure>" +
      '<div class="lb-status"><span class="lb-count" aria-hidden="true"></span></div>';
    document.body.appendChild(box);

    const preview = box.querySelector("img");
    const caption = box.querySelector("figcaption");
    const closeBtn = box.querySelector(".lb-close");
    const prevBtn = box.querySelector(".lb-prev");
    const nextBtn = box.querySelector(".lb-next");
    const countEl = box.querySelector(".lb-count");
    let opener = null;
    let group = [];
    let index = 0;

    // Hanya gambar dalam tab yang sedang aktif, supaya panah berpindah
    // secara berurutan di antara tangkapan layar yang sedang dilihat.
    const visibleGroup = (source) => {
      const pane = source.closest(".pane");
      const scope = pane || document;
      const found = [...scope.querySelectorAll(".shot img, .phone img")].filter((el) => el.src);
      return found.length ? found : [source];
    };

    const render = () => {
      const source = group[index];
      const figure = source.closest("figure");
      const label = figure && figure.querySelector("figcaption");
      preview.src = source.currentSrc || source.src;
      preview.alt = source.alt || "";
      caption.textContent = label ? label.textContent.trim() : source.alt || "Tangkapan layar";
      const many = group.length > 1;
      prevBtn.hidden = !many;
      nextBtn.hidden = !many;
      countEl.textContent = many ? index + 1 + " / " + group.length : "";
    };

    const step = (delta) => {
      if (group.length < 2) return;
      index = (index + delta + group.length) % group.length;
      render();
    };

    const open = (source) => {
      group = visibleGroup(source);
      index = Math.max(0, group.indexOf(source));
      render();
      box.hidden = false;
      document.body.style.overflow = "hidden";
      opener = source;
      closeBtn.focus({ preventScroll: true });
    };

    const close = () => {
      box.hidden = true;
      preview.removeAttribute("src");
      group = [];
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
    prevBtn.addEventListener("click", () => step(-1));
    nextBtn.addEventListener("click", () => step(1));

    // Geser di layar sentuh untuk berpindah tangkapan layar.
    let touchX = null;
    let touchY = null;
    box.addEventListener("pointerdown", (event) => {
      if (event.pointerType === "mouse" || group.length < 2) return;
      touchX = event.clientX;
      touchY = event.clientY;
    }, { passive: true });
    box.addEventListener("pointerup", (event) => {
      if (touchX === null || group.length < 2) return;
      const dx = event.clientX - touchX;
      const dy = event.clientY - touchY;
      touchX = touchY = null;
      if (Math.abs(dx) > 48 && Math.abs(dx) > Math.abs(dy) * 1.4) step(dx < 0 ? 1 : -1);
    }, { passive: true });

    window.addEventListener("keydown", (event) => {
      if (box.hidden) return;
      if (event.key === "Escape") close();
      else if (event.key === "ArrowLeft") { event.preventDefault(); step(-1); }
      else if (event.key === "ArrowRight") { event.preventDefault(); step(1); }
      else if (event.key === "Tab") {
        event.preventDefault();
        closeBtn.focus();
      }
    });
  }

  /* ---- salin checksum --------------------------------------------------- */
  const copyButtons = document.querySelectorAll("[data-copy]");
  if (copyButtons.length) {
    let live = document.querySelector(".sr-live");
    if (!live) {
      live = document.createElement("p");
      live.className = "sr-live";
      live.setAttribute("aria-live", "polite");
      live.hidden = true;
      document.body.appendChild(live);
    }
    const flash = (button, text) => {
      const original = "Salin";
      button.textContent = text;
      button.classList.add("done");
      window.setTimeout(() => {
        button.textContent = original;
        button.classList.remove("done");
      }, 2000);
    };

    copyButtons.forEach((button) => {
      button.addEventListener("click", async () => {
        const value = button.dataset.copy;
        let ok = false;
        try {
          if (navigator.clipboard && window.isSecureContext) {
            await navigator.clipboard.writeText(value);
            ok = true;
          }
        } catch (error) {
          ok = false;
        }
        if (!ok) {
          // Cadangan untuk browser tanpa Clipboard API atau di halaman file://.
          const area = document.createElement("textarea");
          area.value = value;
          area.setAttribute("readonly", "");
          area.style.position = "fixed";
          area.style.top = "-1000px";
          document.body.appendChild(area);
          area.select();
          try { ok = document.execCommand("copy"); } catch (error) { ok = false; }
          area.remove();
        }
        flash(button, ok ? "Tersalin" : "Gagal");
        live.textContent = ok
          ? "Checksum " + value.slice(0, 8) + "… tersalin"
          : "Penyalinan gagal, salin manual";
      });
    });
  }

  /* ---- akordeon faq (satu terbuka) -------------------------------------- */
  const faqItems = [...document.querySelectorAll(".faq-item")];
  if (faqItems.length > 1) {
    faqItems.forEach((item) => {
      item.addEventListener("toggle", () => {
        if (!item.open) return;
        faqItems.forEach((other) => {
          if (other !== item && other.open) other.open = false;
        });
      });
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

  /* ---- deteksi platform di halaman unduh -------------------------------
   * Ditangani di download.html (inline, sebelum render pertama). Logika hanya
   * boleh punya satu sumber supaya tidak balapan. */
})();