import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import crypto from 'node:crypto';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { createServer } = require('../server.js');
const push = require('../push.js');
const { parseEvents, patchIcs, buildIcs } = require('../caldav.js');

const listen = (srv) => new Promise((r) => srv.listen(0, '127.0.0.1', () => r(`http://127.0.0.1:${srv.address().port}`)));

// ---------------- auth ----------------
test('password auth: 401 without cookie, login sets cookie, wrong password rejected', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'dn-auth-'));
  const srv = createServer(dir, { password: 'secret' });
  const base = await listen(srv);
  try {
    assert.equal((await fetch(base + '/api/notes')).status, 401);
    assert.deepEqual(await (await fetch(base + '/api/auth')).json(), { required: true, ok: false });
    assert.equal((await fetch(base + '/')).status, 200); // app shell itself holds no data
    assert.equal((await fetch(base + '/api/login', { method: 'POST', body: JSON.stringify({ password: 'nope' }) })).status, 401);
    const ok = await fetch(base + '/api/login', { method: 'POST', body: JSON.stringify({ password: 'secret' }) });
    assert.equal(ok.status, 200);
    const cookie = ok.headers.get('set-cookie');
    assert.match(cookie, /HttpOnly/);
    const c = cookie.split(';')[0];
    assert.equal((await fetch(base + '/api/notes', { headers: { cookie: c } })).status, 200);
    assert.equal((await fetch(base + '/api/notes', { headers: { cookie: 'dn_auth=forged' } })).status, 401);
  } finally { srv.close(); fs.rmSync(dir, { recursive: true, force: true }); }
});

// ---------------- web push crypto ----------------
test('RFC 8291 appendix A vector', () => {
  const keys = { p256dh: 'BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4', auth: 'BTBZMqHH6r4Tts7J_aSIgg' };
  const body = push.encrypt(keys, 'When I grow up, I want to be a watermelon', { asPrivate: 'yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw', salt: 'DGv6ra1nlYgDCS1FRnbzlw' });
  assert.equal(body.toString('base64url'),
    'DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN');
});

test('push encrypt/decrypt roundtrip + VAPID header shape', () => {
  const ua = crypto.createECDH('prime256v1'); ua.generateKeys();
  const keys = { p256dh: ua.getPublicKey().toString('base64url'), auth: crypto.randomBytes(16).toString('base64url') };
  const body = push.encrypt(keys, '{"title":"ทดสอบ"}');
  assert.equal(push.decrypt(keys, ua.getPrivateKey().toString('base64url'), body), '{"title":"ทดสอบ"}');
  const v = push.generateVapid();
  const h = push.vapidHeader('https://push.example/abc', v);
  const jwt = /t=([^,]+),/.exec(h)[1].split('.');
  const ok = crypto.verify('sha256', Buffer.from(jwt[0] + '.' + jwt[1]), { key: crypto.createPublicKey({ key: { kty: 'EC', crv: 'P-256', x: v.privateJwk.x, y: v.privateJwk.y }, format: 'jwk' }), dsaEncoding: 'ieee-p1363' }, Buffer.from(jwt[2], 'base64url'));
  assert.ok(ok, 'VAPID JWT signature verifies');
  assert.equal(JSON.parse(Buffer.from(jwt[1], 'base64url')).aud, 'https://push.example');
});

