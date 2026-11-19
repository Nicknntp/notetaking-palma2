// Port of Shortcuts.kt. State = { text, pos } (collapsed cursor) unless noted.
export const DIVIDER = '---';
export const INDENT = '    ';
export const UNCHECKED = '☐';
export const CHECKED = '☑';
export const BULLETS = ['•', '◦', '▪', '▫', '‣', '–'];

const listRe = /^( *)(•|◦|▪|▫|‣|–|☐|☑) /;
const isTodo = (m) => m === UNCHECKED || m === CHECKED;

export const bulletForLevel = (l) => BULLETS[Math.max(0, Math.min(BULLETS.length - 1, l))];
const lastNl = (t, i) => (i < 0 ? -1 : t.lastIndexOf('\n', i));
const nextLineStart = (t, pos) => { const i = t.indexOf('\n', pos); return i < 0 ? t.length : i + 1; };
const edit = (text, from, to, rep) => ({ text: text.slice(0, from) + rep + text.slice(to), pos: from + rep.length });

export function lineBounds(text, cursor) {
  const s = lastNl(text, cursor - 1) + 1;
  const i = text.indexOf('\n', cursor);
  return [s, i < 0 ? text.length : i];
}

export function isDividerLine(line) {
  const t = line.trim();
  return t === DIVIDER || (t.length > 0 && [...t].every((c) => c === '─'));
}

export function headerLevel(line) {
  let n = 0;
  while (line[n] === '#') n++;
  return n >= 1 && n <= 3 && line.length > n && line[n] === ' ' ? n : 0;
}

/** After a top-level list item is removed, its children move up one level. */
export function promoteChildren(text, from) {
  if (from >= text.length) return text;
  const head = text.slice(0, from);
  const lines = text.slice(from).split('\n');
  for (let i = 0; i < lines.length; i++) {
    const m = listRe.exec(lines[i]);
    if (!m) break;
    const level = m[1].length / INDENT.length;
    if (level < 1) break;
    const marker = m[2];
    const nm = isTodo(marker) ? marker : bulletForLevel(level - 1);
    lines[i] = INDENT.repeat(level - 1) + nm + ' ' + lines[i].slice(m[0].length);
  }
  return head + lines.join('\n');
}

function applyBackspace(old, nw) {
  const c = old.pos;
  if (c < 1 || nw.text.length !== old.text.length - 1 || nw.pos !== c - 1) return null;
  if (nw.text !== old.text.slice(0, c - 1) + old.text.slice(c)) return null;
  const text = old.text;
  if (text[c - 1] === '\n') return null;
  const lineStart = lastNl(text, c - 1) + 1;
  const before = text.slice(lineStart, c);
  const m = listRe.exec(before);
  if (m && m[0].length === before.length) {
    const at = lineStart + m[1].length;
    const replaced = text.slice(0, at) + '  ' + text.slice(c);
    const out = m[1] === '' ? promoteChildren(replaced, nextLineStart(replaced, lineStart)) : replaced;
    return { text: out, pos: at + 2 };
  }
  if (!(before.length > 1 && /^ +$/.test(before))) return null;
  return { text: text.slice(0, lineStart) + text.slice(c), pos: lineStart };
}

