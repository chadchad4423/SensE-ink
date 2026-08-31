package com.chad.sensieink.data

import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject

sealed interface RealtimeEvent {
    data object Connected : RealtimeEvent
    data class StateUpdate(val devices: List<ThermostatState>) : RealtimeEvent
    data class ConnectError(val message: String) : RealtimeEvent
    data class Disconnected(val reason: String) : RealtimeEvent
}

/**
 * Wraps io.socket:socket.io-client for wss://rt.sensiapi.io/thermostat/. Confirmed
 * 2026-08-31 against the live endpoint: both EIO=3 and EIO=4 handshakes succeed, so
 * this uses the library's current default (EIO=4) rather than pinning an old version.
 *
 * Native reconnection is disabled deliberately: a reconnect after the access_token
 * expires needs a fresh Authorization header, which the underlying engine.io socket
 * cannot pick up on its own reconnect attempts. [ThermostatRepository] owns retry by
 * re-collecting [events], which re-derives the token on every connection attempt.
 */
class SensiRealtimeClient(private val authClient: SensiAuthClient) {

    private var socket: Socket? = null

    fun events(): Flow<RealtimeEvent> = callbackFlow {
        val accessToken = authClient.ensureAccessToken()

        val options = IO.Options.builder()
            .setTransports(arrayOf("websocket"))
            .setPath("/thermostat/")
            .setQuery("capabilities=$CAPABILITIES")
            .setExtraHeaders(mapOf("Authorization" to listOf("bearer $accessToken")))
            .setReconnection(false)
            .build()

        val s = IO.socket(SOCKET_BASE_URL, options)
        socket = s

        s.on(Socket.EVENT_CONNECT) {
            trySend(RealtimeEvent.Connected)
        }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            trySend(RealtimeEvent.ConnectError(args.firstOrNull()?.toString() ?: "unknown"))
            // Reconnection is disabled (see class doc) - end this flow so the
            // repository's retry loop can re-collect with a fresh access token.
            close()
        }
        s.on(Socket.EVENT_DISCONNECT) { args ->
            trySend(RealtimeEvent.Disconnected(args.firstOrNull()?.toString() ?: "unknown"))
            close()
        }
        s.on("state") { args ->
            val devices = parseStateEvent(args.firstOrNull())
            if (devices.isNotEmpty()) {
                trySend(RealtimeEvent.StateUpdate(devices))
            }
        }

        s.connect()

        awaitClose {
            s.off()
            s.disconnect()
            socket = null
        }
    }

    fun sendSetTemperature(icdId: String, mode: OperatingMode, targetTempF: Int) {
        socket?.emit(
            "set_temperature",
            JSONObject()
                .put("icd_id", icdId)
                .put("scale", "f")
                .put("mode", mode.wireValue)
                .put("target_temp", targetTempF),
        )
    }

    fun sendSetOperatingMode(icdId: String, mode: OperatingMode) {
        socket?.emit(
            "set_operating_mode",
            JSONObject().put("icd_id", icdId).put("value", mode.wireValue),
        )
    }

    fun sendSetFanSelection(icdId: String, selection: FanSelection, currentDutyCycle: Int) {
        when (selection) {
            FanSelection.ON -> socket?.emit(
                "set_fan_mode",
                JSONObject().put("icd_id", icdId).put("value", FanMode.ON.wireValue),
            )
            FanSelection.AUTO -> {
                socket?.emit(
                    "set_fan_mode",
                    JSONObject().put("icd_id", icdId).put("value", FanMode.AUTO.wireValue),
                )
                socket?.emit(
                    "set_circulating_fan",
                    JSONObject().put("icd_id", icdId).put(
                        "value",
                        JSONObject().put("enabled", "off").put("duty_cycle", currentDutyCycle),
                    ),
                )
            }
            FanSelection.CIRCULATE -> {
                socket?.emit(
                    "set_fan_mode",
                    JSONObject().put("icd_id", icdId).put("value", FanMode.AUTO.wireValue),
                )
                socket?.emit(
                    "set_circulating_fan",
                    JSONObject().put("icd_id", icdId).put(
                        "value",
                        JSONObject().put("enabled", "on").put("duty_cycle", currentDutyCycle),
                    ),
                )
            }
        }
    }

    private fun parseStateEvent(payload: Any?): List<ThermostatState> {
        val array = when (payload) {
            is JSONArray -> payload
            is JSONObject -> JSONArray().put(payload)
            else -> return emptyList()
        }
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { ThermostatState.fromJson(it) }
        }
    }

    private companion object {
        const val SOCKET_BASE_URL = "https://rt.sensiapi.io"

        // Trimmed from pysensi's full list (sensi-client-spec.md section 3) to what
        // the four screens in scope actually need.
        const val CAPABILITIES = "display_humidity,operating_mode_settings,fan_mode_settings," +
            "indoor_equipment,outdoor_equipment,indoor_stages,outdoor_stages,circulating_fan," +
            "min_heat_setpoint,max_heat_setpoint,min_cool_setpoint,max_cool_setpoint"
    }
}
