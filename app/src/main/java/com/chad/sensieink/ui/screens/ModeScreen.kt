package com.chad.sensieink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chad.sensieink.data.OperatingMode
import com.chad.sensieink.data.ThermostatUiState
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD

private fun modeLabel(mode: OperatingMode): String = when (mode) {
    OperatingMode.OFF -> "Off"
    OperatingMode.HEAT -> "Heat"
    OperatingMode.COOL -> "Cool"
    OperatingMode.AUTO -> "Auto"
}

// This ST55 is single-stage electric heat / single-stage AC on separate
// equipment, so all four modes are legitimately available; sensi-client-spec.md
// section 4 calls for reading this from the payload, which needs a live
// capabilities event to confirm (section 7, unverified).
private val AVAILABLE_MODES = listOf(
    OperatingMode.OFF,
    OperatingMode.HEAT,
    OperatingMode.COOL,
    OperatingMode.AUTO,
)

@Composable
fun ModeScreen(uiState: ThermostatUiState, onModeSelected: (OperatingMode) -> Unit) {
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

        TextMMD(text = "Committed mode: ${modeLabel(thermostat.operatingMode)}")
        uiState.pendingMode?.let { pending ->
            TextMMD(text = "Requested: ${modeLabel(pending)} (waiting for confirmation)")
        }

        val selectedMode = uiState.pendingMode ?: thermostat.operatingMode

        AVAILABLE_MODES.forEach { mode ->
            if (mode == selectedMode) {
                ButtonMMD(
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextMMD(text = modeLabel(mode))
                }
            } else {
                OutlinedButtonMMD(
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextMMD(text = modeLabel(mode))
                }
            }
        }
    }
}
