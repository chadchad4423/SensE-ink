package com.chad.sensieink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chad.sensieink.BuildConfig
import com.chad.sensieink.R
import com.chad.sensieink.data.ConnectionStatus
import com.chad.sensieink.data.TemperatureUnit
import com.chad.sensieink.data.ThermostatRepository
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD

/**
 * Row list per the independent design review (2026-09-01,
 * sensi-ui-revision.md - not committed here): label left, value
 * right-aligned, tap to act. "Thermostat" and "About" are informational
 * only - the review's version also links to a detail screen with
 * firmware/MAC/wifi RSSI/battery, which this app doesn't have data for
 * (none of those fields exist on ThermostatState), so that row stays
 * non-interactive rather than opening a screen with fabricated values.
 */
@Composable
fun SettingsScreen(
    temperatureUnit: TemperatureUnit,
    onUnitSelected: (TemperatureUnit) -> Unit,
    connectionStatus: ConnectionStatus,
    onUpdateToken: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsRow(label = "Thermostat", value = "ST55")
        HorizontalDividerMMD()

        SettingsRow(
            label = "Connection",
            value = connectionLabel(connectionStatus),
            onClick = onUpdateToken,
        )
        HorizontalDividerMMD()

        SettingsRow(
            label = "Units",
            // Kelvin has no degree symbol - same rule HomeScreen's hero
            // number already applies.
            value = if (temperatureUnit == TemperatureUnit.KELVIN) "K" else "°${temperatureUnit.symbol}",
            onClick = { onUnitSelected(nextUnit(temperatureUnit)) },
        )
        HorizontalDividerMMD()

        SettingsRow(label = "Refresh", value = "${ThermostatRepository.REFRESH_INTERVAL_MS / 1000}s")
        HorizontalDividerMMD()

        SettingsRow(label = "About", value = "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}")
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

@Composable
private fun SettingsRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextMMD(text = label)
        TextMMD(text = value, fontSize = 16.sp) // bodyMedium/labelLarge
    }
}
