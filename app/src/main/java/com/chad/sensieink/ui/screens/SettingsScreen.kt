package com.chad.sensieink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chad.sensieink.BuildConfig
import com.chad.sensieink.R
import com.chad.sensieink.data.TemperatureUnit
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
fun SettingsScreen(
    temperatureUnit: TemperatureUnit,
    onUnitSelected: (TemperatureUnit) -> Unit,
    onUpdateToken: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextMMD(text = "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}")

        TextMMD(text = "Temperature unit")
        // Per MMD's Radio component guide (zeroheight): a whole row is the
        // hit target and click either the circle or the label, grouped with
        // selectableGroup() for correct accessibility semantics. The guide's
        // E Ink note calls for bold labels alongside the filled/unfilled
        // circle as the selection cue, not grayscale alone.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            TemperatureUnit.entries.forEach { unit ->
                val selected = unit == temperatureUnit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = selected,
                            onClick = { onUnitSelected(unit) },
                            role = Role.RadioButton,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButtonMMD(selected = selected, onClick = null)
                    TextMMD(
                        text = unit.label,
                        modifier = Modifier.padding(start = 16.dp),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        TextMMD(text = "Account")
        OutlinedButtonMMD(
            onClick = onUpdateToken,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextMMD(text = "Update refresh_token")
        }
    }
}
