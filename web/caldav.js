'use strict';
// Minimal CalDAV client (iCloud / Nextcloud / Radicale). No dependencies. Config: { url, username, password }.

const unxml = (s) => s.replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');
const tag = (xml, name) => { const m = new RegExp(`<(?:\\w+:)?${name}(?:\\s[^>]*)?>([\\s\\S]*?)</(?:\\w+:)?${name}>`, 'i').exec(xml); return m ? m[1] : null; };
const responses = (xml) => [...xml.matchAll(/<(?:\w+:)?response(?:\s[^>]*)?>([\s\S]*?)<\/(?:\w+:)?response>/gi)].map((m) => m[1]);
const hrefOf = (block) => { const h = tag(block, 'href'); return h ? unxml(h.trim()) : null; };

// ---------- iCalendar ----------
const unfold = (s) => s.replace(/\r?\n[ \t]/g, '');
const icsText = (s) => s.replace(/\\n/gi, '\n').replace(/\\([,;\\])/g, '$1');
const escText = (s) => String(s).replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\r?\n/g, '\\n');
const pad = (n, l = 2) => String(n).padStart(l, '0');

function zonedToUtcMs(y, mo, d, h, mi, s, tz) {
  const guess = Date.UTC(y, mo - 1, d, h, mi, s);
  try {
    const f = new Intl.DateTimeFormat('en-US', { timeZone: tz, hourCycle: 'h23', year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: 'numeric', second: 'numeric' });
    const p = Object.fromEntries(f.formatToParts(new Date(guess)).map((x) => [x.type, +x.value]));
    const asIfUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
    return guess - (asIfUtc - guess);
  } catch { return guess; }
}

/** Parse a DTSTART/DTEND value. Returns { iso, allDay }. UTC -> '...Z'; floating -> no zone suffix. */
function parseDt(params, value) {
  const m = /^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})(Z)?)?$/.exec(value.trim());
  if (!m) return null;
  const [, y, mo, d, h, mi, s, z] = m;
  if (h === undefined) return { iso: `${y}-${mo}-${d}`, allDay: true };
  if (z) return { iso: `${y}-${mo}-${d}T${h}:${mi}:${s}Z`, allDay: false };
  const tz = /TZID=([^;:]+)/i.exec(params)?.[1];
  if (tz) return { iso: new Date(zonedToUtcMs(+y, +mo, +d, +h, +mi, +s, tz)).toISOString().replace('.000', ''), allDay: false };
  return { iso: `${y}-${mo}-${d}T${h}:${mi}:${s}`, allDay: false };
}

function parseEvents(ics) {
  const out = [];
  const lines = unfold(ics).split(/\r?\n/);
  let cur = null, depth = 0;
  for (const line of lines) {
    if (line === 'BEGIN:VEVENT') { cur = {}; depth = 0; continue; }
    if (!cur) continue;
    if (line.startsWith('BEGIN:')) { depth++; continue; }
    if (line === 'END:VEVENT') { out.push(cur); cur = null; continue; }
    if (line.startsWith('END:')) { depth--; continue; }
    if (depth > 0) continue; // inside VALARM etc.
    const i = line.indexOf(':');
    if (i < 0) continue;
    const [name, ...rest] = line.slice(0, i).split(';');
    const params = rest.join(';');
    const value = line.slice(i + 1);
    cur[name.toUpperCase()] = { params, value };
  }
  return out.map((p) => {
    const s = p.DTSTART && parseDt(p.DTSTART.params, p.DTSTART.value);
    if (!s) return null;
    let e = p.DTEND && parseDt(p.DTEND.params, p.DTEND.value);
    if (!e) e = s.allDay ? { iso: addDay(s.iso), allDay: true } : { iso: s.iso, allDay: false };
    return {
      uid: p.UID?.value || '', title: icsText(p.SUMMARY?.value || ''), location: icsText(p.LOCATION?.value || ''),
      start: s.iso, end: e.iso, allDay: s.allDay, recurring: !!p['RECURRENCE-ID'] || !!p.RRULE,
    };
  }).filter(Boolean);
}
const addDay = (d) => { const t = new Date(d + 'T00:00:00Z'); t.setUTCDate(t.getUTCDate() + 1); return t.toISOString().slice(0, 10); };

const icsDt = (iso, allDay, key) => (allDay ? `${key};VALUE=DATE:${iso.replace(/-/g, '').slice(0, 8)}` : `${key}:${new Date(iso).toISOString().replace(/[-:]/g, '').replace(/\.\d+/, '')}`);

function eventLines(ev) {
  const l = [icsDt(ev.start, ev.allDay, 'DTSTART'), icsDt(ev.end || ev.start, ev.allDay, 'DTEND'), `SUMMARY:${escText(ev.title || '')}`];
  if (ev.location) l.push(`LOCATION:${escText(ev.location)}`);
  return l;
}

function buildIcs(ev, uid) {
  const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d+/, '');
  return ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//DailyNote//EN', 'BEGIN:VEVENT', `UID:${uid}`, `DTSTAMP:${stamp}`, ...eventLines(ev), 'END:VEVENT', 'END:VCALENDAR', ''].join('\r\n');
}

