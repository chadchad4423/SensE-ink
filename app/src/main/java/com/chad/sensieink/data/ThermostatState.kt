package com.chad.sensieink.data

import org.json.JSONObject

enum class OperatingMode(val wireValue: String) {
    OFF("off"), HEAT("heat"), COOL("cool"), AUTO("auto");

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wireValue == value } ?: OFF
    }
}

/**
 * The thermostat's `fan_mode` wire values. "Circulate" is not a fan_mode value -
 * it is a separate `circulating_fan.enabled` setting layered on top of AUTO (see
 * [ThermostatState.circulatingFanEnabled]); the UI's 3-way Auto/On/Circulate
 * picker is a synthesized view over these two independent fields.
 */
enum class FanMode(val wireValue: String) {
    AUTO("auto"), ON("on");

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wireValue == value } ?: AUTO
    }
}

/** The synthesized 3-way selection shown on the Fan screen. */
enum class FanSelection { AUTO, ON, CIRCULATE }

/**
 * A single thermostat's last-known state, parsed from a socket "state" event.
 * Field names follow the raw wire payload documented in iprak/sensi's client;
 * this app has a single, single-stage thermostat, so demand values are 0 or 100.
 */
data class ThermostatState(
    val icdId: String,
    val name: String,
    val online: Boolean,
    val displayTempF: Double?,
    val humidityPct: Int?,
    val operatingMode: OperatingMode,
    val fanMode: FanMode,
    val heatSetpointF: Int?,
    val coolSetpointF: Int?,
    val heatDemand: Int,
    val coolDemand: Int,
    val fanDemand: Int,
    val circulatingFanEnabled: Boolean,
    val circulatingFanDutyCycle: Int,
) {
    /** True if any equipment is actively running (single-stage: 0 or 100). */
    val running: Boolean get() = heatDemand > 0 || coolDemand > 0

    /** The setpoint relevant to the current mode; null for Off or before first sync. */
    val activeSetpointF: Int? get() = when (operatingMode) {
        OperatingMode.HEAT -> heatSetpointF
        OperatingMode.COOL -> coolSetpointF
        OperatingMode.AUTO -> heatSetpointF
        OperatingMode.OFF -> null
    }

    val fanSelection: FanSelection get() = when {
        fanMode == FanMode.ON -> FanSelection.ON
        circulatingFanEnabled -> FanSelection.CIRCULATE
        else -> FanSelection.AUTO
    }

    companion object {
        /** Parses one device entry from a "state" event's device list. */
        fun fromJson(device: JSONObject): ThermostatState? {
            val icdId = device.optString("icd_id").ifBlank { return null }
            val registration = device.optJSONObject("registration")
            val state = device.optJSONObject("state") ?: JSONObject()
            val demand = state.optJSONObject("demand_status") ?: JSONObject()
            val circulatingFan = state.optJSONObject("circulating_fan")

            return ThermostatState(
                icdId = icdId,
                name = registration?.optString("name").orEmpty().ifBlank { "Thermostat" },
                online = state.optString("status") == "online",
                displayTempF = state.optDouble("display_temp").takeIf { !it.isNaN() },
                humidityPct = state.optInt("humidity", -1).takeIf { it >= 0 },
                operatingMode = OperatingMode.fromWire(state.optString("operating_mode")),
                fanMode = FanMode.fromWire(state.optString("fan_mode")),
                heatSetpointF = state.optInt("current_heat_temp", -1).takeIf { it >= 0 },
                coolSetpointF = state.optInt("current_cool_temp", -1).takeIf { it >= 0 },
                heatDemand = demand.optInt("heat", 0),
                coolDemand = demand.optInt("cool", 0),
                fanDemand = demand.optInt("fan", 0),
                circulatingFanEnabled = circulatingFan?.optString("enabled") == "on",
                circulatingFanDutyCycle = circulatingFan?.optInt("duty_cycle", 10) ?: 10,
            )
        }
    }
}