/** Notion-like shortcuts, applied when exactly one character was typed. */
export function applyShortcuts(old, nw) {
  const bs = applyBackspace(old, nw);
  if (bs) return bs;
  if (nw.text.length !== old.text.length + 1) return nw;
  const text = nw.text;
  const cursor = nw.pos;
  if (cursor < 1 || cursor > text.length) return nw;
  const typed = text[cursor - 1];

  if (typed === '\n') {
    const prevStart = lastNl(text, cursor - 2) + 1;
    const prevLine = text.slice(prevStart, cursor - 1);
    const te = tableEnter(text, cursor);
    if (te === false) return nw;
    if (te) return te;
    const m = listRe.exec(prevLine);
    if (!m) return nw;
    const indent = m[1];
    const rest = prevLine.slice(m[0].length);
    if (rest.trim() === '') {
      const r = edit(text, prevStart, cursor, '');
      return indent === '' ? { text: promoteChildren(r.text, prevStart), pos: r.pos } : r;
    }
    return edit(text, cursor, cursor, indent + (isTodo(m[2]) ? UNCHECKED : m[2]) + ' ');
  }

  const lineStart = lastNl(text, cursor - 2) + 1;
  const lineBefore = text.slice(lineStart, cursor);
  const ind = lineBefore.match(/^ */)[0];
  const body = lineBefore.slice(ind.length);
  const level = Math.floor(ind.length / INDENT.length);

  if (body === '- ' || body === '* ' || body === '+ ') return edit(text, lineStart, cursor, ind + bulletForLevel(level) + ' ');
  if (body === '[] ') return edit(text, lineStart, cursor, ind + UNCHECKED + ' ');
  if (ind === '' && body && [...body].every((c) => c === '-' || c === '—' || c === '–') && (body.length >= 3 || body === '—-')) {
    return edit(text, lineStart, cursor, DIVIDER + '\n');
  }
  if (typed === '"') {
    const prev = cursor >= 2 ? text[cursor - 2] : ' ';
    const q = /\s/.test(prev) || '([{'.includes(prev) ? '“' : '”';
    return edit(text, cursor - 1, cursor, q);
  }
  return nw;
}

// ---- toolbar actions: state = { text, start, end } ----
function linesRange(text, start, end) {
  const s = lastNl(text, start - 1) + 1;
  const i = text.indexOf('\n', Math.max(start, end > start ? end - 1 : end));
  return [s, i < 0 ? text.length : i];
}
function mapLines(st, fn) {
  const [s, e] = linesRange(st.text, st.start, st.end);
  const out = st.text.slice(s, e).split('\n').map(fn).join('\n');
  const delta = out.length - (e - s);
  return { text: st.text.slice(0, s) + out + st.text.slice(e), start: Math.max(s, st.start), end: st.end + delta };
}

export function changeIndent(st, delta) {
  const r = mapLines(st, (line) => {
    const m = listRe.exec(line);
    if (!m) return line;
    const level = Math.max(0, Math.min(BULLETS.length - 1, m[1].length / INDENT.length + delta));
    const marker = isTodo(m[2]) ? m[2] : bulletForLevel(level);
    return INDENT.repeat(level) + marker + ' ' + line.slice(m[0].length);
  });
  return r;
}

export function toggleList(st, todo) {
  return mapLines(st, (line) => {
    const m = listRe.exec(line);
    const want = todo ? UNCHECKED : '•';
    if (m) {
      const same = todo ? isTodo(m[2]) : !isTodo(m[2]);
      const body = line.slice(m[0].length);
      if (same) return m[1] + body; // remove marker
      return m[1] + want + ' ' + body;
    }
    const ind = line.match(/^ */)[0];
    return ind + want + ' ' + line.slice(ind.length);
  });
}

export function toggleCheck(st) {
  return mapLines(st, (line) => {
    const m = listRe.exec(line);
    if (!m || !isTodo(m[2])) return line;
    return m[1] + (m[2] === UNCHECKED ? CHECKED : UNCHECKED) + line.slice(m[1].length + 1);
  });
}

export function cycleHeader(st) {
  return mapLines(st, (line) => {
    const lv = headerLevel(line);
    const body = lv ? line.slice(lv + 1) : line;
    return lv >= 3 ? body : '#'.repeat(lv + 1) + ' ' + body;
  });
}

export function insertDivider(st) {
  const cursor = st.start;
  const [s, e] = lineBounds(st.text, cursor);
  const r = s === e ? edit(st.text, s, e, DIVIDER + '\n') : edit(st.text, e, e, '\n' + DIVIDER + '\n');
  return { text: r.text, start: r.pos, end: r.pos };
}

