# Boss KD Tracker share API

Boss KD Tracker keeps `!KD` local by default. The optional **Share !KD in chat** setting follows the same user-initiated pattern as RuneLite commands such as `!task`: the plugin temporarily consumes the exact command the player typed, publishes the matching K/D snapshot, then calls `ChatInput.resume()` so RuneLite sends that same original command normally.

The plugin never rewrites or auto-types outgoing chat text.

## Privacy and opt-in behavior

Chat sharing is disabled by default. Enabling it shows RuneLite's third-party-server warning. When enabled, a share request sends the player's RuneScape name, the boss query they typed, the resolved boss identifier/name, kills, deaths, and the normal network metadata visible to the server such as the source IP address.

## Base URL

The client accepts an HTTPS base URL from the plugin configuration. If the configured base URL is:

```text
https://example.com/boss-kd
```

then the K/D endpoint is:

```text
https://example.com/boss-kd/v1/kd
```

HTTP URLs are rejected.

## Submit a share

```http
POST /v1/kd
Content-Type: application/json
```

Example body:

```json
{
  "name": "Example Player",
  "query": "whisp",
  "bossKey": "the-whisperer",
  "bossName": "Whisperer",
  "kills": 98,
  "deaths": 98,
  "timestamp": 1788620000000
}
```

A `2xx` response means the snapshot was accepted. Only after a successful response does the client call `ChatInput.resume()` and allow the player's original `!KD ...` command to be sent.

The service should keep only a short-lived latest snapshot per player/query. A TTL around 30-60 seconds is recommended so stale command lookups cannot be reused later.

## Look up a share

```http
GET /v1/kd?name=Example%20Player&query=whisp
```

Return the same JSON object that was submitted. A missing, expired, or mismatched share should return `404`.

The receiving plugin validates the returned player name and query against the chat message it is currently handling before changing the RuneLite-formatted view of that `MessageNode`.

## Rendered result

The visible RuneLite-side result is:

```text
Whisperer - Kills: 98 | Deaths: 98 | K/D: 1.00
```

The underlying RuneScape chat message remains the literal player-entered command, such as:

```text
!kd whisp
```

Therefore this feature is intentionally client-side: players without Boss KD Tracker do not receive the formatted replacement.
