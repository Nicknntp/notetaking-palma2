import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
const { createServer } = createRequire(import.meta.url)('../server.js');

let server, base, dir;
before(async () => {
  dir = fs.mkdtempSync(path.join(os.tmpdir(), 'dn-'));
  server = createServer(dir);
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  base = `http://127.0.0.1:${server.address().port}`;
});
after(() => { server.close(); fs.rmSync(dir, { recursive: true, force: true }); });

const j = async (method, url, body) => {
  const r = await fetch(base + url, { method, headers: { 'content-type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
  return { status: r.status, body: await r.json().catch(() => null) };
};

test('note roundtrip, list, delete on empty', async () => {
  assert.equal((await j('GET', '/api/notes/2026-11-12')).body.text, '');
  const put = await j('PUT', '/api/notes/2026-11-12', { text: 'hello', base: '0' });
  assert.equal(put.status, 200);
  assert.equal((await j('GET', '/api/notes/2026-11-12')).body.text, 'hello');
  assert.deepEqual((await j('GET', '/api/notes')).body, [{ date: '2026-11-12', preview: 'hello' }]);
  const cur = (await j('GET', '/api/notes/2026-11-12')).body.rev;
  await j('PUT', '/api/notes/2026-11-12', { text: '', base: cur });
  assert.equal((await j('GET', '/api/notes')).body.length, 0);
});

test('note conflict returns 409 with server text', async () => {
  const first = await j('PUT', '/api/notes/2026-11-13', { text: 'A', base: '0' });
  await new Promise((r) => setTimeout(r, 15));
  await j('PUT', '/api/notes/2026-11-13', { text: 'B', base: first.body.rev }); // other device wins
  const stale = await j('PUT', '/api/notes/2026-11-13', { text: 'C', base: first.body.rev });
  assert.equal(stale.status, 409);
  assert.equal(stale.body.text, 'B');
  assert.equal((await j('PUT', '/api/notes/2026-11-13', { text: 'B', base: first.body.rev })).status, 200); // identical text is not a conflict
});

test('rejects bad ids / traversal', async () => {
  assert.equal((await j('GET', '/api/notes/..%2f..%2fetc')).status, 400);
  assert.equal((await j('GET', '/api/free/..%2fx')).status, 400);
  const r = await fetch(base + '/..%2fserver.js');
  assert.equal(r.status, 404);
});

test('free notes: create, pin, emoji, list order, delete', async () => {
  await j('PUT', '/api/free/n1', { text: 'first\nbody' });
  await j('PUT', '/api/free/n2', { text: 'second', pinned: true, emoji: '📌' });
  const list = (await j('GET', '/api/free')).body;
  assert.deepEqual(list.map((x) => x.id), ['n2', 'n1']);
  assert.equal(list[0].emoji, '📌');
  await j('DELETE', '/api/free/n2');
  assert.equal((await j('GET', '/api/free/n2')).status, 404);
});

test('habits + shifts docs, export/import roundtrip', async () => {
  assert.equal((await j('PUT', '/api/habits', { nope: 1 })).status, 400);
  await j('PUT', '/api/habits', { habits: [{ id: '1', name: 'x', perWeek: 7, created: '2026-11-01', done: [] }] });
  await j('PUT', '/api/shifts', { types: [], days: {}, others: {} });
  const bundle = (await j('GET', '/api/export')).body;
  assert.equal(bundle.habits.habits.length, 1);
  assert.equal(bundle.free.n1.text, 'first\nbody');
  const imp = await j('POST', '/api/import', { notes: { '2026-01-01': 'old' }, free: { z1: { text: 'z', pinned: true, emoji: '⭐' } } });
  assert.equal(imp.body.imported, 2);
  assert.equal((await j('GET', '/api/notes/2026-01-01')).body.text, 'old');
  assert.equal((await j('GET', '/api/free/z1')).body.pinned, true);
});

test('backup writes a daily bundle file', () => {
  server.backup();
  const files = fs.readdirSync(path.join(dir, 'backups'));
  assert.equal(files.length, 1);
});
