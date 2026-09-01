package com.chad.sensieink.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chad.sensieink.data.ConnectionStatus
import com.chad.sensieink.data.FanSelection
import com.chad.sensieink.data.OperatingMode
import com.chad.sensieink.data.TemperatureUnit
import com.chad.sensieink.data.ThermostatState
import com.chad.sensieink.data.ThermostatUiState
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import kotlinx.coroutines.delay

/**
 * Redesigned per an independent design review (2026-09-01, see
 * sensi-ui-revision.md / home-screen.svg - not committed here, they're
 * personal files): the setpoint - not indoor temperature - is the hero,
 * because it's what the +/- keys act on; indoor temp is now a caption
 * beneath it. This reverses the earlier "poster" layout deliberately.
 *
 * Structure and interaction ideas (hero swap, rectangular keys, debounced
 * writes, one relative-age line, dot-selection elsewhere) come from that
 * review, but its specific sizes/weights do not - those are pulled from
 * MMD's real `eInkTypography` scale instead (`TypographyMMD.kt` on
 * github.com/mudita/MMD): 14/15/16/18/20/24/28sp, everything Medium
 * weight by default. Bold is reserved for the same two places this app
 * already used it for genuine emphasis (the hero number, the running
 * status word) - MMD ships a real Bold cut of Lato, but no role in
 * eInkTypography uses it, and there's no 16sp-floor/400-vs-500 system in
 * the library the way the review assumed.
 *
 * "Live" plus a separate absolute timestamp was two encodings of one
 * fact, and "Live" was actively misleading on a 30s reconnect-poll
 * architecture (see PROJECT-STATUS.md's reconnect-loop bug history: the
 * UI once sat on "Live" for 2+ minutes with stale data). Replaced with a
 * single relative-age line, three states: fresh/stale/disconnected - the
 * disconnected state is the one inverted (filled black) element in the
 * whole screen, on purpose, so a stale reading is unmistakable without
 * color.
 */
@Composable
fun HomeScreen(
    uiState: ThermostatUiState,
    temperatureUnit: TemperatureUnit,
    onSetpointChange: (Int) -> Unit,
    onChangeModeFan: () -> Unit,
) {
    val thermostat = uiState.thermostat

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        if (thermostat == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TextMMD(text = "Waiting for the first update from the thermostat...")
            }
            return@Column
        }

        val disconnected = uiState.connection is ConnectionStatus.Error

        StatusWord(running = thermostat.running, disconnected = disconnected)

        Spacer(modifier = Modifier.weight(1f))

        SetpointHero(
            uiState = uiState,
            thermostat = thermostat,
            temperatureUnit = temperatureUnit,
            onSetpointChange = onSetpointChange,
        )

        Spacer(modifier = Modifier.weight(1f))

        HorizontalDividerMMD()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onChangeModeFan)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // bodyLarge (20sp) is the theme default TextMMD already falls back to
            // when no size is given - matches ModeScreen/FanScreen's own row labels.
            TextMMD(text = "${modeLabel(thermostat.operatingMode)} · ${fanLabel(thermostat.fanSelection)}")
            TextMMD(text = "change", fontSize = 15.sp) // labelMedium
        }

        FreshnessLine(lastUpdatedAtMillis = uiState.lastUpdatedAtMillis, disconnected = disconnected)
    }
}

@Composable
private fun StatusWord(running: Boolean, disconnected: Boolean) {
    val text = when {
        disconnected -> "last known"
        running -> "running"
        else -> "idle"
    }
    // Same slot, same size (titleSmall, 16sp) - a fixed slot means this line
    // never reflows the layout around it. Bold for "running" is the one
    // deliberate emphasis beyond eInkTypography's default Medium weight,
    // same as the hero number below; "idle"/"last known" get no override.
    TextMMD(
        text = text,
        fontSize = 16.sp,
        fontWeight = if (running && !disconnected) FontWeight.Bold else null,
    )
}

