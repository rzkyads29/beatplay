#!/usr/bin/env python3
"""Render every BeatPlay promo template to a high-resolution PNG.

Usage
-----
    python render_all.py                 # render all templates
    python render_all.py feed story      # render only these
    python render_all.py --scale 2       # override device scale factor
    python render_all.py --out png       # output folder (default: out)

Output goes to ``promo/out/beatplay-<name>-<W>x<H>.png`` where W/H are the
final pixel size (template size multiplied by the device scale factor).

Requires Playwright + Chromium::

    pip install playwright
    python -m playwright install chromium

The console output is ASCII only, so it stays readable on Windows terminals
with a legacy code page (the old render_poster.py crashed printing a check
mark on cp1252).
"""
from __future__ import annotations

import argparse
import asyncio
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent

# name -> (template file, css viewport width, css viewport height)
TEMPLATES: dict[str, tuple[str, int, int]] = {
    "feed": ("feed.html", 1080, 1350),
    "square": ("square.html", 1080, 1080),
    "story": ("story.html", 1080, 1920),
    "banner": ("banner.html", 1600, 900),
    "thumbnail": ("thumbnail.html", 1280, 720),
    "carousel-1": ("carousel-1.html", 1080, 1350),
    "carousel-2": ("carousel-2.html", 1080, 1350),
    "carousel-3": ("carousel-3.html", 1080, 1350),
    "poster": ("poster.html", 1080, 1350),
}


async def render_all(names: list[str], scale: int, out_dir: Path) -> int:
    try:
        from playwright.async_api import async_playwright
    except ImportError:
        print("ERROR: Playwright not installed.")
        print("  pip install playwright")
        print("  python -m playwright install chromium")
        return 2

    out_dir.mkdir(parents=True, exist_ok=True)
    failures = 0

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        try:
            for name in names:
                html, width, height = TEMPLATES[name]
                source = ROOT / html
                if not source.exists():
                    print(f"[SKIP] {name}: {html} not found")
                    failures += 1
                    continue

                context = await browser.new_context(
                    viewport={"width": width, "height": height},
                    device_scale_factor=scale,
                )
                page = await context.new_page()
                try:
                    await page.goto(source.as_uri(), wait_until="load")
                    # Wait for webfonts and local images before capturing.
                    await page.evaluate("document.fonts.ready")
                    await page.wait_for_timeout(350)
                    target = out_dir / f"beatplay-{name}-{width * scale}x{height * scale}.png"
                    await page.screenshot(path=str(target), full_page=False)
                    size_kb = target.stat().st_size / 1024
                    print(f"[OK]   {name:<11} {width}x{height} @{scale}x -> {target.name} ({size_kb:.0f} KB)")
                except Exception as exc:  # noqa: BLE001 - report and keep going
                    failures += 1
                    print(f"[FAIL] {name}: {exc}")
                finally:
                    await context.close()
        finally:
            await browser.close()

    print(f"\nDone. {len(names) - failures}/{len(names)} rendered into {out_dir}")
    return 1 if failures else 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Render BeatPlay promo templates to PNG.")
    parser.add_argument("names", nargs="*", help="template names (default: all)")
    parser.add_argument("--scale", type=int, default=2, help="device scale factor (default: 2)")
    parser.add_argument("--out", default="out", help="output folder relative to promo/ (default: out)")
    args = parser.parse_args(argv)

    names = args.names or list(TEMPLATES)
    unknown = [n for n in names if n not in TEMPLATES]
    if unknown:
        print(f"Unknown template(s): {', '.join(unknown)}")
        print(f"Available: {', '.join(TEMPLATES)}")
        return 2

    out_dir = Path(args.out)
    if not out_dir.is_absolute():
        out_dir = ROOT / out_dir

    return asyncio.run(render_all(names, args.scale, out_dir))


if __name__ == "__main__":
    try:  # keep non-ASCII filenames/errors printable on legacy consoles
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:  # noqa: BLE001
        pass
    raise SystemExit(main(sys.argv[1:]))
