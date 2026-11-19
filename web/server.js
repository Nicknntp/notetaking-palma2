'use strict';
// DailyNote web server. No dependencies. Data = plain files under DATA_DIR.
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const push = require('./push');
const { CalDav } = require('./caldav');

const PUB = path.join(__dirname, 'public');
const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.webmanifest': 'application/manifest+json',
  '.png': 'image/png', '.svg': 'image/svg+xml', '.ico': 'image/x-icon',
};
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const ID_RE = /^[\w-]{1,64}$/;
const MAX_BODY = 20 * 1024 * 1024;

function createServer(dataDir, opts = {}) {
  const DATA = path.resolve(dataDir);
  const fetchImpl = opts.fetch || fetch;
  const dirs = { notes: path.join(DATA, 'notes'), free: path.join(DATA, 'free'), backups: path.join(DATA, 'backups') };
  Object.values(dirs).forEach((d) => fs.mkdirSync(d, { recursive: true }));

  const habitsFile = path.join(DATA, 'habits.json');
  const shiftsFile = path.join(DATA, 'shifts.json');

  const writeAtomic = (file, str) => {
    const tmp = file + '.tmp' + process.pid;
    fs.writeFileSync(tmp, str);
    fs.renameSync(tmp, file);
  };
  const readText = (file) => (fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : null);
  const revOf = (file) => (fs.existsSync(file) ? String(fs.statSync(file).mtimeMs) : '0');
  const readJson = (file, def) => { try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return def; } };

  const noteFile = (d) => path.join(dirs.notes, d + '.txt');
  const freeFile = (id, ext = 'txt') => path.join(dirs.free, id + '.' + ext);

  // ---- bundle (export / import / backup) ----
  function makeBundle() {
    const notes = {};
    for (const f of fs.readdirSync(dirs.notes)) if (f.endsWith('.txt')) notes[f.slice(0, -4)] = fs.readFileSync(path.join(dirs.notes, f), 'utf8');
    const free = {};
    for (const f of fs.readdirSync(dirs.free)) {
      if (!f.endsWith('.txt')) continue;
      const id = f.slice(0, -4);
      free[id] = {
        text: fs.readFileSync(freeFile(id), 'utf8'),
        pinned: fs.existsSync(freeFile(id, 'pin')),
        emoji: (readText(freeFile(id, 'icon')) || '').trim(),
      };
    }
    return { format: 'dailynote-bundle', version: 1, notes, free, habits: readJson(habitsFile, { habits: [] }), shifts: readJson(shiftsFile, null) };
  }
  function putFree(id, { text, pinned, emoji }) {
    if (text !== undefined) writeAtomic(freeFile(id), text);
    if (pinned !== undefined) { if (pinned) fs.writeFileSync(freeFile(id, 'pin'), '1'); else fs.rmSync(freeFile(id, 'pin'), { force: true }); }
    if (emoji !== undefined) { if (emoji) fs.writeFileSync(freeFile(id, 'icon'), emoji); else fs.rmSync(freeFile(id, 'icon'), { force: true }); }
  }
  function applyBundle(b) {
    if (!b || typeof b !== 'object') throw Object.assign(new Error('bad bundle'), { status: 400 });
    let n = 0;
    for (const [d, t] of Object.entries(b.notes || {})) if (DATE_RE.test(d) && typeof t === 'string') { writeAtomic(noteFile(d), t); n++; }
    for (const [id, f] of Object.entries(b.free || {})) if (ID_RE.test(id) && f && typeof f.text === 'string') { putFree(id, { text: f.text, pinned: !!f.pinned, emoji: f.emoji || '' }); n++; }
    if (b.habits && Array.isArray(b.habits.habits)) { writeAtomic(habitsFile, JSON.stringify(b.habits)); n++; }
    if (b.shifts && typeof b.shifts === 'object') { writeAtomic(shiftsFile, JSON.stringify(b.shifts)); n++; }
    return n;
  }
  function backup() {
    const name = new Date().toISOString().slice(0, 10) + '.json';
    const file = path.join(dirs.backups, name);
    if (fs.existsSync(file)) return;
    writeAtomic(file, JSON.stringify(makeBundle()));
    const all = fs.readdirSync(dirs.backups).filter((f) => f.endsWith('.json')).sort();
    all.slice(0, Math.max(0, all.length - 14)).forEach((f) => fs.rmSync(path.join(dirs.backups, f), { force: true }));
  }

  // ---- http plumbing ----
  const send = (res, code, obj) => {
    const body = JSON.stringify(obj);
    res.writeHead(code, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
    res.end(body);
  };
  const readBody = (req) => new Promise((resolve, reject) => {
    let size = 0; const chunks = [];
    req.on('data', (c) => { size += c.length; if (size > MAX_BODY) { reject(Object.assign(new Error('too large'), { status: 413 })); req.destroy(); } else chunks.push(c); });
    req.on('end', () => {
      if (!chunks.length) return resolve({});
      try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8'))); } catch { reject(Object.assign(new Error('bad json'), { status: 400 })); }
    });
    req.on('error', reject);
  });

  // ---- config, auth ----
  const configFile = path.join(DATA, 'config.json'); // user-edited: { password, caldav: { url, username, password } }
  const pushFile = path.join(DATA, 'push.json'); // server-managed: vapid keys, subscriptions, reminder times
  const config = () => readJson(configFile, {});
  const getPassword = () => opts.password ?? process.env.DN_PASSWORD ?? config().password ?? '';
  const sessionToken = (pw) => crypto.createHash('sha256').update('dailynote-session:' + pw).digest('hex');
  const safeEq = (a, b) => { const x = Buffer.from(String(a)), y = Buffer.from(String(b)); return x.length === y.length && crypto.timingSafeEqual(x, y); };
  const cookieOf = (req, name) => (req.headers.cookie || '').split(/;\s*/).map((c) => c.split('=')).find((c) => c[0] === name)?.[1];
  const authed = (req) => { const pw = getPassword(); return !pw || safeEq(cookieOf(req, 'dn_auth') || '', sessionToken(pw)); };
  let failures = 0;

  // ---- push ----
  const pushState = () => {
    const s = readJson(pushFile, {});
    if (!s.vapid) s.vapid = push.generateVapid();
    s.subs ||= []; s.reminder ||= { enabled: true, times: ['21:00'] };
    return s;
  };
  const savePush = (s) => writeAtomic(pushFile, JSON.stringify(s));
  async function pushAll(payload) {
    const s = pushState();
    const alive = [];
    for (const sub of s.subs) {
      try { const st = await push.send(sub, payload, s.vapid, fetchImpl); if (st !== 404 && st !== 410) alive.push(sub); } catch { alive.push(sub); }
    }
    if (alive.length !== s.subs.length) { s.subs = alive; }
    savePush(s);
    return alive.length;
  }
  const sentMarks = new Set();
  /** Call periodically with the current Date; sends the daily-note reminder at configured HH:MM (server local time). */
  async function tick(now = new Date()) {
    const s = pushState();
    if (!s.reminder.enabled || !s.subs.length) return 0;
    const hhmm = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
    const key = `${now.toDateString()} ${hhmm}`;
    if (!s.reminder.times.includes(hhmm) || sentMarks.has(key)) return 0;
    sentMarks.add(key);
    if (sentMarks.size > 50) sentMarks.delete(sentMarks.values().next().value);
    return pushAll({ title: 'ถึงเวลาจดบันทึกแล้ว', body: 'แตะเพื่อเปิดหน้าของวันนี้', url: './#/note' });
  }

  // ---- calendar ----
  let dav = null, davKey = '';
  const caldav = () => {
    const c = config().caldav;
    if (!c || !c.url || !c.username || !c.password) throw Object.assign(new Error('ยังไม่ได้ตั้งค่า CalDAV ใน data/config.json'), { status: 501 });
    const key = JSON.stringify(c);
    if (key !== davKey) { dav = new CalDav(c, fetchImpl); davKey = key; }
    return dav;
  };

  async function api(req, res, url) {
    const parts = url.pathname.split('/').filter(Boolean).slice(1); // after "api"
    const m = req.method;
    const [a, b] = parts;

    if (a === 'auth' && m === 'GET') return send(res, 200, { required: !!getPassword(), ok: authed(req) });
    if (a === 'login' && m === 'POST') {
      const pw = getPassword();
      const { password } = await readBody(req);
      if (!pw) return send(res, 200, { ok: true });
      if (failures >= 5) { await new Promise((r) => setTimeout(r, Math.min(failures, 10) * 500)); }
      if (typeof password !== 'string' || !safeEq(password, pw)) { failures++; return send(res, 401, { error: 'wrong password' }); }
      failures = 0;
      const secure = req.socket.encrypted || req.headers['x-forwarded-proto'] === 'https';
      res.setHeader('set-cookie', `dn_auth=${sessionToken(pw)}; HttpOnly; SameSite=Lax; Path=/; Max-Age=31536000${secure ? '; Secure' : ''}`);
      return send(res, 200, { ok: true });
    }
    if (a === 'logout' && m === 'POST') { res.setHeader('set-cookie', 'dn_auth=; HttpOnly; Path=/; Max-Age=0'); return send(res, 200, { ok: true }); }
    if (!authed(req)) return send(res, 401, { error: 'login required' });

    if (a === 'push') {
      const s = pushState();
      if (b === 'info' && m === 'GET') { savePush(s); return send(res, 200, { publicKey: s.vapid.publicKey, reminder: s.reminder, subscriptions: s.subs.length }); }
      if (b === 'subscribe' && m === 'POST') {
        const { subscription: sub } = await readBody(req);
        if (!sub || typeof sub.endpoint !== 'string' || !/^https:\/\//.test(sub.endpoint) || !sub.keys?.p256dh || !sub.keys?.auth) return send(res, 400, { error: 'bad subscription' });
        s.subs = s.subs.filter((x) => x.endpoint !== sub.endpoint).concat({ endpoint: sub.endpoint, keys: { p256dh: sub.keys.p256dh, auth: sub.keys.auth } });
        savePush(s);
        return send(res, 200, { subscriptions: s.subs.length });
      }
      if (b === 'unsubscribe' && m === 'POST') { const { endpoint } = await readBody(req); s.subs = s.subs.filter((x) => x.endpoint !== endpoint); savePush(s); return send(res, 200, { subscriptions: s.subs.length }); }
      if (b === 'reminder' && m === 'PUT') {
        const { enabled, times } = await readBody(req);
        const ok = Array.isArray(times) && times.every((t) => /^([01]\d|2[0-3]):[0-5]\d$/.test(t));
        if (!ok) return send(res, 400, { error: 'times must be HH:MM[]' });
        s.reminder = { enabled: !!enabled, times: [...new Set(times)].sort() };
        savePush(s);
        return send(res, 200, s.reminder);
      }
      if (b === 'test' && m === 'POST') return send(res, 200, { sent: await pushAll({ title: 'Daily Note', body: 'ทดสอบการแจ้งเตือน', url: './#/note' }) });
    }

    if (a === 'cal') {
      try {
        const c = caldav();
        if (b === 'calendars' && m === 'GET') return send(res, 200, await c.calendars(true));
        if (b === 'events' && m === 'GET') {
          const from = url.searchParams.get('from'), to = url.searchParams.get('to');
          if (!from || !to || isNaN(Date.parse(from)) || isNaN(Date.parse(to))) return send(res, 400, { error: 'from/to required' });
          return send(res, 200, await c.events(from, to));
        }
        if (b === 'events' && m === 'PUT') {
          const ev = await readBody(req);
          if (typeof ev.title !== 'string' || !ev.start || (!ev.href && !ev.calendar)) return send(res, 400, { error: 'title/start/calendar required' });
          return send(res, 200, await c.save(ev));
        }
        if (b === 'events' && m === 'DELETE') { await c.remove(url.searchParams.get('href')); return send(res, 200, { ok: true }); }
      } catch (e) { return send(res, e.status || 502, { error: e.message }); }
    }

    if (a === 'notes') {
      if (!b && m === 'GET') {
        const list = fs.readdirSync(dirs.notes).filter((f) => f.endsWith('.txt') && DATE_RE.test(f.slice(0, -4))).map((f) => f.slice(0, -4)).sort().reverse()
          .map((date) => ({ date, preview: (readText(noteFile(date)) || '').split('\n').find((l) => l.trim()) || '' }));
        return send(res, 200, list);
      }
      if (!DATE_RE.test(b || '')) return send(res, 400, { error: 'bad date' });
      const file = noteFile(b);
      if (m === 'GET') return send(res, 200, { text: readText(file) || '', rev: revOf(file) });
      if (m === 'PUT') {
        const { text, base } = await readBody(req);
        if (typeof text !== 'string') return send(res, 400, { error: 'text required' });
        const cur = readText(file) || '';
        if (base !== undefined && base !== revOf(file) && cur !== text) return send(res, 409, { text: cur, rev: revOf(file) });
        if (text === '') fs.rmSync(file, { force: true }); else writeAtomic(file, text);
        return send(res, 200, { rev: revOf(file) });
      }
    }

    if (a === 'free') {
      if (!b && m === 'GET') {
        const list = fs.readdirSync(dirs.free).filter((f) => f.endsWith('.txt')).map((f) => {
          const id = f.slice(0, -4);
          const text = readText(freeFile(id)) || '';
          return { id, title: text.split('\n').find((l) => l.trim()) || '', pinned: fs.existsSync(freeFile(id, 'pin')), emoji: (readText(freeFile(id, 'icon')) || '').trim(), mtime: fs.statSync(freeFile(id)).mtimeMs };
        }).sort((x, y) => (y.pinned - x.pinned) || (y.mtime - x.mtime));
        return send(res, 200, list);
      }
      if (!ID_RE.test(b || '')) return send(res, 400, { error: 'bad id' });
      const file = freeFile(b);
      if (m === 'GET') {
        if (!fs.existsSync(file)) return send(res, 404, { error: 'not found' });
        return send(res, 200, { text: readText(file), rev: revOf(file), pinned: fs.existsSync(freeFile(b, 'pin')), emoji: (readText(freeFile(b, 'icon')) || '').trim() });
      }
      if (m === 'PUT') {
        const body = await readBody(req);
        if (body.text !== undefined) {
          if (typeof body.text !== 'string') return send(res, 400, { error: 'text' });
          const cur = readText(file);
          if (body.base !== undefined && cur !== null && body.base !== revOf(file) && cur !== body.text) return send(res, 409, { text: cur, rev: revOf(file) });
        }
        putFree(b, body);
        return send(res, 200, { rev: revOf(file) });
      }
      if (m === 'DELETE') { ['txt', 'pin', 'icon'].forEach((e) => fs.rmSync(freeFile(b, e), { force: true })); return send(res, 200, { ok: true }); }
    }

    if (a === 'habits' || a === 'shifts') {
      const file = a === 'habits' ? habitsFile : shiftsFile;
      if (m === 'GET') return send(res, 200, readJson(file, a === 'habits' ? { habits: [] } : null) ?? {});
      if (m === 'PUT') {
        const body = await readBody(req);
        if (a === 'habits' && !Array.isArray(body.habits)) return send(res, 400, { error: 'habits[] required' });
        writeAtomic(file, JSON.stringify(body));
        return send(res, 200, { ok: true });
      }
    }

    if (a === 'export' && m === 'GET') {
      res.writeHead(200, { 'content-type': 'application/json; charset=utf-8', 'content-disposition': `attachment; filename="dailynote-${new Date().toISOString().slice(0, 10)}.json"` });
      return res.end(JSON.stringify(makeBundle()));
    }
    if (a === 'import' && m === 'POST') return send(res, 200, { imported: applyBundle(await readBody(req)) });
    if (a === 'ping') return send(res, 200, { ok: true });
    return send(res, 404, { error: 'not found' });
  }

  function serveStatic(req, res, url) {
    let rel = decodeURIComponent(url.pathname);
    if (rel.endsWith('/')) rel += 'index.html';
    const file = path.join(PUB, rel);
    if (!file.startsWith(PUB + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); return res.end('Not found'); }
    const ext = path.extname(file);
    res.writeHead(200, { 'content-type': MIME[ext] || 'application/octet-stream', 'cache-control': 'no-cache' });
    fs.createReadStream(file).pipe(res);
  }

  const server = http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url, 'http://x');
      if (url.pathname.startsWith('/api/')) return await api(req, res, url);
      if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405); return res.end(); }
      return serveStatic(req, res, url);
    } catch (e) {
      if (!res.headersSent) send(res, e.status || 500, { error: e.message });
    }
  });
  server.backup = backup;
  server.tick = tick;
  return server;
}

module.exports = { createServer };

if (require.main === module) {
  const PORT = +process.env.PORT || 3000;
  const HOST = process.env.HOST || '0.0.0.0';
  const server = createServer(process.env.DATA_DIR || path.join(__dirname, 'data'));
  try { server.backup(); } catch (e) { console.error('backup failed', e); }
  setInterval(() => server.tick().catch((e) => console.error('reminder failed', e)), 20 * 1000).unref();
  setInterval(() => { try { server.backup(); } catch (e) { console.error('backup failed', e); } }, 3600 * 1000).unref();
  server.listen(PORT, HOST, () => console.log(`DailyNote on http://${HOST}:${PORT}`));
}
