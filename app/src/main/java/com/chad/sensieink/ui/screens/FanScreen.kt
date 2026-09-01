package com.chad.sensieink.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chad.sensieink.data.FanSelection
import com.chad.sensieink.data.ThermostatUiState
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD

private fun fanLabel(selection: FanSelection): String = when (selection) {
    FanSelection.AUTO -> "Auto"
    FanSelection.ON -> "On"
    FanSelection.CIRCULATE -> "Circulate"
}

private val FAN_SELECTIONS = listOf(FanSelection.AUTO, FanSelection.ON, FanSelection.CIRCULATE)

/**
 * Same row-list + fixed-slot dot pattern as ModeScreen (see its doc
 * comment). Circulate gets a sub-row showing the real duty cycle from the
 * payload - the reference HA integration hardcodes this to 10%, which
 * would be wrong for this unit (actually 50% / 30 min/hr); read it live
 * instead. Shown read-only for now: adjusting it needs a
 * `circulating_fan.duty_cycle` write event that isn't implemented (no
 * setter exists in ThermostatRepository), not just a UI change - a
 * +/- control here would silently do nothing if built ahead of that.
 */
@Composable
fun FanScreen(uiState: ThermostatUiState, onFanSelected: (FanSelection) -> Unit) {
    val thermostat = uiState.thermostat

    if (thermostat == null) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            TextMMD(text = "Waiting for the first update from the thermostat...")
        }
        return
    }

    val selected = uiState.pendingFanSelection ?: thermostat.fanSelection

    Column(modifier = Modifier.fillMaxSize().selectableGroup()) {
        uiState.pendingFanSelection?.let { pending ->
            TextMMD(
                text = "Requested: ${fanLabel(pending)} (waiting for confirmation)",
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        FAN_SELECTIONS.forEach { selection ->
            SelectableRow(
                label = fanLabel(selection),
                selected = selection == selected,
                onClick = { onFanSelected(selection) },
            )
            if (selection == FanSelection.CIRCULATE && selected == FanSelection.CIRCULATE) {
                TextMMD(
                    text = "${thermostat.circulatingFanDutyCycle}% · ${thermostat.circulatingFanDutyCycle * 6 / 10} min/hr",
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 36.dp, top = 4.dp, bottom = 12.dp),
                )
            }
            HorizontalDividerMMD()
        }
    }
}
