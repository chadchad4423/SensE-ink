package com.chad.sensieink.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sensi_eink_prefs")

/** The one thing this app remembers between launches that isn't a secret: the display unit. */
class PreferencesStore(private val context: Context) {

    private object Keys {
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
    }

    val temperatureUnit: Flow<TemperatureUnit> = context.dataStore.data.map { prefs ->
        val stored = prefs[Keys.TEMPERATURE_UNIT]
        TemperatureUnit.entries.firstOrNull { it.name == stored } ?: TemperatureUnit.FAHRENHEIT
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        context.dataStore.edit { prefs -> prefs[Keys.TEMPERATURE_UNIT] = unit.name }
    }
}
