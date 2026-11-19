// Client data layer: server is the source of truth; localStorage is an offline cache + write queue.
const LS = {
  get(k, d) { try { const v = localStorage.getItem(k); return v === null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* quota / private mode */ } },
};

let queue = LS.get('dn:queue', []); // [{key, method, url, body}]
const revs = {}; // key -> last known server rev
let flushing = false;
const listeners = new Set();
export const onStatus = (fn) => listeners.add(fn);
let online = true;
const emit = () => listeners.forEach((fn) => fn({ pending: queue.length, online }));
let conflictHandler = null;
export const setConflictHandler = (fn) => { conflictHandler = fn; };
let authHandler = null;
export const setAuthHandler = (fn) => { authHandler = fn; };

async function req(method, url, body) {
  const r = await fetch(url, { method, headers: { 'content-type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
  if (r.status === 401) { if (authHandler) authHandler(); throw Object.assign(new Error('login required'), { status: 401 }); }
  if (r.status === 409) throw Object.assign(new Error('conflict'), { status: 409, data: await r.json() });
  if (!r.ok) { const j = await r.json().catch(() => ({})); throw Object.assign(new Error(j.error || 'HTTP ' + r.status), { status: r.status }); }
  return r.json();
}
const cache = (key, v) => LS.set('dn:c:' + key, v);
const cached = (key, d) => LS.get('dn:c:' + key, d);
const pending = (key) => queue.find((q) => q.key === key);
const persist = () => { LS.set('dn:queue', queue); emit(); };

export async function flush() {
  if (flushing) return;
  flushing = true;
  try {
    while (queue.length) {
      const item = queue[0];
      try {
        const res = await req(item.method, item.url, item.body);
        online = true;
        if (res && res.rev) revs[item.key] = res.rev;
        queue = queue.filter((q) => q !== item);
        const next = pending(item.key);
        if (next && next.body && next.body.base !== undefined && res.rev) next.body.base = res.rev;
        persist();
      } catch (e) {
        if (e.status === 409 && conflictHandler) {
          const keepMine = await conflictHandler(item, e.data);
          if (keepMine) { item.body.base = e.data.rev; } else {
            queue = queue.filter((q) => q !== item);
            revs[item.key] = e.data.rev;
            cache(item.key, { ...cached(item.key, {}), text: e.data.text, rev: e.data.rev });
            persist();
            listeners.forEach((fn) => fn({ pending: queue.length, online, reload: item.key }));
          }
          continue;
        }
        if (e.status === 401) { emit(); break; } // keep the queue until logged in again
        if (e.status && e.status < 500 && e.status !== 409) { queue = queue.filter((q) => q !== item); persist(); continue; } // bad request: drop
        online = false; emit(); break; // network/server down: retry later
      }
    }
  } finally { flushing = false; emit(); }
}

function enqueue(key, method, url, body) {
  queue = queue.filter((q) => q.key !== key);
  queue.push({ key, method, url, body });
  persist();
  flush();
}

async function get(key, url) {
  const q = pending(key);
  try {
    const d = await req('GET', url);
    online = true; emit();
    if (d && d.rev) revs[key] = d.rev;
    if (!q) cache(key, d);
    return q ? { ...d, ...q.body, rev: d.rev } : d; // unsent local edits win
  } catch (e) {
    online = false; emit();
    if (q) return { ...cached(key, {}), ...q.body };
    const c = cached(key, null);
    if (c) return c;
    throw e;
  }
}

// ---- notes ----
export const loadNote = async (date) => { const d = await get('note:' + date, `/api/notes/${date}`); return { text: d.text || '', rev: d.rev }; };
export function saveNote(date, text) {
  const key = 'note:' + date;
  cache(key, { text, rev: revs[key] });
  const q = pending(key);
  enqueue(key, 'PUT', `/api/notes/${date}`, { text, base: q ? q.body.base : revs[key] ?? '0' });
}
export async function listNotes() { try { const l = await req('GET', '/api/notes'); cache('notes', l); return l; } catch { return cached('notes', []); } }

// ---- free notes ----
export async function listFree() { try { const l = await req('GET', '/api/free'); cache('free', l); return l; } catch { return cached('free', []); } }
export async function loadFree(id) { return get('free:' + id, `/api/free/${id}`); }
export function saveFree(id, patch) {
  const key = 'free:' + id;
  const q = pending(key);
  const body = { ...(q ? q.body : {}), ...patch };
  if (patch.text !== undefined) body.base = q ? q.body.base : revs[key] ?? '0';
  cache(key, { ...cached(key, {}), ...body });
  enqueue(key, 'PUT', `/api/free/${id}`, body);
}
export async function deleteFree(id) {
  queue = queue.filter((q) => q.key !== 'free:' + id);
  try { await req('DELETE', `/api/free/${id}`); } catch { /* offline: stays on server until retried manually */ }
  persist();
}

// ---- whole-document stores (habits, shifts): last write wins ----
export const loadDoc = (name) => get('doc:' + name, `/api/${name}`);
export function saveDoc(name, doc) { cache('doc:' + name, doc); enqueue('doc:' + name, 'PUT', `/api/${name}`, doc); }

export const sendImport = (bundle) => req('POST', '/api/import', bundle);
export const api = req;

window.addEventListener('online', flush);
setInterval(flush, 15000);
document.addEventListener('visibilitychange', () => { if (!document.hidden) flush(); });
flush();