test('reminder tick sends an encrypted push once per minute', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'dn-push-'));
  const got = [];
  const sink = http.createServer((req, res) => { const c = []; req.on('data', (d) => c.push(d)); req.on('end', () => { got.push({ h: req.headers, body: Buffer.concat(c) }); res.writeHead(201); res.end(); }); });
  const sinkBase = await listen(sink);
  const ua = crypto.createECDH('prime256v1'); ua.generateKeys();
  const keys = { p256dh: ua.getPublicKey().toString('base64url'), auth: crypto.randomBytes(16).toString('base64url') };
  const srv = createServer(dir);
  const base = await listen(srv);
  try {
    assert.equal((await fetch(base + '/api/push/subscribe', { method: 'POST', body: JSON.stringify({ subscription: { endpoint: 'http://evil', keys } }) })).status, 400); // https only
    const info = await (await fetch(base + '/api/push/info')).json();
    assert.ok(info.publicKey.length > 80);
    assert.equal((await fetch(base + '/api/push/reminder', { method: 'PUT', body: JSON.stringify({ enabled: true, times: ['25:00'] }) })).status, 400);
    await fetch(base + '/api/push/reminder', { method: 'PUT', body: JSON.stringify({ enabled: true, times: ['21:00', '08:30'] }) });
    const st = JSON.parse(fs.readFileSync(path.join(dir, 'push.json'), 'utf8'));
    st.subs = [{ endpoint: sinkBase + '/send', keys }]; // bypass https-only check for the local sink
    fs.writeFileSync(path.join(dir, 'push.json'), JSON.stringify(st));
    assert.equal(await srv.tick(new Date(2026, 10, 12, 20, 59)), 0);
    assert.equal(await srv.tick(new Date(2026, 10, 12, 21, 0)), 1);
    assert.equal(await srv.tick(new Date(2026, 10, 12, 21, 0)), 0); // same minute again: no duplicate
    assert.equal(got.length, 1);
    assert.match(got[0].h.authorization, /^vapid t=.+, k=.+/);
    assert.equal(got[0].h['content-encoding'], 'aes128gcm');
    assert.equal(JSON.parse(push.decrypt(keys, ua.getPrivateKey().toString('base64url'), got[0].body)).title, 'ถึงเวลาจดบันทึกแล้ว');
  } finally { srv.close(); sink.close(); fs.rmSync(dir, { recursive: true, force: true }); }
});

// ---------------- CalDAV ----------------
const ICS = [
  'BEGIN:VCALENDAR', 'VERSION:2.0', 'BEGIN:VEVENT', 'UID:e1', 'DTSTART:20261112T090000Z', 'DTEND:20261112T100000Z',
  'SUMMARY:Meet\\, team', 'LOCATION:Room 1', 'BEGIN:VALARM', 'TRIGGER:-PT15M', 'ACTION:DISPLAY', 'DESCRIPTION:Reminder', 'END:VALARM', 'END:VEVENT', 'END:VCALENDAR', '',
].join('\r\n');

test('ics parsing: UTC, all-day, TZID, recurrence flag, alarms ignored', () => {
  const evs = parseEvents(ICS + 'BEGIN:VEVENT\r\nUID:a\r\nDTSTART;VALUE=DATE:20261113\r\nSUMMARY:Day\r\nEND:VEVENT\r\nBEGIN:VEVENT\r\nUID:t\r\nDTSTART;TZID=Asia/Bangkok:20261112T090000\r\nDTEND;TZID=Asia/Bangkok:20261112T100000\r\nRECURRENCE-ID:20261112T090000\r\nSUMMARY:Zoned\r\nEND:VEVENT\r\n');
  assert.equal(evs[0].title, 'Meet, team');
  assert.equal(evs[0].start, '2026-11-12T09:00:00Z');
  assert.equal(evs[1].allDay, true); assert.equal(evs[1].end, '2026-11-14');
  assert.equal(evs[2].start, '2026-11-12T02:00:00Z'); assert.equal(evs[2].recurring, true); // 09:00 +07
});

test('patchIcs keeps alarms, replaces summary/time', () => {
  const out = patchIcs(ICS, { title: 'New; title', start: '2026-11-12T11:00:00Z', end: '2026-11-12T12:00:00Z', allDay: false, location: '' });
  assert.match(out, /BEGIN:VALARM/);
  assert.match(out, /SUMMARY:New\\; title/);
  assert.match(out, /DTSTART:20261112T110000Z/);
  assert.doesNotMatch(out, /Room 1/);
  assert.match(buildIcs({ title: 'x', start: '2026-11-13', end: '2026-11-14', allDay: true }, 'u1'), /DTSTART;VALUE=DATE:20261113/);
});

