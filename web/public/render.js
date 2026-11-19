// Read-mode renderer for a note's plain text (port of NoteRender.kt basics). Pure: text -> HTML string.
import { isDividerLine, headerLevel, isTableLine, isTableSeparator, tableCells, UNCHECKED, CHECKED, INDENT } from './shortcuts.js';

const esc = (s) => s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const inline = (s) => esc(s).replace(/\*\*(.+?)\*\*/g, '<b>$1</b>').replace(/__(.+?)__/g, '<u>$1</u>').replace(/~~(.+?)~~/g, '<s>$1</s>');
const listRe = /^( *)(•|◦|▪|▫|‣|–|☐|☑) (.*)$/;

export function renderNote(text) {
  const lines = text.split('\n');
  const out = [];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (isTableLine(line)) {
      const rows = [];
      let j = i;
      while (j < lines.length && isTableLine(lines[j])) rows.push(lines[j++]);
      const sep = rows.findIndex(isTableSeparator);
      out.push('<table class="nt">' + rows.map((r, k) => (isTableSeparator(r) ? '' : `<tr>${tableCells(r).map((c) => `<${sep === 1 && k === 0 ? 'th' : 'td'}>${inline(c.trim())}</${sep === 1 && k === 0 ? 'th' : 'td'}>`).join('')}</tr>`)).join('') + '</table>');
      i = j - 1;
      continue;
    }
    if (isDividerLine(line)) { out.push('<hr>'); continue; }
    const h = headerLevel(line);
    if (h) { out.push(`<h${h} class="nh">${inline(line.slice(h + 1))}</h${h}>`); continue; }
    const m = listRe.exec(line);
    if (m) {
      const lvl = Math.floor(m[1].length / INDENT.length);
      const todo = m[2] === UNCHECKED || m[2] === CHECKED;
      const marker = todo ? `<span class="cb" data-line="${i}" role="checkbox" aria-checked="${m[2] === CHECKED}">${m[2]}</span>` : `<span class="bl">${m[2]}</span>`;
      out.push(`<div class="li${m[2] === CHECKED ? ' done' : ''}" style="padding-left:${lvl * 1.4}em">${marker}<span>${inline(m[3])}</span></div>`);
      continue;
    }
    out.push(line.trim() ? `<p>${inline(line)}</p>` : '<br>');
  }
  return out.join('');
}

/** Toggle the todo marker on line `index` (used when tapping a checkbox in read mode). */
export function toggleCheckLine(text, index) {
  const lines = text.split('\n');
  const m = /^( *)(☐|☑)( .*)?$/.exec(lines[index] ?? '');
  if (!m) return text;
  lines[index] = m[1] + (m[2] === UNCHECKED ? CHECKED : UNCHECKED) + (m[3] || '');
  return lines.join('\n');
}