export function insertAtCursor(st, str) {
  const r = edit(st.text, st.start, st.end, str);
  return { text: r.text, start: r.pos, end: r.pos };
}

// ---------- tables (Markdown: | a | b |) ----------
const tableSepRe = /^\|(\s*:?-+:?\s*\|)+\s*$/;
export const isTableLine = (line) => line.startsWith('|');
export const isTableSeparator = (line) => tableSepRe.test(line);
export function tableCells(line) {
  const inner = line.split('|').slice(1);
  return line.trimEnd().endsWith('|') ? inner.slice(0, -1) : inner;
}
const TABLE_HEAD = 'หัวข้อ 1';
export function insertTable(st) {
  const text = st.text;
  const [s, e] = lineBounds(text, st.start);
  const tpl = `| ${TABLE_HEAD} | หัวข้อ 2 |\n|---|---|\n|  |  |\n|  |  |`;
  const at = s === e ? s : e;
  const prefix = s === e ? '' : '\n';
  const suffix = at >= text.length ? '\n' : '';
  const head = at + prefix.length + 2;
  return { text: text.slice(0, at) + prefix + tpl + suffix + text.slice(at), start: head, end: head + TABLE_HEAD.length };
}

/** Enter at the end of a table row adds a new row with the same number of cells. Returns null if not applicable. */
function tableEnter(text, cursor) {
  const prevStart = lastNl(text, cursor - 2) + 1;
  const prevLine = text.slice(prevStart, cursor - 1);
  const atLineEnd = cursor >= text.length || text[cursor] === '\n';
  if (!isTableLine(prevLine) || !atLineEnd) return null;
  const cells = tableCells(prevLine);
  const nextStart = Math.min(cursor + 1, text.length);
  const ne = text.indexOf('\n', nextStart);
  const nextIsTable = cursor < text.length && isTableLine(text.slice(nextStart, ne < 0 ? text.length : ne));
  if (!isTableSeparator(prevLine) && cells.every((c) => c.trim() === '') && !nextIsTable) return false; // empty last row: leave table
  const n = Math.max(1, cells.length);
  const row = '|' + '  |'.repeat(n);
  return { text: text.slice(0, cursor) + row + text.slice(cursor), pos: cursor + 2 };
}
export { tableEnter };

// ---------- inline marks: ** bold, __ underline, ~~ strike ----------
export function toggleWrap(st, mark) {
  const { text } = st;
  const s = st.start, e = st.end, ml = mark.length;
  const wrappedOutside = s >= ml && text.startsWith(mark, s - ml) && text.startsWith(mark, e);
  if (s === e) {
    if (wrappedOutside) return { text: text.slice(0, s - ml) + text.slice(s + ml), start: s - ml, end: s - ml };
    return { text: text.slice(0, s) + mark + mark + text.slice(s), start: s + ml, end: s + ml };
  }
  if (wrappedOutside) return { text: text.slice(0, s - ml) + text.slice(s, e) + text.slice(e + ml), start: s - ml, end: e - ml };
  const sel = text.slice(s, e);
  if (sel.length >= 2 * ml && sel.startsWith(mark) && sel.endsWith(mark)) {
    const inner = sel.slice(ml, sel.length - ml);
    return { text: text.slice(0, s) + inner + text.slice(e), start: s, end: s + inner.length };
  }
  return { text: text.slice(0, s) + mark + sel + mark + text.slice(e), start: s + ml, end: e + ml };
}

/** Tapping the ☐/☑ glyph in the editor toggles it. Returns new text or null. */
export function toggleCheckAt(text, pos) {
  const [s, e] = lineBounds(text, pos);
  const m = listRe.exec(text.slice(s, e));
  if (!m || !isTodo(m[2])) return null;
  const at = s + m[1].length;
  if (pos < at || pos > at + 1) return null;
  return text.slice(0, at) + (m[2] === UNCHECKED ? CHECKED : UNCHECKED) + text.slice(at + 1);
}
