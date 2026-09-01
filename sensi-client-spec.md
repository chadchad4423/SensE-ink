# Sensi thermostat client — build spec

Native Android client for an Emerson Sensi thermostat, targeting a
de-Googled E Ink phone. Replaces the official Sensi app, which cannot
run on the target device.

**UI is built with Mudita Mindful Design (MMD).** See §5. This is a
requirement, not a suggestion — do not fall back to stock Material 3.

---

## 1. Target device

**Mudita Kompakt**, running MuditaOS K.

- AOSP-based. **No Google Play Services, no GMS, no Firebase.** Any
  dependency that transitively pulls in `play-services-*` or
  `firebase-*` will fail at runtime.
- **microG is not available** (signature spoofing requires an OS patch
  MuditaOS K does not ship). Sandboxed Play is GrapheneOS-only. Do not
  design around either.
- **E Ink display.** See §5.
- Install path is `adb install` or Mudita Center sideload. Confirm the
  device's Android API level before setting `minSdk`/`targetSdk`.

Why the official app fails: it refuses to launch with an
"invalid / not installed via Play Store" error. This is an install-source
check (Play Licensing or Play Integrity app-recognition), unrelated to
the app's actual networking. It is *not* evidence that the Sensi API
needs Google services.

## 2. Hardware being controlled

| Property | Value |
|---|---|
| Model (marketing) | ST55 |
| Hardware family | 1F87U-42WF (inferred — see below) |
| Firmware | 6004850907 |
| MAC | `34:6F:92:24:A0:A3` |
| Indoor | Electric, 1 stage |
| Outdoor | AC, 1 stage |

The ST55 name covers two different boards. `1F86U-42WF` lacks both
HomeKit and the circulating fan; `1F87U-42WF` has both. The unit's app
exposes a **Circulate Fan** control set to 50% (30 min/hr), which
indicates 1F87U hardware - and as of 2026-09-01 this is no longer just
inferred from the app's UI: the live socket payload itself returns a real
`circulating_fan.duty_cycle` of 50%, which the `1F86U-42WF` board doesn't
have a field for at all. A definitive check (the `menu`+`mode` combo at
the wall unit: `11`/`22` = HomeKit-capable, `33` = not) would settle it
outright, but no longer matters for the chosen transport (cloud socket.io,
not local HomeKit).

**Single-stage on both sides** — staging/demand values will only ever be
0 or 100. Do not implement two-stage handling.

**Device ID:** the API identifies thermostats by the EUI-64 form of the
MAC (flip the universal/local bit, insert `FFFE` mid-word). For this
unit: `36:6F:92:FF:FE:24:A0:A3`. Verify this against the live payload
before hardcoding it — derive it, don't assume.

## 3. Transport (decided)

**Cloud socket.io.** Chosen over local HomeKit/HAP because it works
outside the LAN. Do not substitute a HAP implementation.

### Auth

`POST https://oauth.sensiapi.io/token` — OAuth. Use the
**`refresh_token` grant**.

