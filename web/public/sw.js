// App-shell cache so the PWA opens offline. API calls are never cached here (sync.js handles data).
const CACHE = 'dailynote-shell-v2';
const SHELL = ['./', 'index.html', 'style.css', 'app.js', 'sync.js', 'shortcuts.js', 'habits.js', 'shifts.js', 'calc.js', 'render.js', 'manifest.webmanifest', 'icon.svg', 'icon-180.png'];

self.addEventListener('install', (e) => { e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('push', (e) => {
  let d = {};
  try { d = e.data ? e.data.json() : {}; } catch (err) { /* plain text */ }
  e.waitUntil(self.registration.showNotification(d.title || 'Daily Note', { body: d.body || '', icon: 'icon-192.png', data: { url: d.url || './#/note' } }));
});
self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  const url = new URL((e.notification.data && e.notification.data.url) || './#/note', self.registration.scope).href;
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((cs) => {
    const c = cs[0];
    if (c) { c.navigate(url).catch(() => {}); return c.focus(); }
    return self.clients.openWindow(url);
  }));
});
// network-first for shell files (so updates show up), fall back to cache when offline
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin || url.pathname.startsWith('/api/')) return;
  e.respondWith(
    fetch(e.request).then((r) => { const copy = r.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); return r; })
      .catch(() => caches.match(e.request).then((m) => m || caches.match('index.html')))
  );
});
