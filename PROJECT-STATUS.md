# SensE-ink — Project Status

Status: Active — verified end-to-end on the physical Mudita Kompakt against the real thermostat
Last verified: 2026-09-01
Canonical location: `C:\Users\Chad\AndroidStudioProjects\Sensi-eink\PROJECT-STATUS.md`
(also reachable at `D:\Dev\AndroidStudioProjects\Sensi-eink` - same directory via an NTFS
junction; edit either path, it's one file)
Secrets: None stored here

The app is branded **SensE-ink** as of `6b1dde7` (deliberately not "Sensi" -
trademark concern raised and accepted by Chad). The repository folder,
`applicationId` (`com.chad.sensieink`), and package name were deliberately
**not** renamed to match - only the user-visible name changed
(`R.string.app_name` and the launcher icon).

Repo has a GitHub remote as of 2026-09-01: `github.com/chadchad4423/Sensi-eink`,
**private**. Pushed through `cd33e03`; the most recent commit (the
Home/Mode/Fan/Settings redesign, `734ea15`) is committed locally but not
yet pushed - check `git log origin/master..HEAD` before assuming parity.

## What exists

A Kotlin/Jetpack Compose Android app targeting the Mudita Kompakt, built per
`sensi-client-spec.md` (protocol/scope) and, as of 2026-09-01, a follow-on UI
revision (see "Home/Mode/Fan/Settings redesign" below - source doc not
committed, it's a personal file: `sensi-ui-revision.md` plus two SVG mockups,
share those directly with any other agent working on UI).

- **Protocol layer** (`app/src/main/java/com/chad/sensieink/data/`):
  `SensiAuthClient` (OAuth refresh_token grant), `SensiRealtimeClient`
  (socket.io client for the realtime channel), `TokenStore`
  (EncryptedSharedPreferences), `ThermostatState`/`ThermostatRepository`
  (parsing + UI-facing state with committed/pending separation), `DeviceId`
  (EUI-64 derivation), `PairingServer` (see below).
- **UI** (`app/src/main/java/com/chad/sensieink/ui/`):
  - `SetupScreen` - 2-step onboarding: a brief intro, then a paste step that
    starts a local `PairingServer` (NanoHTTPD) so a desktop browser on the
    same WiFi can submit the harvested `refresh_token` via a PIN-gated web
    form, instead of typing a ~330-char string on the Kompakt's own keyboard
    (typing/scripted entry both proved unreliable - see "Pairing server"
    below). The webpage itself now hosts the full DevTools harvest
    walkthrough, since it only ever needs to be read on the computer doing
    that work. A manual paste field remains as a fallback.
  - `HomeScreen` - **redesigned 2026-09-01**, see below. Setpoint is the
    hero; indoor temp is a caption.
  - `ModeScreen` / `FanScreen` - **redesigned 2026-09-01**: row list with a
    fixed-slot selection dot, replacing a button-per-option list.
  - `SettingsScreen` - **redesigned 2026-09-01**: a 5-row list
    (Thermostat/Connection/Units/Refresh/About), replacing a
    version-line + radio-list + button stack.
  - Bottom nav is Home/Mode/Fan (3 tabs); Settings is a gear icon in the
    shared header, with a back arrow and `BackHandler` to return.
    "Update refresh_token" in Settings opens `SetupScreen` as a dismissible
    overlay (`showReauth`) rather than destroying the stored token
    immediately - the old token stays valid until a new one is actually
    saved.
  - Built from MMD components (`TextMMD`, `TopAppBarMMD`, `NavigationBarMMD`,
    `RadioButtonMMD`, `HorizontalDividerMMD`) plus hand-rolled composables
    only where MMD has no equivalent (the setpoint keys' press-invert
    feedback - see below).
- **Pairing server** (`data/PairingServer.kt`): a NanoHTTPD server started
  only while `SetupScreen`'s paste step is on screen. Shows a LAN IP and a
  6-digit PIN on the Kompakt; a computer on the same WiFi opens that address,
  reads the harvest instructions, and submits the PIN + token. Deliberately
  plain HTTP, not HTTPS - a LAN IP has no real domain name, so the only way
  to get TLS is a bundled self-signed cert (throws a "connection not
  private" warning); the PIN substitutes for that (physical-proximity
  gating), at the cost of not encrypting the token in transit. This exists
  because on-device token entry was proven unreliable both for real users
  and for scripted testing (`adb shell input text` corrupts long strings at
  composing-text chunk boundaries).
- **Launcher icon**: a plain bold "S" monogram (Arial Bold, rendered to a
  432x432 PNG and centered on its true ink bounds, not font line-height -
  `drawable-xxxhdpi/ic_launcher_foreground.png`). Went through several
  hand-drawn concepts first (a dial, a thermometer, a fan, a flame/snowflake
  split by a slash, a few house variants) before landing here; see git log
  `965787d`..`cd33e03` for the full sequence if it matters later.
- **Remote config** (`data/RemoteConfig.kt`, `BuildConfig.CONFIG_URL`):
  fetches `docs/config.json` from this repo's GitHub raw URL at launch for
  an optional broadcast message or update nudge. Repo now has a remote, but
  `docs/config.json` doesn't exist yet - still fails silently by design,
  not yet verified to show a real notice.
- **Temperature unit preference** (`data/PreferencesStore.kt`, DataStore):
  Fahrenheit/Celsius/Kelvin, display-only. Kelvin has no degree symbol
  (`K`, not `°K`) - both `HomeScreen`'s hero number and `SettingsScreen`'s
  Units row apply this rule now (the latter was a real bug, fixed
  2026-09-01).
- **Build**: `./gradlew assembleDebug` succeeds. Toolchain mirrors
  `KompaktAudioProbe`: Gradle 9.5.0, AGP 9.3.1, Kotlin 2.2.10, Compose BOM
  2026.02.01, `compileSdk`/`targetSdk` 37, `minSdk` 31, Java 11 source/target
  compatibility.
- JAVA_HOME for local builds: Android Studio's bundled JBR at
  `C:\Program Files\Android\Android Studio\jbr`.
- **Environment note (2026-09-01)**: this project has been worked on from at
  least two different machine/environment configurations this history - one
  with the Android SDK at `C:\Users\Chad\AppData\Local\Android\Sdk`
  (a real directory), another where that path is an NTFS junction to
  `D:\Android\Sdk` (which didn't exist - broken) and the real SDK was
  actually at `D:\Dev\Android\Sdk`. If a build fails with "SDK location not
  found," don't assume `local.properties` is wrong - check whether
  `sdk.dir`'s target directory actually exists before touching anything
  else; `local.properties` itself is gitignored and not committed, so a new
  environment needs one written fresh pointing at whatever's real.

## Home/Mode/Fan/Settings redesign (`734ea15`, 2026-09-01)

Prompted by two rounds of feedback on the original "poster" Home layout
(see below) - fonts too small/inconsistent, a redundant "Live" badge next
to a separate absolute timestamp, and finally a full revision spec Chad
commissioned from an independent (separate, context-free) Claude session
and forwarded here. That review's structural and interaction ideas were
adopted; its specific type sizes/weights were not (see below). The source
doc (`sensi-ui-revision.md`) and two SVG mockups are personal files, not
committed to this repo - share them directly with any other agent that
needs the original rationale in full.

- **Setpoint is now the Home screen hero**, not indoor temperature - it's
  what the +/- keys act on, and showing the wrong number huge was the
  original design's real flaw. Indoor temp is a caption beneath it. This
  is a deliberate reversal of the "poster" layout's own earlier choice
  (`6b1dde7`) and should not be flip-flopped again without a real reason.
- **The setpoint keys are now large rectangles (132x72dp)**, not the
  earlier circular buttons - same black/white press-invert technique
  (still hand-rolled, not `ButtonMMD` - see below), just a different shape
  and a bigger hit target.
- **Setpoint taps now debounce**: a tap updates a local, unsent value
  immediately (redrawing just the digits), and only the settled value
  after a ~600ms pause is actually sent over the socket. Before this,
  holding + through six degrees fired six separate socket writes and six
  redraws.
- **"Live" + a separate absolute timestamp collapsed into one relative-age
  line** ("updated 40s ago"), recomputed at coarse intervals (on payload,
  then at the 60s boundary, then every 60s) rather than ticking every
  second - a per-second tick would force a partial e-ink refresh every
  second. A connection error renders as one inverted (filled black) band
  reading "not connected" instead - the *only* inverted element anywhere
  in the app, on purpose, so a stale reading is unmistakable without
  color. This directly answers a real prior bug (see the reconnect-loop
  fix below): "Live" had previously stayed on screen for 2+ minutes with
  actually-stale data.
- **Mode and Fan collapsed into one tappable row** on Home
  ("Heat · Auto — change"), which jumps to the Mode tab via a new
  `onChangeModeFan` callback threaded through `SensiApp.kt`.
- **Mode/Fan screens**: button-per-option replaced with a row list and a
  fixed 10dp selection dot, present (but unfilled) on every row so
  selecting a different option never reflows the list. Fan's Circulate row
  gained a sub-row showing the *real* duty cycle from the payload
  (confirmed live: 50% / 30 min/hr on this unit - the reference HA
  integration hardcodes 10%, which would have been wrong here). That
  sub-row is **read-only**: there's no `circulating_fan.duty_cycle` write
  event implemented (`ThermostatRepository` has no setter for it), so no
  +/- control was built for it - a control that looks functional but
  silently does nothing would be worse than not having one.
- **Settings rebuilt as a 5-row list** (Thermostat/Connection/Units/
  Refresh/About). `Thermostat` is static (`ST55`, no detail screen - no
  firmware/MAC/RSSI/battery data exists anywhere in `ThermostatState`, and
  wasn't fabricated for this). `Connection` derives a label from the real
  `ConnectionStatus` (connected/reconnecting/error) and taps through to
  reauth. `Refresh` shows the real `ThermostatRepository.REFRESH_INTERVAL_MS`
  (made non-private for this) instead of a second hardcoded `"30s"` that
  could drift out of sync.
- **Typography is grounded in MMD's real `eInkTypography` scale**, not the
  review's own numbers. Pulled directly from `TypographyMMD.kt` on
  `github.com/mudita/MMD`: 14/15/16/18/20/24/28sp, **all Medium weight by
  default** - there is no defined "Regular/400" role anywhere in the
  library, and the review's assumed "16sp floor, two weights (400/500)"
  system doesn't correspond to anything MMD actually ships. Bold is used
  only in the two places this app already used it for deliberate emphasis
  (the hero number, a "running" status word) - a real cut of Lato exists
  for it, it's just not what any role defaults to.
- First-run (`SetupScreen`) was **not** touched by this pass - the review
  assumed a plain paste field and didn't know about the pairing-server flow
  above, which is the real, working, better mechanism. Its typography is
  not yet aligned with the rest of the app; worth a pass if picked up
  again.

## Verified against the live Sensi backend

Per spec §7 ("unverified — test, don't assume"):

1. **`manager.sensicomfort.com` does mint a refresh_token for a plain
   consumer account without an active Sensi Manager subscription.** The
   account hits a $1.50/mo-per-thermostat paywall screen *after* login, but
   the OAuth token exchange (`token?device=...`) already fires during the
   login POST itself, before that screen renders.
2. **The `refresh_token` grant is live and working**, but needs both
   `client_id=fleet` *and* a `client_secret` (hardcoded in Sensi's own app,
   documented in `iprak/sensi`'s `auth.py`). The server **rotates the
   refresh_token on every use** (not always observed as a *different*
   token, but always re-persisted); `SensiAuthClient.refresh()` persists the
   new one immediately.
3. **`rt.sensiapi.io/thermostat/` accepts both `EIO=3` and `EIO=4`**, and an
   actual authenticated websocket connection using the Java
   `io.socket:socket.io-client` 2.x default (EIO=4) works end-to-end.
4. EUI-64 device ID math checks out against the token's `thermostats` claim.
5. Mudita Kompakt's Android API level: **31 (Android 12)**, 480x800
   physical, ~213dpi. Confirmed directly on the physical unit (serial
   `MK20250414220`), not just inferred from `KompaktAudioProbe`.
6. `com.mudita:MMD` is real and on Maven Central. Latest is `1.0.2`, used
   here.

## Verified on the physical Mudita Kompakt (2026-08-31 through 2026-09-01)

This was the biggest open item as of the previous status update - now
closed. Full details of individual bugs found this way are in git history
(commit messages for each fix are more precise than a summary here); the
short version:

- Full live round trip confirmed on real hardware: auth, realtime socket,
  all screens' data flows, a real setpoint write-and-revert, a real fan
  mode write-and-revert (Circulate, confirming the real duty cycle).
- Real device-only bugs found and fixed: status bar inset overlap on
  first-run `SetupScreen` (fixed by wrapping it in an empty `Scaffold`),
  the IME covering the Save button on the paste step (`.imePadding()` +
  `.verticalScroll()`), an autofill popup triggered by `KeyboardType.Password`
  (reverted, kept only `autoCorrectEnabled = false`), and a token-entry
  corruption issue traced to `adb shell input text` chunking (not an app
  bug - resolved for testing via native OS copy/paste, and for real users
  via the pairing server above).
- A connection-loop leak: re-entering a token while a previous
  `ThermostatRepository` connection was still running left two loops
  running concurrently. Fixed - `ThermostatRepository.stop()` plus
  `MainViewModel` cancelling the prior repository/collector job before
  starting a new one.
- A "no way back" bug: "Update refresh_token" in Settings used to call a
  destructive `forgetToken()` immediately, with no cancel path. Fixed via
  the `showReauth` overlay pattern described above.
- One unresolved, likely-hardware cosmetic quirk: an intermittent
  invisible-button-text glitch on the Settings→SetupScreen transition.
  Tried a `key(...)` wrapper to force a full recompose; did not fix it,
  cleanly reverted. Most likely an e-ink panel-level partial-refresh
  artifact outside app-code control - not chased further.
- MMD library quirks found: `TopAppBarMMD`'s own divider constant
  (`TopAppBarDefaultsMMD.dividerLineHeight = 3.dp`) is applied via
  `.width()` instead of a thickness, so it silently never renders at its
  documented weight - worked around by drawing a `HorizontalDivider`
  manually with that same constant. `ButtonMMD`/`OutlinedButtonMMD` cannot
  report press state (no `interactionSource` parameter) - hence the
  hand-rolled press-invert composables for the setpoint keys.
- A layout bug worth remembering generally: a fixed `.size(220.dp)` on an
  early ring-stepper design silently rendered as an oval, because the
  Column's remaining vertical space was narrower than requested and
  Compose clamped the height, not the width. Fixed by switching to
  `aspectRatio(1f)`. **Prefer `aspectRatio` over a fixed `.size()`** for
  anything that must render as a specific shape.

## Not yet done

- First-run (`SetupScreen`) typography not yet aligned with the
  Home/Mode/Fan/Settings redesign (see above).
- No live write path for `circulating_fan.duty_cycle` - the Fan screen's
  Circulate sub-row is read-only pending this.
- Mode screen still hardcodes `[Off, Heat, Cool, Auto]` rather than reading
  available modes from a live `capabilities` event (spec §4 asks for the
  latter). Not yet a problem in practice - all four are genuinely available
  on this unit - but not verified against an actual `capabilities` payload.
- Settings' `Thermostat` row has no detail screen (model/firmware/MAC/
  RSSI/battery) - no source of that data exists yet.
- `docs/config.json` doesn't exist yet, so `RemoteConfig`'s second
  (GitHub-raw) fallback URL has nothing to fetch - only the silent-failure
  path is verified.
- Most recent commit (`734ea15`, the redesign) is not yet pushed to
  `origin/master` - confirm before assuming the remote is current.

## Exact next action

1. Push `734ea15` (and anything after it) to `origin/master` once Chad
   confirms.
2. If picking UI work back up: align `SetupScreen`'s typography with the
   rest of the app, per the redesign's scale.
3. If picking protocol work back up: a `circulating_fan.duty_cycle` write
   event, to make Fan's Circulate sub-row actually editable.
4. Once `docs/config.json` exists and GitHub Pages (or raw) is confirmed
   reachable: verify `RemoteConfig` actually renders a real notice, not
   just the silent-failure path.
