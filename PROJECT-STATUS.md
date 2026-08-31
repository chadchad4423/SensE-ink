# SensE-ink — Project Status

Status: Active — verified end-to-end on the emulator against the real thermostat; physical Kompakt pass still needed
Last verified: 2026-08-31
Canonical location: `C:\Users\Chad\AndroidStudioProjects\Sensi-eink\PROJECT-STATUS.md`
Secrets: None stored here

The app is branded **SensE-ink** as of `6b1dde7` (deliberately not "Sensi" -
trademark concern raised and accepted by Chad). The repository folder,
`applicationId` (`com.chad.sensieink`), and package name were deliberately
**not** renamed to match - only the user-visible name changed
(`R.string.app_name` and the launcher icon). Renaming those would churn
every file path and lose install identity with the existing debug build for
no user-visible benefit; revisit only if there's a real reason to.

## What exists

A Kotlin/Jetpack Compose Android app targeting the Mudita Kompakt, built per
`sensi-client-spec.md`. Seven commits, latest `6b1dde7`. Working tree clean.

- **Protocol layer** (`app/src/main/java/com/chad/sensieink/data/`):
  `SensiAuthClient` (OAuth refresh_token grant), `SensiRealtimeClient`
  (socket.io client for the realtime channel), `TokenStore`
  (EncryptedSharedPreferences), `ThermostatState`/`ThermostatRepository`
  (parsing + UI-facing state with committed/pending separation), `DeviceId`
  (EUI-64 derivation).
