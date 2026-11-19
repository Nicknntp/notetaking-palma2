import * as Sh from './shortcuts.js';
import * as H from './habits.js';
import * as Sf from './shifts.js';
import { calc } from './calc.js';
import { renderNote, toggleCheckLine } from './render.js';
import * as D from './sync.js';

const $ = (s, r = document) => r.querySelector(s);
const view = $('#view');
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const thaiDate = (iso) => new Date(iso + 'T00:00:00').toLocaleDateString('th-TH', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
const setTitle = (t) => { $('#title').textContent = t; };

// ---------- save debounce (flushed on navigation / hide) ----------
let flushSave = null;
const runFlush = () => { if (flushSave) { const f = flushSave; flushSave = null; f(); } };
function debounced(fn, ms = 500) {
  let t = null;
  return (...a) => {
    clearTimeout(t);
    flushSave = () => { clearTimeout(t); fn(...a); };
    t = setTimeout(() => { flushSave = null; fn(...a); }, ms);
  };
}
document.addEventListener('visibilitychange', () => { if (document.hidden) runFlush(); });
window.addEventListener('pagehide', runFlush);

// ---------- modal ----------
function modal(html) {
  const bg = document.createElement('div');
  bg.className = 'modal-bg';
  bg.innerHTML = `<div class="modal">${html}</div>`;
  document.body.appendChild(bg);
  const close = () => bg.remove();
  bg.addEventListener('click', (e) => { if (e.target === bg) close(); });
  return { el: bg.firstChild, close };
}

// ---------- status ----------
D.onStatus(({ pending, online, reload }) => {
  const el = $('#status');
  el.className = online ? '' : 'bad';
  el.textContent = !online ? `ออฟไลน์${pending ? ` · รอซิงก์ ${pending}` : ''}` : pending ? `⏳ รอซิงก์ ${pending}` : '✓ ซิงก์แล้ว';
  if (reload && location.hash.includes(reload.split(':')[1] || '\u0000')) route();
});
D.setConflictHandler(async (item) =>
  confirm(`"${item.key.split(':')[1]}" ถูกแก้จากอีกเครื่องไปแล้ว\n\nOK = ใช้ของเครื่องนี้ทับ\nยกเลิก = ใช้ของเซิร์ฟเวอร์ (ของเครื่องนี้จะหาย)`));

// ---------- text editor (textarea + notion-style shortcuts) ----------
function mountEditor(host, text, onChange, extra = '') {
  host.innerHTML = `<div class="editor"><textarea autocapitalize="off" autocorrect="off" spellcheck="false" placeholder="พิมพ์ที่นี่…  (- เว้นวรรค = bullet,  [] = ☐,  --- = เส้นคั่น)"></textarea>
  <div class="tools">
    <button data-a="out" title="ลดระดับ">⇤</button><button data-a="in" title="เพิ่มระดับ">⇥</button>
    <button data-a="bullet">•</button><button data-a="todo">☐</button><button data-a="check">☑</button>
    <button data-a="div">─</button><button data-a="head">H</button><button data-a="b"><b>B</b></button><button data-a="u"><u>U</u></button><button data-a="s"><s>S</s></button>
    <button data-a="table">▦</button><button data-a="emoji">😀</button><button data-a="calc">🧮</button><button data-a="view" title="โหมดอ่าน (แตะ ☐ เพื่อติ๊ก)">👁</button>${extra}
  </div><div class="reader" hidden></div></div>`;
  const ta = $('textarea', host);
  const reader = $('.reader', host);
  ta.value = text;
  const setMode = (read) => {
    reader.hidden = !read; ta.hidden = read;
    if (read) reader.innerHTML = renderNote(ta.value) || '<p class="muted">(ว่าง)</p>';
    try { localStorage.setItem('dn:readmode', read ? '1' : '0'); } catch { /* ignore */ }
  };
  reader.addEventListener('click', (e) => {
    const cb = e.target.closest('.cb');
    if (!cb) { setMode(false); ta.focus(); return; }
    ta.value = toggleCheckLine(ta.value, +cb.dataset.line); onChange(ta.value); setMode(true);
  });
  ta.addEventListener('click', () => {
    if (ta.selectionStart !== ta.selectionEnd) return;
    const r = Sh.toggleCheckAt(ta.value, ta.selectionStart);
    if (r) { const p = ta.selectionStart; ta.value = r; ta.setSelectionRange(p, p); onChange(ta.value); }
  });
  let prev = { text: ta.value, pos: ta.selectionStart, collapsed: true };
  ta.addEventListener('beforeinput', () => { prev = { text: ta.value, pos: ta.selectionStart, collapsed: ta.selectionStart === ta.selectionEnd }; });
  ta.addEventListener('input', (e) => {
    if (!e.isComposing && prev.collapsed && ta.selectionStart === ta.selectionEnd) {
      const cur = { text: ta.value, pos: ta.selectionStart };
      const r = Sh.applyShortcuts(prev, cur);
      if (r.text !== cur.text || r.pos !== cur.pos) { ta.value = r.text; ta.setSelectionRange(r.pos, r.pos); }
    }
    onChange(ta.value);
  });
  const act = (fn) => {
    const r = fn({ text: ta.value, start: ta.selectionStart, end: ta.selectionEnd });
    ta.value = r.text; ta.setSelectionRange(r.start, r.end); ta.focus(); onChange(ta.value);
  };
  ta.addEventListener('keydown', (e) => {
    if (e.key === 'Tab') { e.preventDefault(); act((s) => Sh.changeIndent(s, e.shiftKey ? -1 : 1)); }
    else if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); act(Sh.toggleCheck); }
  });
  try { if (localStorage.getItem('dn:readmode') === '1' && text) setMode(true); } catch { /* ignore */ }
  $('.tools', host).addEventListener('mousedown', (e) => e.preventDefault()); // keep keyboard/cursor
  $('.tools', host).addEventListener('click', (e) => {
    const a = e.target.closest('button')?.dataset.a;
    if (!a) return;
    if (a === 'in') act((s) => Sh.changeIndent(s, 1));
    if (a === 'out') act((s) => Sh.changeIndent(s, -1));
    if (a === 'bullet') act((s) => Sh.toggleList(s, false));
    if (a === 'todo') act((s) => Sh.toggleList(s, true));
    if (a === 'check') act(Sh.toggleCheck);
    if (a === 'div') act(Sh.insertDivider);
    if (a === 'head') act(Sh.cycleHeader);
    if (a === 'b') act((s) => Sh.toggleWrap(s, '**'));
    if (a === 'u') act((s) => Sh.toggleWrap(s, '__'));
    if (a === 's') act((s) => Sh.toggleWrap(s, '~~'));
    if (a === 'table') act(Sh.insertTable);
    if (a === 'emoji') emojiModal((str) => act((s) => Sh.insertAtCursor(s, str)));
    if (a === 'view') setMode(reader.hidden);
    if (a === 'calc') calcModal((str) => act((s) => Sh.insertAtCursor(s, str)));
  });
  return { ta, host };
}

