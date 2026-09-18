package com.senseink.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.senseink.app.data.ConnectionStatus
import com.senseink.app.data.TemperatureUnit
import com.senseink.app.data.ThermostatRepository

/**
 * Renamed from SettingsScreen (2026-09-18) once the gear icon became a
 * menu (MenuScreen.kt) rather than opening this screen directly - "Device"
 * is now one destination among several (Setup, License, Privacy, About),
 * not the whole of what the gear icon does, so it holds only device-status
 * rows: Thermostat, Connection, Units, Refresh.
 *
 * Row list per the independent design review (2026-09-01,
 * sensi-ui-revision.md - not committed here): label left, value
 * right-aligned, tap to act. "Thermostat" stays non-interactive - the
 * review's version links it to a detail screen with firmware/MAC/wifi
 * RSSI/battery, which this app doesn't have data for (none of those
 * fields exist on ThermostatState), so it's not wired to anything rather
 * than opening a screen with fabricated values. "Connection" was
 * tappable (opened re-auth) until 2026-09-18, when re-auth moved to the
 * new top-level "Setup" menu item and this row went back to being plain
 * status text - Chad: "get rid of the 'clickable' ... 'connected' text."
 */
@Composable
fun DeviceScreen(
    temperatureUnit: TemperatureUnit,
    onUnitSelected: (TemperatureUnit) -> Unit,
    connectionStatus: ConnectionStatus,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsRow(label = "Thermostat", value = "ST55")
        DashedDividerMMD()

        SettingsRow(label = "Connection", value = connectionLabel(connectionStatus))
        DashedDividerMMD()

        SettingsRow(
            label = "Units",
            // Kelvin has no degree symbol - same rule HomeScreen's hero
            // number already applies.
            value = if (temperatureUnit == TemperatureUnit.KELVIN) "K" else "°${temperatureUnit.symbol}",
            onClick = { onUnitSelected(nextUnit(temperatureUnit)) },
        )
        DashedDividerMMD()

        SettingsRow(label = "Refresh", value = "${ThermostatRepository.REFRESH_INTERVAL_MS / 1000}s")
        // No trailing divider - the list should end flush after the last row.
    }
}

private fun connectionLabel(status: ConnectionStatus): String = when (status) {
    is ConnectionStatus.Live -> "connected"
    is ConnectionStatus.Connecting -> "reconnecting"
    is ConnectionStatus.Error -> "error"
}

private fun nextUnit(current: TemperatureUnit): TemperatureUnit {
    val entries = TemperatureUnit.entries
    return entries[(entries.indexOf(current) + 1) % entries.size]
}
