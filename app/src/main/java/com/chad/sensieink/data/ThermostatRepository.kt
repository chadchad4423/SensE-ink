package com.chad.sensieink.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface ConnectionStatus {
    data object Connecting : ConnectionStatus
    data object Live : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}

/**
 * UI-facing state. [pendingSetpointF]/[pendingMode]/[pendingFanSelection] hold a
 * locally-requested value that has not yet been confirmed by a server "state"
 * event; per sensi-client-spec.md section 5, the UI must show these distinctly
 * from [thermostat]'s last-confirmed values rather than optimistically animating
 * toward them.
 */
data class ThermostatUiState(
    val connection: ConnectionStatus = ConnectionStatus.Connecting,
    val thermostat: ThermostatState? = null,
    val lastUpdatedAtMillis: Long? = null,
    val pendingSetpointF: Int? = null,
    val pendingMode: OperatingMode? = null,
    val pendingFanSelection: FanSelection? = null,
)

class ThermostatRepository(
    private val realtimeClient: SensiRealtimeClient,
    private val icdId: String,
    scope: CoroutineScope,
) {
    private val _uiState = MutableStateFlow(ThermostatUiState())
    val uiState: StateFlow<ThermostatUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                _uiState.update { it.copy(connection = ConnectionStatus.Connecting) }
                try {
                    // The server only pushes a "state" event on connect, not on a
                    // timer, and there's no request-refresh event (confirmed
                    // 2026-08-31 against the live backend, and noted in
                    // iprak/sensi's client.py: "There doesn't seem to be [an]
                    // event for force state refresh"). Reconnecting periodically
                    // is how the spec's ~30s refresh cadence is actually achieved.
                    withTimeoutOrNull(REFRESH_INTERVAL_MS) {
                        realtimeClient.events().collect(::handle)
                    }
                    // A closed-before-timeout cycle (connect_error/disconnect)
                    // would otherwise hot-loop reconnecting; always pace cycles.
                    delay(MIN_CYCLE_DELAY_MS)
                } catch (e: SensiAuthException) {
                    _uiState.update { it.copy(connection = ConnectionStatus.Error(e.message ?: "Authentication failed")) }
                    delay(RECONNECT_DELAY_MS)
                }
            }
        }
    }

    private fun handle(event: RealtimeEvent) {
        when (event) {
            is RealtimeEvent.Connected ->
                _uiState.update { it.copy(connection = ConnectionStatus.Live) }

            is RealtimeEvent.ConnectError ->
                _uiState.update { it.copy(connection = ConnectionStatus.Error(event.message)) }

            is RealtimeEvent.Disconnected ->
                _uiState.update { it.copy(connection = ConnectionStatus.Error("Disconnected: ${event.reason}")) }

            is RealtimeEvent.StateUpdate -> {
                val device = event.devices.firstOrNull { it.icdId == icdId } ?: event.devices.firstOrNull()
                if (device != null) {
                    _uiState.update { current ->
                        current.copy(
                            thermostat = device,
                            lastUpdatedAtMillis = System.currentTimeMillis(),
                            connection = ConnectionStatus.Live,
                            pendingSetpointF = current.pendingSetpointF.takeUnless { it == device.activeSetpointF },
                            pendingMode = current.pendingMode.takeUnless { it == device.operatingMode },
                            pendingFanSelection = current.pendingFanSelection.takeUnless { it == device.fanSelection },
                        )
                    }
                }
            }
        }
    }

    fun setSetpoint(targetTempF: Int) {
        val device = _uiState.value.thermostat ?: return
        if (device.operatingMode == OperatingMode.OFF) return
        _uiState.update { it.copy(pendingSetpointF = targetTempF) }
        realtimeClient.sendSetTemperature(icdId, device.operatingMode, targetTempF)
    }

    fun setMode(mode: OperatingMode) {
        _uiState.update { it.copy(pendingMode = mode) }
        realtimeClient.sendSetOperatingMode(icdId, mode)
    }

    fun setFanSelection(selection: FanSelection) {
        val device = _uiState.value.thermostat ?: return
        _uiState.update { it.copy(pendingFanSelection = selection) }
        realtimeClient.sendSetFanSelection(icdId, selection, device.circulatingFanDutyCycle)
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 30_000L
        const val MIN_CYCLE_DELAY_MS = 3_000L
        const val RECONNECT_DELAY_MS = 5_000L
    }
}