/** Keep everything else in the original resource (alarms, attendees, ...) and replace only the edited properties. */
function patchIcs(raw, ev) {
  const lines = unfold(raw).split(/\r?\n/).filter((l) => l !== '');
  const out = [];
  let inEvent = false, depth = 0, done = false;
  for (const line of lines) {
    if (line === 'BEGIN:VEVENT' && !done) { inEvent = true; depth = 0; out.push(line); continue; }
    if (inEvent) {
      if (line.startsWith('BEGIN:')) { depth++; out.push(line); continue; }
      if (line === 'END:VEVENT') { out.push(...eventLines(ev)); out.push(line); inEvent = false; done = true; continue; }
      if (line.startsWith('END:')) { depth--; out.push(line); continue; }
      if (depth === 0 && /^(SUMMARY|DTSTART|DTEND|DURATION|LOCATION)[;:]/i.test(line)) continue;
    }
    out.push(line);
  }
  return out.join('\r\n') + '\r\n';
}

// ---------- DAV plumbing ----------
class CalDav {
  constructor(cfg, fetchImpl = fetch) {
    this.cfg = cfg; this.f = fetchImpl; this.base = new URL(cfg.url); this.cache = null;
    this.auth = 'Basic ' + Buffer.from(`${cfg.username}:${cfg.password}`).toString('base64');
  }
  abs(href) { return new URL(href, this.base).toString(); }
  async dav(method, url, body, headers = {}) {
    const r = await this.f(url, { method, headers: { Authorization: this.auth, 'Content-Type': 'application/xml; charset=utf-8', ...headers }, body, redirect: 'follow' });
    const text = await r.text();
    if (r.status === 401) throw Object.assign(new Error('CalDAV: ชื่อผู้ใช้/รหัสผ่านไม่ถูกต้อง'), { status: 502 });
    if (r.status >= 400) throw Object.assign(new Error(`CalDAV ${method} ${r.status}`), { status: r.status === 412 ? 409 : 502, text });
    return { status: r.status, text, headers: r.headers };
  }

  async calendars(force = false) {
    if (this.cache && !force) return this.cache;
    const h0 = { Depth: '0' };
    const p = await this.dav('PROPFIND', this.abs(this.base.pathname || '/'), '<d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>', h0);
    const principal = hrefOf(tag(p.text, 'current-user-principal') || '') ;
    if (!principal) throw new Error('CalDAV: หา principal ไม่เจอ');
    const hp = await this.dav('PROPFIND', this.abs(principal), '<d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:prop><c:calendar-home-set/></d:prop></d:propfind>', h0);
    const home = hrefOf(tag(hp.text, 'calendar-home-set') || '');
    if (!home) throw new Error('CalDAV: หา calendar-home ไม่เจอ');
    const homeUrl = this.abs(home);
    const lp = await this.dav('PROPFIND', homeUrl, '<d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:prop><d:displayname/><d:resourcetype/><c:supported-calendar-component-set/></d:prop></d:propfind>', { Depth: '1' });
    this.cache = responses(lp.text).filter((b) => /<(?:\w+:)?calendar\s*\/>/i.test(tag(b, 'resourcetype') || '') && (!/supported-calendar-component-set/i.test(b) || /name="VEVENT"/i.test(b)))
      .map((b) => ({ href: new URL(hrefOf(b), homeUrl).toString(), name: unxml(tag(b, 'displayname') || '').trim() || 'ปฏิทิน' }));
    return this.cache;
  }

  async events(fromIso, toIso) {
    const fmt = (iso) => new Date(iso).toISOString().replace(/[-:]/g, '').replace(/\.\d+/, '');
    const body = `<c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:prop><d:getetag/><c:calendar-data><c:expand start="${fmt(fromIso)}" end="${fmt(toIso)}"/></c:calendar-data></d:prop><c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VEVENT"><c:time-range start="${fmt(fromIso)}" end="${fmt(toIso)}"/></c:comp-filter></c:comp-filter></c:filter></c:calendar-query>`;
    const out = [];
    for (const cal of await this.calendars()) {
      const r = await this.dav('REPORT', cal.href, body, { Depth: '1' });
      for (const b of responses(r.text)) {
        const data = tag(b, 'calendar-data');
        if (!data) continue;
        const href = new URL(hrefOf(b), cal.href).toString();
        const etag = unxml(tag(b, 'getetag') || '').trim();
        for (const ev of parseEvents(unxml(data))) out.push({ ...ev, href, etag, calendar: cal.href, calendarName: cal.name });
      }
    }
    return out.sort((a, b) => a.start.localeCompare(b.start));
  }

  /** Only touch resources inside the user's own calendars (blocks SSRF via client-supplied hrefs). */
  async guard(href) {
    const cals = await this.calendars();
    if (typeof href !== 'string' || !cals.some((c) => href === c.href || href.startsWith(c.href.endsWith('/') ? c.href : c.href + '/'))) throw Object.assign(new Error('bad href'), { status: 400 });
  }

  /** Create (no href) or update (href) an event. */
  async save(ev) {
    await this.guard(ev.href || ev.calendar);
    if (!ev.href) {
      const uid = `dn-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
      const url = new URL(`${uid}.ics`, ev.calendar.endsWith('/') ? ev.calendar : ev.calendar + '/').toString();
      await this.dav('PUT', url, buildIcs(ev, uid), { 'Content-Type': 'text/calendar; charset=utf-8', 'If-None-Match': '*' });
      return { href: url, uid };
    }
    const g = await this.f(ev.href, { headers: { Authorization: this.auth } });
    if (!g.ok) throw Object.assign(new Error('CalDAV GET ' + g.status), { status: 502 });
    const etag = g.headers.get('etag');
    await this.dav('PUT', ev.href, patchIcs(await g.text(), ev), { 'Content-Type': 'text/calendar; charset=utf-8', ...(etag ? { 'If-Match': etag } : {}) });
    return { href: ev.href };
  }

  async remove(href) { await this.guard(href); await this.dav('DELETE', href, undefined, {}); }
}

module.exports = { CalDav, parseEvents, parseDt, buildIcs, patchIcs };
