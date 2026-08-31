package com.chad.sensieink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chad.sensieink.data.OperatingMode
import com.chad.sensieink.data.ThermostatUiState
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
fun SetpointScreen(uiState: ThermostatUiState, onSetpointChange: (Int) -> Unit) {
    val thermostat = uiState.thermostat

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (thermostat == null) {
            TextMMD(text = "Waiting for the first update from the thermostat...")
            return@Column
        }

        if (thermostat.operatingMode == OperatingMode.OFF) {
            TextMMD(text = "System is off. Switch to Heat, Cool, or Auto on the Mode screen to set a temperature.")
            return@Column
        }

        val committed = thermostat.activeSetpointF
        TextMMD(
            text = committed?.let { "Committed setpoint: $it F" } ?: "Committed setpoint: unknown",
        )

        // Never animate toward a value the server hasn't acknowledged - show it
        // as a distinct, explicitly-labeled pending line instead.
        uiState.pendingSetpointF?.let { pending ->
            TextMMD(text = "Requested: $pending F (waiting for confirmation)")
        }

        val displayedValue = uiState.pendingSetpointF ?: committed ?: 68

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ButtonMMD(onClick = { onSetpointChange(displayedValue - 1) }) {
                TextMMD(text = "-")
            }
            TextMMD(text = "$displayedValue F")
            ButtonMMD(onClick = { onSetpointChange(displayedValue + 1) }) {
                TextMMD(text = "+")
            }
        }
    }
}
