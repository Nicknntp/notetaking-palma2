// Port of ShiftStore.kt logic (types, entries, monthly pay). Calendar mirroring is not ported.
// Doc shape: { types: [...], days: { 'YYYY-MM-DD': [entry] }, others: { 'YYYY-MM': [{name, amount}] } }
const sp = (s, e) => ({ start: s, end: e });
const vipSched = { weekday: sp('16:00', '20:00'), sat: sp('08:00', '14:00'), sun: sp('08:00', '20:00') };

export const defaultTypes = () => [
  { id: 'morning', name: 'เวรเช้า', start: '08:00', end: '16:00', wkStart: '', wkEnd: '', pay: 900, clinic: false, scheds: {}, names: ['Port1', 'Port2', 'Port3', 'OPDเช้า', 'IPDเช้า', 'IRเช้า'] },
  { id: 'clinic', name: 'คลินิคพิเศษ', start: '16:00', end: '20:00', wkStart: '08:00', wkEnd: '12:00', pay: 0, clinic: true, scheds: { VIP: vipSched }, names: ['VIP', 'BMD'] },
  { id: 'afternoon', name: 'เวรบ่าย', start: '16:00', end: '23:00', wkStart: '', wkEnd: '', pay: 1250, clinic: false, scheds: {}, names: ['Port4', 'Port5', 'Port6', 'OPDบ่าย', 'IPDบ่าย', 'IRบ่าย'] },
  { id: 'night', name: 'เวรดึก', start: '23:00', end: '23:55', wkStart: '', wkEnd: '', pay: 1270, clinic: false, scheds: {}, names: ['Port7', 'Port8', 'Port9', 'Genดึก', 'IPDดึก', 'IRดึก'] },
];

export const emptyDoc = () => ({ types: defaultTypes(), days: {}, others: {} });
export const normDoc = (d) => ({ types: d?.types?.length ? d.types : defaultTypes(), days: d?.days || {}, others: d?.others || {} });

const dow = (date) => new Date(date + 'T00:00:00Z').getUTCDay(); // 0 Sun, 6 Sat

export function spanOn(type, date, name) {
  const w = dow(date);
  const sc = type.scheds?.[name];
  if (sc) return w === 6 ? sc.sat : w === 0 ? sc.sun : sc.weekday;
  const weekend = w === 0 || w === 6;
  return { start: weekend && type.wkStart ? type.wkStart : type.start, end: weekend && type.wkEnd ? type.wkEnd : type.end };
}

export function makeEntry(type, date, name) {
  const s = spanOn(type, date, name);
  return { typeId: type.id, typeName: type.name, name, start: s.start, end: s.end, pay: type.clinic ? 0 : type.pay, clinic: !!type.clinic };
}

export const timeRange = (s, e) => `${s.replace(':', '.')}-${e.replace(':', '.')}`;
export const baht = (v) => (Number.isInteger(v) ? v.toLocaleString('en-US') : v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }));

/** [[date, index, entry]...] for a month ('YYYY-MM'), sorted by date. */
export function monthIndexed(doc, ym) {
  return Object.keys(doc.days).filter((d) => d.startsWith(ym + '-')).sort()
    .flatMap((d) => doc.days[d].map((e, i) => [d, i, e]));
}

export function monthPay(doc, ym) {
  const all = monthIndexed(doc, ym).map((x) => x[2]);
  const shift = all.filter((e) => !e.clinic).reduce((a, e) => a + e.pay, 0);
  const clinic = all.filter((e) => e.clinic).reduce((a, e) => a + e.pay, 0);
  const other = (doc.others[ym] || []).reduce((a, o) => a + o.amount, 0);
  const total = shift + clinic + other;
  return { shift, clinic, other, total, net: total * 0.95 };
}

export function typeSummary(doc, ym) {
  const order = doc.types.map((t) => t.id);
  const map = new Map();
  for (const [, , e] of monthIndexed(doc, ym)) {
    const s = map.get(e.typeId) || { name: e.typeName, count: 0, pay: 0, clinic: e.clinic, order: order.indexOf(e.typeId) < 0 ? 1e9 : order.indexOf(e.typeId) };
    s.count++; s.pay += e.pay; map.set(e.typeId, s);
  }
  return [...map.values()].sort((a, b) => a.order - b.order);
}