const EMOJI = {
  'อารมณ์': '😀 😄 😁 😊 🙂 😍 🥰 😘 😎 🤩 🥳 😌 😴 🤔 😐 😅 😂 🤣 😢 😭 😤 😡 🥺 😱',
  'สัญลักษณ์': '❤️ 💔 ⭐ ✨ 🔥 ✅ ❌ ⚠️ ❗ ❓ 💡 📌 📍 🔔 💯 🎯 ➡️ ⬅️ ⬆️ ⬇️ 🏷️ 🔖 ♻️ ⏰',
  'กิจกรรม': '📚 ✏️ 💻 📝 🏃 🏋️ 🧘 🚴 🍳 ☕ 🍜 🛒 💊 💰 ✈️ 🚗 🏠 🎵 🎮 🎬 📞 💬 🛏️ 🎁',
  'ธรรมชาติ': '☀️ 🌤️ ☁️ 🌧️ ⛈️ ❄️ 🌈 🌙 🌸 🌳 🍀 🌊 🐶 🐱 🐟 🦋 🍎 🍉 🍓 🥑 🌽 🍞 🧀 🍰',
};
function emojiModal(insert) {
  let recent = [];
  try { recent = JSON.parse(localStorage.getItem('dn:recent-emoji') || '[]'); } catch { /* ignore */ }
  const groups = { ...(recent.length ? { 'ล่าสุด': recent.join(' ') } : {}), ...EMOJI };
  const m = modal(Object.entries(groups).map(([k, v]) => `<div class="muted" style="margin-top:8px">${esc(k)}</div><div class="emo">${v.split(' ').map((e) => `<button data-e="${e}">${e}</button>`).join('')}</div>`).join('') + '<div class="actions"><button data-x="close">ปิด</button></div>');
  m.el.addEventListener('click', (e) => {
    const em = e.target.closest('[data-e]')?.dataset.e;
    if (em) { insert(em); recent = [em, ...recent.filter((x) => x !== em)].slice(0, 24); try { localStorage.setItem('dn:recent-emoji', JSON.stringify(recent)); } catch { /* ignore */ } }
    if (e.target.dataset.x) m.close();
  });
}

function calcModal(insert) {
  const keys = ['7', '8', '9', '÷', '4', '5', '6', '×', '1', '2', '3', '-', '0', '.', '%', '+', '(', ')', '⌫', 'C'];
  const m = modal(`<h2 style="margin:0 0 8px">เครื่องคิดเลข</h2>
    <input id="ce" inputmode="none" autocomplete="off" placeholder="เช่น 1250*4+900">
    <div id="cr" style="font-size:28px;text-align:right;margin:8px 0;min-height:42px"></div>
    <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:6px">${keys.map((k) => `<button data-k="${k}">${k}</button>`).join('')}</div>
    <div class="actions"><button data-x="cancel">ปิด</button><button data-x="res">แทรกผล</button><button class="primary" data-x="both">แทรก นิพจน์ = ผล</button></div>`);
  const ce = $('#ce', m.el), cr = $('#cr', m.el);
  let val = null;
  const upd = () => { try { val = calc(ce.value); cr.textContent = '= ' + Sf.baht(val); cr.classList.remove('err'); } catch (e) { val = null; cr.textContent = ce.value ? '…' : ''; } };
  ce.addEventListener('input', upd);
  m.el.addEventListener('click', (e) => {
    const k = e.target.closest('[data-k]')?.dataset.k, x = e.target.closest('[data-x]')?.dataset.x;
    if (k) { ce.value = k === 'C' ? '' : k === '⌫' ? ce.value.slice(0, -1) : ce.value + k; upd(); }
    if (x === 'cancel') m.close();
    if ((x === 'res' || x === 'both') && val !== null) { insert(x === 'both' ? `${ce.value} = ${Sf.baht(val)}` : Sf.baht(val)); m.close(); }
  });
}

