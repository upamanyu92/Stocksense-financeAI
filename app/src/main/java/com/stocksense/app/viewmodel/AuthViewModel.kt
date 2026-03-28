package com.stocksense.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksense.app.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.MessageDigest

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val isCheckingAuth: Boolean = true,
    val loginEmail: String = "",
    val loginPin: String = "",
    val registerName: String = "",
    val registerEmail: String = "",
    val registerPin: String = "",
    val registerConfirmPin: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class AuthViewModel(
    private val prefsManager: UserPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsManager.userPreferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        isLoggedIn = prefs.isLoggedIn,
                        isCheckingAuth = false
                    )
                }
            }
        }
    }

    fun updateLoginEmail(email: String) {
        _uiState.update { it.copy(loginEmail = email, error = null) }
    }

    fun updateLoginPin(pin: String) {
        if (pin.length <= 6) {
            _uiState.update { it.copy(loginPin = pin, error = null) }
        }
    }

    fun updateRegisterName(name: String) {
        _uiState.update { it.copy(registerName = name, error = null) }
    }

    fun updateRegisterEmail(email: String) {
        _uiState.update { it.copy(registerEmail = email, error = null) }
    }

    fun updateRegisterPin(pin: String) {
        if (pin.length <= 6) {
            _uiState.update { it.copy(registerPin = pin, error = null) }
        }
    }

    fun updateRegisterConfirmPin(pin: String) {
        if (pin.length <= 6) {
            _uiState.update { it.copy(registerConfirmPin = pin, error = null) }
        }
    }

    fun login() {
        val state = _uiState.value
        if (state.loginEmail.isBlank()) {
            _uiState.update { it.copy(error = "Email is required") }
            return
        }
        if (state.loginPin.length < 4) {
            _uiState.update { it.copy(error = "PIN must be at least 4 digits") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val hashedPin = hashPin(state.loginPin)
            val valid = prefsManager.validatePin(state.loginEmail, hashedPin)
            if (valid) {
                prefsManager.login(state.loginEmail)
                _uiState.update { it.copy(isLoading = false) }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, error = "Invalid email or PIN")
                }
            }
        }
    }

    fun register() {
        val state = _uiState.value
        if (state.registerName.isBlank()) {
            _uiState.update { it.copy(error = "Name is required") }
            return
        }
        if (state.registerEmail.isBlank()) {
            _uiState.update { it.copy(error = "Email is required") }
            return
        }
        if (state.registerPin.length < 4) {
            _uiState.update { it.copy(error = "PIN must be at least 4 digits") }
            return
        }
        if (state.registerPin != state.registerConfirmPin) {
            _uiState.update { it.copy(error = "PINs do not match") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val hashedPin = hashPin(state.registerPin)
            prefsManager.register(state.registerName, state.registerEmail, hashedPin)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            prefsManager.logout()
        }
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
