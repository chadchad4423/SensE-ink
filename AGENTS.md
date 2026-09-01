# Instructions for AI Assistants

Status: Active
Last verified: 2026-09-01
Canonical location: `D:\Dev\AndroidStudioProjects\Sensi-eink\AGENTS.md`
(`C:\Users\Chad\AndroidStudioProjects\Sensi-eink` is an NTFS junction to this
directory as of 2026-09-01's folder migration - same files either path)
Secrets: None stored here — the harvested Sensi `refresh_token` lives only in
the device's `EncryptedSharedPreferences` and this session's local scratchpad,
never in this repository. See `.gitignore` (`secrets/`, `*.token`, `*.secret`).

Read `sensi-client-spec.md` first — it is the full build spec and takes
precedence over anything below. This file is a durable-rule summary for future
sessions; `PROJECT-STATUS.md` holds the exact current state and next action.

## Non-negotiable constraints (from the spec, restated because they're easy to violate by habit)

- **UI is Mudita Mindful Design (`com.mudita:MMD`), not stock Material 3.**
  Compose screens are built from MMD components (`ButtonMMD`, `TextMMD`,
  `TopAppBarMMD`, `NavigationBarMMD`, etc.); only write a custom composable
  where MMD genuinely has no equivalent.
- **No GMS/Play Services/Firebase, anywhere, transitively included.** The
  target device (Mudita Kompakt, MuditaOS K) has none of it, and microG isn't
  available either. A dependency that pulls in `play-services-*` or
  `firebase-*` will fail at runtime on-device even though it may build fine.
- **No animation, no ripple, no spinners.** `ThemeMMD` already disables ripple
  (`LocalRippleConfiguration provides null`). Don't reach for
  `androidx.compose.animation` or a progress indicator; use a static status
  text line instead (see `HomeScreen.kt`'s freshness line).
- **Never optimistically animate a value toward an unacknowledged setpoint.**
  Show the last-confirmed (`ThermostatState`) and locally-pending
  (`ThermostatUiState.pendingSetpointF`/`pendingMode`/`pendingFanSelection`)
  values as visibly distinct text, not a single interpolated number. This
  matters because the realtime channel only refreshes every ~30s.
- **Password grant is not implemented and should not be re-added.** Sensi's
  own web login uses it internally, but it's reCAPTCHA-gated since app
  v8.6.3+ and will look like a working code path that silently fails outside
  a real browser session. Only the `refresh_token` grant is supported; the
  refresh_token **rotates on every use** and must be re-persisted each time
  (`SensiAuthClient.refresh()` already does this — don't cache the original
  token value anywhere else).
- **Single-stage indoor and outdoor equipment.** Demand/staging values are
  only ever 0 or 100 for this specific thermostat. Don't add two-stage
  handling.
- **Five screens, no more.** Home (current state + setpoint, merged into
  one screen since the spec was written - setpoint is not a separate
  screen), Mode, Fan, Settings, and Setup (first-run + re-auth). What
  actually matters is the constraint behind that count, which still holds:
  scheduling, geofencing, usage reports, alerts, push notifications,
  multi-thermostat support, and remote sensors are explicitly out of scope.
  Resist scope growth toward any of those, not toward this specific number.
- **Isolate all Sensi protocol concerns behind `SensiAuthClient` /
  `SensiRealtimeClient` / `ThermostatRepository`.** Emerson has rewritten this
  backend's auth at least twice historically (see spec §8) — a future break
  should be fixable by touching those three files, not screens.

## Program context

This repository is tracked in the shared e-ink Android program library at
`C:\Users\Chad\rowdyram-ops\projects\eink-android\` (see that directory's
`README.md`, `CURRENT-STATE.md`, and `ROADMAP.md`). Update those when this
project's status changes materially — not for routine commits.
