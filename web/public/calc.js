// Small safe expression evaluator for the note calculator: + - * / ( ) % and decimals. No eval().
export function calc(src) {
  const s = src.replace(/×/g, '*').replace(/÷/g, '/').replace(/,/g, '').replace(/\s+/g, '');
  let i = 0;
  const peek = () => s[i];
  function expr() {
    let v = term();
    while (peek() === '+' || peek() === '-') { const op = s[i++]; const r = term(); v = op === '+' ? v + r : v - r; }
    return v;
  }
  function term() {
    let v = unary();
    while (peek() === '*' || peek() === '/') {
      const op = s[i++]; const r = unary();
      if (op === '/' && r === 0) throw new Error('หารด้วยศูนย์');
      v = op === '*' ? v * r : v / r;
    }
    return v;
  }
  function unary() {
    if (peek() === '-') { i++; return -unary(); }
    if (peek() === '+') { i++; return unary(); }
    return postfix();
  }
  function postfix() {
    let v = atom();
    while (peek() === '%') { i++; v /= 100; }
    return v;
  }
  function atom() {
    if (peek() === '(') {
      i++; const v = expr();
      if (peek() !== ')') throw new Error('ขาด )');
      i++; return v;
    }
    const m = /^\d*\.?\d+/.exec(s.slice(i));
    if (!m) throw new Error('นิพจน์ไม่ถูกต้อง');
    i += m[0].length;
    return parseFloat(m[0]);
  }
  if (!s) throw new Error('ว่าง');
  const v = expr();
  if (i < s.length) throw new Error('นิพจน์ไม่ถูกต้อง');
  if (!Number.isFinite(v)) throw new Error('คำนวณไม่ได้');
  return Math.round(v * 1e10) / 1e10;
}