**Confirmed against the live backend:** the request needs both
`client_id=fleet` *and* a `client_secret` (hardcoded in Sensi's own app,
documented in `iprak/sensi`'s `auth.py`) — `client_id` alone returns
`invalid_client`. The server **rotates the refresh_token on every use**;
persist the new one immediately after each refresh, don't cache the
originally-harvested value anywhere else. See §7 for the rest of what's
been verified.

The refresh token is obtained manually, once:

1. Browser → `https://manager.sensicomfort.com/`
2. DevTools → Network tab
3. Log in (a subscription is *not* required; the token is issued on the
   login request itself)
4. Copy `refresh_token` from the `token?device=` response

The app should treat this token as a user-supplied config value, persist
it encrypted, and refresh access tokens from it indefinitely. Re-harvest
is required only on password change.

**Do not implement the password grant.** Older code (`pysensi`) posts
`grant_type=password` with a hardcoded `client_id`/`client_secret` to
this endpoint. Sensi app v8.6.3+ moved to reCAPTCHA-gated login and this
path no longer works. Attempting it will look like an auth bug.

### Realtime

`wss://rt.sensiapi.io/thermostat/` — Engine.IO over websocket.

Query parameters: `transport=websocket`, `EIO=<version>`, and a
`capabilities=` list declaring which fields the server should stream.

The known-good capability list (from pysensi) includes:
`display_humidity, operating_mode_settings, fan_mode_settings,
indoor_equipment, outdoor_equipment, indoor_stages, outdoor_stages,
continuous_backlight, degrees_fc, display_time, keypad_lockout,
temp_offset, compressor_lockout, boost, heat_cycle_rate,
heat_cycle_rate_steps, cool_cycle_rate, cool_cycle_rate_steps,
aux_cycle_rate, aux_cycle_rate_steps, early_start, min_heat_setpoint,
max_heat_setpoint, min_cool_setpoint, max_cool_setpoint,
circulating_fan, humidity_control, humidity_offset,
humidity_offset_lower_bound, humidity_offset_upper_bound,
temp_offset_lower_bound, temp_offset_upper_bound`

Trim to what §4 actually needs.

> **Engine.IO version note (resolved — see §7).** pysensi pins `EIO=3`,
> which raised a concern that the Java `io.socket:socket.io-client`
> client's EIO=4 default (2.x) might mismatch and fail at handshake in a
> way that presents as an auth error. Confirmed against the live server:
> it accepts both, and the 2.x default works end-to-end. Kept here as a
> footnote in case a future backend change narrows that.

Poll/refresh cadence in the reference implementation is 30 seconds.

## 4. Scope

As built (see `PROJECT-STATUS.md` for the current, exact screen list -
this section describes intent, not a screen count to hold fixed): current
state and setpoint merged into one Home screen, plus Mode, Fan, and a
Settings screen (units, connection status, refresh cadence, about) that
wasn't part of the original plan but fits the same "control this one
thermostat" scope. Resist scope growth toward the following, which is the
constraint that actually matters:

1. **Current state** — indoor temp, humidity, running/idle
2. **Setpoint** — single target temp, +/- adjust
3. **Mode** — Auto / Heat / Cool / Off (available set depends on
   thermostat config; read it from the payload)
4. **Fan** — Auto / On / Circulate

Explicitly out of scope: scheduling, geofencing, usage reports, alerts,
push notifications, multi-thermostat support, remote sensors.

Optional if cheap: battery voltage, wifi RSSI, temp/humidity offset,
keypad lockout, continuous backlight.

## 5. UI — Mudita Mindful Design

MMD is Mudita's in-house design framework for E Ink devices: design
principles, a component library, and interaction patterns. It is a
Jetpack Compose library built on top of Material 3 guidelines and
classes, so it is a drop-in-shaped replacement rather than a parallel
universe. It is the native design language of the target device.

**Artifacts:**

- Library: `com.mudita:MMD` on Maven Central (Apache-2.0)
- Source: `github.com/mudita/MMD` (Kotlin)
- Migration helper: `github.com/mudita/mmd-migrator` — an Android
  library for converting Material 3 components to MMD equivalents.
  Useful if any scaffolding starts out as stock M3.
- Documentation: published publicly on Zeroheight, linked from
  `mudita.com/developers`. **Read the component inventory there before
  designing screens** — build the UI from components that exist rather
  than inventing them.

**Working method:** start from the MMD component library and compose
the four screens out of what it provides. Only write a custom composable
where MMD genuinely has no equivalent, and when you do, follow the MMD
principles below rather than Material defaults.

**Reference implementation:** `davidraywilson/CalmDirectory` is a
community Kotlin app for de-Googled E Ink devices built on MMD. It
demonstrates real usage of `eInkColorScheme` and `eInkTypography`, and
its README documents the e-ink reasoning behind each choice. Read it
alongside the MMD docs — worked examples on this stack are scarce.

**Hard constraints (these hold whether or not MMD enforces them):**

- **No animation, no transitions, no spinners.** MMD disables ripple by
  default; do not re-enable it or reach for `androidx.compose.animation`.
  Use a static text status line in place of a progress indicator.
- **Full-refresh redraws on screen change; avoid churn, not partial
  updates as a category.** The concern is thrashing the panel with
  frequent redraws that ghost - not partial updates themselves. A
  deliberate, infrequent partial redraw of a small region (e.g.
  redrawing just a changed digit after a debounced input, rather than
  the whole screen) is fine and is what this app actually does for its
  setpoint keys; what to avoid is redrawing on every intermediate value
  of something the user is still adjusting.
- **Monochrome.** Use MMD's e-ink color scheme rather than defining
  colors. No greys for body text, no gradients, no shadows, no
  elevation, no anti-aliased hairlines.
- **MMD typography scale**, not Material's defaults. High contrast and
  generous weight for legibility on e-paper.
- **Large hit targets.** Assume slow, deliberate taps.
- **Latency is expected and must be visible.** Show committed state and
  last-known state distinctly. Never optimistically animate a value
  toward a setpoint that hasn't been acknowledged by the server — on a
  30-second refresh cycle over a cloud API, that lie will be on screen
  for a long time.

## 6. Reference implementations (protocol)

Read these rather than guessing at the protocol.

| Project | Lang | Note |
|---|---|---|
| `iprak/sensi` | Python, MIT | Most complete. HA integration. Uses python-socketio since 2.0.0. |
| `w1ll1am23/pysensi` | Python | Original reverse engineering. Source of the capabilities string. |
| `homebridge-sensi` | TypeScript | Credits iprak for API code. **Closest to Kotlin — read this first.** |

## 7. Verified against the live backend (2026-08-31 through 2026-09-01)

Everything in this section was an open question as of the original spec
and has since been settled against the real account, real device, and
real thermostat (serial `MK20250414220`). See `PROJECT-STATUS.md` for the
full verification history; this section folds in the specifics that
belong in the spec itself rather than only in status notes.

1. **`manager.sensicomfort.com` does mint a refresh token for a plain
   non-subscriber account.** The account hits a $1.50/mo-per-thermostat
   paywall screen *after* login, but the OAuth token exchange
   (`token?device=...`) already fires during the login POST itself,
   before that screen renders - the paywall never blocks token capture.
   This was the single load-bearing assumption of the whole design, and
   it held.
2. **The `refresh_token` grant needs both `client_id=fleet` *and* a
   `client_secret`** (hardcoded in Sensi's own app, documented in
   `iprak/sensi`'s `auth.py`) - `client_id` alone returns `invalid_client`.
   This isn't in `pysensi` and is exactly the kind of undocumented detail
   that turns into a multi-hour debugging session after a backend change.
3. **The `refresh_token` rotates on every use** and must be re-persisted
   immediately after each refresh (`SensiAuthClient.refresh()` does this).
4. **`rt.sensiapi.io/thermostat/` accepts both `EIO=3` and `EIO=4`**, and
   `io.socket:socket.io-client` 2.x's default (EIO=4) works end-to-end -
   the version-mismatch concern below turned out not to apply.
5. **EUI-64 device ID derivation matches the live payload's `thermostats`
   claim.**
6. **Device: Android API 31, 480x800 physical, ~213dpi**, confirmed
   directly on the physical Kompakt (not just inferred from a sibling
   project's release notes).
7. **`com.mudita:MMD` 1.0.2** on Maven Central is the version in use;
   confirmed real and resolvable directly against `repo1.maven.org`
   (Maven Central's own search index was stale/empty for this group at
   the time).

## 8. Known fragility

Emerson has changed this backend repeatedly — `iprak/sensi` has 45+
releases and has rewritten auth at least twice. Isolate all protocol
concerns behind a single interface so a backend change touches one file.
Log auth and handshake failures with enough detail to distinguish
expired token / changed endpoint / changed protocol version.
