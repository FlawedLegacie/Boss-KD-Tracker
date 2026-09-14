import { createServer } from 'node:http';
import { createHmac, randomBytes } from 'node:crypto';
import { isIP } from 'node:net';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const MAX_BODY = 4096;
const MAX_COUNT = 2_000_000;
const MINUTE = 60_000;
const FIELDS = new Set(['name', 'query', 'bossKey', 'bossName', 'kills', 'deaths', 'timestamp']);

class HttpError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

function text(value, max, allowEmpty = false) {
  if (typeof value !== 'string' || /[<>\x00-\x1f\x7f]/u.test(value)) {
    throw new HttpError(400, 'Invalid text field');
  }
  value = value.trim();
  if ((!allowEmpty && !value) || value.length > max) throw new HttpError(400, 'Invalid text length');
  return value;
}

function playerName(value) {
  const name = text(value, 12);
  if (!/^[A-Za-z0-9 _-]+$/.test(name)) throw new HttpError(400, 'Invalid player name');
  return name;
}

function queryText(value) {
  return text(value, 80).replace(/\s+/g, ' ').toLowerCase();
}

function snapshot(value, now) {
  if (!value || typeof value !== 'object' || Array.isArray(value)
      || Object.keys(value).some(key => !FIELDS.has(key))) {
    throw new HttpError(400, 'Invalid snapshot');
  }
  if (![value.kills, value.deaths].every(count => Number.isInteger(count) && count >= 0 && count <= MAX_COUNT)) {
    throw new HttpError(400, 'Invalid kills or deaths');
  }
  return {
    name: playerName(value.name),
    query: queryText(value.query),
    bossKey: text(value.bossKey, 80, true),
    bossName: text(value.bossName, 80),
    kills: value.kills,
    deaths: value.deaths,
    // Client clocks cannot extend a snapshot's lifetime.
    timestamp: now,
  };
}

function readJson(request) {
  if ((request.headers['content-type'] ?? '').split(';')[0].trim().toLowerCase() !== 'application/json') {
    throw new HttpError(415, 'Use application/json');
  }
  if (request.headers['content-encoding'] && request.headers['content-encoding'] !== 'identity') {
    throw new HttpError(415, 'Compressed bodies are not supported');
  }
  if (Number(request.headers['content-length']) > MAX_BODY) throw new HttpError(413, 'Body too large');
  return new Promise((resolveBody, reject) => {
    let size = 0;
    let chunks = [];
    let rejected = false;
    request.on('data', chunk => {
      if (rejected) return;
      size += chunk.length;
      if (size > MAX_BODY) {
        rejected = true;
        chunks = [];
        reject(new HttpError(413, 'Body too large'));
      } else chunks.push(chunk);
    });
    request.on('end', () => {
      if (rejected) return;
      try {
        resolveBody(JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks))));
      } catch { reject(new HttpError(400, 'Invalid JSON')); }
    });
    request.on('aborted', () => reject(new HttpError(400, 'Incomplete request')));
    request.on('error', () => reject(new HttpError(400, 'Invalid request')));
  });
}

function reply(response, status, value, extra = {}) {
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    ...(status >= 400 ? { Connection: 'close' } : {}),
    ...extra,
  });
  response.end(JSON.stringify(value));
}

