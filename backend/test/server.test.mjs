import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { request } from 'node:http';
import { createKdServer } from '../server.mjs';

const sample = { name: 'Example User', query: 'whisp', bossKey: 'the-whisperer',
  bossName: 'Whisperer', kills: 100, deaths: 5, timestamp: 1 };

async function start(t, options = {}) {
  const server = createKdServer(options);
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(async () => {
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
  });
  const base = `http://127.0.0.1:${server.address().port}`;
  return {
    server, base,
    post: (payload = sample, headers = {}) => fetch(base + '/v1/kd', {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(payload),
    }),
    get: (name = sample.name, query = sample.query, headers = {}) => fetch(
      base + '/v1/kd?' + new URLSearchParams({ name, query }), { headers }),
  };
}

async function status(response, expected) {
  assert.equal(response.status, expected, await response.clone().text());
  return response.json();
}

test('sender publishes and independent receiver fetches the exact snapshot', async t => {
  const api = await start(t, { now: () => 100_000 });
  const accepted = await status(await api.post(), 201);
  assert.equal(accepted.expiresAt, 160_000);
  const response = await api.get();
  assert.equal(response.headers.get('cache-control'), 'no-store');
  const result = await status(response, 200);
  assert.deepEqual(result, { ...sample, timestamp: 100_000 });
  assert.equal(`${result.bossName} - Kills: ${result.kills} | Deaths: ${result.deaths} | K/D: ${(result.kills / result.deaths).toFixed(2)}`,
    'Whisperer - Kills: 100 | Deaths: 5 | K/D: 20.00');
});

test('player names are case insensitive and queries normalize spaces and case', async t => {
  const api = await start(t);
  await status(await api.post({ ...sample, query: '  CHAOS   ELE  ' }), 201);
  await status(await api.get('example user', 'chaos ele'), 200);
});

test('different players and queries cannot retrieve each others result', async t => {
  const api = await start(t);
  await status(await api.post(), 201);
  await status(await api.get('Other User'), 404);
  await status(await api.get(sample.name, 'vork'), 404);
});

test('expiry uses server time even when a client supplies a future timestamp', async t => {
  let time = 100_000;
  const api = await start(t, { now: () => time });
  await status(await api.post({ ...sample, timestamp: Number.MAX_SAFE_INTEGER }), 201);
  time += 59_999;
  await status(await api.get(), 200);
  time++;
  await status(await api.get(), 404);
});

test('a newer share replaces the same player/query and resets expiry', async t => {
  let time = 100_000;
  const api = await start(t, { now: () => time });
  await status(await api.post(), 201);
  time += 50_000;
  await status(await api.post({ ...sample, kills: 101 }), 201);
  time += 20_000;
  assert.equal((await status(await api.get(), 200)).kills, 101);
});

test('malformed, tagged, out-of-range, and unexpected fields are rejected', async t => {
  const api = await start(t);
  for (const invalid of [null, [], {}, { ...sample, kills: -1 }, { ...sample, deaths: 0.5 },
    { ...sample, kills: 2_000_001 }, { ...sample, kills: '100' },
    { ...sample, bossName: '<col=ff0000>Fake' }, { ...sample, query: 'x\ny' },
    { ...sample, name: 'name too long!' }, { ...sample, query: 'x'.repeat(81) },
    { ...sample, admin: true }, { ...sample, bossKey: null }]) {
    await status(await api.post(invalid), 400);
  }
  await status(await api.get(), 404);
});

test('custom bosses and zero counts are supported', async t => {
  const api = await start(t);
  await status(await api.post({ ...sample, bossKey: '', kills: 0, deaths: 0 }), 201);
  assert.equal((await status(await api.get(), 200)).bossKey, '');
});

test('invalid JSON, media types, compressed and oversized bodies are rejected', async t => {
  const api = await start(t);
  await status(await fetch(api.base + '/v1/kd', { method: 'POST', body: '{bad', headers: { 'Content-Type': 'application/json' } }), 400);
  await status(await api.post(sample, { 'Content-Type': 'text/plain' }), 415);
  await status(await api.post(sample, { 'Content-Encoding': 'gzip' }), 415);
  await status(await api.post({ ...sample, bossName: 'x'.repeat(5000) }), 413);
});

test('streaming bodies cannot bypass the size limit', async t => {
  const api = await start(t);
  const code = await new Promise((resolve, reject) => {
    const req = request(api.base + '/v1/kd', { method: 'POST', headers: { 'Content-Type': 'application/json' } }, response => {
      response.resume();
      response.on('end', () => resolve(response.statusCode));
    });
    req.on('error', reject);
    for (let i = 0; i < 5; i++) req.write('x'.repeat(1000));
    req.end();
  });
  assert.equal(code, 413);
});

test('per-IP upload limits ignore spoofed forwarding headers and reset', async t => {
  let time = 100_000;
  const api = await start(t, { now: () => time, submitLimit: 1 });
  await status(await api.post(), 201);
  const response = await api.post(sample, { 'X-Forwarded-For': '203.0.113.2' });
  assert.equal(response.headers.get('retry-after'), '60');
  await status(response, 429);
  // Lookup has its own budget.
  await status(await api.get(), 200);
  time += 60_000;
  await status(await api.post(), 201);
});

test('lookup limits are enforced', async t => {
  const api = await start(t, { lookupLimit: 1 });
  await status(await api.get(), 404);
  await status(await api.get(), 429);
});

test('explicit proxy uses rightmost validated client IP', async t => {
  const api = await start(t, { trustedProxyIp: '127.0.0.1', submitLimit: 1 });
  await status(await api.post(sample, { 'X-Forwarded-For': '203.0.113.10' }), 201);
  await status(await api.post(sample, { 'X-Forwarded-For': '198.51.100.1, 203.0.113.10' }), 429);
  await status(await api.post(sample, { 'X-Forwarded-For': '203.0.113.11' }), 201);
});

test('snapshot capacity is bounded; updates and expired slots still work', async t => {
  let time = 100_000;
  const api = await start(t, { maxSnapshots: 1, now: () => time });
  await status(await api.post(), 201);
  await status(await api.post({ ...sample, name: 'Other User' }), 503);
  await status(await api.post({ ...sample, kills: 101 }), 201);
  time += 60_000;
  await status(await api.post({ ...sample, name: 'Other User' }), 201);
});

test('rate bookkeeping has a capacity bound', async t => {
  const api = await start(t, { maxRateEntries: 1 });
  await status(await api.post(), 201);
  await status(await api.get(), 503);
});

test('health, missing routes, methods, duplicate and missing parameters', async t => {
  const api = await start(t);
  assert.deepEqual(await status(await fetch(api.base + '/health'), 200), { status: 'ok' });
  await status(await fetch(api.base + '/'), 404);
  const response = await fetch(api.base + '/v1/kd', { method: 'DELETE' });
  assert.equal(response.headers.get('allow'), 'GET, POST');
  await status(response, 405);
  for (const params of ['', '?name=Example', '?name=A&query=x&query=y', '?name=A&query=x&all=1']) {
    await status(await fetch(api.base + '/v1/kd' + params), 400);
  }
});

test('fresh server does not retain another instances snapshots', async t => {
  const sender = await start(t);
  await status(await sender.post(), 201);
  const fresh = await start(t);
  await status(await fresh.get(), 404);
});
