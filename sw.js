// Minimal service worker for the 2048 PWA (GitHub Pages build).
//
// Network-first: while online it always serves fresh assets, and falls back
// to a runtime cache only when offline. Paths are RELATIVE so the SW works
// under the /2048-reframe-2/ project subpath — they resolve against this
// script's location, which also scopes the worker to that subpath.
//
// Bump CACHE when the shell changes to evict the old runtime cache.
const CACHE = '2048-rf2-v1';
const SHELL = [
  './',
  'index.html',
  'css/style.css',
  'js/main.js',
  'icons/icon-192.png',
  'icons/icon-512.png',
];

self.addEventListener('install', (event) => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(SHELL).catch(() => {}))
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  event.respondWith(
    fetch(req)
      .then((res) => {
        const copy = res.clone();
        caches.open(CACHE).then((cache) => cache.put(req, copy)).catch(() => {});
        return res;
      })
      .catch(() => caches.match(req).then((hit) => hit || caches.match('./')))
  );
});
