import { test } from 'node:test';
import assert from 'node:assert/strict';
import { promoteChildren, applyShortcuts, changeIndent, toggleList, toggleCheck, cycleHeader, insertDivider } from '../public/shortcuts.js';
import { calc } from '../public/calc.js';
import * as H from '../public/habits.js';
import * as S from '../public/shifts.js';

// helper: simulate typing one char at end of `text`
const type = (text, ch) => applyShortcuts({ text, pos: text.length }, { text: text + ch, pos: text.length + 1 });

test('dash-space becomes bullet', () => assert.deepEqual(type('-', ' '), { text: '• ', pos: 2 }));
test('[] becomes todo', () => assert.deepEqual(type('[]', ' '), { text: '☐ ', pos: 2 }));
test('--- becomes divider', () => assert.deepEqual(type('--', '-'), { text: '---\n', pos: 4 }));
test('nested dash uses level bullet', () => assert.equal(type('    -', ' ').text, '    ◦ '));
test('smart quotes open/close', () => {
  assert.equal(type('', '"').text, '“');
  assert.equal(type('hi', '"').text, 'hi”');
});
test('enter continues bullet; todo continues unchecked', () => {
  assert.equal(type('• a', '\n').text, '• a\n• ');
  assert.equal(type('☑ a', '\n').text, '☑ a\n☐ ');
});
test('enter on empty item leaves list and promotes children', () => {
  assert.equal(type('• ', '\n').text, '');
  assert.equal(promoteChildren('    ◦ child\n        ▪ g\nplain', 0), '• child\n    ◦ g\nplain');
});
test('backspace after marker removes marker', () => {
  const r = applyShortcuts({ text: '• ', pos: 2 }, { text: '•', pos: 1 });
  assert.deepEqual(r, { text: '  ', pos: 2 });
});
test('plain typing is untouched', () => assert.deepEqual(type('abc', 'd'), { text: 'abcd', pos: 4 }));

test('indent/outdent, toggle list, check, header, divider', () => {
  assert.equal(changeIndent({ text: '• a', start: 3, end: 3 }, 1).text, '    ◦ a');
  assert.equal(changeIndent({ text: '    ◦ a', start: 3, end: 3 }, -1).text, '• a');
  assert.equal(toggleList({ text: 'a', start: 0, end: 0 }, false).text, '• a');
  assert.equal(toggleList({ text: '• a', start: 0, end: 0 }, false).text, 'a');
  assert.equal(toggleList({ text: '• a', start: 0, end: 0 }, true).text, '☐ a');
  assert.equal(toggleCheck({ text: '☐ a', start: 0, end: 0 }).text, '☑ a');
  assert.equal(cycleHeader({ text: 'a', start: 0, end: 0 }).text, '# a');
  assert.equal(cycleHeader({ text: '### a', start: 0, end: 0 }).text, 'a');
  assert.equal(insertDivider({ text: 'abc', start: 1, end: 1 }).text, 'abc\n---\n');
});

test('calc', () => {
  assert.equal(calc('1+2*3'), 7);
  assert.equal(calc('(1+2)*3'), 9);
  assert.equal(calc('1,250 × 4'), 5000);
  assert.equal(calc('50%'), 0.5);
  assert.equal(calc('0.1+0.2'), 0.3);
  assert.throws(() => calc('1/0'));
  assert.throws(() => calc('1+'));
  assert.throws(() => calc('alert(1)'));
});

test('habit dates + streaks', () => {
  assert.equal(H.weekStartOf('2026-11-12'), '2026-11-08'); // Thursday -> Sunday
  assert.equal(H.addDays('2026-12-31', 1), '2027-01-01');
  const h = { ...H.blankHabit(), created: '2026-11-01', done: ['2026-11-09', '2026-11-10', '2026-11-11'] };
  assert.equal(H.currentStreak(h, '2026-11-12'), 3); // today not done yet, doesn't break
  assert.equal(H.bestStreak(h, '2026-11-12'), 3);
  assert.equal(H.missedYesterday({ ...h, done: ['2026-11-10'] }, '2026-11-12'), true);
  const w = { ...H.blankHabit(), perWeek: 2, created: '2026-11-01', done: ['2026-11-09', '2026-11-10'] };
  assert.equal(H.currentStreak(w, '2026-11-12'), 1);
  assert.equal(H.toggleDone(w, '2026-11-09').done.length, 1);
});

