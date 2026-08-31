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
indicates 1F87U hardware.

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

> **Engine.IO version gotcha.** pysensi pins `EIO=3`. The Java client
> `io.socket:socket.io-client` speaks EIO=4 in 2.x and EIO=3 only in
> 1.x. A version mismatch fails at handshake and *presents as an auth
> error*. Determine the server's current version empirically before
> blaming credentials.

Poll/refresh cadence in the reference implementation is 30 seconds.

## 4. Scope

Four screens. Resist scope growth.

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
- **Full-refresh redraws** on screen change. Avoid partial-update churn
  that ghosts.
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

## 7. Unverified — test, don't assume

1. Whether `manager.sensicomfort.com` still mints a refresh token for a
   plain consumer (non-subscriber) account. **The entire design rests on
   this.** Verify before writing code.
2. Current Engine.IO version on `rt.sensiapi.io`.
3. Exact event names and payload shape on the socket — take these from
   the reference implementations, then confirm against live traffic.
4. Whether the EUI-64 device ID derivation matches the live payload.
5. MuditaOS K's Android API level, and the MMD release compatible with it.
6. Current MMD component inventory and API — check the Zeroheight docs
   and the repo rather than relying on names quoted in this spec.

## 8. Known fragility

Emerson has changed this backend repeatedly — `iprak/sensi` has 45+
releases and has rewritten auth at least twice. Isolate all protocol
concerns behind a single interface so a backend change touches one file.
Log auth and handshake failures with enough detail to distinguish
expired token / changed endpoint / changed protocol version.
