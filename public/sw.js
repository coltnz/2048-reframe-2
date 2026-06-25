// Minimal service worker for the 2048 PWA.
//
// Chrome's "Install app" flow wants a service worker with a fetch handler.
// This one is network-first: while online it always serves fresh assets
// (so `npx shadow-cljs watch` hot-reload keeps working), and falls back to
// a runtime cache only when offline — giving an installed app that still
// opens without a connection after its first visit.
//
// Bump CACHE when the shell changes to evict the old runtime cache.
const CACHE = '2048-rf2-v1';
const SHELL = [
  '/',
  '/index.html',
  '/css/style.css',
  '/js/main.js',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
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
      .catch(() => caches.match(req).then((hit) => hit || caches.match('/')))
  );
});
