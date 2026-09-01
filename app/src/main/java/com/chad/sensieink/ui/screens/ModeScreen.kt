package com.chad.sensieink.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chad.sensieink.data.OperatingMode
import com.chad.sensieink.data.ThermostatUiState
import com.mudita.mmd.components.divider.HorizontalDividerMMD
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
// capabilities event to confirm (section 7, unverified) - unchanged by this
// redesign pass, which only restyles the row itself.
private val AVAILABLE_MODES = listOf(
    OperatingMode.OFF,
    OperatingMode.HEAT,
    OperatingMode.COOL,
    OperatingMode.AUTO,
)

/**
 * Row list with a fixed-slot selection dot, per the independent design
 * review (2026-09-01, sensi-ui-revision.md/subscreens.svg - not committed
 * here): a dot is a smaller redraw region than a highlighted row or a
 * button-per-option, and every row reserves the same dot slot whether or
 * not it's filled, so selecting a different mode never reflows the list.
 * One tap selects and returns to Home - no confirm button.
 */
@Composable
fun ModeScreen(uiState: ThermostatUiState, onModeSelected: (OperatingMode) -> Unit) {
    val thermostat = uiState.thermostat

    if (thermostat == null) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            TextMMD(text = "Waiting for the first update from the thermostat...")
        }
        return
    }

    val selectedMode = uiState.pendingMode ?: thermostat.operatingMode

    Column(modifier = Modifier.fillMaxSize().selectableGroup()) {
        uiState.pendingMode?.let { pending ->
            TextMMD(
                text = "Requested: ${modeLabel(pending)} (waiting for confirmation)",
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        AVAILABLE_MODES.forEachIndexed { index, mode ->
            SelectableRow(
                label = modeLabel(mode),
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
            )
            // No divider after the last row - the list should end flush,
            // not with a trailing rule and empty space beneath it.
            if (index != AVAILABLE_MODES.lastIndex) HorizontalDividerMMD()
        }
    }
}

@Composable
internal fun SelectableRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(
            text = label,
            fontWeight = if (selected) FontWeight.Bold else null,
            modifier = Modifier.weight(1f),
        )
        // A fixed 5dp-dot slot, present in every row whether filled or not,
        // so a selection change never reflows the row.
        Box(
            modifier = Modifier.size(10.dp).background(
                color = if (selected) Color.Black else Color.Transparent,
                shape = CircleShape,
            ),
        )
    }
}
