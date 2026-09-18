package com.senseink.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senseink.app.data.FanSelection
import com.senseink.app.data.ThermostatUiState
import com.mudita.mmd.components.text.TextMMD

private fun fanLabel(selection: FanSelection): String = when (selection) {
    FanSelection.AUTO -> "Auto"
    FanSelection.ON -> "On"
    FanSelection.CIRCULATE -> "Circulate"
}

private val FAN_SELECTIONS = listOf(FanSelection.AUTO, FanSelection.ON, FanSelection.CIRCULATE)

// Matches ModeScreen's ROW_SPACING - see SelectableRow's doc comment there
// for why spacing, not a divider, carries the between-row separation now,
// and why it's kept small rather than added on top of each row's own
// centered internal padding (ROW_HEIGHT, also in ModeScreen.kt).
private val ROW_SPACING = 4.dp

// Fixed height for the Circulate duty-cycle sub-row - always laid out,
// regardless of whether Circulate is selected. See FanScreen's doc
// comment for why.
private val CIRCULATE_DUTY_CYCLE_ROW_HEIGHT = 40.dp

// Aligns the sub-row's text under Circulate's own label rather than under
// its leading RadioButtonMMD: row horizontal padding (20dp) + the radio's
// footprint (24dp) + the label's own start padding (16dp).
private val CIRCULATE_DUTY_CYCLE_ROW_INDENT = 60.dp

// Reserved on every composition, pending selection or not - see
// FanScreen's doc comment for why (same fixed-slot reasoning as
// CIRCULATE_DUTY_CYCLE_ROW_HEIGHT, and as ModeScreen's matching banner).
private val PENDING_BANNER_HEIGHT = 44.dp

/**
 * Same row-list + trailing RadioButtonMMD pattern as ModeScreen (see its
 * doc comment). Circulate gets a sub-row showing the real duty cycle from
 * the payload - the reference HA integration hardcodes this to 10%, which
 * would be wrong for this unit (actually 50% / 30 min/hr); read it live
 * instead. Shown read-only for now: adjusting it needs a
 * `circulating_fan.duty_cycle` write event that isn't implemented (no
 * setter exists in ThermostatRepository), not just a UI change - a
 * +/- control here would silently do nothing if built ahead of that.
 *
 * The sub-row's [CIRCULATE_DUTY_CYCLE_ROW_HEIGHT] slot is reserved on
 * every composition, whether or not Circulate is selected - only its
 * *contents* are conditional. That slot used to appear or disappear with
 * selection, which pushed a divider below it to a new Y position on every
 * change - the worst reflow in either screen, and the reason both
 * screens' dividers were removed outright. Reserving the space
 * permanently means selecting Circulate only changes what's drawn inside
 * a fixed region and moves nothing else; the cost is blank space beneath
 * Auto/On, which on e-ink is cheaper than a reflow.
 *
 * The "Requested: ..." banner above the rows gets the same treatment for
 * the same reason ([PENDING_BANNER_HEIGHT]): it only exists while a fan
 * write is pending, and letting it appear/disappear pushed every row
 * below it up and down on every selection.
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
        Box(
            modifier = Modifier.fillMaxWidth().height(PENDING_BANNER_HEIGHT).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            uiState.pendingFanSelection?.let { pending ->
                TextMMD(
                    text = "Requested: ${fanLabel(pending)} (waiting for confirmation)",
                    fontSize = 15.sp,
                )
            }
        }
        FAN_SELECTIONS.forEachIndexed { index, selection ->
            SelectableRow(
                label = fanLabel(selection),
                selected = selection == selected,
                onClick = { onFanSelected(selection) },
            )
            if (selection == FanSelection.CIRCULATE) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CIRCULATE_DUTY_CYCLE_ROW_HEIGHT)
                        .padding(start = CIRCULATE_DUTY_CYCLE_ROW_INDENT),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (selected == FanSelection.CIRCULATE) {
                        TextMMD(
                            text = "${thermostat.circulatingFanDutyCycle}% · ${thermostat.circulatingFanDutyCycle * 6 / 10} min/hr",
                            fontSize = 15.sp,
                        )
                    }
                }
            }
            // No spacer after the last row (Circulate's reserved sub-row
            // already ends the list) - it should end flush, not with
            // trailing empty space beneath it.
            if (index != FAN_SELECTIONS.lastIndex) Spacer(modifier = Modifier.height(ROW_SPACING))
        }
    }
}