// ---------- pages ----------
async function noteView(date) {
  date = date || H.today();
  setTitle(thaiDate(date));
  view.innerHTML = `<div class="bar"><button data-d="-1">‹</button><span class="grow" id="dl">${esc(thaiDate(date))}</span><button data-d="1">›</button><button data-d="0">วันนี้</button></div><div id="ed" class="editor"></div>`;
  const go = (d) => { runFlush(); location.hash = '#/note/' + d; };
  $('.bar', view).addEventListener('click', (e) => {
    const d = e.target.closest('[data-d]')?.dataset.d;
    if (d === undefined) return;
    go(d === '0' ? H.today() : H.addDays(date, +d));
  });
  const n = await D.loadNote(date).catch(() => ({ text: '' }));
  const save = debounced((t) => D.saveNote(date, t));
  mountEditor($('#ed'), n.text, save);
}

async function historyView() {
  setTitle('ประวัติ');
  view.innerHTML = `<div class="bar"><input type="date" id="jump" value="${H.today()}"><button id="go">ไปวันที่</button></div><div class="list" id="l"></div>`;
  $('#go').onclick = () => { if ($('#jump').value) location.hash = '#/note/' + $('#jump').value; };
  const l = await D.listNotes();
  $('#l').innerHTML = l.length ? l.map((x) => `<a href="#/note/${x.date}"><div><div>${esc(thaiDate(x.date))}</div><div class="sub">${esc(x.preview.slice(0, 80))}</div></div></a>`).join('') : '<p class="pad muted">ยังไม่มีบันทึก</p>';
}

async function freeListView() {
  setTitle('โน้ตอิสระ');
  view.innerHTML = `<div class="bar"><button class="primary" id="new">＋ โน้ตใหม่</button></div><div class="list" id="l"></div>`;
  $('#new').onclick = () => { location.hash = '#/free/n' + Date.now().toString(36); };
  const l = await D.listFree();
  $('#l').innerHTML = l.length ? l.map((x) => `<a href="#/free/${x.id}"><span>${esc(x.emoji || (x.pinned ? '📌' : '🗒'))}</span><div><div>${esc(x.title || '(ว่าง)')}</div>${x.pinned && x.emoji ? '<div class="sub">📌 ปักหมุด</div>' : ''}</div></a>`).join('') : '<p class="pad muted">ยังไม่มีโน้ต</p>';
}

async function freeEditView(id) {
  const n = await D.loadFree(id).catch(() => ({ text: '', pinned: false, emoji: '' }));
  let { pinned = false, emoji = '' } = n;
  setTitle('โน้ต');
  view.innerHTML = `<div class="bar"><a href="#/free"><button>‹ กลับ</button></a><span class="grow"></span><button id="emo">${esc(emoji || '😀')}</button><button id="pin">${pinned ? '📌' : '📍'}</button><button id="del" class="danger">🗑</button></div><div id="ed" class="editor"></div>`;
  const save = debounced((t) => D.saveFree(id, { text: t }));
  mountEditor($('#ed'), n.text || '', save);
  $('#emo').onclick = () => { const e = prompt('อีโมจิของโน้ต (เว้นว่าง = ไม่ใช้)', emoji); if (e !== null) { emoji = e.trim(); $('#emo').textContent = emoji || '😀'; D.saveFree(id, { emoji }); } };
  $('#pin').onclick = () => { pinned = !pinned; $('#pin').textContent = pinned ? '📌' : '📍'; D.saveFree(id, { pinned }); };
  $('#del').onclick = async () => { if (confirm('ลบโน้ตนี้?')) { flushSave = null; await D.deleteFree(id); location.hash = '#/free'; } };
}