test('CalDAV endpoints against a mock server (read, create, update, delete, href guard)', async () => {
  const log = [];
  const xml = (inner) => `<?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">${inner}</d:multistatus>`;
  const mock = http.createServer((req, res) => {
    const c = []; req.on('data', (d) => c.push(d));
    req.on('end', () => {
      const body = Buffer.concat(c).toString();
      log.push(`${req.method} ${req.url} ${req.headers.authorization ? 'auth' : 'NOAUTH'}`);
      const send = (code, t, ct = 'application/xml', extra = {}) => { res.writeHead(code, { 'content-type': ct, ...extra }); res.end(t); };
      if (req.method === 'PROPFIND' && req.url === '/') return send(207, xml('<d:response><d:href>/</d:href><d:propstat><d:prop><d:current-user-principal><d:href>/principals/u/</d:href></d:current-user-principal></d:prop></d:propstat></d:response>'));
      if (req.method === 'PROPFIND' && req.url === '/principals/u/') return send(207, xml('<d:response><d:href>/principals/u/</d:href><d:propstat><d:prop><c:calendar-home-set><d:href>/cals/</d:href></c:calendar-home-set></d:prop></d:propstat></d:response>'));
      if (req.method === 'PROPFIND' && req.url === '/cals/') return send(207, xml('<d:response><d:href>/cals/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response><d:response><d:href>/cals/work/</d:href><d:propstat><d:prop><d:displayname>Work</d:displayname><d:resourcetype><d:collection/><c:calendar/></d:resourcetype><c:supported-calendar-component-set><c:comp name="VEVENT"/></c:supported-calendar-component-set></d:prop></d:propstat></d:response>'));
      if (req.method === 'REPORT') return send(207, xml(`<d:response><d:href>/cals/work/e1.ics</d:href><d:propstat><d:prop><d:getetag>"1"</d:getetag><c:calendar-data><![CDATA[${ICS}]]></c:calendar-data></d:prop></d:propstat></d:response>`));
      if (req.method === 'GET' && req.url === '/cals/work/e1.ics') return send(200, ICS, 'text/calendar', { etag: '"1"' });
      if (req.method === 'PUT') { log.push('BODY ' + body.replace(/\r\n/g, '|')); return send(201, ''); }
      if (req.method === 'DELETE') return send(204, '');
      send(404, '');
    });
  });
  const mockBase = await listen(mock);
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'dn-cal-'));
  const srv = createServer(dir);
  const base = await listen(srv);
  const call = (m, u, b) => fetch(base + u, { method: m, body: b && JSON.stringify(b) });
  try {
    assert.equal((await call('GET', '/api/cal/calendars')).status, 501); // not configured
    fs.writeFileSync(path.join(dir, 'config.json'), JSON.stringify({ caldav: { url: mockBase + '/', username: 'u', password: 'p' } }));
    const cals = await (await call('GET', '/api/cal/calendars')).json();
    assert.deepEqual(cals.map((x) => x.name), ['Work']);
    const evs = await (await call('GET', '/api/cal/events?from=2026-11-08T00:00:00Z&to=2026-11-15T00:00:00Z')).json();
    assert.equal(evs.length, 1); assert.equal(evs[0].title, 'Meet, team'); assert.equal(evs[0].calendarName, 'Work');
    const created = await (await call('PUT', '/api/cal/events', { calendar: cals[0].href, title: 'New', start: '2026-11-14T03:00:00Z', end: '2026-11-14T04:00:00Z' })).json();
    assert.match(created.href, /\/cals\/work\/dn-.+\.ics$/);
    assert.equal((await call('PUT', '/api/cal/events', { href: evs[0].href, title: 'Edited', start: evs[0].start, end: evs[0].end, calendar: cals[0].href })).status, 200);
    assert.ok(log.some((l) => l.includes('BODY') && l.includes('SUMMARY:Edited') && l.includes('VALARM')));
    assert.equal((await call('DELETE', '/api/cal/events?href=' + encodeURIComponent(evs[0].href))).status, 200);
    assert.equal((await call('DELETE', '/api/cal/events?href=' + encodeURIComponent('http://169.254.169.254/x'))).status, 400); // outside user's calendars
    assert.ok(!log.some((l) => l.includes('NOAUTH')));
  } finally { srv.close(); mock.close(); fs.rmSync(dir, { recursive: true, force: true }); }
});
