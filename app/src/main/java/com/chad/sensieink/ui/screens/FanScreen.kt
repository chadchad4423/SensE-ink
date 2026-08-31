package com.chad.sensieink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chad.sensieink.data.FanSelection
import com.chad.sensieink.data.ThermostatUiState
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD

private fun fanLabel(selection: FanSelection): String = when (selection) {
    FanSelection.AUTO -> "Auto"
    FanSelection.ON -> "On"
    FanSelection.CIRCULATE -> "Circulate"
}

private val FAN_SELECTIONS = listOf(FanSelection.AUTO, FanSelection.ON, FanSelection.CIRCULATE)

@Composable
fun FanScreen(uiState: ThermostatUiState, onFanSelected: (FanSelection) -> Unit) {
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

        TextMMD(text = "Committed fan mode: ${fanLabel(thermostat.fanSelection)}")
        uiState.pendingFanSelection?.let { pending ->
            TextMMD(text = "Requested: ${fanLabel(pending)} (waiting for confirmation)")
        }

        val selected = uiState.pendingFanSelection ?: thermostat.fanSelection

        FAN_SELECTIONS.forEach { selection ->
            if (selection == selected) {
                ButtonMMD(
                    onClick = { onFanSelected(selection) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextMMD(text = fanLabel(selection))
                }
            } else {
                OutlinedButtonMMD(
                    onClick = { onFanSelected(selection) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextMMD(text = fanLabel(selection))
                }
            }
        }
    }
}
