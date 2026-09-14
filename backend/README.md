# Boss KD sharing backend

A working, dependency-free Node.js service for the future `!KD` sharing feature.
It is separate from the Java plugin: **the plugin remains local-only and its hooks
remain disabled**. No hosting account is needed to run the backend tests locally.

## Run and test

Requires Node.js 22 or newer. From this directory:

```sh
node --test
node server.mjs
```

The local service listens on `http://127.0.0.1:8787`. `GET /health` returns
`{"status":"ok"}`. Stop with Ctrl+C. It uses only Node's standard library;
there are no packages to install. The tests run actual HTTP servers and exercise
uploads, independent lookups, expiry, isolation, malformed input and limits.

## API contract

`POST /v1/kd`, with `Content-Type: application/json`:

```json
{
  "name": "Example User",
  "query": "whisp",
  "bossKey": "the-whisperer",
  "bossName": "Whisperer",
  "kills": 100,
  "deaths": 5,
  "timestamp": 1789170000000
}
```

Returns HTTP 201 with `{"ok":true,"expiresAt":...}` only after storing the snapshot.
The server replaces the submitted timestamp with its own time. A custom boss may
have an empty `bossKey`. Names are at most 12 ASCII letters/digits/spaces/underscores/
hyphens; query, key and display name are at most 80 characters. Counts must be
integers from 0 through 2,000,000. Tags, control characters and unknown fields are
rejected. Request bodies are limited to 4 KiB, including chunked requests.

`GET /v1/kd?name=Example%20User&query=whisp` returns the stored snapshot, including
the server timestamp, or HTTP 404 if missing/expired. Player matching ignores
case; queries ignore case and repeated spaces. Queries must match the submitted
query: a `whisp` upload is not retrieved under `whisperer`. There is no list/search
endpoint. All responses use `Cache-Control: no-store`.

Each player/query keeps only its latest result. It expires exactly 60 seconds
after upload and becomes unavailable immediately at expiry. An in-memory sweep
removes expired entries at most 30 seconds later. Nothing is written to disk.
Restarting clears all snapshots. Run **one instance/replica**: multiple instances
would have different state. For scaling later, introduce shared storage first.

## Abuse limits and data handling

Per client address, uploads are limited to 30/minute and lookups to 300/minute.
HTTP 429 includes `Retry-After`. The service caps snapshots and rate-counter entries
at 10,000 each, returning 503 when full. Counters store a salted per-process hash
of the address rather than a raw IP. No request bodies, player names or IPs are
logged by this application. Your hosting provider/proxy may still have access logs;
disable query-string logging or set a suitable retention policy there.

These are **self-reported game statistics**. This API cannot prove ownership of a
RuneScape name or verify kills/deaths. Someone could submit under another name.
Do not use results for verified rankings, moderation, payments or access decisions.
There are no RuneScape credentials or account-linking flows in this service.

## Hosting

Use any host that supports one long-running Node.js process or a Docker container.
For a host-managed container, build with this directory as the build context:

```sh
docker build -t boss-kd-sharing .
docker run --rm -p 127.0.0.1:8787:8787 boss-kd-sharing
```

The Docker definition runs as the unprivileged Node user and includes a health
check. Docker is optional; it has not been exercised by the Node test suite.

Set `HOST=0.0.0.0` when required by your hosting platform, and set `PORT` to the
platform's assigned port (default 8787). **The public service must use HTTPS**,
provided by the host or a TLS reverse proxy. Plain HTTP is for local tests or the
private hop behind that proxy. The current Java plugin does not accept a server
address or make these requests; future integration must retain HTTPS validation.

By default forwarding headers are ignored. Behind a single trusted proxy, set
`TRUSTED_PROXY_IP` to the exact address Node sees for that proxy. The proxy must
append or overwrite the rightmost `X-Forwarded-For` address with the true client
address. Firewall the backend so only that proxy can reach it. Do not blindly
trust arbitrary forwarding headers. Without this setting, clients behind one
proxy share that proxy's rate allowance.

## Still required for in-game sharing

1. Deploy one instance and obtain its public HTTPS URL.
2. Implement the Java submit/lookup transport behind the standby hooks, including
   opt-in, privacy disclosure, timeouts, expiry validation, and lifecycle handling.
3. Test with two actual RuneLite clients. The HTTP tests do not demonstrate that
   the current disabled Java hooks display another player's result.
4. Review the complete feature with maintainers before releasing it. Approval
   of the local-only plugin does not enable this backend or approve sharing.

Creating this backend does not deploy it, enable the plugin hooks, change the
Plugin Hub pointer, or submit anything to RuneLite's maintainers.
