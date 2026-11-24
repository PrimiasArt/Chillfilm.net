/* ChillFilm — NKI AI Cinema PWA Service Worker (v17 Turbo)
 * - Cache-First (<5ms) for versioned JS/CSS (?v=...) & static icons/assets
 * - Network-First for HTML (/) so new deploys roll out immediately
 * - NEVER intercepts /api/*, HLS streams (.m3u8/.ts/.mp4), or cross-origin media
 */
const CACHE_NAME = 'chillfilm-nki-shell-v26';
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
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith('/api/')) return;
  if (/\.(m3u8|ts|mp4|webm|mkv|aac|srt|vtt)(\?|$)/i.test(url.pathname)) return;

  // Versioned JS/CSS (?v=...) and static icons: Cache-First (<5ms instant load!)
  const isVersionedBundle = /\.(js|css)$/i.test(url.pathname) && url.searchParams.has('v');
  if (isVersionedBundle || url.pathname.startsWith('/assets/')) {
    event.respondWith(
      caches.match(req).then((cached) => {
        if (cached) return cached;
        return fetch(req).then((res) => {
          if (res && res.ok && res.type === 'basic') {
            const clone = res.clone();
            caches.open(CACHE_NAME).then((c) => c.put(req, clone)).catch(() => {});
          }
          return res;
        });
      })
    );
    return;
  }

  // HTML document & unversioned files: network-first with offline cache fallback
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