test('shifts: spans and pay', () => {
  const doc = S.emptyDoc();
  const clinic = doc.types.find((t) => t.id === 'clinic');
  assert.deepEqual(S.spanOn(clinic, '2026-11-14', 'VIP'), { start: '08:00', end: '14:00' }); // Saturday VIP
  assert.deepEqual(S.spanOn(clinic, '2026-11-12', 'BMD'), { start: '16:00', end: '20:00' });
  assert.deepEqual(S.spanOn(clinic, '2026-11-14', 'BMD'), { start: '08:00', end: '12:00' });
  doc.days['2026-11-12'] = [S.makeEntry(doc.types[0], '2026-11-12', 'Port1'), { ...S.makeEntry(clinic, '2026-11-12', 'VIP'), pay: 1000 }];
  doc.others['2026-11'] = [{ name: 'x', amount: 100 }];
  const p = S.monthPay(doc, '2026-11');
  assert.deepEqual([p.shift, p.clinic, p.other, p.total, p.net], [900, 1000, 100, 2000, 1900]);
  assert.equal(S.typeSummary(doc, '2026-11').length, 2);
});

import { insertTable, toggleWrap, toggleCheckAt, tableEnter } from '../public/shortcuts.js';
import { renderNote, toggleCheckLine } from '../public/render.js';

test('tables: insert, enter adds row, empty last row exits', () => {
  const r = insertTable({ text: '', start: 0, end: 0 });
  assert.ok(r.text.startsWith('| หัวข้อ 1 | หัวข้อ 2 |\n|---|---|'));
  assert.equal(r.text.slice(r.start, r.end), 'หัวข้อ 1');
  assert.equal(type('| a | b |', '\n').text, '| a | b |\n|  |  |');
  assert.equal(type('| a | b |\n|  |  |', '\n').text, '| a | b |\n|  |  |\n');
  assert.equal(tableEnter('plain\n', 6), null);
});

test('inline marks toggle', () => {
  assert.deepEqual(toggleWrap({ text: 'abc', start: 0, end: 3 }, '**'), { text: '**abc**', start: 2, end: 5 });
  assert.equal(toggleWrap({ text: '**abc**', start: 2, end: 5 }, '**').text, 'abc');
  assert.equal(toggleWrap({ text: '', start: 0, end: 0 }, '~~').text, '~~~~');
});

test('tap on checkbox glyph toggles only there', () => {
  assert.equal(toggleCheckAt('☐ a', 0), '☑ a');
  assert.equal(toggleCheckAt('☐ a', 1), '☑ a');
  assert.equal(toggleCheckAt('☐ a', 3), null);
  assert.equal(toggleCheckAt('    ☑ a', 4), '    ☐ a');
  assert.equal(toggleCheckAt('• a', 0), null);
});

test('renderNote: headers, lists, todos, tables, marks, escaping', () => {
  const h = renderNote('# T\n• **b** <i>\n    ☐ todo\n☑ done\n---\n| h1 | h2 |\n|---|---|\n| 1 | 2 |');
  assert.match(h, /<h1 class="nh">T<\/h1>/);
  assert.match(h, /<b>b<\/b> &lt;i&gt;/);
  assert.match(h, /data-line="2"[^>]*>☐/);
  assert.match(h, /li done/);
  assert.match(h, /<hr>/);
  assert.match(h, /<th>h1<\/th>/);
  assert.match(h, /<td>1<\/td>/);
  assert.equal(toggleCheckLine('a\n☐ x', 1), 'a\n☑ x');
  assert.equal(toggleCheckLine('a\n• x', 1), 'a\n• x');
});