// ----- habits -----
let weekOffset = 0;
async function habitsView() {
  setTitle('นิสัย');
  const doc = await D.loadDoc('habits').catch(() => ({ habits: [] }));
  doc.habits ||= [];
  const t = H.today();
  const ws = H.addDays(H.weekStartOf(t), weekOffset * 7);
  const names = ['อา', 'จ', 'อ', 'พ', 'พฤ', 'ศ', 'ส'];
  const save = () => D.saveDoc('habits', doc);

  const draw = () => {
    view.innerHTML = `<div class="bar"><button data-w="-1">‹</button><span class="grow">${esc(thaiDate(ws))} – ${esc(thaiDate(H.addDays(ws, 6)))}</span><button data-w="1">›</button><button data-w="0">สัปดาห์นี้</button></div>
    <div id="hs">${doc.habits.filter((h) => !H.completed(h, t) || weekOffset < 0).map((h) => {
      const cnt = H.weekCount(h, ws), goal = H.goalFor(h, ws);
      return `<div class="habit" data-id="${esc(h.id)}"><div class="row"><span>${esc(h.emoji)}</span><span class="name" data-edit>${esc(h.name)}</span>
        <span class="muted">${cnt}/${H.daily(h) ? 7 : goal} · 🔥${H.currentStreak(h, t)} · ${H.rate30(h, t)}%</span></div>
        ${H.missedYesterday(h, t) ? '<div class="err" style="font-size:14px">อย่าพลาด 2 วันติด!</div>' : ''}
        <div class="week">${[0, 1, 2, 3, 4, 5, 6].map((i) => { const d = H.addDays(ws, i); const on = h.done.includes(d); return `<button data-day="${d}" class="${on ? 'done' : ''} ${d === t ? 'today' : ''} ${H.activeOn(h, d) ? '' : 'off'}">${names[i]}<br>${+d.slice(8)}</button>`; }).join('')}</div></div>`;
    }).join('') || '<p class="pad muted">ยังไม่มีนิสัย กด ＋ เพื่อเพิ่ม</p>'}</div>
    <div class="bar"><button class="primary" id="addh">＋ เพิ่มนิสัย</button></div>`;
  };
  draw();
  view.onclick = (e) => {
    const w = e.target.closest('[data-w]')?.dataset.w;
    if (w !== undefined) { weekOffset = w === '0' ? 0 : weekOffset + +w; return habitsView(); }
    if (e.target.id === 'addh') return editHabit(H.blankHabit());
    const card = e.target.closest('.habit');
    if (!card) return;
    const h = doc.habits.find((x) => x.id === card.dataset.id);
    if (e.target.closest('[data-edit]')) return editHabit(h);
    const day = e.target.closest('[data-day]')?.dataset.day;
    if (day) { doc.habits[doc.habits.indexOf(h)] = H.toggleDone(h, day); save(); draw(); }
  };
  function editHabit(h) {
    const isNew = !h.id;
    const m = modal(`<h2 style="margin:0 0 4px">${isNew ? 'นิสัยใหม่' : 'แก้ไขนิสัย'}</h2>
      <label>ชื่อ</label><input id="hn" value="${esc(h.name)}">
      <label>อีโมจิ</label><input id="he" value="${esc(h.emoji)}">
      <label>เป้าหมายต่อสัปดาห์ (7 = ทุกวัน)</label><select id="hp">${[7, 6, 5, 4, 3, 2, 1].map((n) => `<option ${n === h.perWeek ? 'selected' : ''}>${n}</option>`).join('')}</select>
      <label>ระยะเวลา (วัน, 0 = ไม่กำหนด)</label><input id="hd" type="number" min="0" value="${h.days}">
      <div class="actions">${isNew ? '' : '<button class="danger" data-x="del">ลบ</button>'}<button data-x="cancel">ยกเลิก</button><button class="primary" data-x="ok">บันทึก</button></div>`);
    m.el.onclick = (e) => {
      const x = e.target.dataset.x;
      if (!x) return;
      if (x === 'del') { if (confirm('ลบนิสัยนี้และประวัติทั้งหมด?')) { doc.habits = doc.habits.filter((y) => y.id !== h.id); save(); m.close(); draw(); } return; }
      if (x === 'ok') {
        const name = $('#hn', m.el).value.trim();
        if (!name) return;
        const nh = { ...h, name, emoji: $('#he', m.el).value.trim(), perWeek: +$('#hp', m.el).value, days: Math.max(0, +$('#hd', m.el).value || 0) };
        if (isNew) { nh.id = String(Date.now()); doc.habits.push(nh); } else doc.habits[doc.habits.findIndex((y) => y.id === h.id)] = nh;
        save(); draw();
      }
      m.close();
    };
  }
}

