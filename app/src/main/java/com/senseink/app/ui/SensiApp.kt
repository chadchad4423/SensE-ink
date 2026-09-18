package com.senseink.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.senseink.app.R
import com.senseink.app.ui.screens.AboutScreen
import com.senseink.app.ui.screens.DeviceScreen
import com.senseink.app.ui.screens.FanScreen
import com.senseink.app.ui.screens.HomeScreen
import com.senseink.app.ui.screens.LICENSE_TEXT
import com.senseink.app.ui.screens.LegalDocScreen
import com.senseink.app.ui.screens.ModeScreen
import com.senseink.app.ui.screens.PRIVACY_POLICY_TEXT
import com.senseink.app.ui.screens.SetupScreen
import com.mudita.mmd.components.cards.CardMMD
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD
import com.mudita.mmd.components.nav_bar.NavigationBarItemDefaultsMMD
import com.mudita.mmd.components.nav_bar.NavigationBarItemMMD
import com.mudita.mmd.components.nav_bar.NavigationBarMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarDefaultsMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD

// Icons picked 2026-09-18 from Material's own set (Icons.Filled.*) rather
// than hand-drawn glyphs - "tune"/"air" pull in material-icons-extended
// (see build.gradle.kts), since only "home" ships in the -core artifact
// already used elsewhere in this app.
enum class Screen(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    MODE("Mode", Icons.Filled.Tune),
    FAN("Fan", Icons.Filled.Air),
}

// A real stack (2026-09-18), not the pair of booleans this replaced - once
// Device, About, and the legal docs are all separate destinations, a flat
// "closeAll" Back button would skip straight to Home from several levels
// deep instead of stepping back one page at a time. The gear icon itself
// opens a DropdownMenuMMD (2026-09-18), not a screen - its expanded state
// is transient UI state, not a stack entry - and every overlay below is
// reached directly from that menu as a sibling, not nested inside one
// another, so a plain push/pop stack (no replace-in-place case) is all
// this needs.
private enum class Overlay(val title: String) {
    DEVICE("Device"),
    REAUTH("Setup"),
    ABOUT("About"),
    PRIVACY("Privacy policy"),
    LICENSE("License"),
}

// NavigationBarItemMMD's own active-tab indicator (NavigationBarItemDefaultsMMD,
// in NavigationBottomBarMMD.kt) is a 4dp bar drawn flush against the very
// bottom edge of the item's 80dp container - on the physical Kompakt it
// sits right against the device's own nav buttons with no breathing room.
// TabIndicatorBottomPadding nudges the whole item (and so the indicator)
// up from that edge without touching MMD's own indicator drawing.
private val TabIndicatorBottomPadding = 6.dp

