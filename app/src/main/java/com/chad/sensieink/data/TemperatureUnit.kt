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

    /**
     * Kelvin gets one decimal place; F/C stay whole numbers. 1 degree F is
     * only ~0.556 K, so at whole-Kelvin resolution adjacent whole-degree F
     * setpoints frequently round to the *same* displayed K value (73F and
     * 74F both show 296K) - meaning the hero number on Home, which is what
     * the +/- keys visibly act on, can fail to change on a tap. One decimal
     * (0.1K granularity, well under the ~0.556K step) makes every
     * whole-degree F value map to a distinct displayed K value.
     */
    fun format(fahrenheit: Double): String = when (this) {
        KELVIN -> {
            val kelvin = (fahrenheit - 32) * 5.0 / 9.0 + 273.15
            "%.1f %s".format(kelvin, symbol)
        }
        else -> "${fromFahrenheit(fahrenheit)} $symbol"
    }

    fun format(fahrenheit: Int): String = format(fahrenheit.toDouble())
}
