# Standby K/D sharing hooks

This release remains local-only. `BossDeathTrackerPlugin` binds its final hook
field to `KdSharingHooks.DISABLED`. The disabled implementation makes no network
requests, reads no player identity, stores no snapshots, never resumes chat,
and never changes incoming messages. No configuration can activate it.

## Integration points

- **Submit:** called only after a boss is successfully resolved. Receives the
  original RuneLite `ChatInput`, parsed query, boss key/name, kills and deaths.
  Returning false preserves the existing local queued output. Returning true
  means the implementation owns completion and the plugin consumes the input.
- **Lookup:** registered with RuneLite's command manager for received `!KD`
  messages. Currently does nothing. It must never publish the receiving player's
  stats or send an automatic chat reply.

## Work required before activation

1. Agree the sharing design/backend with maintainers. Acceptance of this local
   plugin is not approval of a future network feature.
2. Implement a reviewed transport and service. These hooks do not give access to
   RuneLite's servers and do not make sharing work between installed clients.
3. Add explicit opt-in and appropriate privacy disclosure. Read player identity
   and create snapshots only after opt-in. Define validation, expiration and
   request limits; handle shutdown, logout, channel changes, and network errors.
4. Publish data asynchronously. Resume only the original user-entered command;
   never fill the chatbox, rewrite outgoing text, or invoke chat-send scripts.
   On failure provide local feedback and complete the pending operation safely.
5. Fetch and validate incoming data asynchronously and update only the matching
   local message display on the client thread. Register async lookup behavior
   as appropriate; the current registration is synchronous because it does no work.
6. Test with two clients, including failure and lifecycle cases, then ship a
   separate version. There is no remote switch or automatic approval detection.
