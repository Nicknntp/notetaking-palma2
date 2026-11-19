// Builds an importable bundle from files copied off the Android app.
// Usage: node tools/pack-android.mjs <dir> [out.json]
// <dir> should contain: notes/*.txt, free/*, habits.json (all from files/) and optionally shifts.xml (from shared_prefs/).
import fs from 'node:fs';
import path from 'node:path';

const dir = process.argv[2];
const out = process.argv[3] || 'dailynote-bundle.json';
if (!dir) { console.error('usage: node tools/pack-android.mjs <dir> [out.json]'); process.exit(1); }

const ls = (d) => (fs.existsSync(path.join(dir, d)) ? fs.readdirSync(path.join(dir, d)) : []);
const read = (...p) => fs.readFileSync(path.join(dir, ...p), 'utf8');
const bundle = { format: 'dailynote-bundle', version: 1, notes: {}, free: {}, habits: { habits: [] }, shifts: null };

for (const f of ls('notes')) if (/^\d{4}-\d{2}-\d{2}\.txt$/.test(f)) bundle.notes[f.slice(0, -4)] = read('notes', f);
for (const f of ls('free')) {
  if (!f.endsWith('.txt')) continue;
  const id = f.slice(0, -4);
  bundle.free[id] = { text: read('free', f), pinned: ls('free').includes(id + '.pin'), emoji: ls('free').includes(id + '.icon') ? read('free', id + '.icon').trim() : '' };
}
if (fs.existsSync(path.join(dir, 'habits.json'))) bundle.habits = JSON.parse(read('habits.json'));

const shiftsXml = ['shifts.xml', path.join('shared_prefs', 'shifts.xml')].find((p) => fs.existsSync(path.join(dir, p)));
if (shiftsXml) {
  const xml = read(shiftsXml);
  const un = (s) => s.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
  const prefs = {};
  for (const m of xml.matchAll(/<string name="([^"]+)">([\s\S]*?)<\/string>/g)) prefs[m[1]] = un(m[2]);
  const doc = { types: [], days: {}, others: {} };
  if (prefs.types) {
    doc.types = JSON.parse(prefs.types).map((t) => ({
      id: t.id, name: t.name, start: t.start, end: t.end, wkStart: t.wkStart || '', wkEnd: t.wkEnd || '', pay: t.pay ?? 0, clinic: !!t.clinic, names: t.names || [],
      scheds: Object.fromEntries(Object.entries(t.scheds || {}).map(([k, s]) => [k, { weekday: { start: s.wdS, end: s.wdE }, sat: { start: s.saS, end: s.saE }, sun: { start: s.suS, end: s.suE } }])),
    }));
  }
  for (const [k, v] of Object.entries(prefs)) {
    if (/^d_\d{4}-\d{2}-\d{2}$/.test(k)) doc.days[k.slice(2)] = JSON.parse(v).map((e) => ({ typeId: e.typeId, typeName: e.typeName, name: e.name, start: e.start, end: e.end, pay: e.pay, clinic: e.clinic }));
    else if (/^others_\d{4}-\d{2}$/.test(k)) doc.others[k.slice(7)] = JSON.parse(v);
  }
  bundle.shifts = doc;
}

fs.writeFileSync(out, JSON.stringify(bundle));
console.log(`wrote ${out}: ${Object.keys(bundle.notes).length} notes, ${Object.keys(bundle.free).length} free notes, ${bundle.habits.habits.length} habits, shifts ${bundle.shifts ? 'yes' : 'no'}`);
