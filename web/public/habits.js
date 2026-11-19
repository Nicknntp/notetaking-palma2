// Port of HabitStore.kt (model + HabitStats). Dates are 'YYYY-MM-DD' strings.
export const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
export const today = () => iso(new Date());
const utc = (s) => { const [y, m, d] = s.split('-').map(Number); return Date.UTC(y, m - 1, d); };
const fromUtc = (ms) => { const d = new Date(ms); return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`; };
export const addDays = (s, n) => fromUtc(utc(s) + n * 86400000);
export const diffDays = (a, b) => Math.round((utc(b) - utc(a)) / 86400000);
/** Week starts on Sunday (same as the app). */
export const weekStartOf = (s) => addDays(s, -new Date(utc(s)).getUTCDay());

export const blankHabit = () => ({ id: '', name: '', perWeek: 7, created: today(), done: [], days: 0, emoji: '', weekGoals: {} });

export const daily = (h) => h.perWeek >= 7;
export const goalFor = (h, ws) => (h.weekGoals && h.weekGoals[ws]) ?? h.perWeek;
export const since = (h) => { const d = [...h.done].sort()[0]; return d && d < h.created ? d : h.created; };
export const endOf = (h) => (h.days > 0 ? addDays(since(h), h.days - 1) : null);
export const activeOn = (h, d) => d >= since(h) && (endOf(h) === null || d <= endOf(h));
export function completed(h, t = today()) {
  const e = endOf(h);
  if (!e) return false;
  return t > e || (t === e && h.done.includes(t));
}

export const weekCount = (h, ws) => { let n = 0; for (let i = 0; i < 7; i++) if (h.done.includes(addDays(ws, i))) n++; return n; };
const weekMet = (h, ws) => weekCount(h, ws) >= goalFor(h, ws);

export function currentStreak(h, t = today()) {
  if (daily(h)) {
    let d = h.done.includes(t) ? t : addDays(t, -1);
    let n = 0;
    while (h.done.includes(d)) { n++; d = addDays(d, -1); }
    return n;
  }
  let ws = weekStartOf(t);
  let n = 0;
  if (weekMet(h, ws)) n++;
  ws = addDays(ws, -7);
  const first = weekStartOf(since(h));
  while (ws >= first && weekMet(h, ws)) { n++; ws = addDays(ws, -7); }
  return n;
}

export function bestStreak(h, t = today()) {
  let best = 0, run = 0;
  if (daily(h)) {
    let prev = null;
    for (const d of [...h.done].sort()) {
      run = prev && diffDays(prev, d) === 1 ? run + 1 : 1;
      best = Math.max(best, run);
      prev = d;
    }
    return best;
  }
  let ws = weekStartOf(since(h));
  const last = weekStartOf(t);
  while (ws <= last) { run = weekMet(h, ws) ? run + 1 : 0; best = Math.max(best, run); ws = addDays(ws, 7); }
  return best;
}

export function rate30(h, t = today()) {
  const from = addDays(t, -29) > since(h) ? addDays(t, -29) : since(h);
  const days = diffDays(from, t) + 1;
  const done = h.done.filter((d) => d >= from && d <= t).length;
  let expected = days;
  if (!daily(h)) { expected = 0; for (let i = 0; i < days; i++) expected += goalFor(h, weekStartOf(addDays(from, i))) / 7; }
  if (expected <= 0) return 0;
  return Math.min(100, Math.floor((done * 100) / expected));
}

export function missedYesterday(h, t = today()) {
  const y = addDays(t, -1);
  return daily(h) && y >= since(h) && !h.done.includes(y) && !h.done.includes(t);
}

export function toggleDone(h, day) {
  const has = h.done.includes(day);
  return { ...h, done: (has ? h.done.filter((d) => d !== day) : [...h.done, day]).sort() };
}
