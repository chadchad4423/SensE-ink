# Sensi eInk — Project Status

Status: Active — verified end-to-end on the emulator against the real thermostat; physical Kompakt pass still needed
Last verified: 2026-08-31
Canonical location: `C:\Users\Chad\AndroidStudioProjects\Sensi-eink\PROJECT-STATUS.md`
Secrets: None stored here

## What exists

A Kotlin/Jetpack Compose Android app targeting the Mudita Kompakt, built per
`sensi-client-spec.md`. Three commits: `1033b55` (initial scaffold), `1a22908`
(this documentation), `7f370f5` (periodic-refresh fix, see below). Working
tree clean.

- **Protocol layer** (`app/src/main/java/com/chad/sensieink/data/`):
  `SensiAuthClient` (OAuth refresh_token grant), `SensiRealtimeClient`
  (socket.io client for the realtime channel), `TokenStore`
  (EncryptedSharedPreferences), `ThermostatState`/`ThermostatRepository`
  (parsing + UI-facing state with committed/pending separation), `DeviceId`
  (EUI-64 derivation).
- **UI** (`app/src/main/java/com/chad/sensieink/ui/`): `SetupScreen` (token
  entry, gates the rest of the app until a refresh_token is stored),
  `CurrentStateScreen`, `SetpointScreen`, `ModeScreen`, `FanScreen`, all built
  from MMD components (`ButtonMMD`, `TextMMD`, `CardMMD`, `TopAppBarMMD`,
  `NavigationBarMMD`).
- **Build**: `./gradlew assembleDebug` succeeds
  (`app/build/outputs/apk/debug/app-debug.apk` produced 2026-08-31). Toolchain
  mirrors `KompaktAudioProbe` (a known-working sibling project): Gradle 9.5.0,
  AGP 9.3.1, Kotlin 2.2.10, Compose BOM 2026.02.01, `compileSdk`/`targetSdk`
  37, `minSdk` 31, Java 11 source/target compatibility.
- JAVA_HOME for local builds: Android Studio's bundled JBR at
  `C:\Program Files\Android\Android Studio\jbr`.

## Verified against the live Sensi backend (2026-08-31)

Per spec §7 ("unverified — test, don't assume"):

1. **`manager.sensicomfort.com` does mint a refresh_token for a plain
   consumer account without an active Sensi Manager subscription.** The
   account hit a $1.50/mo-per-thermostat paywall screen *after* login, but
   the OAuth token exchange (`token?device=...`) already fires during the
   login POST itself, before that screen renders — the paywall never blocks
   token capture. Confirmed via a real harvested token for this account.
