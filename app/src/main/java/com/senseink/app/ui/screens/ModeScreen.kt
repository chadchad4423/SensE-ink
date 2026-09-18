package com.senseink.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senseink.app.data.OperatingMode
import com.senseink.app.data.ThermostatUiState
import com.mudita.mmd.components.radio_button.RadioButtonMMD
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

// With the divider removed (see SelectableRow's doc comment), this is what
// makes consecutive rows read as discrete instead of one tall block. Kept
// small deliberately: each row already centers its content inside its own
// touch target (see ROW_HEIGHT), so the row itself contributes most of
// the visual gap - this only needs to mark the boundary, not add a second
// gap on top of it.
private val ROW_SPACING = 4.dp

// 56dp rather than the original 80dp: 80dp's own internal centering (a
// single line of text inside a much taller box) turned out to be the
// dominant source of the "rows read as too spaced out" look, not
// ROW_SPACING. 56dp is still comfortably above Android's 48dp minimum
// touch target.
private val ROW_HEIGHT = 56.dp

// Reserved on every composition, pending mode or not - see ModeScreen's
// doc comment for why.
private val PENDING_BANNER_HEIGHT = 44.dp

/**
 * Row list with a fixed-slot RadioButtonMMD, per the independent design
 * review (2026-09-01, sensi-ui-revision.md/subscreens.svg - not committed
 * here): a small trailing control is a smaller redraw region than a
 * highlighted row or a button-per-option, and every row reserves the same
 * slot whether or not it's filled, so selecting a different mode never
 * reflows the list. One tap selects and returns to Home - no confirm
 * button.
 *
 * The "Requested: ..." banner above the rows is the same kind of
 * fixed-slot problem as FanScreen's Circulate sub-row (see its doc
 * comment): it only exists while a write is pending, and letting it
 * appear/disappear pushed every row below it up and down on every
 * selection - a reflow of the entire list to show one line of status
 * text. [PENDING_BANNER_HEIGHT] reserves that slot permanently; only its
 * contents are conditional on `uiState.pendingMode`.
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
        Box(
            modifier = Modifier.fillMaxWidth().height(PENDING_BANNER_HEIGHT).padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            uiState.pendingMode?.let { pending ->
                TextMMD(
                    text = "Requested: ${modeLabel(pending)} (waiting for confirmation)",
                    fontSize = 15.sp,
                )
            }
        }
        AVAILABLE_MODES.forEachIndexed { index, mode ->
            SelectableRow(
                label = modeLabel(mode),
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
            )
            // No spacer after the last row - the list should end flush,
            // not with trailing empty space beneath it.
            if (index != AVAILABLE_MODES.lastIndex) Spacer(modifier = Modifier.height(ROW_SPACING))
        }
    }
}

/**
 * No horizontal divider between rows any more - removed here and in
 * FanScreen. FanScreen's Circulate row expands to show a duty-cycle
 * sub-row on selection, and a divider sitting right below it had to
 * reprint at a new Y position every time that happened: a line moving on
 * every selection change is exactly the reflow the fixed-slot layout
 * exists to prevent. [ROW_SPACING] carries the between-row separation
 * instead, now that there's neither a divider nor (per ButtonMMD, which
 * has no interactionSource) any press feedback to lean on.
 *
 * RadioButtonMMD replaces the earlier hand-rolled dot as the selection
 * control, placed leading (before the label) rather than trailing where
 * the dot used to sit - both Material 3's own radio-button guidance and
 * RadioButtonMMD's own doc sample (`RadioButtonCustom`, in
 * RadioButtonMMD.kt) show it leading, and that took priority over
 * matching Settings' label-left/value-right row rhythm. It draws its
 * ring on every row whether selected or not, same as the dot did, so
 * selecting a different row still never reflows the list.
 */
@Composable
internal fun SelectableRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButtonMMD(selected = selected, onClick = null)
        TextMMD(
            text = label,
            fontWeight = if (selected) FontWeight.Bold else null,
            modifier = Modifier.padding(start = 16.dp).weight(1f),
        )
    }
}
