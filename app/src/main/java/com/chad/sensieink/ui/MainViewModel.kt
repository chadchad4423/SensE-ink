package com.chad.sensieink.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chad.sensieink.data.DeviceId
import com.chad.sensieink.data.FanSelection
import com.chad.sensieink.data.OperatingMode
import com.chad.sensieink.data.SensiAuthClient
import com.chad.sensieink.data.SensiRealtimeClient
import com.chad.sensieink.data.ThermostatRepository
import com.chad.sensieink.data.ThermostatUiState
import com.chad.sensieink.data.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * This app is scoped to the single ST55 thermostat described in
 * sensi-client-spec.md (section 2). The derived icd_id is used to pick this
 * unit out of a "state" event's device list; [ThermostatRepository] falls back
 * to the first device present if it doesn't match, since the derivation is
 * unverified against a live payload.
 */
private const val THERMOSTAT_MAC = "34:6F:92:24:A0:A3"

class MainViewModel(private val tokenStore: TokenStore) : ViewModel() {

    private val authClient = SensiAuthClient(tokenStore)
    private val realtimeClient = SensiRealtimeClient(authClient)
    private var repository: ThermostatRepository? = null

    private val _hasToken = MutableStateFlow(tokenStore.hasRefreshToken())
    val hasToken: StateFlow<Boolean> = _hasToken.asStateFlow()

    private val _uiState = MutableStateFlow(ThermostatUiState())
    val uiState: StateFlow<ThermostatUiState> = _uiState.asStateFlow()

    init {
        if (_hasToken.value) {
            startRepository()
        }
    }

    /** Called from the setup screen once the user has pasted a harvested refresh_token. */
    fun saveRefreshToken(token: String) {
        tokenStore.refreshToken = token.trim()
        _hasToken.value = true
        startRepository()
    }

    fun forgetToken() {
        tokenStore.clear()
        _hasToken.value = false
        _uiState.value = ThermostatUiState()
        repository = null
    }

    fun setSetpoint(targetTempF: Int) = repository?.setSetpoint(targetTempF)

    fun setMode(mode: OperatingMode) = repository?.setMode(mode)

    fun setFanSelection(selection: FanSelection) = repository?.setFanSelection(selection)

    private fun startRepository() {
        val icdId = DeviceId.macToIcdId(THERMOSTAT_MAC)
        val repo = ThermostatRepository(realtimeClient, icdId, viewModelScope)
        repository = repo
        viewModelScope.launch {
            repo.uiState.collect { _uiState.value = it }
        }
    }

    companion object {
        fun factory(tokenStore: TokenStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(tokenStore) as T
        }
    }
}
