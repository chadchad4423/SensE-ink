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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chad.sensieink.data.ConnectionStatus
import com.chad.sensieink.data.OperatingMode
import com.chad.sensieink.data.TemperatureUnit
import com.chad.sensieink.data.ThermostatState
import com.chad.sensieink.data.ThermostatUiState
import com.mudita.mmd.components.text.TextMMD
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Static status text in place of a spinner - per sensi-client-spec.md section 5,
 * this app never uses progress indicators or animation.
 */
private fun connectionLabel(status: ConnectionStatus): String = when (status) {
    is ConnectionStatus.Connecting -> "Connecting..."
    is ConnectionStatus.Live -> "Live"
    is ConnectionStatus.Error -> "Connection error: ${status.message}"
}

private fun runningLabel(thermostat: ThermostatState): String = when {
    thermostat.heatDemand > 0 -> "Heat running"
    thermostat.coolDemand > 0 -> "Cooling running"
    else -> "Idle"
}

/**
 * Merges the old separate current-state and setpoint screens into one.
 * Centered "poster" composition, picked over two other real mockups (a
 * two-zone split and a 2x2 stat grid) after a live side-by-side comparison -
 * flexible spacers distribute the full screen height around the reading and
 * the control instead of clustering everything at the top with dead space
 * below, which was the original complaint. Facts are plain stacked text with
 * no bordered card (Contacts/Recents/the podcast app and CalmDirectory's own
 * detail screen all present facts this way), and the setpoint control is
 * just the two circular +/- buttons flanking the number - no ring, no
 * "committed" line, both dropped per feedback on the first pass.
 */
@Composable
fun HomeScreen(
    uiState: ThermostatUiState,
    temperatureUnit: TemperatureUnit,
    onSetpointChange: (Int) -> Unit,
) {
    val thermostat = uiState.thermostat

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextMMD(text = connectionLabel(uiState.connection), fontSize = 12.sp)

        if (thermostat == null) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                TextMMD(text = "Waiting for the first update from the thermostat...")
            }
            return@Column
        }

        Box(modifier = Modifier.weight(1f))

        TextMMD(text = thermostat.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)

        thermostat.displayTempF?.let { indoorF ->
            HeroTemperature(fahrenheit = indoorF, unit = temperatureUnit)
        }

        TextMMD(text = runningLabel(thermostat), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

        TextMMD(
            text = "Humidity: ${thermostat.humidityPct?.let { "$it%" } ?: "unknown"}" +
                "  •  " + if (thermostat.online) "Online" else "Offline",
            fontSize = 13.sp,
        )

        Box(modifier = Modifier.weight(1f))

        if (thermostat.operatingMode == OperatingMode.OFF) {
            TextMMD(
                text = "System is off. Switch to Heat, Cool, or Auto on the Mode " +
                    "screen to set a temperature.",
                fontSize = 14.sp,
            )
        } else {
            SetpointRow(
                uiState = uiState,
                thermostat = thermostat,
                temperatureUnit = temperatureUnit,
                onSetpointChange = onSetpointChange,
            )
        }

        Box(modifier = Modifier.weight(0.6f))

        uiState.lastUpdatedAtMillis?.let { millis ->
            TextMMD(text = "Updated ${formatTime(millis)}", fontSize = 11.sp)
        }
    }
}

@Composable
private fun HeroTemperature(fahrenheit: Double, unit: TemperatureUnit) {
    val value = unit.fromFahrenheit(fahrenheit)
    val degreeGlyph = if (unit == TemperatureUnit.KELVIN) "" else "°"
    Row(verticalAlignment = Alignment.Bottom) {
        TextMMD(text = "$value$degreeGlyph", fontSize = 60.sp, fontWeight = FontWeight.Bold)
        TextMMD(
            text = "${unit.symbol} indoors",
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun SetpointRow(
    uiState: ThermostatUiState,
    thermostat: ThermostatState,
    temperatureUnit: TemperatureUnit,
    onSetpointChange: (Int) -> Unit,
) {
    val committedF = thermostat.activeSetpointF

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Never animate toward a value the server hasn't acknowledged - show it
        // as a distinct, explicitly-labeled pending line instead. The
        // committed value itself needs no separate line; it's the number
        // shown right here between the buttons.
        uiState.pendingSetpointF?.let { pendingF ->
            TextMMD(
                text = "Requested: ${temperatureUnit.format(pendingF)} (waiting for confirmation)",
                fontSize = 14.sp,
            )
        }

        // The wire protocol and the +/- step are always whole-degree Fahrenheit
        // (verified working against the real ST55); temperatureUnit only
        // changes how this value is displayed, not what's sent or how big a
        // tap moves it.
        val displayedValueF = uiState.pendingSetpointF ?: committedF ?: 68

        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularStepButton(label = "−", onClick = { onSetpointChange(displayedValueF - 1) })
            TextMMD(text = temperatureUnit.format(displayedValueF), fontSize = 36.sp, fontWeight = FontWeight.Bold)
            CircularStepButton(label = "+", onClick = { onSetpointChange(displayedValueF + 1) })
        }
    }
}

/**
 * A circular, white/transparent-fill stepper button that briefly inverts
 * (black fill, white glyph) while held - requested to match the feedback
 * style used in other Mudita apps (see TripTime's Calculate button in
 * `ui/TripScreen.kt`, DECISIONS.md D-016, for the same black/white-swap
 * principle). Not built on ButtonMMD: it hardcodes its own
 * `NoRippleInteractionSource` internally with no way to observe press state
 * from outside, so this is a small hand-rolled composable instead - the same
 * approach MK Volume+ uses for its own custom buttons
 * (`MainActivity.kt`'s `WarningActionButton`).
 */
@Composable
private fun CircularStepButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .size(52.dp)
            .background(
                color = if (isPressed) Color.Black else Color.Transparent,
                shape = CircleShape,
            )
            .border(width = 2.dp, color = Color.Black, shape = CircleShape)
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
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPressed) Color.White else Color.Black,
        )
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(millis))
