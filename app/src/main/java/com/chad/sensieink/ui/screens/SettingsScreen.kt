package com.chad.sensieink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chad.sensieink.BuildConfig
import com.chad.sensieink.R
import com.chad.sensieink.data.TemperatureUnit
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
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
        TemperatureUnit.entries.forEach { unit ->
            if (unit == temperatureUnit) {
                ButtonMMD(
                    onClick = { onUnitSelected(unit) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextMMD(text = unit.label)
                }
            } else {
                OutlinedButtonMMD(
                    onClick = { onUnitSelected(unit) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextMMD(text = unit.label)
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