// Twice MMD's own fixed 4dp (indicatorHeight isn't an exposed parameter -
// see the Box drawn per selected item below, in the bottomBar content).
private val ActiveTabIndicatorThickness = 8.dp

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
    var overlayStack by remember { mutableStateOf(listOf<Overlay>()) }
    val overlay = overlayStack.lastOrNull()
    val uiState by viewModel.uiState.collectAsState()
    val temperatureUnit by viewModel.temperatureUnit.collectAsState()
    val notice by viewModel.notice.collectAsState()

    fun push(next: Overlay) {
        overlayStack = overlayStack + next
    }
    fun pop() {
        overlayStack = overlayStack.dropLast(1)
    }
    fun closeOverlays() {
        overlayStack = emptyList()
    }

    BackHandler(enabled = overlay != null) { pop() }

    Scaffold(
        topBar = {
            Column {
                TopAppBarMMD(
                    // Home keeps the app name (root screen, nothing to
                    // navigate back to); every other screen names itself -
                    // three screens sharing one identical header carried no
                    // location information. Reverses an earlier deliberate
                    // choice (app name instead of per-page titles); revisit
                    // only with as much intent as that original choice had.
                    title = {
                        // Every overlay names itself (2026-09-18: same
                        // reasoning as Settings/Mode/Fan already did) -
                        // three-plus screens sharing one identical header
                        // carried no location information.
                        val headerText = overlay?.title
                            ?: if (selectedScreen == Screen.HOME) stringResource(R.string.app_name) else selectedScreen.label
                        TextMMD(text = headerText)
                    },
                    navigationIcon = {
                        // Pops one level (2026-09-18) - previously this
                        // closed every overlay at once, which was fine when
                        // Settings/Reauth was as deep as it went, but would
                        // jump straight to Home from a legal doc three
                        // levels under Settings now that About exists.
                        if (overlay != null) {
                            IconButton(onClick = { pop() }) {
                                TextMMD(text = "←")
                            }
                        }
                    },
                    actions = {
                        if (overlay == null) {
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = "Menu",
                                        tint = Color.Black,
                                    )
                                }
                                DropdownMenuMMD(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                    // DropdownMenuItemMMD already applies
                                    // this same 112-280dp range per item
                                    // internally (DropdownMenuItemDefaultMin/
                                    // MaxWidth in MenuMMD.kt) - stated here
                                    // too (2026-09-18: "expand the width to
                                    // be consistent with MMD and M3
                                    // standards") so the menu's own
                                    // container is guaranteed to match
                                    // rather than relying on that internal
                                    // default alone.
                                    modifier = Modifier.widthIn(min = 112.dp, max = 280.dp),
                                ) {
                                    // Each item both navigates and closes the
                                    // menu - MMD doesn't dismiss it for you on
                                    // an item click (see the sample: every
                                    // DropdownMenuItemMMD's own onClick sets
                                    // expanded = false itself).
                                    DropdownMenuItemMMD(
                                        text = { TextMMD(text = "Device") },
                                        onClick = { menuExpanded = false; push(Overlay.DEVICE) },
                                    )
                                    DropdownMenuItemMMD(
                                        text = { TextMMD(text = "Setup") },
                                        onClick = { menuExpanded = false; push(Overlay.REAUTH) },
                                    )
                                    DropdownMenuItemMMD(
                                        text = { TextMMD(text = "License") },
                                        onClick = { menuExpanded = false; push(Overlay.LICENSE) },
                                    )
                                    DropdownMenuItemMMD(
                                        text = { TextMMD(text = "Privacy") },
                                        onClick = { menuExpanded = false; push(Overlay.PRIVACY) },
                                    )
                                    DropdownMenuItemMMD(
                                        text = { TextMMD(text = "About") },
                                        onClick = { menuExpanded = false; push(Overlay.ABOUT) },
                                    )
                                }
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
        // No bottomBar slot (2026-09-18) - it used to live here, conditionally
        // present/absent based on `overlay`, which changed the height Scaffold
        // measured for that slot and so the innerPadding.bottom value handed
        // to the content lambda below. Scaffold's topBar/bottomBar slots are
        // resolved via SubcomposeLayout, and a slot whose presence toggles
        // with app state turned out not to settle atomically with everything
        // else - the content below would render once against the old
        // innerPadding, then again against the new one a frame later
        // (Chad, 2026-09-18: the bayou image on About "printed then moved,"
        // Setup's Back/Next buttons "jumping," and "going from the
        // menu-accessible pages to the main page, everything jumps" - all
        // four were this same transition). Moving the nav bar down into the
        // content Column below, as an ordinary conditional last child of a
        // single Column instead of a Scaffold slot, makes its appearance/
        // disappearance part of the exact same one-pass layout as everything
        // above it - nothing left to settle a frame later.
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // Shown above whichever tab is open, not just Home, so a
            // broadcast message isn't missed by staying on another screen.
            notice?.let {
                CardMMD(modifier = Modifier.padding(16.dp)) {
                    TextMMD(text = it, modifier = Modifier.padding(12.dp))
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (overlay) {
                    Overlay.DEVICE -> DeviceScreen(
                        temperatureUnit = temperatureUnit,
                        onUnitSelected = viewModel::setTemperatureUnit,
                        connectionStatus = uiState.connection,
                    )
                    Overlay.REAUTH -> SetupScreen(
                        onTokenSaved = { token ->
                            viewModel.saveRefreshToken(token)
                            closeOverlays()
                        },
                    )
                    Overlay.ABOUT -> AboutScreen()
                    Overlay.PRIVACY -> LegalDocScreen(text = PRIVACY_POLICY_TEXT)
                    Overlay.LICENSE -> LegalDocScreen(text = LICENSE_TEXT)
                    null -> when (selectedScreen) {
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

            // Hidden for every overlay (2026-09-18) - Settings/About/the
            // legal docs and Setup/Reauth are all a separate context you
            // back out of, not another tab to jump into, so the bar's own
            // tab-switch shortcut doesn't belong there; hiding it also
            // reclaims the vertical space Setup's screens needed (see
            // SetupScreen.kt's own layout-fit history).
            if (overlay == null) {
                NavigationBarMMD {
                    Screen.entries.forEach { screen ->
                        val selected = selectedScreen == screen
                        val indicatorColor = NavigationBarItemDefaultsMMD.indicatorColor
                        // MMD's own active-tab indicator is a fixed 4dp,
                        // internal to NavigationBarItemMMD and not exposed
                        // as a parameter (see NavigationBottomBarMMD.kt's
                        // indicatorHeight) - can't be resized directly. A
                        // wrapping Box per item doesn't work here: it
                        // breaks NavigationBarItemMMD's own internal
                        // `.weight(1f)`, which needs to see RowScope
                        // directly (tried, wouldn't compile). drawBehind
                        // placed BEFORE .padding() instead - a standard
                        // Compose idiom - draws into the padding's space
                        // rather than being clipped by it, letting this
                        // paint an 8dp bar at the item's true bottom edge
                        // that spans both MMD's own 4dp line and the gap
                        // TabIndicatorBottomPadding used to leave visible
                        // below it (2026-09-18: "twice as thick, filling in
                        // the little bit of white space").
                        val indicatorModifier = if (selected) {
                            Modifier.drawBehind {
                                val thicknessPx = ActiveTabIndicatorThickness.toPx()
                                drawRect(
                                    color = indicatorColor,
                                    topLeft = Offset(0f, size.height - thicknessPx),
                                    size = Size(size.width, thicknessPx),
                                )
                            }
                        } else {
                            Modifier
                        }
                        NavigationBarItemMMD(
                            selected = selected,
                            onClick = { selectedScreen = screen },
                            modifier = indicatorModifier.padding(bottom = TabIndicatorBottomPadding),
                            // contentDescription null - the label right below
                            // already names the tab, so the icon is decorative,
                            // not the only source of that information.
                            // Larger than Icon's own 24dp default (2026-09-18:
                            // "render the icons larger") - more legible at
                            // arm's length on the physical Kompakt.
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                )
                            },
                            label = { TextMMD(text = screen.label) },
                        )
                    }
                }
            }
        }
    }
}