@Composable
private fun SetpointHero(
    uiState: ThermostatUiState,
    thermostat: ThermostatState,
    temperatureUnit: TemperatureUnit,
    onSetpointChange: (Int) -> Unit,
) {
    val committedF = thermostat.activeSetpointF

    if (thermostat.operatingMode == OperatingMode.OFF || committedF == null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextMMD(
                text = "System is off. Switch to Heat, Cool, or Auto to set a temperature.",
                fontSize = 18.sp,
            )
        }
        return
    }

    // Local-only accumulation: taps update this immediately (redrawing just
    // the digits), and only the settled value after a pause is actually
    // sent - holding + through six degrees must be one socket write and one
    // redraw, not six of each. Resets once its own debounced send lands,
    // at which point uiState.pendingSetpointF (set optimistically by
    // ThermostatRepository.setSetpoint, before the server confirms) takes
    // over as the displayed value with no visible gap.
    var localTapsF by remember { mutableStateOf<Int?>(null) }
    val displayedF = localTapsF ?: uiState.pendingSetpointF ?: committedF

    LaunchedEffect(localTapsF) {
        val value = localTapsF ?: return@LaunchedEffect
        delay(600)
        onSetpointChange(value)
        localTapsF = null
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // No MMD role covers a hero display number (eInkTypography tops out
        // at headlineLarge, 28sp) - 60sp/Bold is this app's own established
        // hero treatment, unchanged from before this redesign.
        TextMMD(text = temperatureUnit.format(displayedF), fontSize = 60.sp, fontWeight = FontWeight.Bold)
        thermostat.displayTempF?.let { indoorF ->
            TextMMD(text = "now ${temperatureUnit.format(indoorF.toInt())}", fontSize = 15.sp) // bodySmall
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SetpointKey(label = "−") { localTapsF = (localTapsF ?: displayedF) - 1 }
            SetpointKey(label = "+") { localTapsF = (localTapsF ?: displayedF) + 1 }
        }
    }
}

/**
 * A 132x72dp rectangular key (per the review, replacing the earlier
 * circular button) - deliberately large for a slow, deliberate tap. Same
 * black/white-invert-while-held technique as before (see
 * TripTime's Calculate button, DECISIONS.md D-016); still not built on
 * ButtonMMD since it can't report press state (no interactionSource
 * parameter - see PROJECT-STATUS.md).
 */
@Composable
private fun SetpointKey(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .width(132.dp)
            .height(72.dp)
            .background(
                color = if (isPressed) Color.Black else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .border(width = 2.dp, color = Color.Black, shape = RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(
            text = label,
            fontSize = 24.sp, // titleLarge - matches this app's original circular-key glyph size
            color = if (isPressed) Color.White else Color.Black,
        )
    }
}

@Composable
private fun FreshnessLine(lastUpdatedAtMillis: Long?, disconnected: Boolean) {
    if (disconnected) {
        // The one inverted element in the app, deliberately - a stale
        // reading must be unmistakable without relying on color.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .background(Color.Black, RoundedCornerShape(6.dp))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            TextMMD(text = "not connected", fontSize = 15.sp, color = Color.White) // labelMedium
        }
        return
    }

    if (lastUpdatedAtMillis == null) return

    // Recomputed on each new payload, then only at the coarse intervals
    // needed to keep the under/over-60s wording correct - never a
    // per-second tick, which would force a partial refresh every second.
    var now by remember(lastUpdatedAtMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastUpdatedAtMillis) {
        while (true) {
            val elapsedSec = (now - lastUpdatedAtMillis) / 1000
            val nextCheckMs = if (elapsedSec < 60) (60 - elapsedSec) * 1000 else 60_000
            delay(nextCheckMs)
            now = System.currentTimeMillis()
        }
    }

    TextMMD(
        text = "updated ${relativeAge(nowMillis = now, lastUpdatedAtMillis = lastUpdatedAtMillis)}",
        fontSize = 15.sp, // labelMedium
        modifier = Modifier.padding(top = 10.dp),
    )
}

private fun relativeAge(nowMillis: Long, lastUpdatedAtMillis: Long): String {
    val elapsedSec = ((nowMillis - lastUpdatedAtMillis) / 1000).coerceAtLeast(0)
    return when {
        // "0s ago" reads like a per-second counter even though this line
        // only ever recomputes at coarse intervals - "just now" doesn't.
        elapsedSec < 5 -> "just now"
        elapsedSec < 60 -> "${elapsedSec}s ago"
        else -> "${elapsedSec / 60} min ago"
    }
}

private fun modeLabel(mode: OperatingMode): String = when (mode) {
    OperatingMode.OFF -> "Off"
    OperatingMode.HEAT -> "Heat"
    OperatingMode.COOL -> "Cool"
    OperatingMode.AUTO -> "Auto"
}

private fun fanLabel(selection: FanSelection): String = when (selection) {
    FanSelection.AUTO -> "Auto"
    FanSelection.ON -> "On"
    FanSelection.CIRCULATE -> "Circulate"
}
