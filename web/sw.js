/* ChillFilm — NKI AI Cinema PWA Service Worker (v16)
 * Network-first for HTML/JS/CSS shell so deploys update immediately;
 * NEVER intercepts /api/*, HLS streams (.m3u8/.ts/.mp4), or cross-origin media.
 */
const CACHE_NAME = 'chillfilm-nki-shell-v16';
const SHELL_ASSETS = [
  '/',
  '/index.html',
  '/manifest.json?v=2',
  '/assets/favicon.png?v=2',
  '/assets/icon-192.png?v=2',
  '/assets/icon-512.png?v=2',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(SHELL_ASSETS).catch(() => {}))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);
  // Only handle same-origin static shell requests — NEVER intercept API, streams, or third-party media
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith('/api/')) return;
  if (/\.(m3u8|ts|mp4|webm|mkv|aac|srt|vtt)(\?|$)/i.test(url.pathname)) return;

  // Static icons/assets: stale-while-revalidate
  if (url.pathname.startsWith('/assets/')) {
    event.respondWith(
      caches.match(req).then((cached) => {
        const net = fetch(req).then((res) => {
          if (res && res.ok) {
            const clone = res.clone();
            caches.open(CACHE_NAME).then((c) => c.put(req, clone)).catch(() => {});
          }
          return res;
        }).catch(() => cached);
        return cached || net;
      })
    );
    return;
  }

  // App shell (HTML / JS / CSS / manifest): network-first with offline cache fallback
  event.respondWith(
    fetch(req)
      .then((res) => {
        if (res && res.ok && res.type === 'basic') {
          const clone = res.clone();
          caches.open(CACHE_NAME).then((c) => c.put(req, clone)).catch(() => {});
        }
        return res;
      })
      .catch(() => caches.match(req).then((hit) => hit || caches.match('/index.html')))
  );
});