// ----- shifts -----
let shiftYm = H.today().slice(0, 7);
async function shiftsView() {
  setTitle('เวร / ค่าเวร');
  const doc = Sf.normDoc(await D.loadDoc('shifts').catch(() => null));
  const save = () => D.saveDoc('shifts', doc);
  const shiftYmAdd = (n) => { const [y, m] = shiftYm.split('-').map(Number); const d = new Date(Date.UTC(y, m - 1 + n, 1)); shiftYm = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`; };

  const draw = () => {
    const p = Sf.monthPay(doc, shiftYm), rows = Sf.monthIndexed(doc, shiftYm), others = doc.others[shiftYm] || [];
    const [y, mo] = shiftYm.split('-').map(Number);
    view.innerHTML = `<div class="bar"><button data-m="-1">‹</button><span class="grow">${esc(new Date(Date.UTC(y, mo - 1, 1)).toLocaleDateString('th-TH', { month: 'long', year: 'numeric', timeZone: 'UTC' }))}</span><button data-m="1">›</button></div>
    <div class="card"><table class="sum" style="width:100%">
      ${Sf.typeSummary(doc, shiftYm).map((s) => `<tr><td>${esc(s.name)} × ${s.count}</td><td>${Sf.baht(s.pay)}</td></tr>`).join('')}
      <tr><td>เงินเวร</td><td>${Sf.baht(p.shift)}</td></tr><tr><td>คลินิคพิเศษ</td><td>${Sf.baht(p.clinic)}</td></tr><tr><td>อื่นๆ</td><td>${Sf.baht(p.other)}</td></tr>
      <tr><td><b>รวม</b></td><td><b>${Sf.baht(p.total)}</b></td></tr><tr><td>สุทธิ (หัก 5%)</td><td>${Sf.baht(p.net)}</td></tr></table></div>
    <div class="bar"><button class="primary" id="adds">＋ ลงเวร</button><button id="addo">＋ รายการอื่นๆ</button><button id="types">⚙ ประเภทเวร</button></div>
    <div class="list">${rows.map(([d, i, e]) => `<div class="item" data-d="${d}" data-i="${i}"><div style="flex:1"><div>${esc(thaiDate(d))}</div><div class="sub">${esc(e.typeName)} · ${esc(e.name)} · ${esc(Sf.timeRange(e.start, e.end))}</div></div>
      ${e.clinic ? `<input type="number" inputmode="decimal" style="width:90px" value="${e.pay}" data-pay>` : `<span>${Sf.baht(e.pay)}</span>`}<button class="danger" data-del>✕</button></div>`).join('') || '<p class="pad muted">ยังไม่มีเวรเดือนนี้</p>'}</div>
    ${others.length ? '<h2>อื่นๆ</h2>' : ''}<div class="list">${others.map((o, i) => `<div class="item" data-o="${i}"><span style="flex:1">${esc(o.name)}</span><span>${Sf.baht(o.amount)}</span><button class="danger" data-delo>✕</button></div>`).join('')}</div>`;
  };
  draw();

  view.onclick = (e) => {
    const t = e.target;
    const mm = t.closest('[data-m]')?.dataset.m;
    if (mm) { shiftYmAdd(+mm); return draw(); }
    if (t.id === 'adds') return addShift();
    if (t.id === 'addo') return addOther();
    if (t.id === 'types') return editTypes();
    const item = t.closest('.item');
    if (!item) return;
    if (t.matches('[data-del]') && item.dataset.d && confirm('ลบเวรนี้?')) { const l = doc.days[item.dataset.d]; l.splice(+item.dataset.i, 1); if (!l.length) delete doc.days[item.dataset.d]; save(); draw(); }
    if (t.matches('[data-delo]') && confirm('ลบรายการนี้?')) { doc.others[shiftYm].splice(+item.dataset.o, 1); save(); draw(); }
  };
  view.onchange = (e) => {
    if (!e.target.matches('[data-pay]')) return;
    const item = e.target.closest('.item');
    doc.days[item.dataset.d][+item.dataset.i].pay = +e.target.value || 0;
    save(); draw();
  };

  function addShift() {
    const t0 = H.today();
    const m = modal(`<h2 style="margin:0 0 4px">ลงเวร</h2><label>วันที่</label><input type="date" id="sd" value="${t0.startsWith(shiftYm) ? t0 : shiftYm + '-01'}">
      <label>ประเภท</label><select id="st">${doc.types.map((t) => `<option value="${esc(t.id)}">${esc(t.name)}</option>`).join('')}</select>
      <label>ชื่อเวร</label><select id="sn"></select><div class="muted" id="sp" style="margin-top:6px"></div>
      <div class="actions"><button data-x="cancel">ยกเลิก</button><button class="primary" data-x="ok">ลงเวร</button></div>`);
    const typ = () => doc.types.find((t) => t.id === $('#st', m.el).value);
    const fillNames = () => { $('#sn', m.el).innerHTML = typ().names.map((n) => `<option>${esc(n)}</option>`).join(''); span(); };
    const span = () => { const d = $('#sd', m.el).value, n = $('#sn', m.el).value; if (d && n) { const s = Sf.spanOn(typ(), d, n); $('#sp', m.el).textContent = Sf.timeRange(s.start, s.end); } };
    $('#st', m.el).onchange = fillNames; $('#sn', m.el).onchange = span; $('#sd', m.el).onchange = span; fillNames();
    m.el.onclick = (e) => {
      const x = e.target.dataset.x;
      if (x === 'ok') {
        const d = $('#sd', m.el).value, n = $('#sn', m.el).value;
        if (!d || !n) return;
        (doc.days[d] ||= []).push(Sf.makeEntry(typ(), d, n));
        shiftYm = d.slice(0, 7); save(); draw();
      }
      if (x) m.close();
    };
  }
  function addOther() {
    const m = modal(`<h2 style="margin:0 0 4px">รายการอื่นๆ</h2><label>ชื่อ</label><input id="on"><label>จำนวนเงิน (ติดลบได้)</label><input id="oa" type="number" inputmode="decimal" step="any">
      <div class="actions"><button data-x="cancel">ยกเลิก</button><button class="primary" data-x="ok">เพิ่ม</button></div>`);
    m.el.onclick = (e) => {
      const x = e.target.dataset.x;
      if (x === 'ok') { const name = $('#on', m.el).value.trim() || 'อื่นๆ', amount = parseFloat($('#oa', m.el).value); if (isNaN(amount)) return; (doc.others[shiftYm] ||= []).push({ name, amount }); save(); draw(); }
      if (x) m.close();
    };
  }
  function editTypes() {
    const types = doc.types.map((t) => ({ ...t }));
    const render = () => {
      m.el.innerHTML = `<h2 style="margin:0 0 4px">ประเภทเวร</h2>${types.map((t, i) => `<div class="card" data-i="${i}" style="margin:8px 0">
        <label>ชื่อประเภท</label><input data-f="name" value="${esc(t.name)}">
        <div class="row"><div style="flex:1"><label>เริ่ม</label><input type="time" data-f="start" value="${t.start}"></div><div style="flex:1"><label>จบ</label><input type="time" data-f="end" value="${t.end}"></div></div>
        <div class="row"><div style="flex:1"><label>เริ่ม (ส-อา)</label><input type="time" data-f="wkStart" value="${t.wkStart || ''}"></div><div style="flex:1"><label>จบ (ส-อา)</label><input type="time" data-f="wkEnd" value="${t.wkEnd || ''}"></div></div>
        <label>ค่าเวร (บาท)</label><input type="number" data-f="pay" value="${t.pay}">
        <label>ชื่อเวร (คั่นด้วย ,)</label><input data-f="names" value="${esc(t.names.join(', '))}">
        <label><input type="checkbox" data-f="clinic" ${t.clinic ? 'checked' : ''} style="width:auto;min-height:0"> นับเป็นคลินิคพิเศษ (กรอกค่าเวรทีหลัง)</label>
        <button class="danger" data-rm="${i}" style="margin-top:6px">ลบประเภทนี้</button></div>`).join('')}
        <button data-x="add">＋ เพิ่มประเภท</button><div class="actions"><button data-x="cancel">ยกเลิก</button><button class="primary" data-x="ok">บันทึก</button></div>`;
    };
    const read = () => $('.modal', document).querySelectorAll('[data-i]').forEach((card) => {
      const t = types[+card.dataset.i];
      card.querySelectorAll('[data-f]').forEach((inp) => {
        const f = inp.dataset.f;
        t[f] = f === 'clinic' ? inp.checked : f === 'pay' ? +inp.value || 0 : f === 'names' ? inp.value.split(',').map((s) => s.trim()).filter(Boolean) : inp.value;
      });
    });
    const m = modal('');
    render();
    m.el.onclick = (e) => {
      const x = e.target.dataset.x, rm = e.target.dataset.rm;
      if (rm !== undefined) { read(); types.splice(+rm, 1); render(); }
      if (x === 'add') { read(); types.push({ id: 't' + Date.now(), name: 'เวรใหม่', start: '08:00', end: '16:00', wkStart: '', wkEnd: '', pay: 0, clinic: false, scheds: {}, names: [] }); render(); }
      if (x === 'ok') { read(); doc.types = types; save(); draw(); }
      if (x === 'ok' || x === 'cancel') m.close();
    };
  }
}


// ----- calendar (CalDAV via server) -----
let calWeek = 0;
const pad2 = (n) => String(n).padStart(2, '0');
const localDay = (d) => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
const hm = (d) => `${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
const toLocalInput = (iso) => { const d = new Date(iso); return `${localDay(d)}T${hm(d)}`; };

async function calendarView() {
  setTitle('ปฏิทิน');
  const t = H.today();
  const ws = H.addDays(H.weekStartOf(t), calWeek * 7);
  view.innerHTML = `<div class="bar"><button data-w="-1">‹</button><span class="grow">${esc(thaiDate(ws))} – ${esc(thaiDate(H.addDays(ws, 6)))}</span><button data-w="1">›</button><button data-w="0">สัปดาห์นี้</button></div><div id="cl"><p class="pad muted">กำลังโหลด…</p></div>`;
  const nav = (ev) => { const w = ev.target.closest('[data-w]')?.dataset.w; if (w === undefined) return false; calWeek = w === '0' ? 0 : calWeek + +w; calendarView(); return true; };
  let cals, evs;
  try {
    const from = new Date(ws + 'T00:00:00').toISOString(), to = new Date(H.addDays(ws, 7) + 'T00:00:00').toISOString();
    [cals, evs] = await Promise.all([D.api('GET', '/api/cal/calendars'), D.api('GET', `/api/cal/events?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`)]);
  } catch (e) {
    $('#cl').innerHTML = `<p class="pad err">${e.status === 501 ? 'ยังไม่ได้ตั้งค่า CalDAV — ใส่ url / username / รหัสผ่านเฉพาะแอปใน data/config.json (ดู web/README.md)' : esc(e.message)}</p>`;
    view.onclick = nav;
    return;
  }
  const byDay = {};
  for (let i = 0; i < 7; i++) byDay[H.addDays(ws, i)] = [];
  evs.forEach((e) => {
    const s = e.allDay ? e.start : localDay(new Date(e.start));
    let endDay = e.allDay ? H.addDays(e.end, -1) : localDay(new Date(new Date(e.end) - 1));
    if (endDay < s) endDay = s;
    for (let d = s; d <= endDay; d = H.addDays(d, 1)) if (byDay[d]) byDay[d].push(e);
  });
  const names = ['อา', 'จ', 'อ', 'พ', 'พฤ', 'ศ', 'ส'];
  const timeOf = (e) => (e.allDay ? 'ทั้งวัน' : `${hm(new Date(e.start))}–${hm(new Date(e.end))}`);
  $('#cl').innerHTML = Object.entries(byDay).map(([d, l], i) => `<div class="day ${d === t ? 'today' : ''}"><div class="row"><h3 style="flex:1">${names[i]} ${+d.slice(8)}</h3><button data-add="${d}">＋</button></div>
    ${l.map((e) => `<div class="ev" data-i="${evs.indexOf(e)}"><span class="t">${timeOf(e)}</span><span>${esc(e.title || '(ไม่มีชื่อ)')}${e.recurring ? ' 🔁' : ''}<span class="muted"> · ${esc(e.calendarName)}</span></span></div>`).join('')}</div>`).join('');
  view.onclick = (ev) => {
    if (nav(ev)) return;
    const add = ev.target.closest('[data-add]')?.dataset.add;
    if (add) return editEvent({ title: '', allDay: false, start: new Date(add + 'T09:00:00').toISOString(), end: new Date(add + 'T10:00:00').toISOString(), location: '' });
    const i = ev.target.closest('.ev')?.dataset.i;
    if (i !== undefined) editEvent(evs[+i]);
  };
  function editEvent(e) {
    const isNew = !e.href;
    if (e.recurring) {
      const rm = modal(`<h2 style="margin:0 0 8px">${esc(e.title)}</h2><p>${esc(timeOf(e))} · นัดซ้ำ แก้ในแอปปฏิทินเดิม</p><div class="actions"><button data-x="c">ปิด</button></div>`);
      rm.el.onclick = (x) => { if (x.target.dataset.x) rm.close(); };
      return;
    }
    const m = modal(`<h2 style="margin:0 0 4px">${isNew ? 'นัดใหม่' : 'แก้ไขนัด'}</h2>
      <label>ชื่อ</label><input id="et" value="${esc(e.title)}">
      <label>ปฏิทิน</label><select id="ec">${cals.map((c) => `<option value="${esc(c.href)}" ${c.href === e.calendar ? 'selected' : ''}>${esc(c.name)}</option>`).join('')}</select>
      <label><input type="checkbox" id="ea" ${e.allDay ? 'checked' : ''} style="width:auto;min-height:0"> ทั้งวัน</label>
      <div id="et2"></div><label>สถานที่</label><input id="el" value="${esc(e.location || '')}">
      <div class="actions">${isNew ? '' : '<button class="danger" data-x="del">ลบ</button>'}<button data-x="c">ยกเลิก</button><button class="primary" data-x="ok">บันทึก</button></div>`);
    // keep the current field values when toggling all-day
    let cur = { all: e.allDay, s: e.allDay ? e.start : toLocalInput(e.start), en: e.allDay ? H.addDays(e.end, -1) : toLocalInput(e.end) };
    const times = () => {
      const ty = cur.all ? 'date' : 'datetime-local';
      $('#et2', m.el).innerHTML = `<label>เริ่ม</label><input type="${ty}" id="es" value="${cur.s}"><label>จบ</label><input type="${ty}" id="ee" value="${cur.en}">`;
    };
    $('#ea', m.el).onchange = () => {
      const s = $('#es', m.el).value, en = $('#ee', m.el).value;
      const all = $('#ea', m.el).checked;
      cur = all ? { all, s: s.slice(0, 10), en: en.slice(0, 10) } : { all, s: s.slice(0, 10) + 'T09:00', en: en.slice(0, 10) + 'T10:00' };
      times();
    };
    times();
    m.el.onclick = async (x) => {
      const a = x.target.dataset.x;
      if (!a) return;
      try {
        if (a === 'del') { if (!confirm('ลบนัดนี้?')) return; await D.api('DELETE', '/api/cal/events?href=' + encodeURIComponent(e.href)); }
        if (a === 'ok') {
          const all = $('#ea', m.el).checked, s = $('#es', m.el).value, en = $('#ee', m.el).value;
          const title = $('#et', m.el).value.trim();
          if (!s || !en || !title) return;
          await D.api('PUT', '/api/cal/events', { href: e.href, calendar: $('#ec', m.el).value, title, allDay: all, location: $('#el', m.el).value.trim(),
            start: all ? s : new Date(s).toISOString(), end: all ? H.addDays(en, 1) : new Date(en).toISOString() });
        }
        m.close(); calendarView();
      } catch (err) { alert('ไม่สำเร็จ: ' + err.message); }
    };
  }
}

// ----- login -----
function showLogin() {
  if ($('#login')) return;
  const bg = document.createElement('div');
  bg.id = 'login'; bg.className = 'modal-bg';
  bg.innerHTML = '<form class="modal"><h2 style="margin:0 0 8px">เข้าสู่ระบบ</h2><input type="password" id="pw" autocomplete="current-password" placeholder="รหัสผ่าน"><div class="err" id="le"></div><div class="actions"><button class="primary">เข้าสู่ระบบ</button></div></form>';
  document.body.appendChild(bg);
  $('#pw').focus();
  bg.firstChild.onsubmit = async (e) => {
    e.preventDefault();
    try { await D.api('POST', '/api/login', { password: $('#pw').value }); bg.remove(); D.flush(); route(); } catch { $('#le').textContent = 'รหัสผ่านไม่ถูกต้อง'; }
  };
}
D.setAuthHandler(showLogin);

// ----- reminders (web push) -----
const b64ToU8 = (s) => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));
async function reminderCard(host) {
  let info;
  try { info = await D.api('GET', '/api/push/info'); } catch (e) { host.innerHTML = `<span class="err">${esc(e.message)}</span>`; return; }
  const supported = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
  let reg = null, sub = null;
  if (supported) { try { reg = await Promise.race([navigator.serviceWorker.ready, new Promise((r) => setTimeout(r, 1500))]); if (reg) sub = await reg.pushManager.getSubscription(); } catch { /* no service worker (http) */ } }
  const r = info.reminder;
  const draw = () => {
    host.innerHTML = `<label class="row"><input type="checkbox" id="rme" ${r.enabled ? 'checked' : ''} style="width:auto;min-height:0"> แจ้งเตือนให้จดบันทึกทุกวัน</label>
    <div class="row wrap" style="margin:8px 0">${r.times.map((t) => `<button data-rm="${t}">${t} ✕</button>`).join('')}<input type="time" id="rmt"><button id="rma">＋ เพิ่มเวลา</button></div>
    <div class="row wrap"><button id="rmon" class="${sub ? '' : 'primary'}">${sub ? '✓ เครื่องนี้รับแจ้งเตือนแล้ว (ปิด)' : 'เปิดแจ้งเตือนบนเครื่องนี้'}</button><button id="rmt2">ส่งทดสอบ</button></div>
    <p class="muted" style="font-size:14px">${supported && reg ? '' : 'ต้องเปิดผ่าน HTTPS (เช่น Tailscale) และบน iPhone ต้อง "เพิ่มลงหน้าจอโฮม" ก่อน (iOS 16.4+) '}อุปกรณ์ที่ลงทะเบียน: ${info.subscriptions}</p><div id="rmm" class="err"></div>`;
  };
  const save = () => D.api('PUT', '/api/push/reminder', { enabled: r.enabled, times: r.times });
  draw();
  host.onclick = async (e) => {
    const t = e.target;
    try {
      if (t.dataset.rm) { r.times = r.times.filter((x) => x !== t.dataset.rm); await save(); draw(); }
      else if (t.id === 'rma') { const v = $('#rmt', host).value; if (v && !r.times.includes(v)) { r.times = [...r.times, v].sort(); await save(); draw(); } }
      else if (t.id === 'rme') { r.enabled = t.checked; await save(); }
      else if (t.id === 'rmt2') { const x = await D.api('POST', '/api/push/test'); $('#rmm', host).textContent = x.sent ? '' : 'ยังไม่มีอุปกรณ์ที่ลงทะเบียน'; }
      else if (t.id === 'rmon') {
        if (!supported || !reg) { $('#rmm', host).textContent = 'เบราว์เซอร์นี้ยังใช้ไม่ได้ (ต้อง HTTPS / เพิ่มลงหน้าจอโฮม)'; return; }
        if (sub) { await D.api('POST', '/api/push/unsubscribe', { endpoint: sub.endpoint }); await sub.unsubscribe(); sub = null; info.subscriptions--; }
        else {
          if ((await Notification.requestPermission()) !== 'granted') { $('#rmm', host).textContent = 'ไม่ได้รับอนุญาตให้แจ้งเตือน'; return; }
          sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: b64ToU8(info.publicKey) });
          info.subscriptions = (await D.api('POST', '/api/push/subscribe', { subscription: sub.toJSON() })).subscriptions;
        }
        draw();
      }
    } catch (err) { $('#rmm', host).textContent = err.message; }
  };
}