2. **The `refresh_token` grant is live and working**, but needs both
   `client_id=fleet` *and* a `client_secret` (hardcoded in Sensi's own app,
   documented in `iprak/sensi`'s `auth.py`) — `client_id` alone returns
   `invalid_client`. The server **rotates the refresh_token on every use**;
   `SensiAuthClient.refresh()` persists the new one immediately.
3. **`rt.sensiapi.io/thermostat/` accepts both `EIO=3` and `EIO=4`**, and an
   actual authenticated websocket connection using the Java
   `io.socket:socket.io-client` 2.x default (EIO=4) works end-to-end — see
   the emulator verification below for a real `state` event received and
   parsed correctly.
4. EUI-64 device ID math checks out against the token's `thermostats` claim
   (`34:6F:92:24:A0:A3` → `36-6f-92-ff-fe-24-a0-a3`). Whether it exactly
   matches the live `icd_id` field is still formally unconfirmed (no raw
   payload was logged), but is moot in practice: this account has exactly one
   thermostat, `ThermostatRepository`'s fallback-to-first-device logic
   handles it either way, and the emulator test showed the correct device's
   data throughout.
5. Mudita Kompakt's Android API level: **31 (Android 12)**, taken from
   `KompaktAudioProbe`'s verified, physically-released build — not
   re-verified against this project's own device session.
6. `com.mudita:MMD` is real and on Maven Central (confirmed directly against
   `repo1.maven.org`, independent of Maven Central's search index which was
   stale/empty for this group at the time). Latest is `1.0.2`, used here
   (CalmDirectory, the reference implementation, pins the older `1.0.0`).

## Verified end-to-end on the emulator against the real thermostat (2026-08-31)

Ran on the `Mudita_Kompakt` AVD (480×800, matching the physical panel).
Note: emulator boot hung indefinitely with the default GPU setting on this
machine (`Critical: Failed to load opengl32sw` in the emulator log, falling
back to a broken software-OpenGL path); booted fine once with
`-gpu swiftshader_indirect -no-snapshot`. Worth remembering for future
sessions rather than re-discovering.

- Pasted a freshly-harvested `refresh_token` into the setup screen → the app
  authenticated, connected the socket, and **`CurrentStateScreen` displayed
  this account's actual thermostat**: name "Sensi", real indoor temp/humidity,
  running/idle state, all correctly parsed on the first try. This confirms
  `ThermostatState.fromJson`'s field-name assumptions (`display_temp`,
  `humidity`, `status`, `operating_mode`, `fan_mode`,
  `circulating_fan.enabled`, `demand_status.heat`/`cool`) against a live
  payload from this exact ST55, not just against `iprak/sensi`'s reference
  code.
- Mode and Fan screens correctly showed the real committed mode ("Cool") and
  fan selection ("Auto"), including the Fan screen's synthesized
  Auto/On/Circulate mapping over the two independent `fan_mode` /
  `circulating_fan.enabled` wire fields.
- **Confirmed a full read/write round trip against the real hardware**: used
  the Setpoint screen's `+` to request 75°F, watched the pending/committed
  distinction render correctly, and confirmed via a fresh reconnect that the
  physical thermostat's setpoint had actually changed to 75°F. **Set it back
  to the original 74°F immediately after** — this was a live test against
  Chad's actual home thermostat, not a sandbox, so treat any future write-path
  testing the same way (confirm and revert, don't leave the unit altered).
- **Found and fixed a real bug this way**: the socket only pushes a `state`
  event on initial connect, never on a timer, and there's no request-refresh
  event (matches a comment in `iprak/sensi`'s `client.py`: "There doesn't
  seem to be an event for force state refresh"). The original
  only-reconnect-on-disconnect logic in `ThermostatRepository` left the UI
  showing "Live" with an increasingly stale reading indefinitely — the
  setpoint change above took over 2 minutes to become visible in the app
  even though it had already landed on the real device. Fixed in `7f370f5`
  by wrapping each connection cycle in a 30s timeout and reconnecting when it
  elapses; confirmed a fresh update now arrives automatically (~30-35s)
  without any manual app restart.

## Not yet done

- **No physical Mudita Kompakt pass yet** — only the emulator, which is not
  representative for ghosting, dither, fine-line clarity, or real touch
  behavior. Required before any release milestone per the shared
  [device test matrix](../../rowdyram-ops/projects/eink-android/DEVICE-TEST-MATRIX.md).
- Mode screen still hardcodes `[Off, Heat, Cool, Auto]` rather than reading
  available modes from a live `capabilities` event (spec §4 asks for the
  latter). Not yet a problem in practice — all four are genuinely available
  on this unit — but not verified against an actual `capabilities` payload.
- No app icon beyond a placeholder vector glyph.
- Not registered with a git remote — local-only, matching the deliberate
  choice made when this repo was initialized (see `rowdyram-ops` conventions:
  remotes need separate explicit approval).

## Exact next action

1. Physical-Kompakt pass per the shared device test matrix (ghosting, touch
   targets, contrast, sleep/wake) before considering the four-screen
   milestone complete.
2. Consider surfacing a `capabilities` event listener to replace the
   hardcoded Mode-screen mode list, now that a live socket connection is
   proven to work.