- **UI** (`app/src/main/java/com/chad/sensieink/ui/`): `SetupScreen` (a 6-step
  paged onboarding walkthrough for harvesting a refresh_token, gates the rest
  of the app until one is stored), `HomeScreen` (merged current-state +
  setpoint, see below), `ModeScreen`, `FanScreen`, `SettingsScreen` (app
  version, temperature unit toggle, "Update refresh_token" to get back to
  `SetupScreen`). Bottom nav is Home/Mode/Fan only (3 tabs); Settings is a
  gear icon in the header instead, with a back arrow (and the system back
  button, via `BackHandler`) to return. Built from MMD components (`TextMMD`,
  `TopAppBarMMD`, `NavigationBarMMD`) plus one hand-rolled composable (the
  ring stepper's circular buttons - see below for why).
- **Remote config** (`data/RemoteConfig.kt`, `BuildConfig.CONFIG_URL`): fetches
  `docs/config.json` from this repo's GitHub raw/Pages URLs at launch for an
  optional broadcast message or update nudge, mirroring the pattern already
  used in `TripTime`. **Inert until this repo has a GitHub remote and that
  file exists** - every failure is swallowed by design, so this was verified
  as "fails silently, no crash, no notice shown" rather than "shows a real
  notice." Revisit once a remote exists.
- **Temperature unit preference** (`data/PreferencesStore.kt`, DataStore):
  Fahrenheit/Celsius/Kelvin, display-only - the wire protocol and the
  setpoint +/- step stay whole-degree Fahrenheit regardless of the selected
  display unit. Verified persists across an app restart.
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
- **Settings screen (`78cf7d7`) verified**: the 5th nav tab is reachable,
  shows the real `BuildConfig.VERSION_NAME`, the Fahrenheit/Celsius/Kelvin
  toggle converts `CurrentStateScreen`/`SetpointScreen` display and survives
  an app restart (DataStore), and "Update refresh_token" correctly routes
  back to `SetupScreen` — re-pasting a token there reconnects live again.
  Confirmed the setpoint +/- still steps in real whole-degree Fahrenheit
  regardless of display unit (another live round trip against the real
  thermostat, reverted afterward).

## Rebrand and redesign (`6b1dde7`, 2026-08-31)

Driven by a round of feedback against real Kompakt screenshots (18 stock-app
screens: Contacts, Recents, Weather, a meditation timer, a podcast app's
Settings, etc. — not committed here, they're personal photos) and
CalmDirectory's actual screen source (not just its README). Findings worth
knowing before touching this UI again:

- **Stock apps never box a small set of facts.** Contacts/Recents/the
  podcast "Following" list show bold-primary + regular-secondary text rows
  with plain dividers; the weather app's detail screen is a hero number plus
  bullet-joined secondary stats, no card. `HomeScreen.kt` follows that —
  `CardMMD` is gone from it entirely.
- **`TopAppBarMMD` has a real bug**: it defines
  `TopAppBarDefaultsMMD.dividerLineHeight = 3.dp` as its intended divider
  weight, but applies it via `.width()` on an already-`.fillMaxWidth()`
  divider instead of a thickness/`.height()`, so it's silently overridden
  and always renders at Material3's 1dp default. `SensiApp.kt` sets
  `showDivider = false` and draws its own `HorizontalDivider` using that
  same MMD constant as the real thickness. If MMD ever fixes this upstream,
  this workaround becomes redundant (harmless, but worth removing then).
- **`ButtonMMD`/`OutlinedButtonMMD` cannot report press state** - they
  hardcode their own `NoRippleInteractionSource` internally with no
  `interactionSource` parameter to inject. The ring stepper's circular +/-
  buttons (`HomeScreen.kt`'s private `CircularStepButton`) are therefore a
  small hand-rolled `Box` + `Modifier.clickable(interactionSource = ...)` +
  `collectIsPressedAsState()`, not an MMD component - the same real
  black/white-swap principle as TripTime's Calculate button
  (`ui/TripScreen.kt`, DECISIONS.md D-016), applied to genuine touch
  feedback. Verified the invert with a held tap caught mid-press via
  screenshot.
- **A fixed `.size(220.dp)` on the ring silently rendered an oval**, not a
  circle: the Column's remaining vertical space was narrower than requested,
  and Compose clamped the height (not the width, which had room) to fit,
  producing a stadium shape via `CircleShape`'s 50%-of-smaller-dimension
  rounding. This is easy to miss in isolation (an empty preview has plenty
  of room) and only showed up once real content pushed the ring down the
  screen. Fixed with `fillMaxWidth(fraction).aspectRatio(1f)`, which cannot
  produce a non-square result regardless of available space. Worth
  remembering as a general pattern: **prefer `aspectRatio` over a fixed
  `.size()` for anything that must render as a specific shape**, since a
  fixed size only holds when there's provably enough room.
- Bottom nav dropped from 5 tabs to 3 (Home/Mode/Fan); Settings lives behind
  a gear icon in the header instead, matching every stock app's icon
  placement (opposite the title, e.g. Contacts' filter/add/search icons).
- New launcher icon: a bold circular dial with a needle and center dot,
  echoing the ring stepper rather than illustrating a literal thermostat
  unit. Verified rendering correctly in the emulator's app drawer.

## Not yet done

- **No physical Mudita Kompakt pass yet** — only the emulator, which is not
  representative for ghosting, dither, fine-line clarity, or real touch
  behavior. Required before any release milestone per the shared
  [device test matrix](../../rowdyram-ops/projects/eink-android/DEVICE-TEST-MATRIX.md).
- Mode screen still hardcodes `[Off, Heat, Cool, Auto]` rather than reading
  available modes from a live `capabilities` event (spec §4 asks for the
  latter). Not yet a problem in practice — all four are genuinely available
  on this unit — but not verified against an actual `capabilities` payload.
- Not registered with a git remote — local-only, matching the deliberate
  choice made when this repo was initialized (see `rowdyram-ops` conventions:
  remotes need separate explicit approval). This also means remote config
  (`BuildConfig.CONFIG_URL`) has nothing to fetch yet — see above.

## Exact next action

1. Physical-Kompakt pass per the shared device test matrix (ghosting, touch
   targets, contrast, sleep/wake) before considering the milestone complete.
2. Consider surfacing a `capabilities` event listener to replace the
   hardcoded Mode-screen mode list, now that a live socket connection is
   proven to work.
3. Once this repo has a GitHub remote: add `docs/config.json` (schema:
   `{"message": "...", "latestVersion": "..."}`, both optional) and enable
   GitHub Pages from `/docs` to light up the second fallback URL too, then
   verify a real notice actually renders (only the silent-failure path has
   been tested so far).
