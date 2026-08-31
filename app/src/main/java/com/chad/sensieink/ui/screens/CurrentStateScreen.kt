package com.chad.sensieink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chad.sensieink.data.ConnectionStatus
import com.chad.sensieink.data.TemperatureUnit
import com.chad.sensieink.data.ThermostatUiState
import com.mudita.mmd.components.cards.CardMMD
import com.mudita.mmd.components.text.TextMMD
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Static status text in place of a spinner - per sensi-client-spec.md section 5,
 * this app never uses progress indicators or animation.
 */
private fun connectionLabel(status: ConnectionStatus): String = when (status) {
    is ConnectionStatus.Connecting -> "Connecting..."
    is ConnectionStatus.Live -> "Live"
    is ConnectionStatus.Error -> "Connection error: ${status.message}"
}

@Composable
fun CurrentStateScreen(uiState: ThermostatUiState, temperatureUnit: TemperatureUnit) {
    val thermostat = uiState.thermostat

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextMMD(text = connectionLabel(uiState.connection))

        if (thermostat == null) {
            TextMMD(text = "Waiting for the first update from the thermostat...")
        } else {
            CardMMD(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextMMD(text = thermostat.name)
                    TextMMD(
                        text = thermostat.displayTempF?.let { "${temperatureUnit.format(it)} indoors" }
                            ?: "Indoor temp unknown",
                    )
                    TextMMD(
                        text = thermostat.humidityPct?.let { "$it% humidity" }
                            ?: "Humidity unknown",
                    )
                    TextMMD(
                        text = if (thermostat.running) "Running" else "Idle",
                    )
                    TextMMD(
                        text = if (thermostat.online) "Thermostat online" else "Thermostat offline",
                    )
                }
            }

            uiState.lastUpdatedAtMillis?.let { millis ->
                TextMMD(text = "Last updated ${formatTime(millis)}")
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(millis))
