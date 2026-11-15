<?php
declare(strict_types=1);

/*
 * Resolver unduhan desktop BeatPlay (Windows / Linux).
 *
 * Client-side fetch ke api.github.com kena rate limit 60 req/jam per IP publik,
 * jadi HTML memanggil halaman ini dan PHP yang membaca API (IP server terpisah,
 * dan hasilnya di-cache 10 menit supaya tidak ikut kehabisan kuota).
 *
 * Cache file ditulis di folder script; kalau server mematikan penulisan file,
 * halaman tetap jalan tanpa cache, hanya lebih lambat.
 */

const GITHUB_REPO = 'rzkyads29/beatplay';
const CACHE_FILE = __DIR__ . '/.beatplay-desktop-release.json';
const CACHE_TTL = 600;

function beatplay_http_get(string $url): ?string
{
    if (function_exists('curl_init')) {
        $ch = curl_init($url);
        if ($ch !== false) {
            curl_setopt_array($ch, [
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_FOLLOWLOCATION => true,
                CURLOPT_MAXREDIRS => 5,
                CURLOPT_CONNECTTIMEOUT => 5,
                CURLOPT_TIMEOUT => 10,
                CURLOPT_ENCODING => '',
                CURLOPT_USERAGENT => 'BeatPlay-Web/1.0 (+https://beatplay.wuaze.com)',
                CURLOPT_HTTPHEADER => ['Accept: application/vnd.github+json'],
                CURLOPT_SSL_VERIFYPEER => true,
                CURLOPT_SSL_VERIFYHOST => 2,
            ]);
            $body = curl_exec($ch);
            $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
            curl_close($ch);
            if ($status === 200 && is_string($body) && $body !== '') {
                return $body;
            }
        }
    }

    if (ini_get('allow_url_fopen')) {
        $context = stream_context_create([
            'http' => [
                'method' => 'GET',
                'timeout' => 10,
                'follow_location' => 1,
                'max_redirects' => 5,
                'header' => "User-Agent: BeatPlay-Web/1.0\r\nAccept: application/vnd.github+json\r\n",
            ],
            'ssl' => [
                'verify_peer' => true,
                'verify_peer_name' => true,
            ],
        ]);
        $body = @file_get_contents($url, false, $context);
        if (is_string($body) && $body !== '') {
            return $body;
        }
    }

    return null;
}

/** @return array<string, array{name: string, size: int, url: string}> */
function beatplay_latest_desktop_assets(): array
{
    if (is_file(CACHE_FILE)) {
        $cached = @json_decode((string) file_get_contents(CACHE_FILE), true);
        if (is_array($cached)
            && isset($cached['fetched_at'], $cached['assets'])
            && (time() - (int) $cached['fetched_at']) < CACHE_TTL
        ) {
            return $cached['assets'];
        }
    }

    $assets = [];
    $body = beatplay_http_get('https://api.github.com/repos/' . GITHUB_REPO . '/releases?per_page=10');
    if ($body !== null) {
        $releases = json_decode($body, true);
        if (is_array($releases)) {
            foreach ($releases as $release) {
                if (!is_array($release)
                    || !empty($release['draft'])
                    || !empty($release['prerelease'])
                    || empty($release['assets'])
                ) {
                    continue;
                }
                foreach ($release['assets'] as $asset) {
                    if (!is_array($asset)) {
                        continue;
                    }
                    $name = (string) ($asset['name'] ?? '');
                    $downloadUrl = (string) ($asset['browser_download_url'] ?? '');
                    if ($name === '' || $downloadUrl === '') {
                        continue;
                    }
                    $ext = strtolower(pathinfo($name, PATHINFO_EXTENSION));
                    if (in_array($ext, ['msi', 'deb', 'rpm'], true) && !isset($assets[$ext])) {
                        $assets[$ext] = [
                            'name' => $name,
                            'size' => (int) ($asset['size'] ?? 0),
                            'url' => $downloadUrl,
                        ];
                    }
                }
                if (isset($assets['msi'], $assets['deb'], $assets['rpm'])) {
                    break;
                }
            }
        }
    }

    if ($assets !== []) {
        @file_put_contents(
            CACHE_FILE,
            json_encode(['fetched_at' => time(), 'assets' => $assets], JSON_UNESCAPED_SLASHES),
            LOCK_EX
        );
    }

    return $assets;
}

/** @return array<string, mixed>|null */
function beatplay_pick(array $assets, string $kind): ?array
{
    $ext = $kind === 'linux' ? ['deb', 'rpm'] : [$kind];
    foreach ($ext as $candidate) {
        if (isset($assets[$candidate])) {
            return $assets[$candidate];
        }
    }
    return null;
}

header('Cache-Control: no-store, max-age=0');
header('X-Robots-Tag: noindex, nofollow');

$kind = isset($_GET['kind']) && $_GET['kind'] === 'linux' ? 'linux' : 'windows';
$assets = beatplay_latest_desktop_assets();
$picked = beatplay_pick($assets, $kind);

if ($picked === null) {
    http_response_code(502);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['error' => 'No ' . $kind . ' asset found in the latest GitHub releases.']);
    exit;
}

header('Content-Type: application/json; charset=utf-8');
echo json_encode($picked, JSON_UNESCAPED_SLASHES);
