package com.chad.sensieink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chad.sensieink.ui.screens.CurrentStateScreen
import com.chad.sensieink.ui.screens.FanScreen
import com.chad.sensieink.ui.screens.ModeScreen
import com.chad.sensieink.ui.screens.SetpointScreen
import com.chad.sensieink.ui.screens.SetupScreen
import com.mudita.mmd.components.nav_bar.NavigationBarItemMMD
import com.mudita.mmd.components.nav_bar.NavigationBarMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD

enum class Screen(val label: String, val glyph: String) {
    CURRENT_STATE("State", "S"),
    SETPOINT("Setpoint", "T"),
    MODE("Mode", "M"),
    FAN("Fan", "F"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensiApp(viewModel: MainViewModel) {
    val hasToken by viewModel.hasToken.collectAsState()

    if (!hasToken) {
        SetupScreen(onTokenSaved = viewModel::saveRefreshToken)
        return
    }

    var selectedScreen by remember { mutableStateOf(Screen.CURRENT_STATE) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBarMMD(title = { TextMMD(text = selectedScreen.label) })
        },
        bottomBar = {
            NavigationBarMMD {
                Screen.entries.forEach { screen ->
                    NavigationBarItemMMD(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        icon = { TextMMD(text = screen.glyph) },
                        label = { TextMMD(text = screen.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedScreen) {
                Screen.CURRENT_STATE -> CurrentStateScreen(uiState = uiState)
                Screen.SETPOINT -> SetpointScreen(
                    uiState = uiState,
                    onSetpointChange = viewModel::setSetpoint,
                )
                Screen.MODE -> ModeScreen(uiState = uiState, onModeSelected = viewModel::setMode)
                Screen.FAN -> FanScreen(
                    uiState = uiState,
                    onFanSelected = viewModel::setFanSelection,
                )
            }
        }
    }
}