// ----- settings -----
async function settingsView() {
  setTitle('ตั้งค่า');
  view.innerHTML = `<h2>สำรอง / ย้ายข้อมูล</h2>
  <div class="card"><div class="row wrap"><a href="/api/export" download><button>⬇ ส่งออกทั้งหมด (.json)</button></a>
  <label class="row"><input type="file" id="imp" accept="application/json,.json" style="display:none"><button onclick="document.getElementById('imp').click()">⬆ นำเข้า (.json)</button></label></div>
  <p class="muted" style="font-size:14px">เซิร์ฟเวอร์สำรองข้อมูลอัตโนมัติวันละครั้ง เก็บ 14 วันล่าสุดในโฟลเดอร์ data/backups<br>นำเข้าข้อมูลจากแอป Android: ดู web/README.md (tools/pack-android.mjs)</p><div id="msg"></div></div>
  <h2>แจ้งเตือน</h2><div class="card" id="rmc">…</div>
  <h2>ติดตั้งบน iPhone</h2><div class="card muted" style="font-size:15px">Safari → ปุ่มแชร์ → "เพิ่มลงในหน้าจอโฮม" แล้วเปิดจากไอคอน จะใช้งานแบบเต็มจอ</div>
  <h2>ซิงก์</h2><div class="card"><button id="sync">ซิงก์ตอนนี้</button></div>`;
  $('#sync').onclick = () => D.flush();
  reminderCard($('#rmc'));
  D.api('GET', '/api/auth').then((a) => { if (a.required) $('#sync').insertAdjacentHTML('afterend', ' <button id="lo">ออกจากระบบ</button>'); });
  view.onclick = async (e) => { if (e.target.id === 'lo') { await D.api('POST', '/api/logout'); location.reload(); } };
  $('#imp').onchange = async (e) => {
    const f = e.target.files[0];
    if (!f) return;
    try {
      const r = await D.sendImport(JSON.parse(await f.text()));
      $('#msg').textContent = `นำเข้าแล้ว ${r.imported} รายการ`;
    } catch (err) { $('#msg').innerHTML = `<span class="err">นำเข้าไม่สำเร็จ: ${esc(err.message)}</span>`; }
  };
}

// ---------- router ----------
async function route() {
  runFlush();
  const [, page = 'note', arg] = location.hash.split('/');
  document.querySelectorAll('#tabs a').forEach((a) => a.classList.toggle('on', a.dataset.t === (page === 'cal' || page === 'free' || page === 'note' || page === 'history' || page === 'habits' || page === 'shifts' || page === 'settings' ? page : 'note')));
  view.onclick = null; view.onchange = null;
  try {
    if (page === 'note') await noteView(arg);
    else if (page === 'history') await historyView();
    else if (page === 'free') await (arg ? freeEditView(arg) : freeListView());
    else if (page === 'cal') await calendarView();
    else if (page === 'habits') await habitsView();
    else if (page === 'shifts') await shiftsView();
    else if (page === 'settings') await settingsView();
    else location.hash = '#/note';
  } catch (e) { view.innerHTML = `<p class="pad err">โหลดไม่สำเร็จ: ${esc(e.message)}</p>`; }
}
window.addEventListener('hashchange', route);
route();

if ('serviceWorker' in navigator && (location.protocol === 'https:' || location.hostname === 'localhost')) navigator.serviceWorker.register('sw.js').catch(() => {});
