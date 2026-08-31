package com.chad.sensieink.data

import kotlin.math.roundToInt

/**
 * Display-only. The Sensi wire protocol and this app's setpoint stepper both
 * operate in whole-degree Fahrenheit only (verified working against the real
 * ST55 - see PROJECT-STATUS.md); switching this preference changes how a
 * value is *shown*, not what gets sent to the thermostat or the size of a
 * +/- step. Kelvin is here because it was asked for as a joke, not because
 * anyone should set their home to 296 K.
 */
enum class TemperatureUnit(val label: String, val symbol: String) {
    FAHRENHEIT("Fahrenheit", "F"),
    CELSIUS("Celsius", "C"),
    KELVIN("Kelvin", "K");

    fun fromFahrenheit(fahrenheit: Double): Int = when (this) {
        FAHRENHEIT -> fahrenheit
        CELSIUS -> (fahrenheit - 32) * 5.0 / 9.0
        KELVIN -> (fahrenheit - 32) * 5.0 / 9.0 + 273.15
    }.roundToInt()

    fun fromFahrenheit(fahrenheit: Int): Int = fromFahrenheit(fahrenheit.toDouble())

    fun format(fahrenheit: Double): String = "${fromFahrenheit(fahrenheit)} $symbol"

    fun format(fahrenheit: Int): String = "${fromFahrenheit(fahrenheit)} $symbol"
}