/** One process owns all snapshots. Run one replica; restarts deliberately clear them. */
export function createKdServer({
  now = Date.now, ttlMs = MINUTE, maxSnapshots = 10_000, maxRateEntries = 10_000,
  submitLimit = 30, lookupLimit = 300, trustedProxyIp = '',
} = {}) {
  for (const value of [ttlMs, maxSnapshots, maxRateEntries, submitLimit, lookupLimit]) {
    if (!Number.isSafeInteger(value) || value < 1) throw new Error('Limits must be positive integers');
  }
  if (trustedProxyIp && !isIP(trustedProxyIp)) throw new Error('TRUSTED_PROXY_IP must be one IP address');
  const shares = new Map();
  const rates = new Map();
  const salt = randomBytes(32);
  const keyFor = (name, query) => JSON.stringify([name.toLowerCase(), query]);

  function prune(time) {
    for (const [key, entry] of shares) if (entry.expiresAt <= time) shares.delete(key);
    for (const [key, entry] of rates) if (entry.resetAt <= time) rates.delete(key);
  }

  function limit(request, time) {
    let ip = request.socket.remoteAddress ?? 'unknown';
    // Forwarded addresses are trusted only from an explicitly configured proxy.
    // That proxy must overwrite/append the true client as the rightmost value.
    if (trustedProxyIp && ip === trustedProxyIp) {
      const forwarded = request.headers['x-forwarded-for'];
      const candidate = typeof forwarded === 'string' ? forwarded.split(',').at(-1).trim() : '';
      if (isIP(candidate)) ip = candidate;
    }
    const key = createHmac('sha256', salt).update(ip + ':' + request.method).digest('hex');
    let entry = rates.get(key);
    if (!entry || entry.resetAt <= time) {
      if (!entry && rates.size >= maxRateEntries) {
        prune(time);
        if (rates.size >= maxRateEntries) throw new HttpError(503, 'Service busy');
      }
      entry = { count: 0, resetAt: time + MINUTE };
      rates.set(key, entry);
    }
    if (++entry.count > (request.method === 'POST' ? submitLimit : lookupLimit)) {
      return Math.max(1, Math.ceil((entry.resetAt - time) / 1000));
    }
    return 0;
  }

  const server = createServer({ requestTimeout: 10_000, headersTimeout: 10_000,
    keepAliveTimeout: 5_000, maxHeaderSize: 8192 }, async (request, response) => {
    try {
      if ((request.url ?? '').length > 2048) throw new HttpError(414, 'URL too long');
      const url = new URL(request.url, 'http://localhost');
      if (url.pathname === '/health' && request.method === 'GET') {
        reply(response, 200, { status: 'ok' });
        return;
      }
      if (url.pathname !== '/v1/kd') throw new HttpError(404, 'Not found');
      if (!['GET', 'POST'].includes(request.method)) {
        reply(response, 405, { error: 'Method not allowed' }, { Allow: 'GET, POST' });
        return;
      }
      const time = now();
      const retryAfter = limit(request, time);
      if (retryAfter) {
        reply(response, 429, { error: 'Too many requests' }, { 'Retry-After': String(retryAfter) });
        return;
      }
      if (request.method === 'POST') {
        if (url.search) throw new HttpError(400, 'POST does not accept query parameters');
        const payload = snapshot(await readJson(request), now());
        const key = keyFor(payload.name, payload.query);
        if (!shares.has(key) && shares.size >= maxSnapshots) {
          prune(now());
          if (shares.size >= maxSnapshots) throw new HttpError(503, 'Service busy');
        }
        const expiresAt = payload.timestamp + ttlMs;
        shares.set(key, { payload, expiresAt });
        reply(response, 201, { ok: true, expiresAt });
      } else {
        if ([...url.searchParams.keys()].some(key => !['name', 'query'].includes(key))
            || url.searchParams.getAll('name').length !== 1 || url.searchParams.getAll('query').length !== 1) {
          throw new HttpError(400, 'Provide one name and one query');
        }
        const key = keyFor(playerName(url.searchParams.get('name')), queryText(url.searchParams.get('query')));
        const entry = shares.get(key);
        if (!entry || entry.expiresAt <= time) {
          shares.delete(key);
          throw new HttpError(404, 'Share missing or expired');
        }
        reply(response, 200, entry.payload);
      }
    } catch (error) {
      if (!response.headersSent && !response.destroyed) {
        reply(response, error instanceof HttpError ? error.status : 500,
          { error: error instanceof HttpError ? error.message : 'Internal error' });
      }
    }
  });
  server.maxConnections = 256;
  const cleanup = setInterval(() => prune(now()), 30_000);
  cleanup.unref();
  server.on('close', () => { clearInterval(cleanup); shares.clear(); rates.clear(); });
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const port = Number(process.env.PORT ?? 8787);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('PORT must be 1-65535');
  const host = process.env.HOST ?? '127.0.0.1';
  const server = createKdServer({ trustedProxyIp: process.env.TRUSTED_PROXY_IP ?? '' });
  server.listen(port, host, () => console.log(`K/D sharing service listening on ${host}:${port}`));
  server.on('error', error => { console.error(`Server error: ${error.code ?? 'unknown'}`); process.exitCode = 1; });
  for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => {
    server.close();
    const deadline = setTimeout(() => server.closeAllConnections(), 5000);
    deadline.unref();
  });
}
