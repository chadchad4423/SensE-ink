package com.chad.sensieink.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chad.sensieink.R
import com.chad.sensieink.ui.screens.FanScreen
import com.chad.sensieink.ui.screens.HomeScreen
import com.chad.sensieink.ui.screens.ModeScreen
import com.chad.sensieink.ui.screens.SettingsScreen
import com.chad.sensieink.ui.screens.SetupScreen
import com.mudita.mmd.components.cards.CardMMD
import com.mudita.mmd.components.nav_bar.NavigationBarItemMMD
import com.mudita.mmd.components.nav_bar.NavigationBarMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarDefaultsMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD

enum class Screen(val label: String, val glyph: String) {
    HOME("Home", "H"),
    MODE("Mode", "M"),
    FAN("Fan", "F"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensiApp(viewModel: MainViewModel) {
    val hasToken by viewModel.hasToken.collectAsState()

    if (!hasToken) {
        // Wrapped in an empty Scaffold purely for its default window-inset
        // handling - without it this screen's content starts under the
        // status bar. Not visible on the emulator (shorter/thinner status
        // bar), but overlapped the title on the real Kompakt hardware.
        Scaffold { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                SetupScreen(onTokenSaved = viewModel::saveRefreshToken)
            }
        }
        return
    }

    var selectedScreen by remember { mutableStateOf(Screen.HOME) }
    var showSettings by remember { mutableStateOf(false) }
    // A re-entry ("Update refresh_token" in Settings) shows SetupScreen as a
    // dismissible overlay, same as Settings itself - the stored token is
    // untouched until the user actually saves a new one. Distinct from the
    // !hasToken case above, which is first-run onboarding with nothing to
    // cancel back to.
    var showReauth by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val temperatureUnit by viewModel.temperatureUnit.collectAsState()
    val notice by viewModel.notice.collectAsState()

    fun closeOverlays() {
        showSettings = false
        showReauth = false
    }

    BackHandler(enabled = showSettings || showReauth) { closeOverlays() }

    Scaffold(
        topBar = {
            Column {
                TopAppBarMMD(
                    title = { TextMMD(text = stringResource(R.string.app_name)) },
                    navigationIcon = {
                        if (showSettings || showReauth) {
                            IconButton(onClick = { closeOverlays() }) {
                                TextMMD(text = "←")
                            }
                        }
                    },
                    actions = {
                        if (!showSettings && !showReauth) {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.Black,
                                )
                            }
                        }
                    },
                    // MMD's own divider is drawn thinner than its documented
                    // weight - see TopAppBarDefaultsMMD.dividerLineHeight (3.dp),
                    // which the library applies via .width() instead of a
                    // thickness/.height(), so it never actually takes effect.
                    // Drawing it here with that same constant as the real
                    // thickness realizes MMD's intended weight.
                    showDivider = false,
                )
                HorizontalDivider(
                    thickness = TopAppBarDefaultsMMD.dividerLineHeight,
                    color = TopAppBarDefaultsMMD.dividerColor,
                )
            }
        },
        bottomBar = {
            NavigationBarMMD {
                Screen.entries.forEach { screen ->
                    NavigationBarItemMMD(
                        selected = !showSettings && !showReauth && selectedScreen == screen,
                        onClick = {
                            selectedScreen = screen
                            closeOverlays()
                        },
                        icon = { TextMMD(text = screen.glyph) },
                        label = { TextMMD(text = screen.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Shown above whichever tab is open, not just Home, so a
            // broadcast message isn't missed by staying on another screen.
            notice?.let {
                CardMMD(modifier = Modifier.padding(16.dp)) {
                    TextMMD(text = it, modifier = Modifier.padding(12.dp))
                }
            }

            Box {
                when {
                    showSettings -> SettingsScreen(
                        temperatureUnit = temperatureUnit,
                        onUnitSelected = viewModel::setTemperatureUnit,
                        connectionStatus = uiState.connection,
                        onUpdateToken = { showSettings = false; showReauth = true },
                    )
                    showReauth -> SetupScreen(
                        onTokenSaved = { token ->
                            viewModel.saveRefreshToken(token)
                            showReauth = false
                        },
                    )
                    else -> when (selectedScreen) {
                        Screen.HOME -> HomeScreen(
                            uiState = uiState,
                            temperatureUnit = temperatureUnit,
                            onSetpointChange = viewModel::setSetpoint,
                            onChangeModeFan = { selectedScreen = Screen.MODE },
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
    }
}
