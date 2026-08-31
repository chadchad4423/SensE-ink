# Sensi eInk — Project Status

Status: Active — initial scaffold complete, untested on hardware
Last verified: 2026-08-31
Canonical location: `C:\Users\Chad\AndroidStudioProjects\Sensi-eink\PROJECT-STATUS.md`
Secrets: None stored here

## What exists

A Kotlin/Jetpack Compose Android app targeting the Mudita Kompakt, built per
`sensi-client-spec.md`. First commit: `1033b55 Scaffold Sensi eInk client: MMD
UI, Sensi cloud auth/realtime protocol layer`. Working tree was clean
immediately after.

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
3. **`rt.sensiapi.io/thermostat/` accepts both `EIO=3` and `EIO=4`** at the
   polling-handshake level, so the Java `io.socket:socket.io-client` 2.x
   default (EIO=4) needs no downgrade. Not yet verified: an actual
   authenticated websocket connection and the shape of a live `state` event
   for this specific unit (payload shapes in the code are taken from
   `iprak/sensi`'s Python client, not observed directly from this device).
4. EUI-64 device ID math checks out against the token's `thermostats` claim
   (`34:6F:92:24:A0:A3` → `36-6f-92-ff-fe-24-a0-a3`), but has not been
   confirmed against a live `state` event's `icd_id` field yet.
   `ThermostatRepository` falls back to the first device in the payload if
   the derived ID doesn't match, so this isn't a hard blocker.
5. Mudita Kompakt's Android API level: **31 (Android 12)**, taken from
   `KompaktAudioProbe`'s verified, physically-released build — not
   re-verified against this project's own device session.
6. `com.mudita:MMD` is real and on Maven Central (confirmed directly against
   `repo1.maven.org`, independent of Maven Central's search index which was
   stale/empty for this group at the time). Latest is `1.0.2`, used here
   (CalmDirectory, the reference implementation, pins the older `1.0.0`).

## Not yet done

- **No physical-device or emulator run yet.** The build compiles and
  packages, but the UI has never actually been launched — MMD component
  visual behavior, layout on the Kompakt's viewport, and touch-target sizing
  are unverified. Follow the shared
  [device test matrix](../../rowdyram-ops/projects/eink-android/DEVICE-TEST-MATRIX.md)
  before calling any milestone done.
- **No live socket connection tested against this specific thermostat.** The
  `state` event payload shape, and whether the connect-time `Authorization:
  bearer <access_token>` header is actually sufficient (vs. some other auth
  mechanism), are taken from `iprak/sensi`'s Python client and not yet
  confirmed against this ST55 directly.
- Mode screen hardcodes `[Off, Heat, Cool, Auto]` as the available modes
  rather than reading them from a live `capabilities` event (spec §4 asks for
  the latter; deferred because a capabilities event hasn't been observed
  yet).
- No app icon beyond a placeholder vector glyph.
- Not registered with a git remote — local-only, matching the deliberate
  choice made when this repo was initialized (see `rowdyram-ops` conventions:
  remotes need separate explicit approval).

## Exact next action

1. Get the app running against the real thermostat: install the debug APK
   (emulator first, then the physical Kompakt), paste a harvested
   `refresh_token` into the setup screen, and confirm a `state` event
   actually arrives and populates `CurrentStateScreen`.
2. Once a live payload is observed, verify/correct the field-name assumptions
   in `ThermostatState.fromJson` and confirm the derived `icd_id` matches.
3. Physical-Kompakt pass per the shared device test matrix (ghosting,
   touch targets, contrast) before considering the four-screen milestone
   complete.
